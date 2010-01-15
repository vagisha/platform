/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* core-0.00-1.30.sql */

EXEC sp_addtype 'ENTITYID', 'UNIQUEIDENTIFIER'
EXEC sp_addtype 'USERID', 'INT'
GO


EXEC sp_addapprole 'core', 'password'
GO


-- for JDBC Login support, validates email/password,
-- UserId is stored in the Principals table
-- LDAP authenticated users are not in this table

CREATE TABLE core.Logins
(
	Email VARCHAR(255) NOT NULL,
	Crypt VARCHAR(64) NOT NULL,
	Verification VARCHAR(64),

	CONSTRAINT PK_Logins PRIMARY KEY (Email),
)
GO


-- Principals is used for managing security related information
-- It is not used for validating login, that requires an 'external'
-- process, either using SMB, LDAP, JDBC etc (see Logins table)
--
-- It does not contain contact info and other generic user visible data

CREATE TABLE core.Principals
(
	UserId USERID IDENTITY(1000,1),	-- user or group
	Container ENTITYID,				-- NULL for all users, NOT NULL for _ALL_ groups
    OwnerId ENTITYID NULL,
	Name NVARCHAR(64),				-- email (must contain @ and .), group name (no punctuation), or hidden (no @)
	Type CHAR(1),                   -- 'u'=user 'g'=group (NYI 'r'=role, 'm'=managed(module specific)

	CONSTRAINT PK_Principals PRIMARY KEY (UserId),
	CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId)
)
GO


-- maps users to groups
CREATE TABLE core.Members
(
	UserId USERID,
	GroupId USERID,
	
	CONSTRAINT PK_Members PRIMARY KEY (UserId, GroupId)
)
GO


CREATE TABLE core.UsersData
(
	-- standard fields
	_ts TIMESTAMP,
	EntityId ENTITYID DEFAULT NEWID(),
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,
	Owner USERID NULL,

	UserId USERID,

	FirstName NVARCHAR(64) NULL,
	LastName NVARCHAR(64) NULL,
--	Email NVARCHAR(128) NULL,  from Principals table
	Phone NVARCHAR(24) NULL,
	Mobile NVARCHAR(24) NULL,
	Pager NVARCHAR(24) NULL,
	IM NVARCHAR(64)  NULL,
	Description NVARCHAR(255),
	LastLogin DATETIME,

	CONSTRAINT PK_UsersData PRIMARY KEY (UserId),
)
GO


CREATE TABLE core.ACLs
(
	_ts TIMESTAMP,

	Container UNIQUEIDENTIFIER,
	ObjectId UNIQUEIDENTIFIER,
	ACL	VARBINARY(256),
	
	CONSTRAINT UQ_ACLs_ContainerObjectId UNIQUE (Container, ObjectId),
)
GO


CREATE TABLE core.Containers
(
	_ts TIMESTAMP,
	RowId INT IDENTITY(1, 1),
	EntityId ENTITYID DEFAULT NEWID(),
	CreatedBy USERID,
	Created DATETIME,

	Parent ENTITYID,
	Name VARCHAR(255),

	CONSTRAINT UQ_Containers_EntityId UNIQUE (EntityId),
	CONSTRAINT UQ_Containers_Parent_Name UNIQUE (Parent, Name),
	CONSTRAINT FK_Containers_Containers FOREIGN KEY (Parent) REFERENCES core.Containers(EntityId)
)
GO


-- table for all modules
CREATE TABLE core.Modules
(
	Name NVARCHAR(255),
	ClassName NVARCHAR(255),
	InstalledVersion FLOAT,
	Enabled BIT DEFAULT 1,

	CONSTRAINT PK_Modules PRIMARY KEY (Name)
)
GO


-- keep track of sql scripts that have been run in each module
CREATE TABLE core.SqlScripts
(
	-- standard fields
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,

	ModuleName NVARCHAR(100),
	FileName NVARCHAR(300),

	CONSTRAINT PK_SqlScripts PRIMARY KEY (ModuleName, FileName)
)
GO


-- generic table for all attached docs
CREATE TABLE core.Documents
(
	-- standard fields
	_ts TIMESTAMP,
	RowId INT IDENTITY(1,1),
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,
	Owner USERID NULL,

	Container ENTITYID NOT NULL, -- Container of parent, if parent has no ACLs
	Parent ENTITYID NOT NULL,
	DocumentName NVARCHAR(195),		--filename

	DocumentSize INT DEFAULT -1,
	DocumentType VARCHAR(32) DEFAULT 'text/plain',
	Document IMAGE,			-- ContentType LIKE application/*

	CONSTRAINT PK_Documents PRIMARY KEY (RowId),
	CONSTRAINT UQ_Documents_Parent_DocumentName UNIQUE (Parent, DocumentName)
)
GO

/* core-1.30-1.40.sql */

-- Create a log of events (created, verified, password reset, etc.) associated with users
CREATE TABLE core.UserHistory
(
    UserId USERID,
    Date DATETIME,
    Message VARCHAR(500),

	CONSTRAINT PK_UserHistory PRIMARY KEY (UserId, Date),
	CONSTRAINT FK_UserHistory_UserId FOREIGN KEY (UserId) REFERENCES core.Principals(UserId)
)
GO

/* core-1.50-1.60.sql */

ALTER TABLE core.UsersData ALTER COLUMN Phone NVARCHAR(64) NULL
GO
ALTER TABLE core.UsersData ALTER COLUMN Mobile NVARCHAR(64) NULL
GO
ALTER TABLE core.UsersData ALTER COLUMN Pager NVARCHAR(64) NULL
GO

/* core-1.60-1.70.sql */

ALTER TABLE core.UsersData ADD DisplayName NVARCHAR(64) NULL
GO

 DELETE FROM core.UsersData
 WHERE UserID NOT IN
 	(SELECT P1.UserId
  	FROM core.Principals P1
  	WHERE P1.Type = 'u')

UPDATE core.UsersData
SET core.UsersData.DisplayName =
	(SELECT Name
		FROM core.Principals P1
		WHERE P1.Type = 'u'
		AND P1.UserId = core.UsersData.UserId
	)
GO

ALTER TABLE core.UsersData ALTER COLUMN DisplayName NVARCHAR(64) NOT NULL
GO

ALTER TABLE core.UsersData ADD CONSTRAINT UQ_DisplayName UNIQUE (DisplayName)
GO

CREATE TABLE core.Report
(
    RowId INT IDENTITY(1,1) NOT NULL,
    ReportKey NVARCHAR(255),
    CreatedBy USERID,
    ModifiedBy USERID,
    Created DATETIME,
    Modified DATETIME,
    ContainerId ENTITYID NOT NULL,
    EntityId ENTITYID NULL,
    DescriptorXML TEXT,

    CONSTRAINT PK_Report PRIMARY KEY (RowId)
);
GO

/* core-1.70-2.00.sql */

IF EXISTS (SELECT * FROM sysobjects WHERE id = object_id('core.fn_dropifexists') AND sysstat & 0xf = 4)
	DROP PROCEDURE core.fn_dropifexists
GO

CREATE PROCEDURE core.fn_dropifexists (@objname varchar(250), @objschema varchar(50), @objtype varchar(50), @subobjname varchar(250)=NULL)
AS
DECLARE	@ret_code INTEGER
DECLARE	@fullname varchar(300)
SELECT @ret_code = 0
SELECT @fullname = (LOWER(@objschema) + '.' + LOWER(@objname))
IF (UPPER(@objtype)) = 'TABLE'
BEGIN
	IF OBJECTPROPERTY(OBJECT_ID(@fullname), 'IsTable') =1
	BEGIN
		EXEC('DROP TABLE ' + @fullname )
		SELECT @ret_code = 1
	END
		ELSE IF @objname LIKE '##%' AND OBJECT_ID('tempdb.dbo.' + @objname) IS NOT NULL
	BEGIN
		EXEC('DROP TABLE ' + @objname )
		SELECT @ret_code = 1
	END
END
ELSE IF (UPPER(@objtype)) = 'VIEW'
BEGIN
	IF OBJECTPROPERTY(OBJECT_ID(@fullname),'IsView') =1
	BEGIN
		EXEC('DROP VIEW '+@fullname )
		SELECT @ret_code =1
	END
END
ELSE IF (UPPER(@objtype)) = 'INDEX'
BEGIN
	DECLARE @fullername varchar(500)
	SELECT @fullername = @fullname + '.' + @subobjname
	IF INDEXPROPERTY(OBJECT_ID(@fullname),@subobjname, 'IndexID') IS NOT NULL
	BEGIN

		EXEC('DROP INDEX '+ @fullername )
		SELECT @ret_code =1
	END
	ELSE IF EXISTS (SELECT * FROM sysindexes si INNER JOIN sysobjects so
			ON si.id = so.id
			WHERE si.name = @subobjname
			AND so.name <> @objname)
		RAISERROR ('Index does not belong to specified table ' , 16, 1)
END
ELSE IF (UPPER(@objtype)) = 'SCHEMA'
BEGIN
	DECLARE @uid int
	SELECT @uid=uid FROM sysusers WHERE name = LOWER(@objschema) AND IsAppRole=1
	IF @uid IS NOT NULL
	BEGIN
		IF (@objname = '*' )
		BEGIN
			DECLARE @soName sysname, @parent int, @xt char(2), @fkschema sysname
			DECLARE soCursor CURSOR for SELECT so.name, so.xtype, so.parent_obj, su.name
						FROM sysobjects so
						INNER JOIN sysusers su ON (so.uid = su.uid)
						WHERE (so.uid=@uid)
							OR so.id IN (
								SELECT fso.id FROM sysforeignkeys sfk
								INNER JOIN sysobjects fso ON (sfk.constid = fso.id)
								INNER JOIN sysobjects fsr ON (sfk.rkeyid = fsr.id)
								WHERE fsr.uid=@uid)

						ORDER BY (CASE 	WHEN xtype='V' THEN 1
 								WHEN xtype='P' THEN 2
								WHEN xtype='F' THEN 3
								WHEN xtype='U' THEN 4
							  END)


			OPEN soCursor
			FETCH NEXT FROM soCursor INTO @soName, @xt, @parent, @fkschema
			WHILE @@fetch_status = 0
			BEGIN
				SELECT @fullname = @objschema + '.' + @soName
				IF (@xt = 'V')
					EXEC('DROP VIEW ' + @fullname)
				ELSE IF (@xt = 'P')
					EXEC('DROP PROCEDURE ' + @fullname)
				ELSE IF (@xt = 'F')
				BEGIN
					SELECT @fullname = @fkschema + '.' + OBJECT_NAME(@parent)
					EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @soName)
				END
				ELSE IF (@xt = 'U')
					EXEC('DROP TABLE ' + @fullname)

				FETCH NEXT FROM soCursor INTO @soName, @xt, @parent, @fkschema
			END
			CLOSE soCursor
			DEALLOCATE soCursor

			EXEC sp_dropapprole @objschema

			SELECT @ret_code =1
		END
		ELSE IF (@objname = '' OR @objname IS NULL)
		BEGIN
			EXEC sp_dropapprole @objschema
			SELECT @ret_code =1
		END
		ELSE
			RAISERROR ('Invalid @objname for @objtype of SCHEMA   must be either "*" (to drop all dependent objects) or NULL (for dropping empty schema )' , 16, 1)
	END
END
ELSE
	RAISERROR('Invalid object type - %   Valid values are TABLE, VIEW, INDEX, SCHEMA ', 16,1, @objtype )

RETURN @ret_code
GO

EXEC core.fn_dropifexists 'Containers', 'core', 'Index', 'IX_Containers_Parent_Entity'
GO
EXEC core.fn_dropifexists 'Documents', 'core', 'Index', 'IX_Documents_Container'
GO
EXEC core.fn_dropifexists 'Documents', 'core', 'Index', 'IX_Documents_Parent'
GO
CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId)
GO
ALTER TABLE core.Containers ADD CONSTRAINT UQ_Containers_RowId UNIQUE CLUSTERED (RowId)
GO
CREATE INDEX IX_Documents_Container ON core.Documents(Container)
GO
CREATE INDEX IX_Documents_Parent ON core.Documents(Parent)
GO

ALTER TABLE core.Containers ADD
    SortOrder INTEGER NOT NULL DEFAULT 0
GO

ALTER TABLE core.Report
    ADD ReportOwner INT
GO