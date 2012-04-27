/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class QueryTableInfo extends AbstractTableInfo implements ContainerFilterable
{
    QueryRelation _relation;
    private ContainerFilter _containerFilter;

    public QueryTableInfo(QueryRelation relation, String name)
    {
        super(relation._query.getSchema().getDbSchema());
        _relation = relation;
        setName(name);
    }

    public QueryRelation getQueryRelation()
    {
        return _relation;
    }

    @NotNull
    public SQLFragment getFromSQL()
    {
        throw new IllegalStateException();
    }


    @Override
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment f = new SQLFragment();
        SQLFragment sql = _relation.getSql();
        f.append("(").append(sql).append(") ").append(alias);
        return f;
    }


    @Override
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        Query query = _relation._query;
        Collection<QueryService.ParameterDecl> ret = query.getParameters();
        if (null == ret)
            return Collections.EMPTY_LIST;
        return ret;
    }

    @Override
    public boolean needsContainerClauseAdded()
    {
        // Let the underlying schemas do whatever filtering they need on the data, especially since
        // after columns are part of a query we lose track of what was on the base table and what's been joined in
        return false;
    }

    public void setContainerFilter(@NotNull ContainerFilter containerFilter)
    {
        _containerFilter = containerFilter;
        _relation.setContainerFilter(containerFilter);
    }

    public boolean hasDefaultContainerFilter()
    {
        return false;
    }

    @NotNull
    @Override
    public ContainerFilter getContainerFilter()
    {
        if (_containerFilter == null)
            return ContainerFilter.CURRENT;
        return _containerFilter;
    }

    public boolean hasSort()
    {
        return false;
    }


    public void afterConstruct()
    {
        initFieldKeyMap();
        for (ColumnInfo ci : getColumns())
        {
            Map<FieldKey, FieldKey> remap = mapFieldKeyToSiblings.get(ci.getFieldKey());
            if (null == remap || remap.isEmpty())
                continue;
            ci.remapFieldKeys(null, remap);
        }
        super.afterConstruct();
    }

    // map output column to its related columns (grouped by source querytable)
    Map<FieldKey, Map<FieldKey,FieldKey>> mapFieldKeyToSiblings = new TreeMap<FieldKey, Map<FieldKey,FieldKey>>();

    private void initFieldKeyMap()
    {
        Query query = _relation._query;
        for (Map.Entry<QueryTable,Map<FieldKey,QueryRelation.RelationColumn>> maps : query.qtableColumnMaps.entrySet())
        {
            Map<FieldKey,QueryRelation.RelationColumn> map = maps.getValue();
            Map<FieldKey,FieldKey> flippedMap = new TreeMap<FieldKey, FieldKey>();
            for (Map.Entry<FieldKey,QueryRelation.RelationColumn> e : map.entrySet())
            {
                flippedMap.put(e.getValue().getFieldKey(), e.getKey());
                mapFieldKeyToSiblings.put(e.getKey(), flippedMap);
            }
        }
        mapFieldKeyToSiblings = Collections.unmodifiableMap(mapFieldKeyToSiblings);
    }
}
