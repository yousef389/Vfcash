-- =====================================================
-- VF Cash SMS System - قاعدة البيانات
-- شغّله مرة واحدة من phpMyAdmin
-- =====================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `vf_settings` (
  `id`           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `phone`        VARCHAR(20)   NOT NULL DEFAULT '01107122194',
  `store_id`     VARCHAR(100)  NOT NULL DEFAULT '',
  `token`        VARCHAR(255)  NOT NULL,
  `enabled`      TINYINT(1)    NOT NULL DEFAULT 1,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- إدراج صف افتراضي بـ token عشوائي
INSERT INTO `vf_settings` (id, phone, store_id, token, enabled)
VALUES (1, '01107122194', '', REPLACE(UUID(),'-',''), 1)
ON DUPLICATE KEY UPDATE id=id;

CREATE TABLE IF NOT EXISTS `vf_deposits` (
  `id`          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `user_id`     INT UNSIGNED  NOT NULL,
  `user_phone`  VARCHAR(20)   NOT NULL,
  `amount`      DECIMAL(10,2) NOT NULL,
  `status`      ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  `sms_text`    TEXT          NULL,
  `sms_phone`   VARCHAR(20)   NULL,
  `sms_amount`  DECIMAL(10,2) NULL,
  `verified_at` DATETIME      NULL,
  `ip_address`  VARCHAR(45)   NULL,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user`   (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_phone`  (`user_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `vf_sms_log` (
  `id`          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `sms_hash`    VARCHAR(64)   NOT NULL,
  `sms_text`    TEXT          NOT NULL,
  `sms_phone`   VARCHAR(20)   NULL,
  `sms_amount`  DECIMAL(10,2) NULL,
  `deposit_id`  INT UNSIGNED  NULL,
  `matched`     TINYINT(1)    NOT NULL DEFAULT 0,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uniq_hash` (`sms_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `vf_logs` (
  `id`         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `event`      VARCHAR(100)  NOT NULL,
  `deposit_id` INT UNSIGNED  NULL,
  `user_id`    INT UNSIGNED  NULL,
  `details`    TEXT          NULL,
  `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
