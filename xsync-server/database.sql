CREATE TABLE `user` (
	`email` VARCHAR(128) NOT NULL COMMENT 'user email' COLLATE 'utf8mb4_0900_ai_ci',
	`password` VARCHAR(128) NOT NULL COMMENT 'user password' COLLATE 'utf8mb4_0900_ai_ci',
	PRIMARY KEY (`email`) USING BTREE,
	CONSTRAINT `CC3` CHECK ((length(`password`) >= 6)),
	CONSTRAINT `CC4` CHECK (regexp_like(`email`,_utf8mb4\'^[^@]+@[^@]+.[^@]{2,}$\'))
)
COLLATE='utf8mb4_0900_ai_ci'
ENGINE=InnoDB
;

CREATE TABLE `chunk` (
	`hash` VARCHAR(128) NOT NULL COMMENT 'hash string of the chunk' COLLATE 'utf8mb4_0900_ai_ci',
	PRIMARY KEY (`hash`) USING BTREE
)
COLLATE='utf8mb4_0900_ai_ci'
ENGINE=InnoDB
;

CREATE TABLE `file` (
	`id` BIGINT NOT NULL AUTO_INCREMENT,
	`filepath` VARCHAR(256) NOT NULL COMMENT 'filepath for given user' COLLATE 'utf8mb4_0900_ai_ci',
	`email` VARCHAR(128) NOT NULL COMMENT 'user email' COLLATE 'utf8mb4_0900_ai_ci',
	`lastModifiedTime` BIGINT NOT NULL COMMENT 'last modified time of the file(timestamp)',
	`chunkCount` BIGINT NOT NULL COMMENT 'number of chunks',
	`size` BIGINT NOT NULL COMMENT 'file size(byte)',
	`hash` VARCHAR(128) NOT NULL COMMENT 'file hash hex string' COLLATE 'utf8mb4_0900_ai_ci',
	PRIMARY KEY (`id`) USING BTREE,
	UNIQUE INDEX `filepath_userId` (`filepath`, `email`) USING BTREE,
	INDEX `FK_file_user` (`email`) USING BTREE,
	CONSTRAINT `FK_file_user` FOREIGN KEY (`email`) REFERENCES `user` (`email`) ON UPDATE NO ACTION ON DELETE NO ACTION,
	CONSTRAINT `CC2` CHECK (((`size` >= 0) and (`chunkCount` >= 0)))
)
COMMENT='metadata of the file'
COLLATE='utf8mb4_0900_ai_ci'
ENGINE=InnoDB
AUTO_INCREMENT=29
;

CREATE TABLE `fc` (
	`id` BIGINT NOT NULL AUTO_INCREMENT,
	`fileId` BIGINT NOT NULL,
	`chunkHash` VARCHAR(128) NOT NULL COMMENT 'chunk hash' COLLATE 'utf8mb4_0900_ai_ci',
	`index` BIGINT NOT NULL COMMENT 'index of the chunk in the file',
	PRIMARY KEY (`id`) USING BTREE,
	UNIQUE INDEX `fileReference_index` (`index`, `fileId`) USING BTREE,
	INDEX `chunkHash` (`chunkHash`) USING BTREE,
	INDEX `index` (`index`) USING BTREE,
	INDEX `fileId` (`fileId`) USING BTREE,
	CONSTRAINT `FK_fc_chunk` FOREIGN KEY (`chunkHash`) REFERENCES `chunk` (`hash`) ON UPDATE NO ACTION ON DELETE CASCADE,
	CONSTRAINT `FK_fc_file` FOREIGN KEY (`fileId`) REFERENCES `file` (`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
	CONSTRAINT `CC1` CHECK ((`index` >= 0))
)
COMMENT='the middle table between chunk and file'
COLLATE='utf8mb4_0900_ai_ci'
ENGINE=InnoDB
AUTO_INCREMENT=488
;
