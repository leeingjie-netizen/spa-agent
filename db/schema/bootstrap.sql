-- Schema-only baseline; no customer data. Reviewed before deployment.


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;

/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;

/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;

/*!50503 SET NAMES utf8mb4 */;

/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;

/*!40103 SET TIME_ZONE='+00:00' */;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;

/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;

/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;

/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `appointment` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `appointment_no` varchar(32) NOT NULL COMMENT '预约编号',

  `user_id` bigint NOT NULL COMMENT '用户ID',

  `service_id` bigint NOT NULL COMMENT '服务ID',

  `staff_id` bigint NOT NULL COMMENT '技师ID',

  `appointment_date` date NOT NULL COMMENT '预约日期',

  `start_time` time NOT NULL COMMENT '开始时间',

  `end_time` time NOT NULL COMMENT '结束时间',

  `amount` decimal(10,2) NOT NULL COMMENT '预约金额',

  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0待确认 1已预约 2已完成 3已取消',

  `remark` varchar(500) DEFAULT NULL COMMENT '备注',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  `request_id` varchar(64) DEFAULT NULL COMMENT '预约请求唯一标识',

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_appointment_no` (`appointment_no`),

  UNIQUE KEY `uk_request_id` (`request_id`),

  KEY `idx_user_id` (`user_id`),

  KEY `idx_staff_date` (`staff_id`,`appointment_date`),

  KEY `idx_status` (`status`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约表';

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `appointment_slot` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `appointment_id` bigint NOT NULL COMMENT '预约ID',

  `staff_id` bigint NOT NULL COMMENT '技师ID',

  `slot_time` datetime NOT NULL COMMENT '占用时间片',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_staff_slot` (`staff_id`,`slot_time`),

  KEY `idx_appointment_id` (`appointment_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约时间片表';

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `business_day` (

  `work_date` date NOT NULL,

  `closed` tinyint(1) NOT NULL DEFAULT '0',

  `open_time` time DEFAULT NULL,

  `close_time` time DEFAULT NULL,

  `reason` varchar(500) NOT NULL,

  `updated_by` bigint NOT NULL,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`work_date`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `schedule_change_log` (

  `id` bigint NOT NULL,

  `staff_id` bigint NOT NULL,

  `work_date` date NOT NULL,

  `previous_value` text,

  `new_value` text NOT NULL,

  `reason` varchar(500) NOT NULL,

  `operator_id` bigint NOT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  KEY `idx_change_staff_date` (`staff_id`,`work_date`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `schedule_day_lock` (

  `work_date` date NOT NULL,

  PRIMARY KEY (`work_date`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `schedule_exception` (

  `id` bigint NOT NULL,

  `staff_id` bigint NOT NULL,

  `work_date` date NOT NULL,

  `start_time` time NOT NULL,

  `end_time` time NOT NULL,

  `reason` varchar(500) NOT NULL,

  `created_by` bigint NOT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  KEY `idx_exception_staff_date` (`staff_id`,`work_date`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `schedule_exception_revocation` (

  `exception_id` bigint NOT NULL,

  `revoked_by` bigint NOT NULL,

  `reason` varchar(500) NOT NULL,

  `revoked_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`exception_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_account` (

  `id` bigint NOT NULL,

  `user_id` bigint NOT NULL,

  `identity_key` varchar(160) NOT NULL,

  `username` varchar(80) DEFAULT NULL,

  `password_hash` varchar(256) DEFAULT NULL,

  `nickname` varchar(50) NOT NULL,

  `role` varchar(16) NOT NULL DEFAULT 'USER',

  `staff_id` bigint DEFAULT NULL,

  `status` tinyint NOT NULL DEFAULT '1',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_account_identity` (`identity_key`),

  UNIQUE KEY `uk_account_user` (`user_id`),

  UNIQUE KEY `uk_account_username` (`username`),

  UNIQUE KEY `uk_account_staff` (`staff_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_auth_token` (

  `token_hash` char(64) NOT NULL,

  `account_id` bigint NOT NULL,

  `expires_at` datetime NOT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`token_hash`),

  KEY `idx_token_account` (`account_id`),

  KEY `idx_token_expiry` (`expires_at`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_content` (

  `id` bigint NOT NULL,

  `title` varchar(100) NOT NULL,

  `description` varchar(1000) NOT NULL,

  `image_url` varchar(500) DEFAULT NULL,

  `sort_order` int NOT NULL DEFAULT '0',

  `active` tinyint NOT NULL DEFAULT '1',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_coupon_template` (

  `id` bigint NOT NULL,

  `title` varchar(100) NOT NULL,

  `discount_amount` decimal(10,2) NOT NULL,

  `min_amount` decimal(10,2) NOT NULL DEFAULT '0.00',

  `expires_at` datetime NOT NULL,

  `remaining` int NOT NULL,

  `active` tinyint NOT NULL DEFAULT '1',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_loyalty_guard` (

  `user_id` bigint NOT NULL,

  PRIMARY KEY (`user_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_mock_wallet` (

  `user_id` bigint NOT NULL,

  `balance` decimal(12,2) NOT NULL DEFAULT '0.00',

  PRIMARY KEY (`user_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_mock_wallet_ledger` (

  `id` bigint NOT NULL,

  `user_id` bigint NOT NULL,

  `request_id` varchar(100) NOT NULL,

  `amount` decimal(12,2) NOT NULL,

  `balance_after` decimal(12,2) NOT NULL,

  `kind` varchar(20) NOT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_wallet_request` (`user_id`,`request_id`),

  KEY `idx_wallet_user` (`user_id`,`created_at`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_order` (

  `id` bigint NOT NULL,

  `appointment_id` bigint NOT NULL,

  `order_no` varchar(32) NOT NULL,

  `user_id` bigint NOT NULL,

  `amount` decimal(10,2) NOT NULL,

  `status` varchar(32) NOT NULL DEFAULT 'WAITING_PAYMENT',

  `payment_status` varchar(24) NOT NULL DEFAULT 'UNPAID',

  `payment_channel` varchar(16) DEFAULT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  `expires_at` datetime NOT NULL,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_order_appointment` (`appointment_id`),

  UNIQUE KEY `uk_order_no` (`order_no`),

  KEY `idx_order_user` (`user_id`,`created_at`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_order_benefit` (

  `order_id` bigint NOT NULL,

  `user_id` bigint NOT NULL,

  `coupon_id` bigint DEFAULT NULL,

  `points_used` int NOT NULL DEFAULT '0',

  `original_amount` decimal(10,2) NOT NULL,

  `status` varchar(20) NOT NULL DEFAULT 'RESERVED',

  PRIMARY KEY (`order_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_payment` (

  `id` bigint NOT NULL,

  `order_id` bigint NOT NULL,

  `request_id` varchar(64) NOT NULL,

  `kind` varchar(16) NOT NULL,

  `channel` varchar(16) NOT NULL,

  `amount` decimal(10,2) NOT NULL,

  `status` varchar(24) NOT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_payment_request` (`request_id`),

  UNIQUE KEY `uk_payment_order_kind` (`order_id`,`kind`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_points_adjustment` (

  `id` bigint NOT NULL,

  `user_id` bigint NOT NULL,

  `order_id` bigint NOT NULL,

  `points` int NOT NULL,

  `kind` varchar(20) NOT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_points_adjustment` (`order_id`,`kind`),

  KEY `idx_points_adjustment_user` (`user_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_points_entry` (

  `id` bigint NOT NULL,

  `user_id` bigint NOT NULL,

  `order_id` bigint NOT NULL,

  `points` int NOT NULL,

  `reason` varchar(64) NOT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_points_order` (`order_id`),

  KEY `idx_points_user` (`user_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_review` (

  `id` bigint NOT NULL,

  `order_id` bigint NOT NULL,

  `user_id` bigint NOT NULL,

  `rating` tinyint NOT NULL,

  `content` varchar(1000) NOT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_review_order` (`order_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_service` (

  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '服务ID',

  `name` varchar(100) NOT NULL COMMENT '服务名称',

  `description` varchar(500) DEFAULT NULL COMMENT '服务介绍',

  `duration_minutes` int NOT NULL COMMENT '服务时长，单位分钟',

  `price` decimal(10,2) NOT NULL COMMENT '服务价格',

  `cover_url` varchar(255) DEFAULT NULL COMMENT '封面图片',

  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0下架 1上架',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  KEY `idx_status` (`status`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPA服务项目表';

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_user` (

  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',

  `openid` varchar(64) DEFAULT NULL COMMENT '微信openid',

  `nickname` varchar(50) DEFAULT NULL COMMENT '用户昵称',

  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像地址',

  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',

  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0禁用 1正常',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_openid` (`openid`),

  KEY `idx_phone` (`phone`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `spa_user_coupon` (

  `id` bigint NOT NULL,

  `user_id` bigint NOT NULL,

  `template_id` bigint NOT NULL,

  `status` varchar(20) NOT NULL DEFAULT 'AVAILABLE',

  `order_id` bigint DEFAULT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_coupon_claim` (`user_id`,`template_id`),

  KEY `idx_coupon_user` (`user_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `staff` (

  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '技师ID',

  `name` varchar(50) NOT NULL COMMENT '技师姓名',

  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像',

  `introduction` varchar(500) DEFAULT NULL COMMENT '个人介绍',

  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0休息/停用 1正常',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  KEY `idx_status` (`status`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技师表';

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `staff_leave` (

  `id` bigint NOT NULL,

  `staff_id` bigint NOT NULL,

  `work_date` date NOT NULL,

  `all_day` tinyint(1) NOT NULL,

  `start_time` time DEFAULT NULL,

  `end_time` time DEFAULT NULL,

  `reason` varchar(500) NOT NULL,

  `status` varchar(16) NOT NULL DEFAULT 'PENDING',

  `requested_by` bigint NOT NULL,

  `reviewed_by` bigint DEFAULT NULL,

  `review_reason` varchar(500) DEFAULT NULL,

  `reviewed_at` datetime DEFAULT NULL,

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  KEY `idx_leave_staff_date` (`staff_id`,`work_date`,`status`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `staff_schedule` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `staff_id` bigint NOT NULL COMMENT '技师ID',

  `work_date` date NOT NULL COMMENT '工作日期',

  `start_time` time DEFAULT NULL COMMENT '上班时间，休息日为空',

  `end_time` time DEFAULT NULL COMMENT '下班时间，休息日为空',

  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0休息 1上班',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  `source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '排班来源：AUTO自动生成 MANUAL人工维护',

  `break_start_time` time DEFAULT NULL COMMENT '当天午休开始时间',

  `break_end_time` time DEFAULT NULL COMMENT '当天午休结束时间',

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_staff_date` (`staff_id`,`work_date`),

  KEY `idx_work_date` (`work_date`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技师排班表';

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

-- Legacy backup-table structure intentionally excluded from release.

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `staff_service` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `staff_id` bigint NOT NULL COMMENT '技师ID',

  `service_id` bigint NOT NULL COMMENT '服务ID',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_staff_service` (`staff_id`,`service_id`),

  KEY `idx_service_id` (`service_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技师服务能力关联表';

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;

/*!50503 SET character_set_client = utf8mb4 */;

CREATE TABLE `staff_weekly_rule` (

  `id` bigint NOT NULL COMMENT '规则ID',

  `staff_id` bigint NOT NULL COMMENT '技师ID',

  `day_of_week` tinyint NOT NULL COMMENT '星期：1周一至7周日',

  `is_working` tinyint NOT NULL DEFAULT '1' COMMENT '0休息 1上班',

  `start_time` time DEFAULT NULL COMMENT '上班时间',

  `end_time` time DEFAULT NULL COMMENT '下班时间',

  `break_start_time` time DEFAULT NULL COMMENT '午休开始',

  `break_end_time` time DEFAULT NULL COMMENT '午休结束',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_staff_weekday` (`staff_id`,`day_of_week`),

  CONSTRAINT `chk_is_working` CHECK ((`is_working` in (0,1))),

  CONSTRAINT `chk_weekday` CHECK ((`day_of_week` between 1 and 7))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技师每周固定排班规则';

/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;



/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;

/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;

/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;

/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;

/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;

/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
