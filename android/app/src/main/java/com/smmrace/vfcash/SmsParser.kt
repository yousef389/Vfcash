package com.smmrace.vfcash

object SmsParser {
    private val VF_KEYWORDS = listOf(
        "فودافون", "vodafone", "vf cash", "استلمت", "استلام", "تحويل", "received", "transfer"
    )
    private val AMOUNT_RE = listOf(
        Regex("""(?:استلمت|received|مبلغ|amount)[^\d]*([\d]+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d]+(?:[.,]\d{1,2})?)\s*(?:جنيه|egp|le\b)""", RegexOption.IGNORE_CASE),
        Regex("""egp\s*([\d]+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
    )
    private val PHONE_RE = Regex("""(01[0-9]{9})""")

    data class Result(val phone: String, val amount: Double)

    fun isVF(body: String, sender: String, expected: String): Boolean {
        val s = sender.replace(Regex("[^0-9]"), "")
        val e = expected.replace(Regex("[^0-9]"), "")
        if (s.isNotEmpty() && e.isNotEmpty() && s == e) return true
        if (sender.contains("vodafone", true) || sender.contains("VF", true)) return true
        val low = body.lowercase()
        return VF_KEYWORDS.any { low.contains(it.lowercase()) }
    }

    fun parse(body: String): Result? {
        val phone  = PHONE_RE.find(body)?.groupValues?.get(1) ?: return null
        var amount: Double? = null
        for (r in AMOUNT_RE) {
            val v = r.find(body)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            if (v != null && v > 0) { amount = v; break }
        }
        return if (amount != null) Result(phone, amount) else null
    }
}
