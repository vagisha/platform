/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;

import java.util.List;

/*
* User: Dave
* Date: Aug 13, 2008
* Time: 1:21:48 PM
*/
public class RExportScriptModel extends ExportScriptModel
{
    public RExportScriptModel(QueryView view)
    {
        super(view);
    }

    @Override
    public String getViewName()
    {
        return StringUtils.trimToEmpty(super.getViewName());
    }

    public String getFilters()
    {
        List<String> filterExprs = getFilterExpressions();

        if (filterExprs.isEmpty())
            return "NULL";

        StringBuilder filtersExpr = new StringBuilder("makeFilter(");
        String sep = "";

        for (String mf : filterExprs)
        {
            filtersExpr.append(sep);
            filtersExpr.append(mf);
            sep = ",";
        }

        filtersExpr.append(")");

        return filtersExpr.toString();
    }

    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "c(" + doubleQuote(name) + ", " + doubleQuote(operator.getScriptName()) + ", " + doubleQuote(value) + ")";
    }

    public String getContainerFilterString()
    {
        ContainerFilter containerFilter = super.getContainerFilter();
        if (null == containerFilter || null == containerFilter.getType())
            return "NULL";
        else
            return (" " + doubleQuote(containerFilter.getType().name()) + " " );
    }

    @Override
    public String getScriptExportText()
    {
        StringBuilder sb = new StringBuilder();
        String indent = StringUtils.repeat(" ", 4);

        sb.append("## R Script generated by ").append(getInstallationName()).append(" on ").append(getCreatedOn()).append("\n");
        sb.append("#").append("\n");
        sb.append("# This script makes use of the LabKey Remote API for R package (Rlabkey), which can be obtained via CRAN").append("\n");
        sb.append("# using the package name \"Rlabkey\".  The Rlabkey package also depends on the \"rjson\" and \"rCurl\" packages.").append("\n");
        sb.append("#").append("\n");
        sb.append("# See https://www.labkey.org/wiki/home/Documentation/page.view?name=rAPI for more information.").append("\n");
        sb.append("\n");
        sb.append("library(Rlabkey)").append("\n");
        sb.append("\n");
        sb.append("# Select rows into a data frame called 'mydata'").append("\n");
        sb.append("\n");
        sb.append("mydata <- labkey.selectRows(").append("\n");
        sb.append(indent).append("baseUrl=").append(doubleQuote(getBaseUrl())).append(",").append("\n");
        sb.append(indent).append("folderPath=").append(doubleQuote(getFolderPath())).append(",").append("\n");
        sb.append(indent).append("schemaName=").append(doubleQuote(getSchemaName())).append(",").append("\n");
        sb.append(indent).append("queryName=").append(doubleQuote(getQueryName())).append(",").append("\n");
        sb.append(indent).append("viewName=").append(doubleQuote(getViewName())).append(",").append("\n");

        String sort = getSort();
        if (sort != null)
            sb.append(indent).append("colSort=").append(doubleQuote(getSort())).append(",").append("\n");
        sb.append(indent).append("colFilter=").append(getFilters()).append(",").append("\n");
        sb.append(indent).append("containerFilter=").append(getContainerFilterString()).append("\n");
        sb.append(")\n");

        return sb.toString();
    }
}
