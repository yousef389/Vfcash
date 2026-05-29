<?php
// =====================================================
// apivfcash.php  — ارفعه على smmrace.space/apivfcash.php
// =====================================================

// ─── إعدادات قاعدة البيانات — عدّلها فقط ───────────
define('DB_HOST', 'localhost');
define('DB_NAME', 'اسم_قاعدة_البيانات');   // ← عدّل
define('DB_USER', 'مستخدم_قاعدة_البيانات'); // ← عدّل
define('DB_PASS', 'كلمة_المرور');           // ← عدّل

// ─── اسم جدول المستخدمين وعمود الرصيد في موقعك ─────
define('USERS_TABLE',  'users');
define('USERS_ID',     'id');
define('USERS_BALANCE','balance');
// ─────────────────────────────────────────────────────

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(200); exit; }

function db(): PDO {
    static $p;
    if (!$p) $p = new PDO(
        'mysql:host='.DB_HOST.';dbname='.DB_NAME.';charset=utf8mb4',
        DB_USER, DB_PASS,
        [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]
    );
    return $p;
}
function out(bool $ok, string $msg, array $d=[], int $c=200): never {
    http_response_code($c);
    echo json_encode(['success'=>$ok,'message'=>$msg,'data'=>$d], JSON_UNESCAPED_UNICODE);
    exit;
}
function log_event(string $ev, ?int $did, ?int $uid, string $det): void {
    try { db()->prepare("INSERT INTO vf_logs(event,deposit_id,user_id,details)VALUES(?,?,?,?)")->execute([$ev,$did,$uid,$det]); } catch(Exception){}
}

$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';

// ── GET: جلب إعدادات صفحة /adr ────────────────────
if ($method === 'GET' && $action === 'settings') {
    $s = db()->query("SELECT * FROM vf_settings WHERE id=1 LIMIT 1")->fetch();
    out(true, 'ok', $s ?: []);
}

// ── POST: حفظ الإعدادات من صفحة /adr ─────────────
if ($method === 'POST' && $action === 'save_settings') {
    $in = json_decode(file_get_contents('php://input'), true) ?: [];
    $phone    = preg_replace('/[^0-9]/', '', $in['phone']    ?? '');
    $storeId  = trim($in['store_id'] ?? '');
    $newToken = trim($in['token']    ?? '');
    if (empty($phone)) out(false, 'رقم الهاتف مطلوب');
    if (empty($newToken)) $newToken = bin2hex(random_bytes(16));
    db()->prepare("INSERT INTO vf_settings(id,phone,store_id,token)VALUES(1,?,?,?) ON DUPLICATE KEY UPDATE phone=VALUES(phone),store_id=VALUES(store_id),token=VALUES(token)")
        ->execute([$phone, $storeId, $newToken]);
    out(true, 'تم الحفظ', ['token' => $newToken]);
}

// ── POST: يستدعيه APK بعد قراءة SMS ───────────────
if ($method === 'POST' && $action === 'verify') {
    $in = json_decode(file_get_contents('php://input'), true) ?: [];

    $token    = trim($in['token']    ?? '');
    $smsPhone = preg_replace('/[^0-9]/', '', $in['phone'] ?? '');
    $smsAmt   = isset($in['amount']) ? (float)$in['amount'] : 0;
    $smsText  = trim($in['sms_text'] ?? '');
    $smsTime  = trim($in['sms_time'] ?? date('Y-m-d H:i:s'));

    // تحقق من token
    $cfg = db()->query("SELECT * FROM vf_settings WHERE id=1 LIMIT 1")->fetch();
    if (!$cfg || !$cfg['enabled']) out(false, 'Service disabled', [], 503);
    if (!hash_equals((string)$cfg['token'], $token)) {
        log_event('auth_fail', null, null, $_SERVER['REMOTE_ADDR']??'');
        out(false, 'Unauthorized', [], 401);
    }
    if (!$smsPhone || !$smsAmt || !$smsText) out(false, 'Missing fields');

    // منع تكرار نفس الرسالة
    $hash = hash('sha256', $smsText);
    $dup = db()->prepare("SELECT id FROM vf_sms_log WHERE sms_hash=? LIMIT 1");
    $dup->execute([$hash]);
    if ($dup->fetch()) out(false, 'SMS already processed');

    // تسجيل SMS
    db()->prepare("INSERT INTO vf_sms_log(sms_hash,sms_text,sms_phone,sms_amount)VALUES(?,?,?,?)")
        ->execute([$hash, $smsText, $smsPhone, $smsAmt]);
    $smsLogId = (int)db()->lastInsertId();

    // البحث عن طلب pending مطابق
    $stmt = db()->prepare(
        "SELECT id,user_id FROM vf_deposits
         WHERE user_phone=? AND amount=? AND status='pending'
         AND created_at > DATE_SUB(NOW(), INTERVAL 30 MINUTE)
         ORDER BY created_at ASC LIMIT 1"
    );
    $stmt->execute([$smsPhone, $smsAmt]);
    $dep = $stmt->fetch();

    if (!$dep) {
        log_event('no_match', null, null, "phone:$smsPhone amount:$smsAmt");
        out(false, 'No matching deposit', ['sms_phone'=>$smsPhone,'sms_amount'=>$smsAmt]);
    }

    $did = (int)$dep['id'];
    $uid = (int)$dep['user_id'];

    // إضافة الرصيد داخل transaction
    db()->beginTransaction();
    try {
        $upd = db()->prepare("UPDATE vf_deposits SET status='approved',sms_text=?,sms_phone=?,sms_amount=?,verified_at=NOW() WHERE id=? AND status='pending'");
        $upd->execute([$smsText, $smsPhone, $smsAmt, $did]);
        if ($upd->rowCount() === 0) { db()->rollBack(); out(false, 'Already processed'); }

        db()->prepare("UPDATE ".USERS_TABLE." SET ".USERS_BALANCE."=".USERS_BALANCE."+? WHERE ".USERS_ID."=?")
            ->execute([$smsAmt, $uid]);

        db()->prepare("UPDATE vf_sms_log SET deposit_id=?,matched=1 WHERE id=?")->execute([$did, $smsLogId]);
        db()->commit();
        log_event('approved', $did, $uid, "amount:$smsAmt");
        out(true, 'Approved', ['deposit_id'=>$did,'user_id'=>$uid,'amount'=>$smsAmt]);
    } catch(Exception $e) {
        db()->rollBack();
        out(false, 'Server error', [], 500);
    }
}

// ── POST: submit deposit من الموقع ────────────────
if ($method === 'POST' && $action === 'submit') {
    $in    = json_decode(file_get_contents('php://input'), true) ?: [];
    $uid   = (int)($in['user_id'] ?? 0);
    $phone = preg_replace('/[^0-9]/', '', $in['phone'] ?? '');
    $amt   = isset($in['amount']) ? (float)$in['amount'] : 0;
    if (!$uid||!$phone||!$amt) out(false,'Missing fields');
    if (!preg_match('/^01[0-9]{9}$/', $phone)) out(false,'رقم الهاتف غير صحيح');

    $cfg = db()->query("SELECT phone FROM vf_settings WHERE id=1")->fetch();
    db()->prepare("INSERT INTO vf_deposits(user_id,user_phone,amount,ip_address)VALUES(?,?,?,?)")
        ->execute([$uid, $phone, $amt, $_SERVER['REMOTE_ADDR']??null]);
    $did = (int)db()->lastInsertId();
    log_event('submitted', $did, $uid, "phone:$phone amount:$amt");
    out(true, 'تم التسجيل', ['deposit_id'=>$did,'wallet_phone'=>$cfg['phone']??'','amount'=>$amt]);
}

// ── GET: حالة الطلب (polling) ──────────────────────
if ($method === 'GET' && $action === 'status') {
    $did = (int)($_GET['deposit_id']??0);
    $uid = (int)($_GET['user_id']??0);
    if (!$did||!$uid) out(false,'Missing params');
    $r = db()->prepare("SELECT status,amount,verified_at FROM vf_deposits WHERE id=? AND user_id=? LIMIT 1");
    $r->execute([$did,$uid]);
    $d = $r->fetch();
    if (!$d) out(false,'Not found');
    $msgs=['pending'=>'قيد الانتظار','approved'=>'✅ تم إضافة الرصيد','rejected'=>'❌ مرفوض'];
    out(true, $msgs[$d['status']]??'—', $d);
}

out(false, 'Unknown action', [], 404);
