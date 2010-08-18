/*
 * Copyright (c) 2010 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletException;
import java.net.URISyntaxException;
import java.util.*;

// Helper class to serialize a CustomView to/from json
public class CustomViewUtil
{
    protected static final String FILTER_PARAM_PREFIX = "filter";

    public static void update(CustomView view, Map<String, Object> jsonView, boolean saveFilterAndSort)
    {
        List<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>> fields = new ArrayList<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>>();

        for (Map<String, Object> column : (List<Map<String, Object>>)jsonView.get("columns"))
        {
            FieldKey key = FieldKey.fromString((String)column.get("fieldKey"));
            String title = column.containsKey("title") ? (String)column.get("title") : null;
            Map<CustomViewInfo.ColumnProperty, String> map = Collections.emptyMap();
            if (title != null)
            {
                map = new EnumMap<CustomViewInfo.ColumnProperty,String>(CustomViewInfo.ColumnProperty.class);
                map.put(CustomViewInfo.ColumnProperty.columnTitle, title);
            }
            fields.add(Pair.of(key, map));
        }

        view.setColumnProperties(fields);
        if (!saveFilterAndSort)
            return;

        List<Map<String, Object>> filterInfos = (List<Map<String, Object>>)jsonView.get("filters");
        List<String> sortInfos = (List<String>)jsonView.get("sort");

        ActionURL url = new ActionURL();

        for (Map<String, Object> filterInfo : filterInfos)
        {
            String fieldKey = (String)filterInfo.get("fieldKey");
            String op = (String)filterInfo.get("op");
            if (op == null)
                op = "";

            String value = (String)filterInfo.get("value");
            if (value == null)
                value = "";

            url.addParameter(FILTER_PARAM_PREFIX + "." + fieldKey + "~" + op, value);
        }


        if (sortInfos.size() > 0)
        {
            Sort sort = new Sort();
            for (String s : sortInfos)
            {
                sort.insertSort(Sort.fromURLParamValue(s));
            }
            sort.applyToURL(url, FILTER_PARAM_PREFIX);
        }

        // UNDONE: container filter

        view.setFilterAndSortFromURL(url, FILTER_PARAM_PREFIX);
    }

    public static Map<String, Object> toMap(ViewContext context, UserSchema schema, String queryName, String viewName, boolean includeFieldMeta, boolean createDefault)
            throws ServletException
    {
        //build a query view.  XXX: is this necessary?  Old version used queryView.getDisplayColumns() to get cols in the default view
        QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, queryName, viewName);
        QueryView qview = schema.createView(context, settings, null);

        CustomView view = qview.getCustomView();
        if (view == null)
        {
            if (createDefault)
                view = qview.getQueryDef().createCustomView(context.getUser(), viewName);
            else
                return Collections.emptyMap();
        }

        return toMap(schema, view, includeFieldMeta);
    }

    public static Map<String, Object> toMap(QuerySchema schema, CustomView view, boolean includeFieldMeta)
    {
        Map<String, Object> ret = new LinkedHashMap<String, Object>();
        ret.put("name", view.getName() == null ? "" : view.getName());
        ret.put("default", view.getName() == null);
        if (null != view.getOwner())
            ret.put("owner", view.getOwner().getDisplayName(null));
        ret.put("shared", view.isShared());
        ret.put("inherit", view.canInherit());
        ret.put("session", view.isSession());
        ret.put("editable", view.isEditable());
        ret.put("hidden", view.isHidden());

        ActionURL gridURL = view.getQueryDefinition().urlFor(QueryAction.executeQuery);
        if (gridURL != null)
        {
            if (view.getName() != null)
                gridURL.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.viewName.name(), view.getName());
            ret.put("viewDataUrl", gridURL);
        }

//        UserSchema schema = view.getQueryDefinition().getSchema();
        TableInfo tinfo = view.getQueryDefinition().getTable(schema, null, true);

        List<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>> columns = view.getColumnProperties();
        if (columns.size() == 0)
        {
            columns = new ArrayList<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>>();
            for (FieldKey key : tinfo.getDefaultVisibleColumns())
            {
                columns.add(Pair.of(key, Collections.<CustomViewInfo.ColumnProperty, String>emptyMap()));
            }
        }

        Set<FieldKey> allKeys = new LinkedHashSet<FieldKey>();
        List<Map<String, Object>> colInfos = new ArrayList<Map<String, Object>>();
        for (Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>> entry : columns)
        {
            allKeys.add(entry.getKey());

            Map<String, Object> colInfo = new LinkedHashMap<String, Object>();
            colInfo.put("name", entry.getKey().getName());
            colInfo.put("key", entry.getKey().toString());
            colInfo.put("fieldKey", entry.getKey().toString());
            if (!entry.getValue().isEmpty())
            {
                String columnTitle = entry.getValue().get(CustomViewInfo.ColumnProperty.columnTitle);
                if (columnTitle != null)
                    colInfo.put("title", columnTitle);
            }
            colInfos.add(colInfo);
        }
        ret.put("columns", colInfos);

        List<Map<String, Object>> filterInfos = new ArrayList<Map<String, Object>>();
        List<String> sortInfos = new ArrayList<String>();
        try
        {
            CustomViewInfo.FilterAndSort fas = CustomViewInfo.FilterAndSort.fromString(view.getFilterAndSort());
            for (FilterInfo filter : fas.getFilter())
            {
                allKeys.add(filter.getField());

                Map<String, Object> filterInfo = new HashMap<String, Object>();
                filterInfo.put("fieldKey", filter.getField().toString());
                filterInfo.put("op", filter.getOp().toString());
                filterInfo.put("value", filter.getValue());
                filterInfos.add(filterInfo);
            }

            for (Sort.SortField sf : fas.getSort())
            {
                allKeys.add(FieldKey.fromString(sf.getColumnName()));
                sortInfos.add(sf.toUrlString());
            }

        }
        catch (URISyntaxException e)
        {
        }
        ret.put("filter", filterInfos);
        ret.put("sort", sortInfos);

        if (includeFieldMeta)
        {
            Map<FieldKey, ColumnInfo> allCols = QueryService.get().getColumns(tinfo, allKeys);
            List<Map<String, Object>> allColMaps = new ArrayList<Map<String, Object>>(allCols.size());
            for (FieldKey field : allKeys)
            {
                // Column may be in select list but not present in the actual table
                ColumnInfo col = allCols.get(field);
                if (col != null)
                {
                    DisplayColumn dc = col.getDisplayColumnFactory().createRenderer(col);
                    allColMaps.add(JsonWriter.getMetaData(dc, null, true, true));
                }
            }
            // property name "fields" matches LABKEY.Query.ExtendedSelectRowsResults (ie, metaData.fields)
            ret.put("fields", allColMaps);
        }

        return ret;
    }
}
