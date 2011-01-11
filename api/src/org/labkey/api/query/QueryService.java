/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

abstract public class QueryService
{
    static private QueryService instance;

    static public QueryService get()
    {
        return instance;
    }

    static public void set(QueryService impl)
    {
        instance = impl;
    }

    abstract public Map<String, QueryDefinition> getQueryDefs(User user, Container container, String schema);
    abstract public List<QueryDefinition> getQueryDefs(User user, Container container);
    abstract public QueryDefinition createQueryDef(User user, Container container, String schema, String name);
    abstract public QueryDefinition createQueryDef(User user, Container container, UserSchema schema, String name);
    abstract public QueryDefinition createQueryDefForTable(UserSchema schema, String tableName);
    abstract public QueryDefinition getQueryDef(User user, Container container, String schema, String name);
    abstract public QuerySnapshotDefinition getSnapshotDef(Container container, String schema, String name);
    abstract public QuerySnapshotDefinition createQuerySnapshotDef(QueryDefinition queryDef, String name);
    abstract public boolean isQuerySnapshot(Container container, String schema, String name);
    abstract public List<QuerySnapshotDefinition> getQuerySnapshotDefs(Container container, String schema);

    abstract public ActionURL urlQueryDesigner(User user, Container container, String schema);
    abstract public ActionURL urlFor(User user, Container container, QueryAction action, String schema, String queryName);
    abstract public UserSchema getUserSchema(User user, Container container, String schema);
    abstract public List<CustomView> getCustomViews(User user, Container container, String schema, String query);
    abstract public List<CustomViewInfo> getCustomViewInfos(User user, Container container, String schema, String query);
    abstract public CustomView getCustomView(User user, Container container, String schema, String query, String name);
    abstract public int importCustomViews(User user, Container container, File viewDir) throws XmlValidationException;
    abstract public void updateCustomViewsAfterRename(@NotNull Container c, @NotNull String schema,
            @NotNull String oldQueryName, @NotNull String newQueryName);


    /**
     * Loops through the field keys and turns them into ColumnInfos based on the base table
     */
    @NotNull
    abstract public Map<FieldKey, ColumnInfo> getColumns(TableInfo table, Collection<FieldKey> fields);
    abstract public LinkedHashMap<FieldKey, ColumnInfo> getColumns(TableInfo table, Collection<FieldKey> fields, Collection<ColumnInfo> existingColumns);

    abstract public List<DisplayColumn> getDisplayColumns(TableInfo table, Collection<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> fields);

    /**
     * Ensure that <code>columns</code> contains all of the columns necessary for <code>filter</code> and <code>sort</code>.
     * If <code>unresolvedColumns</code> is not null, then the Filter and Sort will be modified to remove any clauses that
     * involve unresolved columns, and <code>unresolvedColumns</code> will contain the names of the unresolved columns.
     *
     * NOTE: shouldn't need to call this anymore unless you really care about the unresolvedColumns
     */
    abstract public Collection<ColumnInfo> ensureRequiredColumns(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Set<String> unresolvedColumns);

    abstract public Map<String, UserSchema> getExternalSchemas(DefaultSchema folderSchema);
    abstract public UserSchema getExternalSchema(DefaultSchema folderSchema, String name);

    abstract public UserSchema createSimpleUserSchema(String name, String description, User user, Container container, DbSchema schema);

    abstract public List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns);

    abstract public String findMetadataOverride(Container container, String schemaName, String tableName, boolean customQuery);

	abstract public ResultSet select(QuerySchema schema, String sql) throws SQLException;
    public Results select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort) throws SQLException
    {
        return select(table,columns,filter,sort,Collections.EMPTY_MAP);
    }
	abstract public Results select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Map<String,Object> parameters) throws SQLException;
    abstract public SQLFragment getSelectSQL(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, int rowCount, long offset);
    abstract public Parameter.ParameterMap insertStatement(Connection conn, User user, TableInfo tableInsert) throws SQLException;


    public interface QueryListener
    {
        void viewChanged(CustomView view);
        void viewDeleted(CustomView view);
    }

    abstract public void addQueryListener(QueryListener listener);

    //
    // Thread local environment for executing a query
    //
    // currently only supports USERID for implementing the USERID() method
    //
    public enum Environment
    {
        USERID(JdbcType.INTEGER);

        public JdbcType type;

        Environment(JdbcType type)
        {
            this.type = type;
        }
    }

    abstract public void setEnvironment(Environment e, Object value);
    abstract public void clearEnvironment();
    abstract public Object cloneEnvironment();
    abstract public void copyEnvironment(Object o);


    public interface ParameterDecl
    {
        String getName();
        JdbcType getType();
        Object getDefault();
        boolean isRequired();
    }


    public static class NamedParameterNotProvided extends SQLException
    {
        public NamedParameterNotProvided(String name)
        {
            super("Parameter not provided: " + name);
        }
    }

    abstract public void bindNamedParameters(SQLFragment frag, Map<String,Object> in);
    abstract public void validateNamedParameters(SQLFragment frag) throws SQLException;
}
