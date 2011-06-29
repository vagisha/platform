/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.study.model;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 10, 2011
 * Time: 3:16:27 PM
 */
public class ParticipantGroup extends Entity
{
    private int _rowId;
    private String _label;
    private int _classificationId;  // fk to participant classification
    private String _classificationLabel;

    private List<String> _participantIds = new ArrayList<String>();

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public int getClassificationId()
    {
        return _classificationId;
    }

    public void setClassificationId(int classificationId)
    {
        _classificationId = classificationId;
    }

    public List<String> getParticipantIds()
    {
        return _participantIds;
    }

    public void setParticipantIds(List<String> participantIds)
    {
        _participantIds = participantIds;
    }

    public void addParticipantId(String participantId)
    {
        _participantIds.add(participantId);
    }

    public String getClassificationLabel()
    {
        return _classificationLabel;
    }

    public void setClassificationLabel(String classificationLabel)
    {
        _classificationLabel = classificationLabel;
    }

    public Pair<FieldKey, String> getFilterColAndValue(Container container)
    {
        FieldKey key = FieldKey.fromParts(StudyService.get().getSubjectColumnName(container), getClassificationLabel());
        return new Pair<FieldKey, String>(key, getLabel());
    }

    public String getURLFilterParameterName(FieldKey filterColumn, String dataRegionName)
    {
        StringBuilder filterKey = new StringBuilder();
        if (!StringUtils.isEmpty(dataRegionName))
        {
            filterKey.append(dataRegionName);
            filterKey.append(".");
        }
        filterKey.append(filterColumn);
        return filterKey.toString();
    }

    public ActionURL addURLFilter(ActionURL url, Container container, String dataRegionName)
    {
        Pair<FieldKey, String> filterColAndValue = getFilterColAndValue(container);
        url.deleteFilterParameters(getURLFilterParameterName(filterColAndValue.getKey(), dataRegionName));
        url.addFilter(dataRegionName, filterColAndValue.getKey(), CompareType.EQUAL, filterColAndValue.getValue());
        return url;
    }

    public ActionURL removeURLFilter(ActionURL url, Container container, String dataRegionName)
    {
        Pair<FieldKey, String> filterColAndValue = getFilterColAndValue(container);
        url.deleteFilterParameters(getURLFilterParameterName(filterColAndValue.getKey(), dataRegionName));
        return url;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParticipantGroup that = (ParticipantGroup) o;

        if (_rowId != that._rowId) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return _rowId;
    }
}
