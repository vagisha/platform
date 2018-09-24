
CREATE TABLE exp.ProtocolInput
(
  RowId INT IDENTITY (1,1) NOT NULL,
  Name NVARCHAR(300) NOT NULL,
  LSID LSIDtype NOT NULL,
  ProtocolId INT NOT NULL,
  Input BIT NOT NULL,

  -- One of 'Material' or 'Data'
  ObjectType NVARCHAR(8) NOT NULL,

  -- DataClassId may be non-null when ObjectType='Data'
  DataClassId INT NULL,
  -- MaterialSourceId may be non-null when ObjectType='Material'
  MaterialSourceId INT NULL,

  CriteriaName NVARCHAR(50) NULL,
  CriteriaConfig NTEXT NULL,
  MinOccurs INT NOT NULL,
  MaxOccurs INT NULL,

  CONSTRAINT PK_ProtocolInput_RowId PRIMARY KEY (RowId),
  CONSTRAINT FK_ProtocolInput_ProtocolId FOREIGN KEY (ProtocolId) REFERENCES exp.Protocol (RowId),
  CONSTRAINT FK_ProtocolInput_DataClassId FOREIGN KEY (DataClassId) REFERENCES exp.DataClass (RowId),
  CONSTRAINT FK_ProtocolInput_MaterialSourceId FOREIGN KEY (MaterialSourceId) REFERENCES exp.MaterialSource (RowId)
);

CREATE INDEX IX_ProtocolInput_ProtocolId ON exp.ProtocolInput (ProtocolId);
CREATE INDEX IX_ProtocolInput_DataClassId ON exp.ProtocolInput (DataClassId);
CREATE INDEX IX_ProtocolInput_MaterialSourceId ON exp.ProtocolInput (MaterialSourceId);


-- Add reference from MaterialInput to the ProtocolInputId that it corresponds to
ALTER TABLE exp.MaterialInput
  ADD ProtocolInputId INT NULL;

ALTER TABLE exp.MaterialInput
  ADD CONSTRAINT FK_MaterialInput_ProtocolInput FOREIGN KEY (ProtocolInputId) REFERENCES exp.ProtocolInput (RowId);

CREATE INDEX IX_MaterialInput_ProtocolInputId ON exp.MaterialInput (ProtocolInputId);


-- Add reference from DataInput to the ProtocolInputId that it corresponds to
ALTER TABLE exp.DataInput
  ADD ProtocolInputId INT NULL;

ALTER TABLE exp.DataInput
  ADD CONSTRAINT FK_DataInput_ProtocolInput FOREIGN KEY (ProtocolInputId) REFERENCES exp.ProtocolInput (RowId);

CREATE INDEX IX_DataInput_ProtocolInputId ON exp.DataInput (ProtocolInputId);

