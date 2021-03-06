/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
package org.labkey.experiment.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.query.FieldKey;

import java.util.Map;

/**
 * User: kevink
 * Date: 3/16/16
 */
public class LineageClause extends CompareType.CompareClause
{
    private String _lsid;
    private int _depth;

    public LineageClause(@NotNull FieldKey fieldKey, Object value)
    {
        super(fieldKey, CompareType.MEMBER_OF, value);
    }

    public LineageClause(@NotNull FieldKey fieldKey, Object value, String lsid, int depth)
    {
        this(fieldKey, value);
        _lsid = lsid;
        _depth = depth;
    }

    protected ExpRunItem getStart()
    {
        if (null != _lsid)
            return LineageHelper.getStart(_lsid);

        Object o = getParamVals().length == 0 ? null : getParamVals()[0];
        if (o == null)
            return null;

        // TODO: support rowId as well
        return LineageHelper.getStart(String.valueOf(o));
    }

    protected int getDepth()
    {
        return _depth;
    }

    protected ExpLineageOptions createOptions()
    {
        return _depth < 0 ? LineageHelper.createParentOfOptions(_depth) : LineageHelper.createChildOfOptions(_depth);
    }

    protected String getLsidColumn()
    {
        return "lsid";
    }

    @Override
    public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
    {
        ColumnInfo colInfo = columnMap != null ? columnMap.get(getFieldKey()) : null;
        String alias = colInfo != null ? colInfo.getAlias() : getFieldKey().getName();

        ExpRunItem start = getStart();
        ExpLineageOptions options = createOptions();

        SQLFragment tree = LineageHelper.createExperimentTreeSQLLsidSeeds(start, options);

        if (tree == null)
            return new SQLFragment("(1 = 2)");

        SQLFragment sql = new SQLFragment();
        sql.append("(").append(alias).append(") IN (");
        sql.append("SELECT ").append(getLsidColumn()).append(" FROM (");
        sql.append(tree);
        sql.append(") AS X)");

        return sql;
    }

    protected String filterTextType()
    {
        return " lineage of";
    }

    @Override
    protected void appendFilterText(StringBuilder sb, SimpleFilter.ColumnNameFormatter formatter)
    {
        ExpRunItem start = getStart();
        if (start == null)
            sb.append("Invalid '").append(filterTextType()).append("' filter");
        else
            sb.append("Is ").append(filterTextType()).append(" '").append(start.getName()).append("'");
    }

}
