-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: localhost    Database: raj
-- ------------------------------------------------------
-- Server version	8.0.45

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
  `GoldPlatingWeight` decimal(10,2) NOT NULL,
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
) ENGINE=InnoDB AUTO_INCREMENT=12358 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bills`
--

DROP TABLE IF EXISTS `bills`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bills` (
  `BillID` int NOT NULL AUTO_INCREMENT,
  `date` datetime DEFAULT CURRENT_TIMESTAMP,
  `customer_name` varchar(150) DEFAULT NULL,
  `drafts` tinyint DEFAULT '0',
  PRIMARY KEY (`BillID`),
  KEY `fk_bill` (`customer_name`),
  CONSTRAINT `fk_bill` FOREIGN KEY (`customer_name`) REFERENCES `customers` (`customer_name`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2907 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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
) ENGINE=InnoDB AUTO_INCREMENT=83 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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
  `plating_grams` decimal(10,2) DEFAULT NULL,
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
) ENGINE=InnoDB AUTO_INCREMENT=474 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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
) ENGINE=InnoDB AUTO_INCREMENT=160 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user` varchar(50) NOT NULL,
  `password` varchar(50) DEFAULT NULL,
  `authority` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
insert into ordertype(type_name) values('कच्चे का बाकी');
insert into ordertype(type_name) values('कच्चे मे जमा');
insert into ordertype(type_name) values('रेपयरिंग');
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-10 14:21:08
