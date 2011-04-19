/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.property.DomainProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Oct 25, 2007
 * Time: 10:06:18 AM
 */
public class QcAwarePropertyForeignKey extends PropertyForeignKey
{
    private final TableInfo _baseTable;
    private final QcMetadata _metadata;

    
    public QcAwarePropertyForeignKey(PropertyDescriptor[] pds, TableInfo baseTable, QuerySchema schema)
    {
        super(getDisplayPds(pds).getDisplayProperties(), schema);
        _baseTable = baseTable;
        // It's annoying that we have to call 'getDisplayPds()' twice, but using
        // a ThreadLocal or the like seems like overkill.
        _metadata = getDisplayPds(pds);
    }

    public QcAwarePropertyForeignKey(DomainProperty[] dps, TableInfo baseTable, QuerySchema schema)
    {
        this(getPropertyDescriptors(dps), baseTable, schema);
    }

    private static PropertyDescriptor[] getPropertyDescriptors(DomainProperty[] domainProperties)
    {
        PropertyDescriptor[] propertyDescriptors = new PropertyDescriptor[domainProperties.length];
        for (int i = 0; i < domainProperties.length; i++)
            propertyDescriptors[i] = domainProperties[i].getPropertyDescriptor();
        return propertyDescriptors;
    }

    private static QcMetadata getDisplayPds(PropertyDescriptor[] pds)
    {
        final Map<String, PropertyDescriptor> nameToPropertyMap = new HashMap<String, PropertyDescriptor>();
        for (PropertyDescriptor pd : pds)
            nameToPropertyMap.put(pd.getName(), pd);
        QcMetadata metadata = new QcMetadata();

        for (PropertyDescriptor displayPd : pds)
        {
            PropertyDescriptor oorIndicatorPd = nameToPropertyMap.get(displayPd.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX);
            if (oorIndicatorPd != null)
            {
                metadata.addOORPropertyDescriptor(displayPd, oorIndicatorPd);
            }
            else if (!(displayPd.getName().toLowerCase().endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    nameToPropertyMap.containsKey(displayPd.getName().substring(0, displayPd.getName().length() - OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.length()))))
            {
                metadata.addNonOORPropetyDescriptor(displayPd);
            }
        }
        return metadata;
    }

    @Override
    protected ColumnInfo constructColumnInfo(ColumnInfo parent, FieldKey name, PropertyDescriptor pd)
    {
        OORColumnGroup group = _metadata.getOORGroupByDisplayColumn(pd);
        if (group != null)
        {
            ColumnInfo col = super.constructColumnInfo(parent, name, pd);
            col.setDisplayColumnFactory(new LateBoundOORDisplayColumnFactory(group.getBaseName()));
            return col;
        }

        group = _metadata.getOORGroupByInRangeColumn(pd);
        if (group != null)
        {
            ColumnInfo inRangeColumn = new InRangeExprColumn(_baseTable, name, group.getBaseName());
            inRangeColumn.setLabel(group.getInRangePd().getLabel());
            inRangeColumn.setFormat(group.getNumberPd().getFormat());
            return inRangeColumn;
        }

        if (pd.isMvEnabled())
        {
            // Just need to set the display column factory
            ColumnInfo col = super.constructColumnInfo(parent, name, pd);
            col.setMvColumnName(pd.getName() + MvColumn.MV_INDICATOR_SUFFIX);
            col.setDisplayColumnFactory(new MVDisplayColumnFactory());
            return col;
        }

        QcColumnGroup qcGroup = _metadata.getQcColumnGroupByIndicator(pd);
        if (qcGroup != null)
        {
            if (parent == null)
            {
                return new ColumnInfo(pd.getName());
            }
            PropertyColumn qcColumn = new PropertyColumn(pd, parent, null, _schema.getUser());
            qcColumn.setParentIsObjectId(_parentIsObjectId);
            qcColumn.setMvIndicator(true);
            qcColumn.setNullable(true);
            qcColumn.setUserEditable(false);
            return qcColumn;
        }

        return super.constructColumnInfo(parent, name, pd);
    }

    public PropertyDescriptor[] getDefaultHiddenProperties()
    {
        return _metadata.getDefaultHiddenProperties();
    }

    private static ColumnInfo getIndicatorColumn(String baseName, ColumnInfo colInfo)
    {
        return getNamedColumn(baseName, colInfo, OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX);
    }

    private static ColumnInfo getNamedColumn(String baseName, ColumnInfo colInfo, String suffix)
    {
        FieldKey thisFieldKey = FieldKey.fromString(colInfo.getName());
        List<FieldKey> keys = new ArrayList<FieldKey>();

        FieldKey otherKey = new FieldKey(thisFieldKey.getParent(), baseName + suffix);
        keys.add(otherKey);
        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(colInfo.getParentTable(), keys);
        return cols.get(otherKey);
    }

    private class LateBoundOORDisplayColumnFactory implements DisplayColumnFactory
    {
        private final String _baseName;

        public LateBoundOORDisplayColumnFactory(String baseName)
        {
            _baseName = baseName;
        }

        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            ColumnInfo oorIndicator = getIndicatorColumn(_baseName, colInfo);
            return new OutOfRangeDisplayColumn(colInfo, oorIndicator);
        }
    }

    public static class InRangeExprColumn extends ExprColumn
    {
        private final String _baseName;

        public InRangeExprColumn(TableInfo parent, FieldKey name, String baseName)
        {
            super(parent, name, null, JdbcType.INTEGER);
            _baseName = baseName;
        }

        public SQLFragment getValueSql(String tableAlias)
        {
            ColumnInfo indicatorCol = getIndicatorColumn(_baseName, this);
            ColumnInfo numberCol = getNumberColumn(_baseName, this);

            SQLFragment inRangeSQL = new SQLFragment("CASE WHEN (");
            inRangeSQL.append(indicatorCol.getValueSql(tableAlias));
            inRangeSQL.append(") IS NULL THEN (");
            inRangeSQL.append(numberCol.getValueSql(tableAlias));
            inRangeSQL.append(") ELSE NULL END");
            return inRangeSQL;
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            ColumnInfo indicatorCol = getIndicatorColumn(_baseName, this);
            if (indicatorCol == null)
            {
                throw new IllegalStateException("Could not find " + _baseName + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX + " column");
            }
            indicatorCol.declareJoins(parentAlias, map);
            ColumnInfo numberCol = getNumberColumn(_baseName, this);
            if (numberCol == null)
            {
                throw new IllegalStateException("Could not find " + _baseName + OORDisplayColumnFactory.NUMBER_COLUMN_SUFFIX + " column");
            }
            numberCol.declareJoins(parentAlias, map);
        }

        private ColumnInfo getNumberColumn(String baseName, ColumnInfo colInfo)
        {
            return getNamedColumn(baseName, colInfo, OORDisplayColumnFactory.NUMBER_COLUMN_SUFFIX);
        }
    }

    private static class OORColumnGroup
    {
        private final String _baseName;
        private final PropertyDescriptor _displayPd;
        private final PropertyDescriptor _indicatorPd;
        private final PropertyDescriptor _numberPd;
        private final PropertyDescriptor _inRangePd;

        public OORColumnGroup(PropertyDescriptor displayPd, PropertyDescriptor indicatorPd,
                              PropertyDescriptor numberPd, PropertyDescriptor inRangePd)
        {
            _baseName = displayPd.getName();
            _displayPd = displayPd;
            _indicatorPd = indicatorPd;
            _numberPd = numberPd;
            _inRangePd = inRangePd;
        }

        public PropertyDescriptor getDisplayPd()
        {
            return _displayPd;
        }

        public PropertyDescriptor getIndicatorPd()
        {
            return _indicatorPd;
        }

        public PropertyDescriptor getNumberPd()
        {
            return _numberPd;
        }

        public PropertyDescriptor getInRangePd()
        {
            return _inRangePd;
        }

        public String getBaseName()
        {
            return _baseName;
        }
    }

    private static class QcColumnGroup
    {
        private final String _baseName;
        private final PropertyDescriptor _displayPd;
        private final PropertyDescriptor _indicatorPd;
        private final PropertyDescriptor _rawValuePd;

        public QcColumnGroup(PropertyDescriptor displayPd, PropertyDescriptor indicatorPd,
                             PropertyDescriptor rawValuePd)
        {
            _baseName = displayPd.getName();
            _displayPd = displayPd;
            _indicatorPd = indicatorPd;
            _rawValuePd = rawValuePd;
        }

        public PropertyDescriptor getDisplayPd()
        {
            return _displayPd;
        }

        public PropertyDescriptor getIndicatorPd()
        {
            return _indicatorPd;
        }

        public PropertyDescriptor getRawValuePd()
        {
            return _rawValuePd;
        }

        public String getBaseName()
        {
            return _baseName;
        }
    }

    private static class QcMetadata
    {
        private final List<OORColumnGroup> _oorGroups = new ArrayList<OORColumnGroup>();
        private final List<QcColumnGroup> _qcGroups = new ArrayList<QcColumnGroup>();
        private final List<PropertyDescriptor> _additionalDisplayPds = new ArrayList<PropertyDescriptor>();

        public void addOORPropertyDescriptor(PropertyDescriptor displayPd, PropertyDescriptor indicatorPd)
        {
            PropertyDescriptor numberPd = displayPd.clone();
            numberPd.setName(displayPd.getName() + OORDisplayColumnFactory.NUMBER_COLUMN_SUFFIX);
            numberPd.setLabel(displayPd.getName() + " Number");

            PropertyDescriptor inRangePd = displayPd.clone();
            inRangePd.setName(displayPd.getName() + OORDisplayColumnFactory.IN_RANGE_COLUMN_SUFFIX);
            inRangePd.setLabel(displayPd.getName() + " In Range");
            _oorGroups.add(new OORColumnGroup(displayPd, indicatorPd, numberPd, inRangePd));
        }

        public void addNonOORPropetyDescriptor(PropertyDescriptor pd)
        {
            // May be a MV PD, but not OOR specifically
            if (pd.isMvEnabled())
            {
                PropertyDescriptor indicatorPd = pd.clone();
                indicatorPd.setName(pd.getName() + MvColumn.MV_INDICATOR_SUFFIX);
                indicatorPd.setLabel(pd.getName() + " MV Indicator");
                indicatorPd.setRangeURI(PropertyType.STRING.getTypeUri());
                indicatorPd.setMvEnabled(false);

                PropertyDescriptor rawValuePd = pd.clone();
                rawValuePd.setName(pd.getName() + RawValueColumn.RAW_VALUE_SUFFIX);
                rawValuePd.setLabel(pd.getName() + " Raw Value");
                rawValuePd.setMvEnabled(false);

                _qcGroups.add(new QcColumnGroup(pd, indicatorPd, rawValuePd));
            }
            else
            {
                _additionalDisplayPds.add(pd);
            }
        }

        public PropertyDescriptor[] getDisplayProperties()
        {
            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            pds.addAll(_additionalDisplayPds);
            for (OORColumnGroup group : _oorGroups)
            {
                pds.add(group.getDisplayPd());
                pds.add(group.getIndicatorPd());
                pds.add(group.getInRangePd());
                pds.add(group.getNumberPd());
            }
            for (QcColumnGroup group : _qcGroups)
            {
                pds.add(group.getDisplayPd());
                pds.add(group.getIndicatorPd());
                pds.add(group.getRawValuePd());
            }
            return pds.toArray(new PropertyDescriptor[pds.size()]);
        }

        public PropertyDescriptor[] getDefaultHiddenProperties()
        {
            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            for (OORColumnGroup group : _oorGroups)
            {
                pds.add(group.getIndicatorPd());
                pds.add(group.getInRangePd());
                pds.add(group.getNumberPd());
            }
            for (QcColumnGroup group : _qcGroups)
            {
                pds.add(group.getIndicatorPd());
                pds.add(group.getRawValuePd());
            }
            return pds.toArray(new PropertyDescriptor[pds.size()]);
        }

        public OORColumnGroup getOORGroupByDisplayColumn(PropertyDescriptor pd)
        {
            for (OORColumnGroup group : _oorGroups)
            {
                if (pd.getName().equals(group.getDisplayPd().getName()))
                    return group;
            }
            return null;
        }

        public OORColumnGroup getOORGroupByInRangeColumn(PropertyDescriptor pd)
        {
            for (OORColumnGroup group : _oorGroups)
            {
                if (pd.getName().equals(group.getInRangePd().getName()))
                    return group;
            }
            return null;
        }

        public QcColumnGroup getQcColumnGroup(PropertyDescriptor pd)
        {
            for (QcColumnGroup group : _qcGroups)
            {
                if (pd.getName().equals(group.getDisplayPd().getName()))
                    return group;
            }
            return null;
        }

        public QcColumnGroup getQcColumnGroupByIndicator(PropertyDescriptor pd)
        {
            for (QcColumnGroup group : _qcGroups)
            {
                if (pd.getName().equals(group.getIndicatorPd().getName()))
                    return group;
            }
            return null;
        }
    }

}
