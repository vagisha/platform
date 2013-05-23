/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.query;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExternalSchemaCustomizer;
import org.labkey.api.data.UserSchemaCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.data.xml.queryCustomView.NamedFiltersType;
import org.labkey.query.data.ExternalSchemaTable;
import org.labkey.query.persist.AbstractExternalSchemaDef;
import org.labkey.query.persist.ExternalSchemaDef;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExternalSchema extends SimpleUserSchema
{
    public static void register()
    {
        DefaultSchema.registerProvider(new DefaultSchema.DynamicSchemaProvider() {
            @Override
            public QuerySchema getSchema(User user, Container container, String name)
            {
                QueryServiceImpl svc = (QueryServiceImpl)QueryService.get();
                return svc.getExternalSchema(user, container, name);
            }

            @NotNull
            @Override
            public Collection<String> getSchemaNames(User user, Container container)
            {
                QueryServiceImpl svc = (QueryServiceImpl) QueryService.get();
                return svc.getExternalSchemas(user, container).keySet();
            }
        });
    }

    // Adaptor for DbSchema and UserSchema.
    protected interface TableSource
    {
        public Collection<String> getTableNames();
        public Collection<String> getQueryNames();
        public boolean isTableAvailable(String tableName);
    }

    protected final AbstractExternalSchemaDef _def;
    protected final TemplateSchemaType _template;
    protected final Map<String, TableType> _metaDataMap;
    protected final Map<String, NamedFiltersType> _namedFilters;

    public static ExternalSchema get(User user, Container container, ExternalSchemaDef def)
    {
        TemplateSchemaType template = def.lookupTemplate(container);
        TablesType tablesType = parseTablesType(def, template);

        NamedFiltersType[] namedFilters = null;
        TableType[] tableTypes = null;
        Collection<UserSchemaCustomizer> schemaCustomizers = null;
        if (tablesType != null)
        {
            namedFilters = tablesType.getFiltersArray();
            tableTypes = tablesType.getTableArray();
            schemaCustomizers = UserSchemaCustomizer.Factory.create(tablesType.getSchemaCustomizerArray());
        }

        final DbSchema schema = getDbSchema(def, template);
        if (null == schema)
            return null;
        TableSource tableSource = new TableSource()
        {
            public Collection<String> getTableNames()         { return schema.getTableNames(); }
            public Collection<String> getQueryNames()         { return Collections.emptyList(); }
            public boolean isTableAvailable(String tableName) { return schema.getTable(tableName) != null; }
        };

        Map<String, TableType> metaDataMap = getMetaDataMap(tableTypes);
        Collection<String> availableTables = getAvailableTables(def, template, tableSource, metaDataMap);
        Collection<String> hiddenTables = getHiddenTables(tableTypes);


        ExternalSchema ret = new ExternalSchema(user, container, def, template, schema, metaDataMap, namedFilters, schemaCustomizers, availableTables, hiddenTables);
        return ret;
    }


    protected ExternalSchema(User user, Container container, AbstractExternalSchemaDef def, TemplateSchemaType template, DbSchema schema,
                             Map<String, TableType> metaDataMap,
                             NamedFiltersType[] namedFilters,
                             Collection<UserSchemaCustomizer> schemaCustomizers,
                             Collection<String> availableTables, Collection<String> hiddenTables)
    {
        super(def.getUserSchemaName(), "Contains data tables from the '" + def.getUserSchemaName() + "' database schema.",
                user, container, schema, schemaCustomizers, availableTables, hiddenTables);

        _def = def;
        _template = template;
        _metaDataMap = metaDataMap;

        // Create the SimpleFilters and give customizers a change to augment them.
        Map<String, NamedFiltersType> filters = new HashMap<>();
        if (namedFilters != null)
        {
            for (NamedFiltersType namedFilter : namedFilters)
                filters.put(namedFilter.getName(), namedFilter);
        }
        _namedFilters = fireCustomizeNamedFilters(filters);
    }

    private static DbSchema getDbSchema(ExternalSchemaDef def, TemplateSchemaType template)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());
        if (scope == null)
            return null;

        String sourceSchemaName = def.getSourceSchemaName();
        if (sourceSchemaName == null && template != null)
            sourceSchemaName = template.getSourceSchemaName();
        return scope.getSchema(sourceSchemaName);
    }

    @Nullable
    protected static TablesType parseTablesType(AbstractExternalSchemaDef def, TemplateSchemaType template)
    {
        String metadata = def.getMetaData();
        if (metadata != null)
        {
            try
            {
                TablesDocument doc = TablesDocument.Factory.parse(metadata);

                if (doc.getTables() != null)
                    return doc.getTables();
            }
            catch (XmlException e)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("Ignoring invalid schema metadata xml for '").append(def.getUserSchemaName()).append("'");
                String containerPath = def.getContainerPath();
                if (containerPath != null && !"".equals(containerPath))
                    sb.append(" in container '").append(containerPath).append("'");
                Logger.getLogger(ExternalSchema.class).warn(sb, e);
                return null;
            }
        }

        if (template != null && template.getMetadata() != null)
            return template.getMetadata().getTables();

        return null;
    }

    protected static @NotNull Collection<String> getAvailableTables(AbstractExternalSchemaDef def, TemplateSchemaType template, @Nullable TableSource tableSource, Map<String, TableType> metaDataMap)
    {
        Collection<String> tableNames = getAvailableTables(def, template, tableSource);

        if (tableNames.isEmpty() || metaDataMap.isEmpty())
            return tableNames;

        // Translate database names to XML table names
        List<String> xmlTableNames = new LinkedList<String>();

        for (String tableName : tableNames)
        {
            TableType tt = metaDataMap.get(tableName);
            xmlTableNames.add(null == tt ? tableName : tt.getTableName());
        }

        return xmlTableNames;
    }

    private static @NotNull Collection<String> getAvailableTables(AbstractExternalSchemaDef def, TemplateSchemaType template, @Nullable TableSource tableSource)
    {
        if (null == tableSource)
            return Collections.emptySet();

        Set<String> allowed = null;
        String allowedTableNames = def.getTables();
        if (allowedTableNames != null)
        {
            if ("*".equals(allowedTableNames))
                return tableSource.getTableNames();
            else
                allowed = new CsvSet(allowedTableNames);
        }
        else if (template != null)
        {
            TemplateSchemaType.Tables tables = template.getTables();
            if (tables != null)
            {
                String[] tableNames = tables.getTableNameArray();
                if (tableNames != null)
                {
                    if (tableNames.length == 1 && tableNames[0].equals("*"))
                        return tableSource.getTableNames();
                    else
                        allowed = new HashSet<String>(Arrays.asList(tableNames));
                }
            }
        }

        if (allowed == null || allowed.size() == 0)
            return Collections.emptySet();

        // Some tables in the "allowed" list may no longer exist or may be query names, so check each table in the schema.  #13002
        Set<String> available = new HashSet<String>(allowed.size());
        for (String name : allowed)
            if (tableSource.isTableAvailable(name))
                available.add(name);

        return available;
    }

    protected static @NotNull Collection<String> getAvailableQueries(AbstractExternalSchemaDef def, TemplateSchemaType template, @Nullable TableSource tableSource)
    {
        if (null == tableSource)
            return Collections.emptySet();

        Collection<String> allowed = null;
        String allowedTableNames = def.getTables();
        if (allowedTableNames != null)
        {
            if ("*".equals(allowedTableNames))
                allowed = tableSource.getQueryNames();
            else
                allowed = new CsvSet(allowedTableNames);
        }
        else if (template != null)
        {
            TemplateSchemaType.Tables tables = template.getTables();
            if (tables != null)
            {
                String[] tableNames = tables.getTableNameArray();
                if (tableNames != null)
                {
                    if (tableNames.length == 1 && tableNames[0].equals("*"))
                        allowed = tableSource.getQueryNames();
                    else
                        allowed = Arrays.asList(tableNames);
                }
            }
        }

        if (allowed == null || allowed.size() == 0)
            return Collections.emptySet();

        // The "allowed" list contains both query and table names, so check each query in the schema.
        Collection<String> queryNames = tableSource.getQueryNames();
        Set<String> available = new HashSet<String>(allowed.size());
        for (String queryName : queryNames)
            if (allowed.contains(queryName))
                available.add(queryName);

        return available;
    }

    protected static @NotNull Collection<String> getHiddenTables(TableType[] tableTypes)
    {
        Set<String> hidden = new HashSet<String>();

        if (tableTypes != null)
        {
            for (TableType tt : tableTypes)
                if (tt.getHidden())
                    hidden.add(tt.getTableName());
        }

        return hidden;
    }

    protected static @NotNull Map<String, TableType> getMetaDataMap(TableType[] tableTypes)
    {
        Map<String, TableType> metaDataMap = new CaseInsensitiveHashMap<TableType>();
        if (tableTypes != null)
        {
            for (TableType tt : tableTypes)
                metaDataMap.put(tt.getTableName(), tt);
        }
        return metaDataMap;
    }

    public static void uncache(ExternalSchemaDef def)
    {
        DbScope scope = DbScope.getDbScope(def.getDataSource());

        if (null != scope)
        {
            String schemaName = def.getSourceSchemaName();

            // Don't uncache module schemas, even those pointed at by external schemas.  Reloading these schemas is
            // unnecessary (they don't change) and causes us to leak DbCaches.  See #10508.
            if (!scope.isModuleSchema(schemaName))
                scope.invalidateSchema(def.getSourceSchemaName());
        }
    }

    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        ExternalSchemaTable ret = new ExternalSchemaTable(this, sourceTable, getXbTable(name));
        ret.init();
        ret.setContainer(_def.getContainerId());
        return ret;
    }

    protected TableType getXbTable(String name)
    {
        return _metaDataMap.get(name);
    }

    public boolean areTablesEditable()
    {
        return _def instanceof ExternalSchemaDef && _def.isEditable();
    }

    public boolean shouldIndexMetaData()
    {
        return _def instanceof ExternalSchemaDef && _def.isIndexable();
    }

    @Override
    public boolean shouldRenderTableList()
    {
        // If more than 100 tables then don't try to render the list in the Query menu
        return getTableNames().size() <= 100;
    }

    protected Map<String, NamedFiltersType> fireCustomizeNamedFilters(Map<String, NamedFiltersType> filters)
    {
        if (_schemaCustomizers != null)
        {
            for (UserSchemaCustomizer customizer : _schemaCustomizers)
                if (customizer instanceof ExternalSchemaCustomizer)
                    ((ExternalSchemaCustomizer)customizer).customizeNamedFilters(filters);
        }
        return filters;
    }
}
