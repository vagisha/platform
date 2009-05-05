/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.reader.TabLoader;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.collections.RowMap;

import java.util.*;
import java.io.IOException;
import java.io.File;
import java.sql.Types;
import java.sql.SQLException;

/**
 * User: Matthew
 * Date: Jun 12, 2006
 * Time: 3:42:15 PM
 *
 *
 * NOTE: I would have put loadTempTable() on TabLoader, but it is
 * in the tools project.  That wouldn't work, so here's a subclass instead.
 */
public class TempTableLoader extends TabLoader
{
    public TempTableLoader(File src) throws IOException
    {
        super(src);
    }

    public TempTableLoader(File src, boolean hasColumnHeaders) throws IOException
    {
        super(src);
        setHasColumnHeaders(hasColumnHeaders);
    }

    public Table.TempTableInfo loadTempTable(DbSchema schema) throws IOException, SQLException
    {
        //
        // Load the file
        //

        List<Map<String, Object>> maps = load();


        //
        // create TableInfo
        //

        SqlDialect dialect = schema.getSqlDialect();

        ArrayList<ColumnInfo> cols = new ArrayList<ColumnInfo>();
        for (ColumnDescriptor col : getColumns())
        {
            String sqlType = getSqlType(dialect, col.clazz);
            ColumnInfo colTT = new ColumnInfo(col.name);
            colTT.setSqlTypeName(sqlType);
            colTT.setNullable(true);
            cols.add(colTT);
        }

        // note: this call sets col.parentTable()
        Table.TempTableInfo tinfoTempTable = new Table.TempTableInfo(schema, "tsv", cols, null);
        String tempTableName = tinfoTempTable.getTempTableName();

        //
        // create table
        //

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(tempTableName).append(" (");
        String comma = "";
        for (int i=0 ; i<cols.size() ; i++)
        {
            ColumnInfo col = cols.get(i);
            sql.append(comma);
            comma = ", ";

            if (col.getSqlTypeName().endsWith("VARCHAR")) // varchar or nvarchar
            {
                int length = -1;
                for (Map m : maps)
                {
                    RowMap map = (RowMap)m;
                    Object v = map.get(i);
                    if (v instanceof String)
                        length = Math.max(((String)v).length(), length);
                }
                if (length == -1)
                    length = 100;
                sql.append(col.getSelectName()).append(" ").append(col.getSqlTypeName()).append("(").append(length).append(")");
            }
            else
            {
                sql.append(col.getSelectName()).append(" ").append(col.getSqlTypeName());
            }
        }
        sql.append(")");


        //
        // Track the table, it will be deleted when tinfoTempTable is GC'd
        //

        Table.execute(schema, sql.toString(), null);
        tinfoTempTable.track();

        //
        // Populate
        //

        StringBuilder sqlInsert = new StringBuilder();
        StringBuilder sqlValues = new StringBuilder();
        sqlInsert.append("INSERT INTO ").append(tempTableName).append(" (");
        sqlValues.append(" VALUES (");
        comma = "";
        for (ColumnInfo col : cols)
        {
            sqlInsert.append(comma).append(col.getSelectName());
            sqlValues.append(comma).append("?");
            comma = ",";
        }
        sqlInsert.append(") ");
        sqlInsert.append(sqlValues);
        sqlInsert.append(")");

        List<Collection<?>> paramList = new ArrayList<Collection<?>>(maps.size());
        for (Map<String, Object> m : maps)
            paramList.add(m.values());

        Table.batchExecute(schema, sqlInsert.toString(), paramList);

        return tinfoTempTable;
    }


    /**
     * UNDONE: this should be more complete and move to a shared location
     * TODO: use a map
     * @see ColumnInfo
     */
    private String getSqlType(SqlDialect dialect, Class clazz)
    {
        int sqlType;

        if (clazz == String.class)
            sqlType = Types.VARCHAR;
        else if (clazz == Date.class)
            sqlType = Types.TIMESTAMP;
        else if (clazz == Integer.class || clazz == Integer.TYPE)
            sqlType = Types.INTEGER;
        else if (clazz == Double.class || clazz == Double.TYPE)
            sqlType = Types.DOUBLE;
        else if (clazz == Float.class || clazz == Float.TYPE)
            sqlType = Types.REAL;
        else if (clazz == String.class)
            sqlType = Types.VARCHAR;
        else if (clazz == Boolean.class || clazz == Boolean.TYPE)
            sqlType = Types.BOOLEAN;
        else if (clazz == Long.class || clazz == Long.TYPE)
            sqlType = Types.BIGINT;
        else
            sqlType = Types.VARCHAR;

        return dialect.sqlTypeNameFromSqlType(sqlType);
    }
}
