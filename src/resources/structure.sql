-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: ab
-- ------------------------------------------------------
-- Server version	8.0.46

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

--
-- Table structure for table `billdetails`
--

DROP TABLE IF EXISTS `billdetails`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billdetails` (
  `BillID` int NOT NULL,
  `SNo` int NOT NULL,
  `ItemName` varchar(100) NOT NULL,
  `DesignID` varchar(50) DEFAULT NULL,
  `ordertype` varchar(100) DEFAULT NULL,
  `LabourCost` decimal(10,2) NOT NULL,
  `DullChillaiCost` decimal(10,2) DEFAULT NULL,
  `MeenaColorMeenaCost` decimal(10,2) DEFAULT NULL,
  `RhodiumCost` decimal(10,2) DEFAULT NULL,
  `NagSettingCost` decimal(10,2) DEFAULT NULL,
  `OtherBaseCosts` decimal(10,2) DEFAULT NULL,
  `OtherBaseCostNotes` text,
  `TotalBaseCosting` decimal(10,2) NOT NULL,
  `GoldRate` decimal(10,2) NOT NULL,
  `GoldPlatingWeight` decimal(10,3) NOT NULL,
  `TotalGoldCost` decimal(10,2) NOT NULL,
  `TotalFinalCost` decimal(10,2) NOT NULL,
  `OrderSlipNumber` bigint DEFAULT NULL,
  `itemid_bill` int NOT NULL AUTO_INCREMENT,
  `RawCost` decimal(10,2) NOT NULL DEFAULT '0.00',
  `quantity` int DEFAULT NULL,
  PRIMARY KEY (`itemid_bill`),
  KEY `DesignID` (`DesignID`),
  KEY `fk_orderslip` (`OrderSlipNumber`),
  KEY `fk_billdetails_bill` (`BillID`),
  CONSTRAINT `fk_billdetails_bill` FOREIGN KEY (`BillID`) REFERENCES `bills` (`BillID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=12380 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bills`
--

DROP TABLE IF EXISTS `bills`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bills` (
  `BillID` int NOT NULL AUTO_INCREMENT,
  `BillNumber` int DEFAULT NULL,
  `date` datetime DEFAULT CURRENT_TIMESTAMP,
  `customer_name` varchar(150) DEFAULT NULL,
  `billType` enum('purchase','sale') NOT NULL,
  `drafts` tinyint DEFAULT '0',
  PRIMARY KEY (`BillID`),
  UNIQUE KEY `BillNumber` (`BillNumber`),
  KEY `fk_bill` (`customer_name`),
  CONSTRAINT `fk_bill` FOREIGN KEY (`customer_name`) REFERENCES `customers` (`customer_name`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2934 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customers`
--

DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `customer_id` int NOT NULL AUTO_INCREMENT,
  `customer_name` varchar(255) NOT NULL,
  `balance` decimal(15,2) DEFAULT NULL,
  `openingaccount` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `customer_name` (`customer_name`)
) ENGINE=InnoDB AUTO_INCREMENT=89 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory`
--

DROP TABLE IF EXISTS `inventory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory` (
  `DESIGNID` varchar(50) NOT NULL,
  `TotalQuantity` int NOT NULL,
  `itemname` varchar(50) DEFAULT NULL,
  `price` int DEFAULT NULL,
  `openingstock` int DEFAULT '0',
  `sellPrice` int DEFAULT NULL,
  `SupplierName` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`DESIGNID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_slips`
--

DROP TABLE IF EXISTS `order_slips`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_slips` (
  `slip_type` varchar(50) DEFAULT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `raw_material_price` decimal(10,2) DEFAULT NULL,
  `item_name` varchar(255) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `plating_grams` decimal(10,3) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `other_details` varchar(50) DEFAULT NULL,
  `design_id` varchar(20) DEFAULT NULL,
  `slip_id` int DEFAULT NULL,
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `sno` int DEFAULT NULL,
  `billed_quantity` int DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_slip_id` (`slip_id`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_slip_type` (`slip_type`),
  KEY `fk_design_id` (`design_id`),
  CONSTRAINT `customer_fk` FOREIGN KEY (`customer_name`) REFERENCES `customers` (`customer_name`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_slip_id` FOREIGN KEY (`slip_id`) REFERENCES `order_slips_main` (`slip_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `order_slips_ibfk_1` FOREIGN KEY (`slip_type`) REFERENCES `ordertype` (`type_name`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=483 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_slips_main`
--

DROP TABLE IF EXISTS `order_slips_main`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_slips_main` (
  `slip_id` int NOT NULL AUTO_INCREMENT,
  `slip_type` varchar(50) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `customer_name` varchar(255) DEFAULT NULL,
  `STATUS` int DEFAULT NULL,
  PRIMARY KEY (`slip_id`)
) ENGINE=InnoDB AUTO_INCREMENT=171 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ordertype`
--

DROP TABLE IF EXISTS `ordertype`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ordertype` (
  `type_id` int NOT NULL AUTO_INCREMENT,
  `type_name` varchar(50) NOT NULL,
  PRIMARY KEY (`type_id`),
  UNIQUE KEY `type_name` (`type_name`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `otherdetail`
--

DROP TABLE IF EXISTS `otherdetail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `otherdetail` (
  `id` int NOT NULL,
  `dnumber` int NOT NULL,
  `details` text
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `transactions`
--

DROP TABLE IF EXISTS `transactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transactions` (
  `transaction_id` int NOT NULL AUTO_INCREMENT,
  `billid` int DEFAULT NULL,
  `amount` decimal(15,2) DEFAULT NULL,
  `date` date NOT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`transaction_id`),
  KEY `fk_customer_name_transaction` (`customer_name`),
  KEY `transactions_ibfk_1` (`billid`),
  CONSTRAINT `fk_customer_name_transaction` FOREIGN KEY (`customer_name`) REFERENCES `customers` (`customer_name`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `transactions_ibfk_1` FOREIGN KEY (`billid`) REFERENCES `bills` (`BillID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1308 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `journalentry`
--

DROP TABLE IF EXISTS `journalentry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `journalentry` (
  `id` int NOT NULL AUTO_INCREMENT,
  `transaction_type` enum('purchase','sale','payments','opening_balance','direct') NOT NULL,
  `date` date NOT NULL,
  `accountname` varchar(255) NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `billid` int DEFAULT NULL,
  `transaction_id` int DEFAULT NULL,
  `credit` decimal(15,2) NOT NULL DEFAULT '0.00',
  `debit` decimal(15,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`id`),
  KEY `fk_journalentry_bill` (`billid`),
  KEY `fk_journalentry_transaction` (`transaction_id`),
  CONSTRAINT `fk_journalentry_bill` FOREIGN KEY (`billid`) REFERENCES `bills` (`BillID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_journalentry_transaction` FOREIGN KEY (`transaction_id`) REFERENCES `transactions` (`transaction_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

--
-- Table structure for table `godowns`
--

DROP TABLE IF EXISTS `godowns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `godowns` (
  `godown_id` int NOT NULL AUTO_INCREMENT,
  `parent_godown_id` int DEFAULT NULL,
  `godown_name` varchar(100) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`godown_id`),
  UNIQUE KEY `uq_godowns_name` (`godown_name`),
  CONSTRAINT `fk_godowns_parent` FOREIGN KEY (`parent_godown_id`) REFERENCES `godowns` (`godown_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- A sub-godown is a godowns row whose parent_godown_id points to its parent godown.

-- Stock quantity per inventory design and godown.
DROP TABLE IF EXISTS `godownstock`;
CREATE TABLE `godownstock` (
  `design_id` varchar(50) NOT NULL,
  `godown_id` int NOT NULL,
  `quantity` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`design_id`, `godown_id`),
  KEY `idx_godownstock_godown` (`godown_id`),
  CONSTRAINT `fk_godownstock_design` FOREIGN KEY (`design_id`) REFERENCES `inventory` (`DESIGNID`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_godownstock_godown` FOREIGN KEY (`godown_id`) REFERENCES `godowns` (`godown_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user` varchar(50) NOT NULL,
  `password` varchar(50) DEFAULT NULL,
  `authority` varchar(50) DEFAULT NULL,
  `default_godown_id` int DEFAULT NULL,
  PRIMARY KEY (`user`),
  KEY `fk_users_default_godown` (`default_godown_id`),
  CONSTRAINT `fk_users_default_godown` FOREIGN KEY (`default_godown_id`) REFERENCES `godowns` (`godown_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quickremarks`
--

DROP TABLE IF EXISTS `quickremarks`;
CREATE TABLE `quickremarks` (
  `remarkid` int NOT NULL AUTO_INCREMENT,
  `remark` varchar(1000) NOT NULL,
  PRIMARY KEY (`remarkid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Manufacturing send and receive tables
--
-- A send_lot is the parent movement. The lot's source/destination values are
-- defaults. Each send_items row stores its final route so a rare item-level
-- destination override remains historically accurate.
--

DROP TABLE IF EXISTS `receive_items`;
DROP TABLE IF EXISTS `receive_actions`;
DROP TABLE IF EXISTS `send_items`;
DROP TABLE IF EXISTS `send_packets`;
DROP TABLE IF EXISTS `send_lots`;

CREATE TABLE `send_lots` (
  `send_lot_id` bigint NOT NULL AUTO_INCREMENT,
  `send_date` date NOT NULL,
  `default_source_godown_id` int NOT NULL,
  `default_destination_godown_id` int NOT NULL,
  `lot_remark` varchar(1000) DEFAULT NULL,
  `created_by_user` varchar(50) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`send_lot_id`),
  KEY `idx_send_lots_send_date` (`send_date`),
  KEY `idx_send_lots_default_source` (`default_source_godown_id`),
  KEY `idx_send_lots_default_destination` (`default_destination_godown_id`),
  CONSTRAINT `fk_send_lots_source_godown` FOREIGN KEY (`default_source_godown_id`) REFERENCES `godowns` (`godown_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_send_lots_destination_godown` FOREIGN KEY (`default_destination_godown_id`) REFERENCES `godowns` (`godown_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `send_packets` (
  `send_packet_id` bigint NOT NULL AUTO_INCREMENT,
  `send_lot_id` bigint NOT NULL,
  `packet_number` int NOT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `packet_remark` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`send_packet_id`),
  UNIQUE KEY `uq_send_packets_lot_number` (`send_lot_id`,`packet_number`),
  KEY `idx_send_packets_customer` (`customer_name`),
  CONSTRAINT `fk_send_packets_lot` FOREIGN KEY (`send_lot_id`) REFERENCES `send_lots` (`send_lot_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_send_packets_customer` FOREIGN KEY (`customer_name`) REFERENCES `customers` (`customer_name`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_send_packets_number` CHECK (`packet_number` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `send_items` (
  `send_item_id` bigint NOT NULL AUTO_INCREMENT,
  `parent_send_item_id` bigint DEFAULT NULL,
  `send_packet_id` bigint NOT NULL,
  `item_number` int NOT NULL,
  `source_godown_id` int NOT NULL,
  `destination_godown_id` int NOT NULL,
  `intended_recipient_user` varchar(50) DEFAULT NULL,
  `order_slip_id` int DEFAULT NULL,
  `order_slip_item_id` bigint DEFAULT NULL,
  `design_id` varchar(50) DEFAULT NULL,
  `item_name` varchar(255) NOT NULL,
  `sent_quantity` int NOT NULL,
  `item_remark` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`send_item_id`),
  UNIQUE KEY `uq_send_items_packet_number` (`send_packet_id`,`item_number`),
  KEY `idx_send_items_destination_recipient` (`destination_godown_id`,`intended_recipient_user`),
  KEY `idx_send_items_parent` (`parent_send_item_id`),
  KEY `idx_send_items_source` (`source_godown_id`),
  KEY `idx_send_items_order_slip` (`order_slip_id`),
  KEY `idx_send_items_order_slip_item` (`order_slip_item_id`),
  KEY `idx_send_items_design` (`design_id`),
  CONSTRAINT `fk_send_items_packet` FOREIGN KEY (`send_packet_id`) REFERENCES `send_packets` (`send_packet_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_send_items_parent` FOREIGN KEY (`parent_send_item_id`) REFERENCES `send_items` (`send_item_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_send_items_source_godown` FOREIGN KEY (`source_godown_id`) REFERENCES `godowns` (`godown_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_send_items_destination_godown` FOREIGN KEY (`destination_godown_id`) REFERENCES `godowns` (`godown_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_send_items_order_slip` FOREIGN KEY (`order_slip_id`) REFERENCES `order_slips_main` (`slip_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_send_items_order_slip_item` FOREIGN KEY (`order_slip_item_id`) REFERENCES `order_slips` (`item_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_send_items_design` FOREIGN KEY (`design_id`) REFERENCES `inventory` (`DESIGNID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_send_items_number` CHECK (`item_number` > 0),
  CONSTRAINT `chk_send_items_quantity` CHECK (`sent_quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `receive_actions` (
  `receive_action_id` bigint NOT NULL AUTO_INCREMENT,
  `send_lot_id` bigint NOT NULL,
  `receive_date` date NOT NULL,
  `receiving_godown_id` int NOT NULL,
  `received_by_user` varchar(50) NOT NULL,
  `receive_lot_remark` varchar(1000) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`receive_action_id`),
  KEY `idx_receive_actions_lot` (`send_lot_id`),
  KEY `idx_receive_actions_godown_user` (`receiving_godown_id`,`received_by_user`),
  CONSTRAINT `fk_receive_actions_lot` FOREIGN KEY (`send_lot_id`) REFERENCES `send_lots` (`send_lot_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_receive_actions_godown` FOREIGN KEY (`receiving_godown_id`) REFERENCES `godowns` (`godown_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `receive_items` (
  `receive_item_id` bigint NOT NULL AUTO_INCREMENT,
  `receive_action_id` bigint NOT NULL,
  `send_item_id` bigint NOT NULL,
  `received_quantity` int NOT NULL,
  `receive_item_remark` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`receive_item_id`),
  UNIQUE KEY `uq_receive_items_action_send_item` (`receive_action_id`,`send_item_id`),
  KEY `idx_receive_items_send_item` (`send_item_id`),
  CONSTRAINT `fk_receive_items_action` FOREIGN KEY (`receive_action_id`) REFERENCES `receive_actions` (`receive_action_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_receive_items_send_item` FOREIGN KEY (`send_item_id`) REFERENCES `send_items` (`send_item_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
insert into ordertype(type_name) values('कच्चे का बाकी');
insert into ordertype(type_name) values('कच्चे मे जमा');
insert into ordertype(type_name) values('रेपयरिंग');
--
-- Dumping events for database 'ab'
--

--
-- Dumping routines for database 'ab'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-12 10:12:55
