-- Rebuilding Container index with more columns
SELECT core.fn_dropifexists('Data', 'exp', 'INDEX', 'IX_Data_Container');
SELECT core.fn_dropifexists('Data', 'exp', 'INDEX', 'IX_Data_SourceApplicationId');

CREATE INDEX IX_Data_Container_ClassId_LSID_Name_Modified_RowId ON exp.Data (Container, classId, LSID, Name,  Modified, RowId);
CREATE INDEX IX_Data_SourceApplicationId_RowId ON exp.Data (SourceApplicationId, RowId);