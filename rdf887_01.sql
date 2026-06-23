-- MySQL dump 10.13  Distrib 8.0.40, for Win64 (x86_64)
--
-- Host: localhost    Database: rdf887_01
-- ------------------------------------------------------
-- Server version	8.0.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `container_data`
--

DROP TABLE IF EXISTS `container_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `container_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `container_main_id` bigint NOT NULL COMMENT '對應 container_main.id',
  `ocr_text` varchar(100) DEFAULT NULL COMMENT 'OCR 掃描結果（容器標示）',
  `estimated_quantity` int DEFAULT NULL COMMENT '預估層數',
  `verified_quantity` int DEFAULT NULL COMMENT '驗證層數',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `container_main_id` (`container_main_id`),
  CONSTRAINT `container_data_ibfk_1` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `container_data`
--

LOCK TABLES `container_data` WRITE;
/*!40000 ALTER TABLE `container_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `container_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `container_data_history`
--

DROP TABLE IF EXISTS `container_data_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `container_data_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `origin_id` bigint NOT NULL COMMENT '對應原始 container_data.id',
  `container_main_id` bigint DEFAULT NULL,
  `ocr_text` varchar(100) DEFAULT NULL,
  `estimated_quantity` int DEFAULT NULL,
  `verified_quantity` int DEFAULT NULL,
  `change_type` enum('INSERT','UPDATE','DELETE') NOT NULL,
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `operator` varchar(50) DEFAULT NULL,
  `remark` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `container_data_history`
--

LOCK TABLES `container_data_history` WRITE;
/*!40000 ALTER TABLE `container_data_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `container_data_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `container_main`
--

DROP TABLE IF EXISTS `container_main`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `container_main` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `serial_code` varchar(20) NOT NULL COMMENT '虛擬容器代號（系統唯一編號）',
  `container_type` enum('TRAY','CASSETTE','FOUP','BOX') NOT NULL COMMENT '實體容器類型',
  `container_code` varchar(50) DEFAULT NULL COMMENT '條碼',
  `lot_no` varchar(50) DEFAULT NULL COMMENT '批號',
  `part_no` varchar(50) DEFAULT NULL COMMENT '料號',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `serial_code` (`serial_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1006 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `container_main`
--

LOCK TABLES `container_main` WRITE;
/*!40000 ALTER TABLE `container_main` DISABLE KEYS */;
INSERT INTO `container_main` VALUES (1,'C001','TRAY','BARCODE001','LOT001','PART001','2025-05-15 08:42:20'),(2,'C002','TRAY','BARCODE002','LOT002','PART002','2025-05-15 08:42:20'),(3,'TEST-001','BOX','BARCODE003','LOT003','PART003','2025-05-15 08:42:20');
/*!40000 ALTER TABLE `container_main` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `container_main_history`
--

DROP TABLE IF EXISTS `container_main_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `container_main_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `origin_id` bigint NOT NULL COMMENT '對應原始 container_main.id',
  `serial_code` varchar(20) DEFAULT NULL,
  `container_type` enum('TRAY','CASSETTE','FOUP','BOX') DEFAULT NULL,
  `container_code` varchar(50) DEFAULT NULL,
  `lot_no` varchar(50) DEFAULT NULL,
  `part_no` varchar(50) DEFAULT NULL,
  `change_type` enum('INSERT','UPDATE','DELETE') NOT NULL,
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `operator` varchar(50) DEFAULT NULL,
  `remark` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `container_main_history`
--

LOCK TABLES `container_main_history` WRITE;
/*!40000 ALTER TABLE `container_main_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `container_main_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `crane_request`
--

DROP TABLE IF EXISTS `crane_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crane_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_key` varchar(100) NOT NULL COMMENT '外部識別用唯一鍵',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本控制（遞增）',
  `request_type` enum('INBOUND','OUTBOUND','RELOCATE') NOT NULL,
  `request_source` enum('UI','ASE','SYSTEM') NOT NULL COMMENT '請求來源',
  `source_request_ref` varchar(100) DEFAULT NULL COMMENT '來源系統傳入之請求參考編號',
  `container_main_id` bigint NOT NULL,
  `source_location_id` bigint DEFAULT NULL,
  `target_location_id` bigint DEFAULT NULL,
  `source_location_name` varchar(50) DEFAULT NULL COMMENT '外部傳入的 Source Location Name',
  `target_location_name` varchar(50) DEFAULT NULL COMMENT '外部傳入的 Target Location Name',
  `accepted` char(1) DEFAULT 'N' COMMENT '是否接受請求（Y/N）',
  `accept_time` datetime DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `operator` varchar(50) DEFAULT NULL,
  `request_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `remark` text,
  `raw_payload` text COMMENT '原始請求內容（JSON 格式）',
  `last_updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `request_key` (`request_key`),
  KEY `container_main_id` (`container_main_id`),
  KEY `source_location_id` (`source_location_id`),
  KEY `target_location_id` (`target_location_id`),
  CONSTRAINT `crane_request_ibfk_1` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`),
  CONSTRAINT `crane_request_ibfk_2` FOREIGN KEY (`source_location_id`) REFERENCES `location_point` (`id`),
  CONSTRAINT `crane_request_ibfk_3` FOREIGN KEY (`target_location_id`) REFERENCES `location_point` (`id`),
  CONSTRAINT `crane_request_chk_1` CHECK ((`accepted` in (_utf8mb4'Y',_utf8mb4'N')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crane_request`
--

LOCK TABLES `crane_request` WRITE;
/*!40000 ALTER TABLE `crane_request` DISABLE KEYS */;
INSERT INTO `crane_request` VALUES (1,'REQ-20250515-999',1,'RELOCATE','ASE','AMR-JOB-999',1,103,1,NULL,NULL,'Y','2025-05-15 11:37:48',NULL,'external_operator','2025-05-15 11:37:48','AMR 自動搬運測試','{\"job\":\"test data\"}','2025-05-15 11:37:48');
/*!40000 ALTER TABLE `crane_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `crane_request_history`
--

DROP TABLE IF EXISTS `crane_request_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crane_request_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `origin_id` bigint NOT NULL COMMENT '對應 crane_request.id',
  `request_key` varchar(100) DEFAULT NULL,
  `version` int DEFAULT NULL,
  `request_type` enum('INBOUND','OUTBOUND','RELOCATE') DEFAULT NULL,
  `request_source` enum('UI','ASE','SYSTEM') DEFAULT NULL,
  `source_request_ref` varchar(100) DEFAULT NULL,
  `container_main_id` bigint DEFAULT NULL,
  `source_location_id` bigint DEFAULT NULL,
  `target_location_id` bigint DEFAULT NULL,
  `source_location_name` varchar(50) DEFAULT NULL COMMENT '外部傳入的 Source Location Name',
  `target_location_name` varchar(50) DEFAULT NULL COMMENT '外部傳入的 Target Location Name',
  `accepted` char(1) DEFAULT NULL,
  `accept_time` datetime DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `operator` varchar(50) DEFAULT NULL,
  `request_time` datetime DEFAULT NULL,
  `remark` text,
  `raw_payload` text,
  `change_type` enum('INSERT','UPDATE','DELETE') DEFAULT 'INSERT' COMMENT '異動類型',
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `origin_id` (`origin_id`),
  KEY `container_main_id` (`container_main_id`),
  KEY `source_location_id` (`source_location_id`),
  KEY `target_location_id` (`target_location_id`),
  CONSTRAINT `crane_request_history_ibfk_1` FOREIGN KEY (`origin_id`) REFERENCES `crane_request` (`id`),
  CONSTRAINT `crane_request_history_ibfk_2` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`),
  CONSTRAINT `crane_request_history_ibfk_3` FOREIGN KEY (`source_location_id`) REFERENCES `location_point` (`id`),
  CONSTRAINT `crane_request_history_ibfk_4` FOREIGN KEY (`target_location_id`) REFERENCES `location_point` (`id`),
  CONSTRAINT `crane_request_history_chk_1` CHECK ((`accepted` in (_utf8mb4'Y',_utf8mb4'N')))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crane_request_history`
--

LOCK TABLES `crane_request_history` WRITE;
/*!40000 ALTER TABLE `crane_request_history` DISABLE KEYS */;
INSERT INTO `crane_request_history` VALUES (1,1,'REQ-20250515-999',1,'RELOCATE','ASE','AMR-JOB-999',1,103,1,NULL,NULL,'N',NULL,NULL,'external_operator','2025-05-15 11:37:48','AMR 自動搬運測試','{\"job\":\"test data\"}','INSERT','2025-05-15 11:37:48'),(2,1,'REQ-20250515-999',1,'RELOCATE','ASE','AMR-JOB-999',1,103,1,NULL,NULL,'Y','2025-05-15 11:37:48',NULL,'external_operator','2025-05-15 11:37:48','AMR 自動搬運測試','{\"job\":\"test data\"}','UPDATE','2025-05-15 11:37:48');
/*!40000 ALTER TABLE `crane_request_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `crane_task`
--

DROP TABLE IF EXISTS `crane_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crane_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL,
  `crane_id` varchar(50) DEFAULT NULL,
  `task_type` enum('INBOUND','OUTBOUND','RELOCATE') NOT NULL,
  `task_status` enum('PENDING','DISPATCHED','COMPLETED','FAILED','CANCELLED','SKIPPED') DEFAULT 'PENDING',
  `priority_level` int DEFAULT '0',
  `container_main_id` bigint NOT NULL,
  `source_location_id` bigint DEFAULT NULL,
  `target_location_id` bigint DEFAULT NULL,
  `dispatched_time` datetime DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `cancelled_time` datetime DEFAULT NULL,
  `cancelled_reason` varchar(100) DEFAULT NULL,
  `remark` text,
  `last_updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `request_id` (`request_id`),
  KEY `container_main_id` (`container_main_id`),
  KEY `source_location_id` (`source_location_id`),
  KEY `target_location_id` (`target_location_id`),
  CONSTRAINT `crane_task_ibfk_1` FOREIGN KEY (`request_id`) REFERENCES `crane_request` (`id`),
  CONSTRAINT `crane_task_ibfk_2` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`),
  CONSTRAINT `crane_task_ibfk_3` FOREIGN KEY (`source_location_id`) REFERENCES `location_point` (`id`),
  CONSTRAINT `crane_task_ibfk_4` FOREIGN KEY (`target_location_id`) REFERENCES `location_point` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crane_task`
--

LOCK TABLES `crane_task` WRITE;
/*!40000 ALTER TABLE `crane_task` DISABLE KEYS */;
INSERT INTO `crane_task` VALUES (1,1,'1','RELOCATE','DISPATCHED',0,1,103,1,NULL,NULL,NULL,NULL,'AMR 自動搬運測試','2025-05-15 11:37:56');
/*!40000 ALTER TABLE `crane_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `crane_task_history`
--

DROP TABLE IF EXISTS `crane_task_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crane_task_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `origin_id` bigint NOT NULL COMMENT '對應 crane_task.id',
  `request_id` bigint DEFAULT NULL,
  `crane_id` varchar(50) DEFAULT NULL,
  `task_type` enum('INBOUND','OUTBOUND','RELOCATE') DEFAULT NULL,
  `task_status` enum('PENDING','DISPATCHED','COMPLETED','FAILED','CANCELLED','SKIPPED') DEFAULT NULL,
  `priority_level` int DEFAULT NULL,
  `container_main_id` bigint DEFAULT NULL,
  `source_location_id` bigint DEFAULT NULL,
  `target_location_id` bigint DEFAULT NULL,
  `dispatched_time` datetime DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `cancelled_time` datetime DEFAULT NULL,
  `cancelled_reason` varchar(100) DEFAULT NULL,
  `remark` text,
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `origin_id` (`origin_id`),
  KEY `request_id` (`request_id`),
  KEY `container_main_id` (`container_main_id`),
  KEY `source_location_id` (`source_location_id`),
  KEY `target_location_id` (`target_location_id`),
  CONSTRAINT `crane_task_history_ibfk_1` FOREIGN KEY (`origin_id`) REFERENCES `crane_task` (`id`),
  CONSTRAINT `crane_task_history_ibfk_2` FOREIGN KEY (`request_id`) REFERENCES `crane_request` (`id`),
  CONSTRAINT `crane_task_history_ibfk_3` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`),
  CONSTRAINT `crane_task_history_ibfk_4` FOREIGN KEY (`source_location_id`) REFERENCES `location_point` (`id`),
  CONSTRAINT `crane_task_history_ibfk_5` FOREIGN KEY (`target_location_id`) REFERENCES `location_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crane_task_history`
--

LOCK TABLES `crane_task_history` WRITE;
/*!40000 ALTER TABLE `crane_task_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `crane_task_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gripper_anomaly_log`
--

DROP TABLE IF EXISTS `gripper_anomaly_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gripper_anomaly_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `gripper_id` varchar(50) NOT NULL COMMENT '裝置代號',
  `anomaly_type` enum('TIMEOUT','DUPLICATE_STATE','LOST_PROGRESS') NOT NULL COMMENT '異常類型',
  `description` text COMMENT '詳細異常說明',
  `related_task_id` bigint DEFAULT NULL COMMENT '若異常與任務有關，記錄任務 ID',
  `occurred_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '異常發生時間',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gripper_anomaly_log`
--

LOCK TABLES `gripper_anomaly_log` WRITE;
/*!40000 ALTER TABLE `gripper_anomaly_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `gripper_anomaly_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gripper_request`
--

DROP TABLE IF EXISTS `gripper_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gripper_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_key` varchar(100) NOT NULL COMMENT '外部請求識別碼',
  `version` int NOT NULL DEFAULT '1' COMMENT '請求版本控制',
  `request_type` enum('PICK','MOVE','PLACE') NOT NULL COMMENT '請求動作類型',
  `request_source` enum('UI','SYSTEM') NOT NULL COMMENT '來源系統（人機操作或系統排程）',
  `container_main_id` bigint DEFAULT NULL,
  `source_location_id` bigint DEFAULT NULL COMMENT '來源位置（僅 PICK、MOVE 使用）',
  `target_location_id` bigint DEFAULT NULL COMMENT '目標位置（僅 PLACE、MOVE 使用）',
  `target_height_mm` decimal(6,2) DEFAULT NULL COMMENT '希望執行的目標高度（參考用）',
  `layer_count` int DEFAULT NULL COMMENT '夾取層數（僅 PICK 使用）',
  `accepted` tinyint(1) DEFAULT NULL COMMENT '是否接受請求（NULL=未處理）',
  `accept_time` datetime DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `operator` varchar(50) DEFAULT NULL,
  `request_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `remark` text,
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `request_key` (`request_key`),
  KEY `container_unit_id` (`container_main_id`),
  KEY `source_location_id` (`source_location_id`),
  KEY `target_location_id` (`target_location_id`),
  CONSTRAINT `gripper_request_ibfk_1` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`),
  CONSTRAINT `gripper_request_ibfk_2` FOREIGN KEY (`source_location_id`) REFERENCES `location_point` (`id`),
  CONSTRAINT `gripper_request_ibfk_3` FOREIGN KEY (`target_location_id`) REFERENCES `location_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gripper_request`
--

LOCK TABLES `gripper_request` WRITE;
/*!40000 ALTER TABLE `gripper_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `gripper_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gripper_request_history`
--

DROP TABLE IF EXISTS `gripper_request_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gripper_request_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `origin_id` bigint NOT NULL COMMENT '對應主表 gripper_request.id',
  `request_key` varchar(100) DEFAULT NULL,
  `version` int DEFAULT NULL,
  `request_type` enum('PICK','MOVE','PLACE') DEFAULT NULL,
  `request_source` enum('UI','SYSTEM') DEFAULT NULL,
  `container_main_id` bigint DEFAULT NULL,
  `source_location_id` bigint DEFAULT NULL,
  `target_location_id` bigint DEFAULT NULL,
  `target_height_mm` decimal(6,2) DEFAULT NULL,
  `layer_count` int DEFAULT NULL,
  `accepted` tinyint(1) DEFAULT NULL,
  `accept_time` datetime DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `operator` varchar(50) DEFAULT NULL,
  `request_time` datetime DEFAULT NULL,
  `remark` text,
  `change_type` enum('INSERT','UPDATE','DELETE') NOT NULL,
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `archived_by` varchar(50) DEFAULT NULL COMMENT '紀錄來源（系統或操作人員）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gripper_request_history`
--

LOCK TABLES `gripper_request_history` WRITE;
/*!40000 ALTER TABLE `gripper_request_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `gripper_request_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gripper_status_transition`
--

DROP TABLE IF EXISTS `gripper_status_transition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gripper_status_transition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `gripper_id` varchar(50) NOT NULL COMMENT '裝置代號',
  `from_status` varchar(30) DEFAULT NULL COMMENT '來源狀態（如 IDLE、RUNNING）',
  `to_status` varchar(30) DEFAULT NULL COMMENT '目標狀態（如 RUNNING、DONE）',
  `sub_status` enum('UNKNOWN','MOVING','PICKING','DROPPING') DEFAULT NULL COMMENT 'RUNNING 狀態細分類（子行為）',
  `triggered_by_task_id` bigint DEFAULT NULL COMMENT '若為任務觸發，紀錄來源任務 ID',
  `snapshot_time` datetime DEFAULT NULL COMMENT '對應 PLC snapshot 時間',
  `duration_ms` bigint DEFAULT NULL COMMENT '來源狀態持續時間（毫秒）',
  `changed_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '狀態變更時間',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gripper_status_transition`
--

LOCK TABLES `gripper_status_transition` WRITE;
/*!40000 ALTER TABLE `gripper_status_transition` DISABLE KEYS */;
/*!40000 ALTER TABLE `gripper_status_transition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gripper_task`
--

DROP TABLE IF EXISTS `gripper_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gripper_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL COMMENT '對應 gripper_request.id',
  `request_version` int DEFAULT NULL COMMENT '對應請求版本',
  `gripper_id` varchar(50) NOT NULL COMMENT '執行任務的 Gripper 裝置代號（如 GRIPPER#1）',
  `task_type` enum('PICK','MOVE','PLACE') NOT NULL COMMENT '任務動作類型',
  `task_status` enum('PENDING','DISPATCHED','COMPLETED','FAILED','CANCELLED','SKIPPED') DEFAULT 'PENDING' COMMENT '任務狀態',
  `container_main_id` bigint DEFAULT NULL,
  `source_location_id` bigint DEFAULT NULL COMMENT '來源位置（僅 PICK、MOVE 使用）',
  `target_location_id` bigint DEFAULT NULL COMMENT '目標位置（僅 PLACE、MOVE 使用）',
  `target_height_mm` decimal(6,2) NOT NULL COMMENT '實際執行目標高度（建立時固定）',
  `layer_count` int DEFAULT NULL COMMENT '夾取層數（僅 PICK 使用）',
  `dispatched_time` datetime DEFAULT NULL COMMENT '任務派發時間',
  `completed_time` datetime DEFAULT NULL COMMENT '任務完成時間',
  `cancelled_time` datetime DEFAULT NULL COMMENT '任務取消時間',
  `cancelled_reason` varchar(100) DEFAULT NULL,
  `operator` varchar(50) DEFAULT NULL,
  `remark` text,
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `request_id` (`request_id`),
  KEY `container_unit_id` (`container_main_id`),
  KEY `source_location_id` (`source_location_id`),
  KEY `target_location_id` (`target_location_id`),
  CONSTRAINT `gripper_task_ibfk_1` FOREIGN KEY (`request_id`) REFERENCES `gripper_request` (`id`),
  CONSTRAINT `gripper_task_ibfk_2` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`),
  CONSTRAINT `gripper_task_ibfk_3` FOREIGN KEY (`source_location_id`) REFERENCES `location_point` (`id`),
  CONSTRAINT `gripper_task_ibfk_4` FOREIGN KEY (`target_location_id`) REFERENCES `location_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gripper_task`
--

LOCK TABLES `gripper_task` WRITE;
/*!40000 ALTER TABLE `gripper_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `gripper_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gripper_task_history`
--

DROP TABLE IF EXISTS `gripper_task_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gripper_task_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `origin_id` bigint NOT NULL COMMENT '對應主表 gripper_task.id',
  `gripper_id` varchar(50) DEFAULT NULL,
  `task_type` enum('PICK','MOVE','PLACE') DEFAULT NULL,
  `task_status` enum('PENDING','DISPATCHED','COMPLETED','FAILED','CANCELLED','SKIPPED') DEFAULT NULL,
  `container_main_id` bigint DEFAULT NULL,
  `source_location_id` bigint DEFAULT NULL,
  `target_location_id` bigint DEFAULT NULL,
  `target_height_mm` decimal(6,2) DEFAULT NULL,
  `layer_count` int DEFAULT NULL,
  `dispatched_time` datetime DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `cancelled_time` datetime DEFAULT NULL,
  `cancelled_reason` varchar(100) DEFAULT NULL,
  `operator` varchar(50) DEFAULT NULL,
  `remark` text,
  `change_type` enum('INSERT','UPDATE','DELETE') NOT NULL,
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `archived_by` varchar(50) DEFAULT NULL COMMENT '紀錄來源（系統或操作人員）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gripper_task_history`
--

LOCK TABLES `gripper_task_history` WRITE;
/*!40000 ALTER TABLE `gripper_task_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `gripper_task_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `location_flow`
--

DROP TABLE IF EXISTS `location_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `location_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `container_main_id` bigint NOT NULL,
  `location_point_id` bigint NOT NULL,
  `entry_type` enum('PLC','MANUAL','EXTERNAL','SYSTEM_REBUILD') NOT NULL COMMENT '帳務建立方式',
  `exit_type` enum('NORMAL','MANUAL','FORCE_REMOVED','TIMEOUT','PLC_LOST') DEFAULT NULL COMMENT '帳務離開方式',
  `arrived_time` datetime NOT NULL COMMENT '進入時間',
  `left_time` datetime DEFAULT NULL COMMENT '離開時間（NULL 表示尚未離開）',
  `entry_operator` varchar(50) DEFAULT NULL COMMENT '進帳操作者',
  `exit_operator` varchar(50) DEFAULT NULL COMMENT '出帳操作者',
  `source_task_id` bigint DEFAULT NULL COMMENT '來源任務 ID（如有）',
  `remark` text COMMENT '備註',
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '歸檔時間',
  PRIMARY KEY (`id`),
  KEY `idx_container_time` (`container_main_id`,`arrived_time` DESC),
  KEY `location_point_id` (`location_point_id`),
  CONSTRAINT `location_flow_ibfk_1` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`),
  CONSTRAINT `location_flow_ibfk_2` FOREIGN KEY (`location_point_id`) REFERENCES `location_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `location_flow`
--

LOCK TABLES `location_flow` WRITE;
/*!40000 ALTER TABLE `location_flow` DISABLE KEYS */;
/*!40000 ALTER TABLE `location_flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `location_point`
--

DROP TABLE IF EXISTS `location_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `location_point` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '位置主鍵',
  `zone_code` varchar(10) NOT NULL COMMENT '所屬邏輯區域（如 A/B 倉）',
  `code` varchar(50) NOT NULL COMMENT '位置代碼',
  `name` varchar(100) DEFAULT NULL COMMENT '位置名稱（人性化顯示）',
  `coordinate_x` decimal(10,3) DEFAULT NULL,
  `coordinate_y` decimal(10,3) DEFAULT NULL,
  `coordinate_z` decimal(10,3) DEFAULT NULL,
  `bank` int DEFAULT NULL,
  `bay` int DEFAULT NULL,
  `level` int DEFAULT NULL,
  `location_type` varchar(50) NOT NULL COMMENT '地點類型（如 STORAGE, SITE）',
  `enabled` char(1) NOT NULL,
  `is_occupied` char(1) NOT NULL,
  `is_locked` char(1) NOT NULL,
  `is_reserved` char(1) NOT NULL,
  `lock_reason` varchar(100) DEFAULT NULL,
  `preferred_status` enum('OK','NG','ANY') DEFAULT NULL COMMENT '偏好產品狀態',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=205 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `location_point`
--

LOCK TABLES `location_point` WRITE;
/*!40000 ALTER TABLE `location_point` DISABLE KEYS */;
INSERT INTO `location_point` VALUES (1,'A','010101','AMR#2',NULL,NULL,NULL,1,1,1,'SITE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-05-15 08:50:47'),(2,'A','010102','',NULL,NULL,NULL,1,1,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 17:52:43'),(3,'A','010103','',NULL,NULL,NULL,1,1,3,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 17:52:43'),(4,'A','010104','',NULL,NULL,NULL,1,1,4,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 17:52:43'),(5,'A','010105','',NULL,NULL,NULL,1,1,5,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 17:52:43'),(6,'A','010106','',NULL,NULL,NULL,1,1,6,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 17:52:43'),(7,'A','010201','',NULL,NULL,NULL,1,2,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(8,'A','010202','',NULL,NULL,NULL,1,2,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(9,'A','010203','',NULL,NULL,NULL,1,2,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(10,'A','010204','',NULL,NULL,NULL,1,2,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(11,'A','010205','',NULL,NULL,NULL,1,2,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(12,'A','010206','',NULL,NULL,NULL,1,2,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(13,'A','010301','',NULL,NULL,NULL,1,3,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(14,'A','010302','',NULL,NULL,NULL,1,3,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(15,'A','010303','',NULL,NULL,NULL,1,3,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(16,'A','010304','',NULL,NULL,NULL,1,3,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(17,'A','010305','',NULL,NULL,NULL,1,3,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(18,'A','010306','',NULL,NULL,NULL,1,3,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(19,'A','010401','',NULL,NULL,NULL,1,4,1,'SITE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 17:38:14'),(20,'A','010402','',NULL,NULL,NULL,1,4,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 17:51:19'),(21,'A','010403','',NULL,NULL,NULL,1,4,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(22,'A','010404','',NULL,NULL,NULL,1,4,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(23,'A','010405','',NULL,NULL,NULL,1,4,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(24,'A','010406','',NULL,NULL,NULL,1,4,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 16:52:43'),(25,'A','010501','',NULL,NULL,NULL,1,5,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:43','2025-04-30 17:51:19'),(26,'A','010502','',NULL,NULL,NULL,1,5,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(27,'A','010503','',NULL,NULL,NULL,1,5,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(28,'A','010504','',NULL,NULL,NULL,1,5,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(29,'A','010505','',NULL,NULL,NULL,1,5,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(30,'A','010506','',NULL,NULL,NULL,1,5,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(31,'A','010601','',NULL,NULL,NULL,1,6,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(32,'A','010602','',NULL,NULL,NULL,1,6,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(33,'A','010603','',NULL,NULL,NULL,1,6,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(34,'A','010604','',NULL,NULL,NULL,1,6,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(35,'A','010605','',NULL,NULL,NULL,1,6,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(36,'A','010606','',NULL,NULL,NULL,1,6,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(37,'A','010701','',NULL,NULL,NULL,1,7,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(38,'A','010702','',NULL,NULL,NULL,1,7,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(39,'A','010703','',NULL,NULL,NULL,1,7,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(40,'A','010704','',NULL,NULL,NULL,1,7,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(41,'A','010705','',NULL,NULL,NULL,1,7,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(42,'A','010706','',NULL,NULL,NULL,1,7,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(43,'A','010801','',NULL,NULL,NULL,1,8,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(44,'A','010802','',NULL,NULL,NULL,1,8,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(45,'A','010803','',NULL,NULL,NULL,1,8,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(46,'A','010804','',NULL,NULL,NULL,1,8,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(47,'A','010805','',NULL,NULL,NULL,1,8,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(48,'A','010806','',NULL,NULL,NULL,1,8,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(49,'A','010901','',NULL,NULL,NULL,1,9,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(50,'A','010902','',NULL,NULL,NULL,1,9,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(51,'A','010903','',NULL,NULL,NULL,1,9,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(52,'A','010904','',NULL,NULL,NULL,1,9,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(53,'A','010905','',NULL,NULL,NULL,1,9,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(54,'A','010906','',NULL,NULL,NULL,1,9,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(55,'A','011001','',NULL,NULL,NULL,1,10,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(56,'A','011002','',NULL,NULL,NULL,1,10,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(57,'A','011003','',NULL,NULL,NULL,1,10,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(58,'A','011004','',NULL,NULL,NULL,1,10,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(59,'A','011005','',NULL,NULL,NULL,1,10,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(60,'A','011006','',NULL,NULL,NULL,1,10,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(61,'A','011101','',NULL,NULL,NULL,1,11,1,'SITE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:38:14'),(62,'A','011102','',NULL,NULL,NULL,1,11,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(63,'A','011103','',NULL,NULL,NULL,1,11,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(64,'A','011104','',NULL,NULL,NULL,1,11,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(65,'A','011105','',NULL,NULL,NULL,1,11,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(66,'A','011106','',NULL,NULL,NULL,1,11,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(67,'A','011201','',NULL,NULL,NULL,1,12,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(68,'A','011202','',NULL,NULL,NULL,1,12,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(69,'A','011203','',NULL,NULL,NULL,1,12,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(70,'A','011204','',NULL,NULL,NULL,1,12,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(71,'A','011205','',NULL,NULL,NULL,1,12,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(72,'A','011206','',NULL,NULL,NULL,1,12,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(73,'A','011301','',NULL,NULL,NULL,1,13,1,'SITE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:38:14'),(74,'A','011302','',NULL,NULL,NULL,1,13,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(75,'A','011303','',NULL,NULL,NULL,1,13,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(76,'A','011304','',NULL,NULL,NULL,1,13,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(77,'A','011305','',NULL,NULL,NULL,1,13,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(78,'A','011306','',NULL,NULL,NULL,1,13,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(79,'A','011401','',NULL,NULL,NULL,1,14,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(80,'A','011402','',NULL,NULL,NULL,1,14,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(81,'A','011403','',NULL,NULL,NULL,1,14,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(82,'A','011404','',NULL,NULL,NULL,1,14,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(83,'A','011405','',NULL,NULL,NULL,1,14,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(84,'A','011406','',NULL,NULL,NULL,1,14,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(85,'A','011501','',NULL,NULL,NULL,1,15,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(86,'A','011502','',NULL,NULL,NULL,1,15,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:51:19'),(87,'A','011503','',NULL,NULL,NULL,1,15,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(88,'A','011504','',NULL,NULL,NULL,1,15,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(89,'A','011505','',NULL,NULL,NULL,1,15,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(90,'A','011506','',NULL,NULL,NULL,1,15,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(91,'A','011601','',NULL,NULL,NULL,1,16,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(92,'A','011602','',NULL,NULL,NULL,1,16,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(93,'A','011603','',NULL,NULL,NULL,1,16,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(94,'A','011604','',NULL,NULL,NULL,1,16,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(95,'A','011605','',NULL,NULL,NULL,1,16,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(96,'A','011606','',NULL,NULL,NULL,1,16,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(97,'A','011701','',NULL,NULL,NULL,1,17,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(98,'A','011702','',NULL,NULL,NULL,1,17,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(99,'A','011703','',NULL,NULL,NULL,1,17,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(100,'A','011704','',NULL,NULL,NULL,1,17,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(101,'A','011705','',NULL,NULL,NULL,1,17,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(102,'A','011706','',NULL,NULL,NULL,1,17,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(103,'A','020101','AMR#1',NULL,NULL,NULL,2,1,1,'SITE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-05-15 08:50:47'),(104,'A','020102','',NULL,NULL,NULL,2,1,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:54:00'),(105,'A','020103','',NULL,NULL,NULL,2,1,3,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:54:00'),(106,'A','020104','',NULL,NULL,NULL,2,1,4,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:54:00'),(107,'A','020105','',NULL,NULL,NULL,2,1,5,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:54:00'),(108,'A','020106','',NULL,NULL,NULL,2,1,6,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 17:54:00'),(109,'A','020201','',NULL,NULL,NULL,2,2,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(110,'A','020202','',NULL,NULL,NULL,2,2,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(111,'A','020203','',NULL,NULL,NULL,2,2,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(112,'A','020204','',NULL,NULL,NULL,2,2,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(113,'A','020205','',NULL,NULL,NULL,2,2,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(114,'A','020206','',NULL,NULL,NULL,2,2,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(115,'A','020301','',NULL,NULL,NULL,2,3,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(116,'A','020302','',NULL,NULL,NULL,2,3,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(117,'A','020303','',NULL,NULL,NULL,2,3,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(118,'A','020304','',NULL,NULL,NULL,2,3,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(119,'A','020305','',NULL,NULL,NULL,2,3,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(120,'A','020306','',NULL,NULL,NULL,2,3,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(121,'A','020401','',NULL,NULL,NULL,2,4,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(122,'A','020402','',NULL,NULL,NULL,2,4,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(123,'A','020403','',NULL,NULL,NULL,2,4,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(124,'A','020404','',NULL,NULL,NULL,2,4,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(125,'A','020405','',NULL,NULL,NULL,2,4,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(126,'A','020406','',NULL,NULL,NULL,2,4,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(127,'A','020501','',NULL,NULL,NULL,2,5,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(128,'A','020502','',NULL,NULL,NULL,2,5,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(129,'A','020503','',NULL,NULL,NULL,2,5,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(130,'A','020504','',NULL,NULL,NULL,2,5,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(131,'A','020505','',NULL,NULL,NULL,2,5,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(132,'A','020506','',NULL,NULL,NULL,2,5,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(133,'A','020601','',NULL,NULL,NULL,2,6,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(134,'A','020602','',NULL,NULL,NULL,2,6,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(135,'A','020603','',NULL,NULL,NULL,2,6,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(136,'A','020604','',NULL,NULL,NULL,2,6,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(137,'A','020605','',NULL,NULL,NULL,2,6,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(138,'A','020606','',NULL,NULL,NULL,2,6,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(139,'A','020701','',NULL,NULL,NULL,2,7,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(140,'A','020702','',NULL,NULL,NULL,2,7,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(141,'A','020703','',NULL,NULL,NULL,2,7,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(142,'A','020704','',NULL,NULL,NULL,2,7,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(143,'A','020705','',NULL,NULL,NULL,2,7,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(144,'A','020706','',NULL,NULL,NULL,2,7,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(145,'A','020801','',NULL,NULL,NULL,2,8,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(146,'A','020802','',NULL,NULL,NULL,2,8,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(147,'A','020803','',NULL,NULL,NULL,2,8,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(148,'A','020804','',NULL,NULL,NULL,2,8,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(149,'A','020805','',NULL,NULL,NULL,2,8,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(150,'A','020806','',NULL,NULL,NULL,2,8,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(151,'A','020901','',NULL,NULL,NULL,2,9,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(152,'A','020902','',NULL,NULL,NULL,2,9,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(153,'A','020903','',NULL,NULL,NULL,2,9,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(154,'A','020904','',NULL,NULL,NULL,2,9,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(155,'A','020905','',NULL,NULL,NULL,2,9,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(156,'A','020906','',NULL,NULL,NULL,2,9,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(157,'A','021001','',NULL,NULL,NULL,2,10,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(158,'A','021002','',NULL,NULL,NULL,2,10,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(159,'A','021003','',NULL,NULL,NULL,2,10,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(160,'A','021004','',NULL,NULL,NULL,2,10,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(161,'A','021005','',NULL,NULL,NULL,2,10,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(162,'A','021006','',NULL,NULL,NULL,2,10,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(163,'A','021101','',NULL,NULL,NULL,2,11,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(164,'A','021102','',NULL,NULL,NULL,2,11,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(165,'A','021103','',NULL,NULL,NULL,2,11,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(166,'A','021104','',NULL,NULL,NULL,2,11,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(167,'A','021105','',NULL,NULL,NULL,2,11,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(168,'A','021106','',NULL,NULL,NULL,2,11,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(169,'A','021201','',NULL,NULL,NULL,2,12,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(170,'A','021202','',NULL,NULL,NULL,2,12,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(171,'A','021203','',NULL,NULL,NULL,2,12,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(172,'A','021204','',NULL,NULL,NULL,2,12,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(173,'A','021205','',NULL,NULL,NULL,2,12,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(174,'A','021206','',NULL,NULL,NULL,2,12,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(175,'A','021301','',NULL,NULL,NULL,2,13,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(176,'A','021302','',NULL,NULL,NULL,2,13,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(177,'A','021303','',NULL,NULL,NULL,2,13,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(178,'A','021304','',NULL,NULL,NULL,2,13,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:44','2025-04-30 16:52:44'),(179,'A','021305','',NULL,NULL,NULL,2,13,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(180,'A','021306','',NULL,NULL,NULL,2,13,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(181,'A','021401','',NULL,NULL,NULL,2,14,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(182,'A','021402','',NULL,NULL,NULL,2,14,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(183,'A','021403','',NULL,NULL,NULL,2,14,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(184,'A','021404','',NULL,NULL,NULL,2,14,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(185,'A','021405','',NULL,NULL,NULL,2,14,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(186,'A','021406','',NULL,NULL,NULL,2,14,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(187,'A','021501','',NULL,NULL,NULL,2,15,1,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(188,'A','021502','',NULL,NULL,NULL,2,15,2,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(189,'A','021503','',NULL,NULL,NULL,2,15,3,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(190,'A','021504','',NULL,NULL,NULL,2,15,4,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(191,'A','021505','',NULL,NULL,NULL,2,15,5,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(192,'A','021506','',NULL,NULL,NULL,2,15,6,'STORAGE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 16:52:45'),(193,'A','021601','',NULL,NULL,NULL,2,16,1,'SITE','Y','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:02:35'),(194,'A','021602','',NULL,NULL,NULL,2,16,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(195,'A','021603','',NULL,NULL,NULL,2,16,3,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(196,'A','021604','',NULL,NULL,NULL,2,16,4,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(197,'A','021605','',NULL,NULL,NULL,2,16,5,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(198,'A','021606','',NULL,NULL,NULL,2,16,6,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(199,'A','021701','',NULL,NULL,NULL,2,17,1,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(200,'A','021702','',NULL,NULL,NULL,2,17,2,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(201,'A','021703','',NULL,NULL,NULL,2,17,3,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(202,'A','021704','',NULL,NULL,NULL,2,17,4,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(203,'A','021705','',NULL,NULL,NULL,2,17,5,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12'),(204,'A','021706','',NULL,NULL,NULL,2,17,6,'STORAGE','N','N','N','N',NULL,'ANY','2025-04-30 16:52:45','2025-04-30 17:57:12');
/*!40000 ALTER TABLE `location_point` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `location_tracking`
--

DROP TABLE IF EXISTS `location_tracking`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `location_tracking` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `container_main_id` bigint NOT NULL,
  `location_point_id` bigint NOT NULL,
  `arrived_time` datetime NOT NULL COMMENT '抵達時間（建帳時間）',
  `last_verified_time` datetime DEFAULT NULL COMMENT '最後一次驗證位置的時間（來自 PLC 或人工）',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後異動時間',
  `source_type` enum('PLC','MANUAL','SYSTEM') NOT NULL COMMENT '位置建立來源（對應 location_flow.entry_type）',
  `flow_id` bigint DEFAULT NULL COMMENT '來源 flow 紀錄 ID（參考用途，不加 FK）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_container_main` (`container_main_id`),
  KEY `location_point_id` (`location_point_id`),
  CONSTRAINT `location_tracking_ibfk_1` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`),
  CONSTRAINT `location_tracking_ibfk_2` FOREIGN KEY (`location_point_id`) REFERENCES `location_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `location_tracking`
--

LOCK TABLES `location_tracking` WRITE;
/*!40000 ALTER TABLE `location_tracking` DISABLE KEYS */;
/*!40000 ALTER TABLE `location_tracking` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `permissions`
--

DROP TABLE IF EXISTS `permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `description` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `permissions`
--

LOCK TABLES `permissions` WRITE;
/*!40000 ALTER TABLE `permissions` DISABLE KEYS */;
INSERT INTO `permissions` VALUES (1,'View','查看資料'),(2,'Edit','編輯資料'),(3,'Delete','刪除資料'),(4,'Manage Equipment','管理設備，包括配置、啟動和停止'),(5,'System Debugging','進行系統調試與監控'),(6,'View Detailed Reports','查看操作報告');
/*!40000 ALTER TABLE `permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_data`
--

DROP TABLE IF EXISTS `product_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_main_id` bigint NOT NULL COMMENT '對應 product_main.id',
  `ocr_text` varchar(100) DEFAULT NULL COMMENT '單片 OCR 結果',
  `layer_index` int DEFAULT NULL COMMENT '所在容器中的層數索引（從下至上）',
  `quality_check_result` enum('OK','NG','UNKNOWN') DEFAULT 'UNKNOWN' COMMENT '異物檢結果',
  `is_lid` tinyint(1) DEFAULT '0' COMMENT '是否為上蓋片',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `product_main_id` (`product_main_id`),
  CONSTRAINT `product_data_ibfk_1` FOREIGN KEY (`product_main_id`) REFERENCES `product_main` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_data`
--

LOCK TABLES `product_data` WRITE;
/*!40000 ALTER TABLE `product_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_data_history`
--

DROP TABLE IF EXISTS `product_data_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_data_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `origin_id` bigint NOT NULL COMMENT '對應原始 product_data.id',
  `product_main_id` bigint DEFAULT NULL,
  `ocr_text` varchar(100) DEFAULT NULL,
  `layer_index` int DEFAULT NULL,
  `quality_check_result` enum('OK','NG','UNKNOWN') DEFAULT NULL,
  `is_lid` tinyint(1) DEFAULT '0',
  `change_type` enum('INSERT','UPDATE','DELETE') NOT NULL,
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `operator` varchar(50) DEFAULT NULL,
  `remark` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_data_history`
--

LOCK TABLES `product_data_history` WRITE;
/*!40000 ALTER TABLE `product_data_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_data_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_main`
--

DROP TABLE IF EXISTS `product_main`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_main` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `serial_code` varchar(20) NOT NULL COMMENT '虛擬產品代號（系統唯一流水編號）',
  `container_main_id` bigint NOT NULL COMMENT '所屬容器主鍵（container_main.id）',
  `product_code` varchar(50) DEFAULT NULL COMMENT '條碼',
  `lot_no` varchar(50) DEFAULT NULL COMMENT '批號',
  `part_no` varchar(50) DEFAULT NULL COMMENT '料號',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `serial_code` (`serial_code`),
  KEY `container_main_id` (`container_main_id`),
  CONSTRAINT `product_main_ibfk_1` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_main`
--

LOCK TABLES `product_main` WRITE;
/*!40000 ALTER TABLE `product_main` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_main` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_main_history`
--

DROP TABLE IF EXISTS `product_main_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_main_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `origin_id` bigint NOT NULL COMMENT '對應原始 product_main.id',
  `serial_code` varchar(20) DEFAULT NULL,
  `container_main_id` bigint DEFAULT NULL,
  `product_code` varchar(50) DEFAULT NULL,
  `lot_no` varchar(50) DEFAULT NULL,
  `part_no` varchar(50) DEFAULT NULL,
  `change_type` enum('INSERT','UPDATE','DELETE') NOT NULL,
  `archived_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `operator` varchar(50) DEFAULT NULL,
  `remark` text,
  PRIMARY KEY (`id`),
  KEY `container_main_id` (`container_main_id`),
  CONSTRAINT `product_main_history_ibfk_1` FOREIGN KEY (`container_main_id`) REFERENCES `container_main` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_main_history`
--

LOCK TABLES `product_main_history` WRITE;
/*!40000 ALTER TABLE `product_main_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_main_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role_permissions`
--

DROP TABLE IF EXISTS `role_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permissions` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `permission_id` (`permission_id`),
  CONSTRAINT `role_permissions_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
  CONSTRAINT `role_permissions_ibfk_2` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_permissions`
--

LOCK TABLES `role_permissions` WRITE;
/*!40000 ALTER TABLE `role_permissions` DISABLE KEYS */;
INSERT INTO `role_permissions` VALUES (1,1),(2,1),(3,1),(4,1),(1,2),(2,2),(3,2),(1,3),(1,4),(2,4),(1,5),(2,5),(1,6),(2,6);
/*!40000 ALTER TABLE `role_permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `description` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (1,'Admin','系統管理員，擁有所有權限'),(2,'Engineer','工程師，擁有比操作員更多的功能'),(3,'Operator','操作員，負責日常操作'),(4,'Inspector','檢查員，負責檢查工作');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role_id` bigint NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `users_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'czkuo','1192',1,'2025-05-06 00:27:25','2025-05-06 00:27:25');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-05-21  9:06:42
