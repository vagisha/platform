/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.data;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.beehive.netui.pageflow.Forward;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.api.util.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class SchemaTableInfo implements TableInfo
{
    private static Logger _log = Logger.getLogger(SchemaTableInfo.class);

    String name;
    String title = null;
    String titleColumn = null;
    protected List<String> _pkColumnNames = new ArrayList<String>();
    List<ColumnInfo> _pkColumns;
    protected ArrayList<ColumnInfo> columns = new ArrayList<ColumnInfo>();
    protected Map<String, ColumnInfo> colMap = null;
    DbSchema parentSchema;
    private int _tableType = TABLE_TYPE_NOT_IN_DB;
    private String _versionColumnName = null;
    private String metaDataName = null;
    private List<FieldKey> defaultVisibleColumns = null;

    protected SQLFragment selectName = null;
    private String _sequence = null;


    protected SchemaTableInfo(DbSchema parentSchema)
    {
        this.parentSchema = parentSchema;
    }


    public SchemaTableInfo(String tableName, DbSchema parentSchema)
    {
        this(parentSchema);

        this.name = tableName;
        String name = getSqlDialect().getTableSelectName(parentSchema.getOwner())
                + "." + getSqlDialect().getTableSelectName(tableName);
        this.selectName = new SQLFragment(name);
    }

    public String getName()
    {
        return name;
    }

    public String getMetaDataName()
    {
        return metaDataName;
    }


    public void setMetaDataName(String metaDataName)
    {
        this.metaDataName = metaDataName;
    }


    public SQLFragment getFromSQL()
    {
        return selectName;
    }

    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment ret = new SQLFragment();
        ret.append(selectName);
        ret.append(" AS ");
        ret.append(alias);
        return ret;
    }

    // usually the same as SelectName, unless getSelectName() returns (SELECT .. ) ALIAS
    // CONSDIER: rename getSelectName() to getFromSql()
    public String getAliasName()
    {
        return selectName.toString();
    }


    public DbSchema getSchema()
    {
        return parentSchema;
    }


    /** getSchema().getSqlDialect() */
    public SqlDialect getSqlDialect()
    {
        return parentSchema.getSqlDialect();
    }


    public List<String> getPkColumnNames()
    {
        return _pkColumnNames;
    }


    public List<ColumnInfo> getPkColumns()
    {
        if (null == _pkColumnNames)
            return null;

        if (null == _pkColumns)
        {
            List<ColumnInfo> cols = new ArrayList<ColumnInfo>(_pkColumnNames.size());

            for (String name : _pkColumnNames)
                cols.add(getColumn(name));

            _pkColumns = Collections.unmodifiableList(cols);
        }

        return _pkColumns;
    }


    public ColumnInfo getVersionColumn()
    {
        if (null == _versionColumnName)
        {
            if (null != getColumn("_ts"))
                _versionColumnName = "_ts";
            else if (null != getColumn("Modified"))
                _versionColumnName = "Modified";
        }

        return null == _versionColumnName ? null : getColumn(_versionColumnName);
    }


    public String getVersionColumnName()
    {
        if (null == _versionColumnName)
        {
            if (null != getColumn("_ts"))
                _versionColumnName = "_ts";
            else if (null != getColumn("Modified"))
                _versionColumnName = "Modified";
        }

        return _versionColumnName;
    }


    public void setVersionColumnName(String colName)
    {
        _versionColumnName = colName;
    }

    public String getTitleColumn()
    {
        if (null == titleColumn)
        {
            for (ColumnInfo column : columns)
            {
                if (column.isStringType() && !column.getSqlTypeName().equalsIgnoreCase("entityid"))
                {
                    //TODO: What's the equivalent of this under pg?
                    titleColumn = column.getName();
                    break;
                }
            }
            if (null == titleColumn)
                titleColumn = columns.get(0).getName();
        }

        return titleColumn;
    }

    public int getTableType()
    {
        return _tableType;
    }


    public String toString()
    {
        return selectName.toString();
    }


    void setTableType(String tableType)
    {
        if (tableType.equals("TABLE"))
            _tableType = TABLE_TYPE_TABLE;
        else if (tableType.equals("VIEW"))
            _tableType = TABLE_TYPE_VIEW;
        else
            _tableType = TABLE_TYPE_NOT_IN_DB;
    }

    void setTableType(int tableType)
    {
        _tableType = tableType;
    }

    public NamedObjectList getSelectList()
    {
        NamedObjectList list = (NamedObjectList) DbCache.get(this, "selectArray");
        if (null != list)
            return list;

        String titleColumn = getTitleColumn();
        StringBuffer pkColumnSelect = new StringBuffer();
        String sep = "";
        for (String pkColumnName : getPkColumnNames())
        {
            pkColumnSelect.append(sep);
            pkColumnSelect.append(pkColumnName);
            sep = "+','+";
        }

        ResultSet rs = null;
        list = new NamedObjectList();
        String sql = null;

        try
        {
            sql = "SELECT " + pkColumnSelect + " AS VALUE, " + titleColumn + " AS TITLE FROM " + selectName + " ORDER BY " + titleColumn;

            rs = Table.executeQuery(parentSchema, sql, null);

            while (rs.next())
            {
                list.put(new SimpleNamedObject(rs.getString(1), rs.getString(2)));
            }
        }
        catch (SQLException e)
        {
            _log.error(this + "\n" + sql, e);
        }
        finally
        {
            if (null != rs)
                try
                {
                    rs.close();
                }
                catch (SQLException x)
                {
                    _log.error("getSelectList", x);
                }
        }

        DbCache.put(this, "selectArray", list);
        return list;
    }

    /** getSelectList().get(pk) */
    public String getRowTitle(Object pk) throws SQLException
    {
        NamedObjectList selectList = getSelectList();
        Object title = selectList.get(pk.toString());
        return title == null ? "" : title.toString();
    }

    // UNDONE: throwing Exception is not great, why not just return NULL???
    public ColumnInfo getColumn(String colName)
    {
        if (null == colName)
            return null;

        // HACK: need to invalidate in case of addition (doesn't handle mixed add/delete, but I don't think we delete
        if (colMap != null && columns.size() != colMap.size())
            colMap = null;

        if (null == colMap)
        {
            HashMap<String, ColumnInfo> m = new CaseInsensitiveHashMap<ColumnInfo>();
            for (ColumnInfo colInfo : columns)
            {
                m.put(colInfo.getName(), colInfo);
            }
            colMap = m;
        }

        int colonIndex;
        if ((colonIndex = colName.indexOf(":")) != -1)
        {
            String first = colName.substring(0, colonIndex);
            String rest = colName.substring(colonIndex + 1);
            ColumnInfo fkColInfo = colMap.get(first);
            if (fkColInfo == null)
                return null; // throw new Exception("Column not found" + first);
            if (fkColInfo.getFk() == null)
                return null; // throw new Exception(first + "is not a foreign key");

            return fkColInfo.getFkTableInfo().getColumn(rest);
        }

        return colMap.get(colName);
    }

    public ColumnInfo getColumnFromPropertyURI(String propertyURI)
    {
        for(ColumnInfo col : columns)
            if (col.getPropertyURI().equals(propertyURI))
                return col;
        return null;
    }

    public void addColumn(ColumnInfo column)
    {
        columns.add(column);
    }

    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(columns);
    }

    public List<ColumnInfo> getUserEditableColumns()
    {
        ArrayList<ColumnInfo> userEditableColumns = new ArrayList<ColumnInfo>(columns.size());
        for (ColumnInfo col : columns)
            if (col.isUserEditable())
                userEditableColumns.add(col);

        return Collections.unmodifiableList(userEditableColumns);
    }


    public List<ColumnInfo> getColumns(String colNames)
    {
        String[] colNameArray = colNames.split(",");
        return getColumns(colNameArray);
    }

    public List<ColumnInfo> getColumns(String... colNameArray)
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>(colNameArray.length);
        for (String name : colNameArray)
        {
            ret.add(getColumn(name.trim()));
        }
        return Collections.unmodifiableList(ret);
    }


    public Set<String> getColumnNameSet()
    {
        Set<String> nameSet = new HashSet<String>();
        for (ColumnInfo aColumnList : columns)
        {
            nameSet.add(aColumnList.getName());
        }

        return Collections.unmodifiableSet(nameSet);
    }


    public void loadFromMetaData(DatabaseMetaData dbmd, String catalogName, String schemaName)
            throws SQLException
    {
        loadColumnsFromMetaData(dbmd, catalogName, schemaName);
        ResultSet rs = dbmd.getPrimaryKeys(catalogName, schemaName, metaDataName); // PostgreSQL change: use metaDataName

        // TODO: Change this to add directly to list

        String[] pkColArray = new String[5]; //Assume no more than 5
        int maxKeySeq = 0;
        while (rs.next())
        {
            String colName = rs.getString("COLUMN_NAME");
            ColumnInfo colInfo = getColumn(colName);
            colInfo.setKeyField(true);
            int keySeq = rs.getInt("KEY_SEQ");
            pkColArray[keySeq - 1] = colInfo.getPropertyName();
            if (keySeq > maxKeySeq)
                maxKeySeq = keySeq;
            //BUG? Assume all non-string key fields are autoInc. (Should use XML instead)
            //if (!ColumnInfo.isStringType(colInfo.getSqlTypeInt()))
            //    colInfo.setAutoIncrement(true);
        }
        rs.close();

        String[] pkColumnNames = new String[maxKeySeq];
        System.arraycopy(pkColArray, 0, pkColumnNames, 0, maxKeySeq);

        _pkColumnNames = Arrays.asList(pkColumnNames);
    }


    private void loadColumnsFromMetaData(DatabaseMetaData dbmd, String catalogName, String schemaName) throws SQLException
    {
        columns = ColumnInfo.createFromDatabaseMetaData(dbmd, catalogName, schemaName, this);
    }


    void copyToXml(TableType xmlTable, boolean bFull)
    {
        xmlTable.setTableName(name);
        if (_tableType == TABLE_TYPE_TABLE)
            xmlTable.setTableDbType("TABLE");
        else if (_tableType == TABLE_TYPE_VIEW)
            xmlTable.setTableDbType("VIEW");
        else
            xmlTable.setTableDbType("NOT_IN_DB");


        if (bFull)
        {
            // changed to write out the value of property directly, without the
            // default calculation applied by the getter
            if (null != title)
                xmlTable.setTableTitle(title);
            if (null != _pkColumnNames && _pkColumnNames.size() > 0)
                xmlTable.setPkColumnName(StringUtils.join(_pkColumnNames, ','));
            if (null != titleColumn)
                xmlTable.setTitleColumn(titleColumn);
            if (null != _versionColumnName)
                xmlTable.setVersionColumnName(_versionColumnName);
        }

        org.labkey.data.xml.TableType.Columns xmlColumns = xmlTable.addNewColumns();
        org.labkey.data.xml.ColumnType xmlCol;
        for (ColumnInfo columnInfo : columns)
        {
            xmlCol = xmlColumns.addNewColumn();
            columnInfo.copyToXml(xmlCol, bFull);
        }
    }


    void loadFromXml(TableType xmlTable, boolean merge)
    {
        //If merging with DB MetaData, don't overwrite pk
        if (!merge || null == _pkColumnNames || _pkColumnNames.isEmpty())
        {
            String pkColumnName = xmlTable.getPkColumnName();
            if (null != pkColumnName && pkColumnName.length() > 0)
            {
                _pkColumnNames = Arrays.asList(pkColumnName.split(","));
                //Make sure they are lower-cased.
                //REMOVED:  Assume names in xml are correctly formed
/*                for (int i = 0; i < _pkColumnNames.length; i++)
                    if (Character.isUpperCase(_pkColumnNames[i].charAt(0)))
                        _pkColumnNames[i] = Introspector.decapitalize(_pkColumnNames[i]);
*/
            }
        }
        if (!merge)
        {
            setTableType(xmlTable.getTableDbType());
        }

        //Override with the table name from the schema so casing is nice...
        name = xmlTable.getTableName();
        title = xmlTable.getTableTitle();
        titleColumn = xmlTable.getTitleColumn();


        ColumnType[] xmlColumnArray = xmlTable.getColumns().getColumnArray();

        if (!merge)
            columns = new ArrayList<ColumnInfo>();

        for (ColumnType xmlColumn : xmlColumnArray)
        {
            ColumnInfo colInfo = null;
            if (merge && getTableType() != TABLE_TYPE_NOT_IN_DB) {
                colInfo = getColumn(xmlColumn.getColumnName());
                if (null != colInfo)
                    colInfo.loadFromXml(xmlColumn, true);
            }

            if (null == colInfo) {
                colInfo = new ColumnInfo(xmlColumn.getColumnName(), this);
                columns.add(colInfo);
                colInfo.loadFromXml(xmlColumn, false);
            }
        }
    }


    public void writeCreateTableSql(Writer out) throws IOException
    {
        out.write("CREATE TABLE ");
        out.write(name);
        out.write(" (\n");
        ColumnInfo[] columns = this.columns.toArray(new ColumnInfo[this.columns.size()]);
        for (int i = 0; i < columns.length; i++)
        {
            if (i > 0)
                out.write(",\n");
            out.write(columns[i].toString());
        }
        if (null != _pkColumnNames)
        {
            out.write(",\n PRIMARY KEY (");
            //BUGBUG: This is untested, but we don't use this functionality anyway
            out.write(StringUtils.join(_pkColumnNames, ","));
            out.write(")");
        }
        out.write("\n)\nGO\n\n");
    }


    public void writeCreateConstraintsSql(Writer out) throws IOException
    {
        ColumnInfo[] columns = this.columns.toArray(new ColumnInfo[this.columns.size()]);

        SqlDialect dialect = getSchema().getSqlDialect();
        for (ColumnInfo col : columns) {
            ColumnInfo.SchemaForeignKey fk = (ColumnInfo.SchemaForeignKey) col.getFk();
            if (fk != null) {
                out.write("ALTER TABLE ");
                out.write(name);
                out.write(" ADD CONSTRAINT ");
                out.write("fk_");
                out.write(name);
                out.write("_");
                out.write(col.getName());
                out.write(" FOREIGN KEY (");
                out.write(col.getSelectName());
                if (fk.isJoinWithContainer())
                    out.write(", Container");
                out.write(") REFERENCES ");
                out.write(dialect.getTableSelectName(fk.getLookupTableName()));
                out.write("(");
                out.write(dialect.getColumnSelectName(fk.getLookupColumnName()));
                if (fk.isJoinWithContainer())
                    out.write(", Container");
                out.write(")\nGO\n");
            }
        }
    }


    public void writeBean(Writer out) throws IOException
    {
        out.write("class ");
        out.write(getName());
        out.write("\n\t{\n");

        String[] methNames = new String[columns.size()];
        String[] typeNames = new String[columns.size()];
        String[] memberNames = new String[columns.size()];
        ColumnInfo[] columns = this.columns.toArray(new ColumnInfo[this.columns.size()]);
        for (int i = 0; i < columns.length; i++)
        {
            ColumnInfo col = columns[i];
            memberNames[i] = "_" + col.getPropertyName();
            String methName = col.getLegalName();
            if (!Character.isUnicodeIdentifierPart(methName.charAt(0)))
                methName = Character.toUpperCase(methName.charAt(0)) + methName.substring(1);
            methNames[i] = methName;
            typeNames[i] = ColumnInfo.javaTypeFromSqlType(col.getSqlTypeInt(), col.isNullable());
        }

        for (int i = 0; i < columns.length; i++)
        {
            out.write("\tprivate ");
            out.write(typeNames[i]);
            out.write(" " + memberNames[i] + ";\n");
        }
        out.write("\n\n");
        for (int i = 0; i < columns.length; i++)
        {
            out.write("\tpublic ");
            out.write(typeNames[i]);

            out.write(" get");
            out.write(methNames[i]);
            out.write("()\n\t\t{\n\t\treturn ");
            out.write(memberNames[i]);
            out.write(";\n\t\t}\n\t");

            out.write("public void set");
            out.write(methNames[i]);
            out.write("(");
            out.write(typeNames[i]);
            out.write(" val)\n\t\t{\n\t\t");
            out.write(memberNames[i]);
            out.write(" = val;\n\t\t");
            out.write("}\n\n");

        }
        out.write("\t}\n\n");
    }

    public String getSequence()
    {
        return _sequence;
    }

    public void setSequence(String sequence)
    {
        _sequence = sequence;
    }

    public void setFromSql(SQLFragment fragment)
    {
        selectName = fragment;
    }

    public String decideAlias(String name)
    {
        return name;
    }

    public StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns)
    {
        return null;
    }

    public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form)
    {
        throw new UnsupportedOperationException();
    }

    public boolean hasPermission(User user, int perm)
    {
        return false;
    }

    public Forward insert(User user, QueryUpdateForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public Forward update(User user, QueryUpdateForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public MethodInfo getMethod(String name)
    {
        return null;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (defaultVisibleColumns != null)
            return defaultVisibleColumns;
        return Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(getColumns()));
    }

    public void setDefaultVisibleColumns(Iterable<FieldKey> keys)
    {
        defaultVisibleColumns = new ArrayList<FieldKey>();
        for (FieldKey key : keys)
            defaultVisibleColumns.add(key);
    }

    public boolean isPublic()
    {
        //schema table infos are not public (i.e., not accessible from Query)
        return false;
    }

    public String getPublicName()
    {
        return null;
    }

    public String getPublicSchemaName()
    {
        return null;
    }

    public boolean needsContainerClauseAdded()
    {
        return true;
    }
}
