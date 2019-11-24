-- --------------------------------------------------------
-- Anfitrião:                    127.0.0.1
-- Versão do servidor:           5.7.14 - MySQL Community Server (GPL)
-- Server OS:                    Win64
-- HeidiSQL Versão:              9.5.0.5196
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;


-- Dumping database structure for dot-files
CREATE DATABASE IF NOT EXISTS `dot-files` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */;
USE `dot-files`;

-- Dumping structure for table dot-files.file
CREATE TABLE IF NOT EXISTS `file` (
  `file_id` int(11) NOT NULL AUTO_INCREMENT,
  `owner_username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_filename` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `local_path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`file_id`),
  KEY `owner_username` (`owner_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Data exporting was unselected.
-- Dumping structure for table dot-files.file_in_transition
CREATE TABLE IF NOT EXISTS `file_in_transition` (
  `username_send` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_id` int(11) NOT NULL,
  `username_receive` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shared_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`username_send`,`username_receive`,`file_id`),
  KEY `FK_file_in_transition_shared_file` (`username_send`,`file_id`),
  KEY `FK_file_in_transition_user` (`username_receive`),
  CONSTRAINT `FK_file_in_transition_shared_file` FOREIGN KEY (`username_send`, `file_id`) REFERENCES `shared_file` (`username`, `file_id`),
  CONSTRAINT `FK_file_in_transition_user` FOREIGN KEY (`username_receive`) REFERENCES `user` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Data exporting was unselected.
-- Dumping structure for table dot-files.key_wallet
CREATE TABLE IF NOT EXISTS `key_wallet` (
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_id` int(11) NOT NULL,
  `shared_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`username`,`file_id`),
  CONSTRAINT `FK__shared_file` FOREIGN KEY (`username`, `file_id`) REFERENCES `shared_file` (`username`, `file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Data exporting was unselected.
-- Dumping structure for table dot-files.log_file
CREATE TABLE IF NOT EXISTS `log_file` (
  `file_id` int(11) NOT NULL,
  `time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('Create','Read','Update Name','Update Content','Delete') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`file_id`,`time`),
  KEY `file_id_username` (`file_id`,`username`),
  KEY `FK_log_file_user` (`username`),
  CONSTRAINT `FK_log_file_file` FOREIGN KEY (`file_id`) REFERENCES `file` (`file_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK_log_file_user` FOREIGN KEY (`username`) REFERENCES `user` (`username`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Data exporting was unselected.
-- Dumping structure for table dot-files.shared_file
CREATE TABLE IF NOT EXISTS `shared_file` (
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_id` int(11) NOT NULL,
  PRIMARY KEY (`username`,`file_id`),
  KEY `FK_shared_file_file` (`file_id`),
  CONSTRAINT `FK_shared_file_file` FOREIGN KEY (`file_id`) REFERENCES `file` (`file_id`),
  CONSTRAINT `FK_shared_file_user` FOREIGN KEY (`username`) REFERENCES `user` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Data exporting was unselected.
-- Dumping structure for table dot-files.user
CREATE TABLE IF NOT EXISTS `user` (
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Data exporting was unselected.
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IF(@OLD_FOREIGN_KEY_CHECKS IS NULL, 1, @OLD_FOREIGN_KEY_CHECKS) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
