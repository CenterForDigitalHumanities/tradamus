#
# Script for initialising a Tradamus database.
#
# Author: Eric Smith 2015-06-17
#

#
# Create a blank database.
#
DROP DATABASE IF EXISTS `tradamus`;
CREATE DATABASE `tradamus` DEFAULT CHARACTER SET `utf8`;
USE `tradamus`;

#
# Table for storing persistent configuration info.
#
CREATE TABLE `config` (
  `setting` varchar(250) NOT NULL,
  `value` varchar(20000) DEFAULT NULL,
  PRIMARY KEY (`setting`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Table for storing message templates.
CREATE TABLE `messages` (
  `id` varchar(250) NOT NULL DEFAULT '',
  `english` varchar(20000) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Table for storing information about repositories (just T-PEN for now)
#
CREATE TABLE `repositories` (
  `prefix` varchar(255) NOT NULL DEFAULT '',
  `type` enum('NONE','TPEN') NOT NULL DEFAULT 'TPEN'
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Create the users table.
#
CREATE TABLE `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `mail` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `hash` varchar(255) NOT NULL,
  `pending` varchar(255) DEFAULT NULL,
  `anonymous` tinyint(1) NOT NULL DEFAULT '0',
  `disabled` tinyint(1) NOT NULL DEFAULT '0',
  `creation` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_login` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_mail` (`mail`)
) ENGINE=InnoDB;

#
# Create the editions table.
# Many more columns to be added.
#
CREATE TABLE `editions` (
   `id` int NOT NULL AUTO_INCREMENT,
   `title` varchar(255) NOT NULL,
   `creator` int NOT NULL,
   `creation` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
   `modification` timestamp NOT NULL,
   PRIMARY KEY (`id`),
   UNIQUE KEY `edition_title` (`title`),
   KEY `edition_creator` (`creator`),
   CONSTRAINT `edition_creator` FOREIGN KEY (`creator`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `permissions` (
  `id` int NOT NULL AUTO_INCREMENT,
  `target_type` enum('EDITION', 'MANIFEST', 'TRANSCRIPTION') NOT NULL,
  `target` int(11) NOT NULL,
  `user` int(11) NOT NULL,
  `role` enum('NONE', 'VIEWER', 'REVIEW_EDITOR', 'CONTRIBUTOR', 'EDITOR', 'OWNER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission` (`target_type`, `target`, `user`),
  KEY `permission_user` (`user`),
  CONSTRAINT `permission_user` FOREIGN KEY (`user`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `outlines` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `edition` int(11) NOT NULL,
  `index` int(11) NOT NULL,
  `title` varchar(255),
  `bounds` int(11),
  PRIMARY KEY (`id`),
  KEY `outline_edition` (`edition`),
  CONSTRAINT `outline_edition` FOREIGN KEY (`edition`) REFERENCES `editions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `parallels` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `outline` int(11) NOT NULL,
  `index` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `parallel_outline` (`outline`),
  CONSTRAINT `parallel_outline` FOREIGN KEY (`outline`) REFERENCES `outlines` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

#
# Create the witnesses table.
#
CREATE TABLE `witnesses` (
   `id` int NOT NULL AUTO_INCREMENT,
   `edition` int NOT NULL,
   `title` varchar(255) NOT NULL,
   `author` varchar(255),
   `siglum` varchar(255),
   `tpen` varchar(255) DEFAULT NULL,
   `tpen_update` timestamp NULL,
   PRIMARY KEY (`id`),
   KEY `witness_edition` (`edition`),
   UNIQUE KEY `witness_title` (`edition`, `title`),
   UNIQUE KEY `witness_siglum` (`edition`, `siglum`),
   CONSTRAINT `witness_edition` FOREIGN KEY (`edition`) REFERENCES `editions` (`id`)
) ENGINE=InnoDB;

#
# The annotations table.
# Lines/rows are now just a special case of annotations.
#
CREATE TABLE `annotations` (
   `id` int NOT NULL AUTO_INCREMENT,
   `start_page` int,      # ID of start page for text anchor
   `start_offset` int,    # Word offset start within start_page
   `end_page` int,        # ID of end page for text anchor
   `end_offset` int,      # Word offset of start within end_page
   `canvas` int,          # Canvas for graphical anchor
   `canvas_fragment` varchar(250), # Typically xywh=, but could be something else
   `target_type` enum('ANNOTATION','EDITION', 'OUTLINE', 'PARALLEL', 'WITNESS'), # Type of target entity
   `target` int,          # ID of target entity
   `target_fragment` varchar(250) DEFAULT NULL, # Optionally identifies sub-region within target
   `type` varchar(250) NOT NULL,
   `content` varchar(20000),
   `attributes` text DEFAULT NULL,
   `tags` varchar(500) DEFAULT NULL,  # Tags which can be used for aggregating annotations
   `modified_by` int,     # ID of user who last modified this annotation
   `approved_by` int,     # ID of user who approved this annotation
   `modification` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
   PRIMARY KEY (`id`)
) ENGINE=InnoDB;

#
# Create the two text-related tables.
#
CREATE TABLE `transcriptions` (
   `id` int NOT NULL AUTO_INCREMENT,
   `witness` int NOT NULL,      # Witness to which the transcription belongs.
   `editor` varchar(255),       # Human who edited the transcription.
   # Other transcription meta-data goes here.
   PRIMARY KEY (`id`),
   KEY `transcription_witness` (`witness`),
   CONSTRAINT `transcription_witness` FOREIGN KEY (`witness`) REFERENCES `witnesses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `pages` (
   `id` int NOT NULL,
   `transcription` int NOT NULL,   # Transcription to which the page belongs.
   `index` int NOT NULL,           # Page number within transcription.
   `canvas` int,                   # Canvas (if any) which corresponds to this page.
   `title` varchar(255),           # Human-friendly title for the page.
   `text` mediumtext DEFAULT NULL,
   PRIMARY KEY (`id`),
   KEY `page_transcription` (`transcription`),
   KEY `page_canvas` (`canvas`),
   CONSTRAINT `page_transcription` FOREIGN KEY (`transcription`) REFERENCES `transcriptions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Create the three image-related tables.
#
CREATE TABLE `manifests` (
   `id` int NOT NULL AUTO_INCREMENT,
   `witness` int NOT NULL,      # Witness to which the manifest belongs.
   PRIMARY KEY (`id`),
   KEY `manifest_witness` (`witness`),
   CONSTRAINT `manifest_witness` FOREIGN KEY (`witness`) REFERENCES `witnesses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `canvasses` (
   `id` int NOT NULL,
   `manifest` int NOT NULL,     # Manifest to which the canvas belongs.
   `index` int NOT NULL,        # Index of canvas within manifest.
   `page` int,                  # Page (if any) which corresponds to this canvas.
   `title` varchar(255),        # Human-friendly title for this canvas.
   `width` int NOT NULL,
   `height` int NOT NULL,
   PRIMARY KEY (`id`),
   KEY `canvas_manifest` (`manifest`),
   KEY `canvas_page` (`page`),
   CONSTRAINT `canvas_manifest` FOREIGN KEY (`manifest`) REFERENCES `manifests` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `images` (
   `id` int NOT NULL AUTO_INCREMENT,
   `canvas` int NOT NULL,           # Canvas to which this image belongs.
   `index` int NOT NULL,            # Index of image within canvas.
   `uri` varchar(1000) NOT NULL,
   `format` enum('NONE', 'JPEG', 'PDF', 'PNG', 'TIFF') NOT NULL,
   `width` int NOT NULL,
   `height` int NOT NULL,
   PRIMARY KEY (`id`),
   KEY `image_canvas` (`canvas`),
   CONSTRAINT `image_canvas` FOREIGN KEY (`canvas`) REFERENCES `canvasses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `activities` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user` int NOT NULL,
  `time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `entity` varchar(255),
  `parent` varchar(255),
  `operation` enum('INSERT', 'UPDATE', 'DELETE', 'VIEW') NOT NULL,
  `content` mediumtext,
  PRIMARY KEY (`id`),
  KEY `activity_user` (`user`),
  CONSTRAINT `activity_user` FOREIGN KEY (`user`) REFERENCES `users` (`id`) ON DELETE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Create the publications table
#
CREATE TABLE `publications` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `edition` int(11) NOT NULL,
  `title` varchar(255) NOT NULL,
  `type` enum('PDF','TEI','DYNAMIC','OAC','XML') NOT NULL,
  `creator` int(11) NOT NULL,
  `creation` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `publication_title` (`title`,`type`),
  KEY `publication_edition` (`edition`),
  KEY `publication_creator` (`creator`),
  CONSTRAINT `publication_creator` FOREIGN KEY (`creator`) REFERENCES `users` (`id`),
  CONSTRAINT `publication_edition` FOREIGN KEY (`edition`) REFERENCES `editions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Create the sections table
#
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

#
# Create the rules table
#
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
# Sources for publication sections
#
CREATE TABLE `sources` (
  `section` int(11) NOT NULL,
  `outline` int(11) NOT NULL,
  KEY `source_section` (`section`),
  KEY `source_source` (`outline`),
  CONSTRAINT `source_section` FOREIGN KEY (`section`) REFERENCES `sections` (`id`) ON DELETE CASCADE,
  CONSTRAINT `source_source` FOREIGN KEY (`outline`) REFERENCES `outlines` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `misspellings` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `dictionary` varchar(255) NOT NULL DEFAULT 'lat',
  `correct` varchar(255) NOT NULL,
  `incorrect` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# For storing responses generated by long-running tasks.
#
CREATE TABLE `deliverables` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `url` text NOT NULL,
  `content_type` varchar(255) DEFAULT NULL,
  `body` longblob,
  `created` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `accessed` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

#
# Hook up any tables which point to each other.
#
ALTER TABLE `pages` ADD CONSTRAINT `page_canvas` FOREIGN KEY (`canvas`) REFERENCES `canvasses` (`id`) ON DELETE SET NULL;
ALTER TABLE `canvasses` ADD CONSTRAINT `canvas_page` FOREIGN KEY (`page`) REFERENCES `pages` (`id`) ON DELETE SET NULL;
