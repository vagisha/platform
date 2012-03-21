/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.study.query;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.study.StudyService;
import org.labkey.study.SampleManager;
import org.labkey.study.model.SpecimenTypeSummary;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 14, 2012
 */
public class SpecimenPivotByRequestingLocation extends BaseSpecimenPivotTable
{
    public static final String PIVOT_BY_REQUESTING_LOCATION = "Vial Counts by Requesting Location";
    private static final String COLUMN_DESCRIPTION_FORMAT = "Number of vials of primary & derivative type %s/%s requested by %s";

    public SpecimenPivotByRequestingLocation(final StudyQuerySchema schema)
    {
        super(SpecimenReportQuery.getPivotByRequestingLocation(schema.getContainer(), schema.getUser()), schema);
        setDescription("Contains up to one row of Specimen Derivative Type totals by Requesting Location for each " + StudyService.get().getSubjectNounSingular(getContainer()) +
            "/visit combination.");

        try {

            Map<Integer, String> primaryTypeMap = getPrimaryTypeMap(getContainer());
            Map<Integer, String> derivativeTypeMap = getDerivativeTypeMap(getContainer());
            Map<Integer, String> siteMap = getSiteMap(getContainer());

            for (ColumnInfo col : getRealTable().getColumns())
            {
                // look for the primary/derivative pivot encoding
                String parts[] = col.getName().split(AGGREGATE_DELIM);

                if (parts != null && parts.length == 2)
                {
                    String types[] = parts[0].split(TYPE_DELIM);

                    if (types != null && types.length == 3)
                    {
                        int primaryId = NumberUtils.toInt(types[0]);
                        int derivativeId = NumberUtils.toInt(types[1]);
                        int siteId = NumberUtils.toInt(types[2]);

                        if (primaryTypeMap.containsKey(primaryId) && derivativeTypeMap.containsKey(derivativeId) && siteMap.containsKey(siteId))
                        {
                            wrapPivotColumn(col,
                                    COLUMN_DESCRIPTION_FORMAT,
                                    primaryTypeMap.get(primaryId),
                                    derivativeTypeMap.get(derivativeId),
                                    siteMap.get(siteId));
                        }
                    }
                }
            }
            setDefaultVisibleColumns(getDefaultVisibleColumns());
            addWrapColumn(_rootTable.getColumn("Container"));
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }
}
