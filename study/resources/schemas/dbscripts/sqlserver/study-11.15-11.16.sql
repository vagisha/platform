/*
 * Copyright (c) 2011 LabKey Corporation
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

-- Rename from ParticipantClassifications to ParticipantCategory (Singular)
EXEC sp_rename 'study.ParticipantClassifications', 'ParticipantCategory'
GO

-- Drop Foreign Key constraint
ALTER TABLE study.ParticipantGroup
	DROP CONSTRAINT fk_participantClassifications_classificationId
GO

-- Drop Primary Key constraint
ALTER TABLE study.ParticipantCategory
	DROP CONSTRAINT pk_participantClassifications
GO

-- Add Primary Key constraint
ALTER TABLE study.ParticipantCategory
	ADD CONSTRAINT pk_participantCategory PRIMARY KEY (RowId)
GO

-- Rename foreign key column
EXEC sp_rename 'study.ParticipantGroup.ClassificationId', 'CategoryId', 'COLUMN'
GO

-- Add Foreign Key constraint
ALTER TABLE study.ParticipantGroup
	ADD CONSTRAINT fk_participantCategory_categoryId FOREIGN KEY (CategoryId) REFERENCES study.ParticipantCategory (RowId)
GO

-- Add Unique constraint
ALTER TABLE study.ParticipantCategory
	ADD CONSTRAINT uq_Label_Container UNIQUE (Label, Container)
GO
