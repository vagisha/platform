/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.study.assay;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.study.assay.AbstractAssayTsvDataHandler;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.NewTabLoader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 11:17:56 AM
 */
public class TsvDataHandler extends AbstractAssayTsvDataHandler
{
    public static final DataType DATA_TYPE = new DataType("AssayRunTSVData");

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    protected boolean allowEmptyData()
    {
        return false;
    }

    @Override
    protected boolean shouldAddInputMaterials()
    {
        return true;
    }

    public List<Map<String, Object>> loadFileData(Domain dataDomain, File inputFile) throws IOException, ExperimentException
    {
        DomainProperty[] columns = dataDomain.getProperties();
        Map<String, Class> expectedColumns = new CaseInsensitiveHashMap<Class>(columns.length);
        Set<String> mvEnabledColumns = new CaseInsensitiveHashSet();
        Set<String> mvIndicatorColumns = new CaseInsensitiveHashSet();


        for (DomainProperty col : columns)
        {
            if (col.isMvEnabled())
            {
                mvEnabledColumns.add(col.getName());
                mvIndicatorColumns.add(col.getName() + MvColumn.MV_INDICATOR_SUFFIX);
            }
            if (col.getLabel() != null)
                expectedColumns.put(col.getLabel(), col.getPropertyDescriptor().getPropertyType().getJavaType());
        }
        for (DomainProperty col : columns)
            expectedColumns.put(col.getName(), col.getPropertyDescriptor().getPropertyType().getJavaType());
        DataLoader<Map<String, Object>> loader = null;
        try
        {

            if (inputFile.getName().toLowerCase().endsWith(".xls"))
            {
                loader = new ExcelLoader(inputFile, true);
            }
            else
            {
                loader = new NewTabLoader(inputFile, true);
            }
            for (ColumnDescriptor column : loader.getColumns())
            {
                if (mvEnabledColumns.contains(column.name))
                {
                    column.setMvEnabled(dataDomain.getContainer());
                }
                else if (mvIndicatorColumns.contains(column.name))
                {
                    column.setMvIndicator(dataDomain.getContainer());
                    column.clazz = String.class;
                }
                Class expectedColumnClass = expectedColumns.get(column.name);
                if (expectedColumnClass != null)
                    column.clazz = expectedColumnClass;
                else
                {
                    // It's not an expected column. Is it a qc indicator column?
                    if (!mvIndicatorColumns.contains(column.name))
                    {
                        column.load = false;
                    }
                }
                column.errorValues = ERROR_VALUE;
            }
            return loader.load();
        }
        finally
        {
            if (loader != null)
                loader.close();
        }
    }
}
