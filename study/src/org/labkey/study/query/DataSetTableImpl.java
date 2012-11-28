/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.DataSetTable;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.SpecimenForeignKey;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Wraps a DatasetSchemaTableInfo and makes it Query-ized. Represents a single dataset's data */
public class DataSetTableImpl extends FilteredTable implements DataSetTable
{
    public static final String QCSTATE_ID_COLNAME = "QCState";
    public static final String QCSTATE_LABEL_COLNAME = "QCStateLabel";
    StudyQuerySchema _schema;
    DataSetDefinition _dsd;
    TableInfo _fromTable;

    public DataSetTableImpl(final StudyQuerySchema schema, DataSetDefinition dsd)
    {
        super(dsd.getTableInfo(schema.getUser(), schema.getMustCheckPermissions()));
        setDescription("Contains up to one row of " + dsd.getLabel() + " data for each " +
                dsd.getKeyTypeDescription() + " combination.");
        _schema = schema;
        _dsd = dsd;
        _title = dsd.getLabel();

        List<FieldKey> defaultVisibleCols = new ArrayList<FieldKey>();

        HashSet<String> standardURIs = new HashSet<String>();
        for (PropertyDescriptor pd :  DataSetDefinition.getStandardPropertiesSet())
            standardURIs.add(pd.getPropertyURI());

        ActionURL updateURL = new ActionURL(DatasetController.UpdateAction.class, dsd.getContainer());
        updateURL.addParameter("datasetId", dsd.getDataSetId());
        setUpdateURL(new DetailsURL(updateURL, Collections.singletonMap("lsid", "lsid")));

        ActionURL insertURL = new ActionURL(DatasetController.InsertAction.class, getContainer());
        insertURL.addParameter(DataSetDefinition.DATASETKEY, dsd.getDataSetId());
        setInsertURL(new DetailsURL(insertURL));

        ActionURL gridURL = new ActionURL(StudyController.DatasetAction.class, dsd.getContainer());
        gridURL.addParameter(DataSetDefinition.DATASETKEY, dsd.getDataSetId());
        setGridURL(new DetailsURL(gridURL));

//        ActionURL importURL = new ActionURL(StudyController.ShowImportDatasetAction.class, dsd.getContainer());
        ActionURL importURL = new ActionURL(StudyController.ImportAction.class, dsd.getContainer());
        importURL.addParameter(DataSetDefinition.DATASETKEY, dsd.getDataSetId());
        setImportURL(new DetailsURL(importURL));

        ActionURL deleteRowsURL = new ActionURL(StudyController.DeleteDatasetRowsAction.class, dsd.getContainer());
        setDeleteURL(new DetailsURL(deleteRowsURL));

        String subjectColName = StudyService.get().getSubjectColumnName(dsd.getContainer());
        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            String name = baseColumn.getName();
            if (subjectColName.equalsIgnoreCase(name))
            {
                ColumnInfo column = new AliasedColumn(this, subjectColName, baseColumn);
                column.setInputType("text");
                // TODO, need a way for a lookup to have a "text" input
                column.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        DataColumn dataColumn = new DataColumn(colInfo, false);
                        dataColumn.setInputType("text");
                        return dataColumn;
                    }
                });
                // Issue 15791: R labkey.data object contains both lookup value and foreign key
                // Use self as display column for participant id to avoid using ParticipantId/ParticipantId as display column.
                column.setDisplayField(column);

                column.setFk(new LookupForeignKey(subjectColName)
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        // Ideally we could just ask the schema for the ParticipantTable (e.g., _schema.getTable(...)),
                        // but we need to pass arguments to ParticipantTable constructor to hide datasets.
                        TableInfo table = new ParticipantTable(_schema, true);
                        table.overlayMetadata(StudyService.get().getSubjectTableName(_schema.getContainer()), schema, new ArrayList<QueryException>());
                        ((ParticipantTable)table).afterConstruct();
                        return table;
                    }

                    @Override
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        return super.getURL(parent, true);
                    }
                });

                if (DemoMode.isDemoMode(schema.getContainer(), schema.getUser()))
                {
                    column.setDisplayColumnFactory(new DisplayColumnFactory() {
                        @Override
                        public DisplayColumn createRenderer(ColumnInfo column)
                        {
                            return new PtidObfuscatingDisplayColumn(column);
                        }
                    });
                }

                addColumn(column);
                if (isVisibleByDefault(column))
                    defaultVisibleCols.add(FieldKey.fromParts(column.getName()));
            }
            else if (getRealTable().getColumn(baseColumn.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX) != null)
            {
                // If this is the value column that goes with an OORIndicator, add the special OOR options
                OORDisplayColumnFactory.addOORColumns(this, baseColumn, getRealTable().getColumn(baseColumn.getName() +
                        OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX));
                if (isVisibleByDefault(baseColumn))
                    defaultVisibleCols.add(FieldKey.fromParts(baseColumn.getName()));
            }
            else if (baseColumn.getName().toLowerCase().endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    getRealTable().getColumn(baseColumn.getName().substring(0, baseColumn.getName().length() - OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.length())) != null)
            {
                // If this is an OORIndicator and there's a matching value column in the same table, don't add this column
            }
            else if (baseColumn.getName().equalsIgnoreCase("Created") || baseColumn.getName().equalsIgnoreCase("Modified") ||
                baseColumn.getName().equalsIgnoreCase("CreatedBy") || baseColumn.getName().equalsIgnoreCase("ModifiedBy")
            )
            {
                ColumnInfo c = addWrapColumn(baseColumn);
                if (baseColumn.getName().equalsIgnoreCase("CreatedBy") || baseColumn.getName().equalsIgnoreCase("ModifiedBy"))
                    UserIdQueryForeignKey.initColumn(schema.getUser(), schema.getContainer(), c, true);
                c.setUserEditable(false);
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
            }
            else if (baseColumn.getName().equalsIgnoreCase("SequenceNum") && _schema.getStudy().getTimepointType() != TimepointType.VISIT)
            {
                // wrap the sequencenum column and set a format to prevent scientific notation, since the sequencenum values
                // for date-based studies can be quite large (e.g., 20091014).
                addWrapColumn(baseColumn).setFormat("#");
                //Don't add to visible cols...
            }
            else if (baseColumn.getName().equalsIgnoreCase("VisitRowId")||baseColumn.getName().equalsIgnoreCase("Dataset"))
            {
                addWrapColumn(baseColumn);
            }
            else if (baseColumn.getName().equalsIgnoreCase(QCSTATE_ID_COLNAME))
            {
                ColumnInfo qcStateColumn = new AliasedColumn(this, QCSTATE_ID_COLNAME, baseColumn);
                qcStateColumn.setFk(new LookupForeignKey("RowId")
                    {
                        public TableInfo getLookupTableInfo()
                        {
                            // Go through the schema so that metadata is overlaid
                            return _schema.getTable(StudyQuerySchema.QCSTATE_TABLE_NAME);
                        }
                    });

                qcStateColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new QCStateDisplayColumn(colInfo);
                    }
                });

                qcStateColumn.setDimension(false);

                addColumn(qcStateColumn);
                // Hide the QCState column if the study doesn't have QC states defined. Otherwise, don't hide it
                // but don't include it in the default set of columns either
                if (!StudyManager.getInstance().showQCStates(_schema.getContainer()))
                    qcStateColumn.setHidden(true);
            }
            else if ("ParticipantSequenceNum".equalsIgnoreCase(name))
            {
                // Add a copy of the ParticipantSequenceNum column without the FK so we can get the value easily when materializing to temp tables:
                addWrapColumn(baseColumn).setHidden(true);
                ColumnInfo pvColumn = new AliasedColumn(this, StudyService.get().getSubjectVisitColumnName(dsd.getContainer()), baseColumn);//addWrapColumn(baseColumn);
                pvColumn.setFk(new LookupForeignKey("ParticipantSequenceNum")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new ParticipantVisitTable(_schema, true);
                    }
                });
                pvColumn.setIsUnselectable(true);
                pvColumn.setUserEditable(false);
                pvColumn.setShownInInsertView(false);
                pvColumn.setShownInUpdateView(false);
                pvColumn.setDimension(false);
                addColumn(pvColumn);
            }
            else
            {
                ColumnInfo col = addWrapColumn(baseColumn);

                // When copying a column, the hidden bit is not propagated, so we need to do it manually
                if (baseColumn.isHidden() || !isVisibleByDefault(baseColumn))
                    col.setHidden(true);

                String propertyURI = col.getPropertyURI();
                if (null != propertyURI && !standardURIs.contains(propertyURI))
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyURI, schema.getContainer());
                    if (null != pd && pd.getLookupQuery() != null)
                        col.setFk(new PdLookupForeignKey(schema.getUser(), pd, schema.getContainer()));
                    
                    if (pd != null && pd.getPropertyType() == PropertyType.MULTI_LINE)
                    {
                        col.setDisplayColumnFactory(new DisplayColumnFactory() {
                            public DisplayColumn createRenderer(ColumnInfo colInfo)
                            {
                                DataColumn dc = new DataColumn(colInfo);
                                dc.setPreserveNewlines(true);
                                return dc;
                            }
                        });
                    }
                }
                if (isVisibleByDefault(col))
                    defaultVisibleCols.add(FieldKey.fromParts(col.getName()));

                // Add a magic lookup to an "GlobalUniqueId" columns that targets the SpecimenDetails query based on the
                // GlobalUniqueId value instead of the real PK, which is RowId
                if (AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equalsIgnoreCase(col.getName()) && col.getJdbcType() == JdbcType.VARCHAR && col.getFk() == null)
                {
                    col.setFk(new LookupForeignKey(SpecimenDetailTable.GLOBAL_UNIQUE_ID_COLUMN_NAME)
                    {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            SpecimenDetailTable table = (SpecimenDetailTable)_schema.getTable(StudyQuerySchema.SPECIMEN_DETAIL_TABLE_NAME);
                            table.addCondition(new SimpleFilter(FieldKey.fromParts("Container"), _schema.getContainer().getId()));
                            return table;
                        }
                    });
                }
            }
        }


        ColumnInfo lsidColumn = getColumn("LSID");
        lsidColumn.setHidden(true);
        lsidColumn.setKeyField(true);
        lsidColumn.setShownInInsertView(false);
        lsidColumn.setShownInUpdateView(false);
        getColumn("SourceLSID").setHidden(true);

        ColumnInfo autoJoinColumn = new AliasedColumn(this, "DataSets", _rootTable.getColumn("ParticipantId"));
        autoJoinColumn.setDescription("Contains lookups to each DataSet that can be joined by the " + _dsd.getLabel() + " DataSet's '" + _dsd.getKeyTypeDescription() + "' combination.");
        autoJoinColumn.setKeyField(false);
        autoJoinColumn.setIsUnselectable(true);
        autoJoinColumn.setUserEditable(false);
        autoJoinColumn.setLabel("DataSets");
        final FieldKey sequenceNumFieldKey = new FieldKey(null, "SequenceNum");
        final FieldKey keyFieldKey = new FieldKey(null, "_Key");
        AbstractForeignKey autoJoinFk = new AbstractForeignKey()
        {
            @Override
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;

                DataSetAutoJoinTable table = new DataSetAutoJoinTable(schema, DataSetTableImpl.this.getDatasetDefinition(), parent, getRemappedField(sequenceNumFieldKey), getRemappedField(keyFieldKey));
                return table.getColumn(displayField);
            }

            @Override
            public TableInfo getLookupTableInfo()
            {
                return new DataSetAutoJoinTable(schema, DataSetTableImpl.this.getDatasetDefinition(), null, null, null);
            }

            @Override
            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        };
        autoJoinFk.addSuggested(sequenceNumFieldKey);
        autoJoinFk.addSuggested(keyFieldKey);
        autoJoinColumn.setFk(autoJoinFk);
        addColumn(autoJoinColumn);

        setDefaultVisibleColumns(defaultVisibleCols);

        // Don't show sequence num for date-based studies
        if (!dsd.getStudy().getTimepointType().isVisitBased())
        {
            getColumn("SequenceNum").setHidden(true);
            getColumn("SequenceNum").setShownInInsertView(false);
            getColumn("SequenceNum").setShownInDetailsView(false);
            getColumn("SequenceNum").setShownInUpdateView(false);
        }

        // columns from the ParticipantVisit table

        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        if (_schema.getStudy().getTimepointType() == TimepointType.DATE)
        {
            ColumnInfo dayColumn = new AliasedColumn(this, "Day", participantVisit.getColumn("Day"));
            dayColumn.setUserEditable(false);
            dayColumn.setDimension(false);
            dayColumn.setMeasure(false);
            addColumn(dayColumn);
        }

        ColumnInfo visitRowId = new AliasedColumn(this, "VisitRowId", participantVisit.getColumn("VisitRowId"));
        visitRowId.setName("VisitRowId");
        visitRowId.setHidden(true);
        visitRowId.setUserEditable(false);
        visitRowId.setShownInInsertView(false);
        visitRowId.setShownInUpdateView(false);
        visitRowId.setMeasure(false);
        addColumn(visitRowId);

        if (_dsd.isAssayData())
        {
            TableInfo assayResultTable = createAssayResultTable();
            if (assayResultTable != null)
            {
                for (final ColumnInfo columnInfo : assayResultTable.getColumns())
                {
                    if (getColumn(columnInfo.getName()) == null)
                    {
                        ExprColumn wrappedColumn = new ExprColumn(this, columnInfo.getName(), columnInfo.getValueSql(ExprColumn.STR_TABLE_ALIAS + "_AR"), columnInfo.getJdbcType())
                        {
                            @Override
                            public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
                            {
                                super.declareJoins(parentAlias, map);
                                columnInfo.declareJoins(getAssayResultAlias(parentAlias), map);
                            }
                        };
                        wrappedColumn.copyAttributesFrom(columnInfo);
                        ForeignKey fk = wrappedColumn.getFk();
                        if (fk != null && fk instanceof SpecimenForeignKey)
                            ((SpecimenForeignKey) fk).setTargetStudyOverride(dsd.getContainer());
                        addColumn(wrappedColumn);
                    }
                }

                for (FieldKey fieldKey : assayResultTable.getDefaultVisibleColumns())
                {
                    if (!defaultVisibleCols.contains(fieldKey) && !defaultVisibleCols.contains(FieldKey.fromParts(fieldKey.getName())))
                    {
                        defaultVisibleCols.add(fieldKey);
                    }
                }

                // Remove the target study column from the dataset version of the table - it's already scoped to the
                // relevant study so don't clutter the UI with it
                for (FieldKey fieldKey : defaultVisibleCols)
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(fieldKey.getName()))
                    {
                        defaultVisibleCols.remove(fieldKey);
                        break;
                    }
                }
                ExpProtocol protocol = _dsd.getAssayProtocol();
                AssayProvider provider = AssayService.get().getProvider(protocol);
                defaultVisibleCols.add(new FieldKey(provider.getTableMetadata(protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.Name.toString()));
                defaultVisibleCols.add(new FieldKey(provider.getTableMetadata(protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.Comments.toString()));
            }
        }
    }

    @Override
    public DataSet getDataSet()
    {
        return _dsd;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result != null)
        {
            return result;
        }

        // Resolve 'ParticipantSequenceKey' to 'ParticipantSequenceNum' for compatibility with versions <12.2.
        if ("ParticipantSequenceKey".equalsIgnoreCase(name))
            return getColumn("ParticipantSequenceNum");

        FieldKey fieldKey = null;

        // Be backwards compatible with the old field keys for these properties.
        // We used to flatten all of the different domains/tables on the assay side into a row in the dataset,
        // so transform to do a lookup instead
        ExpProtocol protocol = _dsd.getAssayProtocol();
        if (protocol != null)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            FieldKey runFieldKey = provider == null ? null : provider.getTableMetadata(protocol).getRunFieldKeyFromResults();
            if (name.toLowerCase().startsWith("run"))
            {
                String runProperty = name.substring("run".length()).trim();
                if (runProperty.length() > 0 && runFieldKey != null)
                {
                    fieldKey = new FieldKey(runFieldKey, runProperty);
                }
            }
            else if (name.toLowerCase().startsWith("batch"))
            {
                String batchPropertyName = name.substring("batch".length()).trim();
                if (batchPropertyName.length() > 0 && runFieldKey != null)
                {
                    fieldKey = new FieldKey(new FieldKey(runFieldKey, "Batch"), batchPropertyName);
                }
            }
            else if (name.toLowerCase().startsWith("analyte"))
            {
                String analytePropertyName = name.substring("analyte".length()).trim();
                if (analytePropertyName.length() > 0)
                {
                    fieldKey = FieldKey.fromParts("Analyte", analytePropertyName);
                    Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
                    result = columns.get(fieldKey);
                    if (result != null)
                    {
                        return result;
                    }
                    fieldKey = FieldKey.fromParts("Analyte", "Properties", analytePropertyName);
                }
            }
            else
            {
                // Check the name to prevent infinite recursion
                if (!name.equalsIgnoreCase("Properties"))
                {
                    // Try looking for it as a NAb specimen property
                    fieldKey = FieldKey.fromParts("Properties", "SpecimenLsid", "Property", name);
                    Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
                    result = columns.get(fieldKey);
                    if (result != null)
                    {
                        return result;
                    }
                }
            }
        }
        if (fieldKey == null && !name.equalsIgnoreCase("Properties"))
        {
            fieldKey = FieldKey.fromParts("Properties", name);
        }

        if (fieldKey != null)
        {
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
            result = columns.get(fieldKey);
            if (null != result)
            {
                result.setFieldKey(new FieldKey(null,name));
                result.setAlias("_DataSetTableImpl_resolvefield$" + result.getAlias());
            }
        }
        return result;
    }

    @Override
    public Domain getDomain()
    {
        if (_dsd != null)
            return _dsd.getDomain();
        return null;
    }

    private String getAssayResultAlias(String mainAlias)
    {
        return mainAlias + "_AR";
    }




    @NotNull
    private SQLFragment _getFromSQL(String alias, boolean includeParticipantVisit)
    {
        if (!includeParticipantVisit && !_dsd.isAssayData())
            return super.getFromSQL(alias);

        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();

        SQLFragment from = new SQLFragment();
        from.append("(SELECT DS.*, PV.VisitRowId");
        if (_schema.getStudy().getTimepointType() == TimepointType.DATE)
            from.append(", PV.Day");
        from.append("\nFROM ").append(super.getFromSQL("DS")).append(" LEFT OUTER JOIN ").append(participantVisit.getFromSQL("PV")).append("\n" +
                " ON DS.ParticipantId=PV.ParticipantId AND DS.SequenceNum=PV.SequenceNum AND PV.Container = '" + _schema.getContainer().getId() + "') AS ").append(alias);

        if (_dsd.isAssayData())
        {
            // Join in Assay-side data to make it appear as if it's in the dataset table itself 
            String assayResultAlias = getAssayResultAlias(alias);
            TableInfo assayResultTable = createAssayResultTable();
            // Check if assay design has been deleted
            if (assayResultTable != null)
            {
                from.append(" LEFT OUTER JOIN ").append(assayResultTable.getFromSQL(assayResultAlias)).append("\n");
                from.append(" ON ").append(assayResultAlias).append(".").append(assayResultTable.getPkColumnNames().get(0)).append(" = ");
                from.append(alias).append(".").append(getSqlDialect().getColumnSelectName(_dsd.getKeyPropertyName()));
            }
        }

        return from;
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return _getFromSQL(alias, true);
    }


    @Override
    public SQLFragment getFromSQL(String alias, Set<FieldKey> cols)
    {
        boolean includePV = false;
        if (cols.contains(new FieldKey(null, "VisitRowId")))
            includePV = true;
        else if (_schema.getStudy().getTimepointType() == TimepointType.DATE && cols.contains(new FieldKey(null, "Day")))
            includePV = true;
        return _getFromSQL(alias, includePV);
    }


    private TableInfo createAssayResultTable()
    {
        ExpProtocol protocol = _dsd.getAssayProtocol();
        if (protocol == null)
        {
            return null;
        }
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
        {
            // Provider must have been in a module that's no longer available
            return null;
        }
        AssayProtocolSchema schema = provider.createProtocolSchema(_schema.getUser(), protocol.getContainer(), protocol, getContainer());
        ContainerFilterable result = schema.createDataTable(false);
        if (result != null)
        {
            result.setContainerFilter(ContainerFilter.EVERYTHING);
        }
        return result;
    }

    @Override
    public ContainerContext getContainerContext()
    {
        return _dsd != null ? _dsd.getContainer() : null;
    }

    @Override
    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        // First apply all the metadata from study.StudyData so that it doesn't have to be duplicated for
        // every dataset
        super.overlayMetadata(StudyQuerySchema.STUDY_DATA_TABLE_NAME, schema, errors);

        if (!_dsd.getName().equalsIgnoreCase(tableName))
        {
            super.overlayMetadata(_dsd.getName(), schema, errors);
        }

        if (_dsd.getLabel() != null && !_dsd.getLabel().equalsIgnoreCase(tableName))
        {
            super.overlayMetadata(_dsd.getLabel(), schema, errors);
        }

        // Then include the specific overrides for this dataset
        super.overlayMetadata(tableName, schema, errors);
    }

    private class QCStateDisplayColumn extends DataColumn
    {
        private Map<Integer, QCState> _qcStateCache;
        public QCStateDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        public String getFormattedValue(RenderContext ctx)
        {
            Object value = getValue(ctx);
            StringBuilder formattedValue = new StringBuilder(super.getFormattedValue(ctx));
            if (value != null && value instanceof Integer)
            {
                QCState state = getStateCache(ctx).get((Integer) value);
                if (state != null && state.getDescription() != null)
                    formattedValue.append(PageFlowUtil.helpPopup("QC State " + state.getLabel(), state.getDescription()));
            }
            return formattedValue.toString();
        }

        private Map<Integer, QCState> getStateCache(RenderContext ctx)
        {
            if (_qcStateCache == null)
            {
                _qcStateCache = new HashMap<Integer, QCState>();
                QCState[] states = StudyManager.getInstance().getQCStates(ctx.getContainer());
                for (QCState state : states)
                    _qcStateCache.put(state.getRowId(), state);
            }
            return _qcStateCache;
        }
    }

    private static final Set<String> defaultHiddenCols = new CaseInsensitiveHashSet("VisitRowId", "Created", "CreatedBy", "ModifiedBy", "Modified", "lsid", "SourceLsid");
    private boolean isVisibleByDefault(ColumnInfo col)
    {
        // If this is a server-managed key, or an assay-backed dataset, don't include the key column in the default
        // set of visible columns
        if ((_dsd.getKeyManagementType() != DataSet.KeyManagementType.None || _dsd.isAssayData()) &&
                col.getName().equals(_dsd.getKeyPropertyName()))
            return false;
        return (!col.isHidden() && !col.isUnselectable() && !defaultHiddenCols.contains(col.getName()));
    }


    protected TableInfo getFromTable()
    {
        if (_fromTable == null)
        {
            _fromTable = _dsd.getTableInfo(_schema.getUser(), _schema.getMustCheckPermissions());
        }
        return _fromTable;
    }

    public DataSetDefinition getDatasetDefinition()
    {
        return _dsd;
    }

    /**
     * In order to discourage the user from selecting data from deeply nested datasets, we hide
     * the "ParticipantID", "ParticipantVisit", and "DataSets" columns when the user could just as easily find
     * the same data further up the tree.
     */
    public void hideParticipantLookups()
    {
        ColumnInfo col = getColumn(StudyService.get().getSubjectColumnName(_dsd.getContainer()));
        if (col != null)
            col.setHidden(true);

        col = getColumn(StudyService.get().getSubjectVisitColumnName(_dsd.getContainer()));
        if (col != null)
            col.setHidden(true);

        col = getColumn("DataSets");
        if (col != null)
            col.setHidden(true);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _schema.getUser();
        DataSet def = getDatasetDefinition();
        if (!def.canWrite(user))
            return null;
        return new DatasetUpdateService(this);
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        DataSet def = getDatasetDefinition();
        if (ReadPermission.class.isAssignableFrom(perm))
            return def.canRead(user);
        if (InsertPermission.class.isAssignableFrom(perm) || UpdatePermission.class.isAssignableFrom(perm) || DeletePermission.class.isAssignableFrom(perm))
            return def.canWrite(user);
        return false;
    }

    @Override
    public Container getContainer()
    {
        return _dsd.getContainer();
    }

    @Override
    public String getDefaultDateFormat()
    {
        return StudyManager.getInstance().getDefaultDateFormatString(getContainer());
    }

    @Override
    public String getDefaultNumberFormat()
    {
        return StudyManager.getInstance().getDefaultNumberFormatString(getContainer());
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    @Override
    public Map<FieldKey, ColumnInfo> getExtendedColumns(boolean hidden)
    {
        Map<FieldKey, ColumnInfo> columns = super.getExtendedColumns(hidden);

        if (_dsd.isAssayData())
        {
            TableInfo assayResultTable = createAssayResultTable();
            if (assayResultTable != null)
            {
                columns = new LinkedHashMap<FieldKey, ColumnInfo>(columns);
                columns.putAll(assayResultTable.getExtendedColumns(hidden));
            }
        }

        return columns;
    }
}
