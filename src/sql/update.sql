#
# 5 - Add modification time field to annotations
#
UPDATE `config` SET `value` = 5 WHERE setting = 'dbVersion';
ALTER TABLE `annotations` ADD `modification` TIMESTAMP NULL  AFTER `approved_by`;
UPDATE `annotations` SET `modification` = NOW();
ALTER TABLE `annotations` CHANGE `modification` `modification` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

#
# 6 - Add tpen link field to witnesses.
#
UPDATE `config` SET `value` = 6 WHERE setting = 'dbVersion';
ALTER TABLE `witnesses` ADD `tpen` VARCHAR(255)  NULL  DEFAULT NULL  AFTER `siglum`;

#
# 7 - Add tpen_update timestamp to witnesses.
#
UPDATE `config` SET `value` = 7 WHERE setting = 'dbVersion';
ALTER TABLE `witnesses` ADD `tpen_update` TIMESTAMP NULL;

#
# 8 - Preliminary tables for Publications module.
#
UPDATE `config` SET `value` = 8 WHERE setting = 'dbVersion';
ALTER TABLE `permissions` CHANGE `target_type` `target_type` ENUM('EDITION','MANIFEST','TRANSCRIPTION','PUBLICATION') NOT NULL;
CREATE TABLE `publications` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `edition` int(11) NOT NULL,
  `title` varchar(255) NOT NULL,
  `type` enum('PDF','TEI','DYNAMIC','OAC','XML') NOT NULL,
  `creator` int(11) NOT NULL,
  `creation` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `modification` timestamp NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `publication_title` (`title`),
  KEY `publication_edition` (`edition`),
  KEY `publication_creator` (`creator`),
  CONSTRAINT `publication_creator` FOREIGN KEY (`creator`) REFERENCES `users` (`id`),
  CONSTRAINT `publication_edition` FOREIGN KEY (`edition`) REFERENCES `editions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `sections` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `publication` int(11) NOT NULL,
  `index` int(11) NOT NULL,
  `title` varchar(255) NOT NULL DEFAULT '',
  `type` enum('TEXT','ENDNOTE','FOOTNOTE','INDEX','TABLE_OF_CONTENTS') NOT NULL DEFAULT 'TEXT',
  `template` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `section_title` (`publication`,`title`),
  CONSTRAINT `section_publication` FOREIGN KEY (`publication`) REFERENCES `publications` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `rules` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `section` int(11) NOT NULL,
  `type` enum('DECORATION','LAYOUT') NOT NULL DEFAULT 'DECORATION',
  `selector` text,
  `action` text,
  PRIMARY KEY (`id`),
  KEY `rule_section` (`section`),
  CONSTRAINT `rule_section` FOREIGN KEY (`section`) REFERENCES `sections` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# 9 - Publication sources table.
#
UPDATE `config` SET `value` = 9 WHERE setting = 'dbVersion';
CREATE TABLE `sources` (
  `section` int(11) NOT NULL,
  `outline` int(11) NOT NULL,
  KEY `source_section` (`section`),
  KEY `source_source` (`outline`),
  CONSTRAINT `source_section` FOREIGN KEY (`section`) REFERENCES `sections` (`id`) ON DELETE CASCADE,
  CONSTRAINT `source_source` FOREIGN KEY (`outline`) REFERENCES `outlines` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# 10 - Allow duplicate titles for publications (as long as the type is different).
#
UPDATE `config` SET `value` = 10 WHERE setting = 'dbVersion';
ALTER TABLE `publications` DROP INDEX `publication_title`;
ALTER TABLE `publications` ADD UNIQUE INDEX `publication_title` (`title`, `type`);


#
# 11 - Tables for orthographic collation
#
UPDATE `config` SET `value` = 11 WHERE `setting` = 'dbVersion';
CREATE TABLE `misspellings` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `dictionary` varchar(255) NOT NULL DEFAULT 'lat',
  `correct` varchar(255) NOT NULL,
  `incorrect` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


#
# 12 - Tables for messages and deliverables (2015-05-29).
#
UPDATE `config` SET `value` = 12 WHERE `setting` = 'dbVersion';
CREATE TABLE `messages` (
  `id` varchar(250) NOT NULL DEFAULT '',
  `english` varchar(20000) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `messages` (`id`, `english`) VALUES
	('COLLATION_COMPLETE', '<!DOCTYPE html>\n<html>\n<head>\n<title>Collation Complete</title>\n</head>\n<body>\nThe collation for <a href=\"%1$s\">%1$s</a> has been completed.<br>\nThe results can be found at <a href=\"%2$s/Tradamus/deliverable/%3$d\">%2$s/Tradamus/deliverable/%3$d</a>.\n</body>\n</html>\n'),
	('CONFIRMATION_SUCCESSFUL', '<!DOCTYPE html>\n<html>\n<head>\n<title>Confirmation Successful</title>\n</head>\n<body>\nAccount confirmed for user %s\n</body>\n</html>\n\n'),
	('DEFERRED_COLLATION', '<!DOCTYPE html>\n<html>\n<head>\n<title>Collation Request Submitted</title>\n</head>\n<body>\nThe collation request for %1$s has been submitted.<br> When the process is complete, the results will be found at <a href=\"%2$s/Tradamus/deliverable/%3$d\">%2$s/Tradamus/deliverable/%3$d</a>.\n</body>\n</html>\n'),
	('INVITATION', '%s has invited you to join Tradamus with access to the edition \"%s\".  Your user name is your email address and your login password is \"%s\" (no quotes).  To confirm this invitation, please visit <%s://%s:%d/Tradamus/users?mail=%s&confirmation=%s>.');

CREATE TABLE `deliverables` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `url` text NOT NULL,
  `body` longtext,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


#
# 13 - Added REVIEW_EDITOR role (2015-06-03).
#
INSERT INTO `messages` (`id`, `english`) VALUES
	('REVIEW_INVITATION', '<!DOCTYPE html>\n<html>\n<head>\n<title>Tradamus Review Invitation</title>\n</head>\n<body>%1$s has invited you to join Tradamus as a reviewer on publication \"%2$s\".  To review this publication go to <a href=\"%3$s://%4$s:%5$d/Tradamus/publication/%6$d/review?key=%7$s\">%3$s://%4$s:%5$d/Tradamus/publication/%6$d/review?key=%7$s</a>\n</body>\n</html>\n');
UPDATE `messages` SET `english` = '<!DOCTYPE html>\n<html>\n<head>\n<title>Tradamus Invitation</title>\n</head>\n<body>%1$s has invited you to join Tradamus with access to the %2$s \"%3$s\".  Your user name is your email address and your login password is \"%4$s\" (no quotes).  To confirm this invitation, please visit <a href=\"%5$s://%6$s:%7$d/Tradamus/users?mail=%8$s&confirmation=%9$s\">%5$s://%6$s:%7$d/Tradamus/users?mail=%8$s&confirmation=%9$s</a>.\n</body>\n</html>\n' WHERE `id` = 'INVITATION';
ALTER TABLE `permissions` CHANGE `role` `role` ENUM('NONE','VIEWER','REVIEW_EDITOR','CONTRIBUTOR','EDITOR','OWNER')  CHARACTER SET utf8  COLLATE utf8_general_ci  NOT NULL  DEFAULT 'NONE';
UPDATE `config` SET `value` = 13 WHERE `setting` = 'dbVersion';


#
# 14 - deliverables table is now also used for PDFs (2015-06-15)
#
INSERT INTO `messages` (`id`, `english`) VALUES
	('DEFERRED_PDF', '<!DOCTYPE html>\n<html>\n<head>\n<title>PDF Request Submitted</title>\n</head>\n<body>\nThe PDF for %1$s is being generated.<br> When the process is complete, the results will be found at <a href=\"%2$s/Tradamus/deliverable/%3$d\">%2$s/Tradamus/deliverable/%3$d</a>.\n</body>\n</html>\n');
ALTER TABLE `deliverables` CHANGE `body` `body` LONGBLOB NULL;
ALTER TABLE `deliverables` ADD `content_type` VARCHAR(255) NULL DEFAULT NULL AFTER `url`;
ALTER TABLE `deliverables` ADD `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `body`;
ALTER TABLE `deliverables` ADD `accessed` TIMESTAMP NULL AFTER `created`;

UPDATE `config` SET `value` = 14 WHERE `setting` = 'dbVersion';
