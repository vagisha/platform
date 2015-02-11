package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by davebradlee on 2/7/15.
 */
public final class PrimaryTypeDomainKind extends AbstractSpecimenDomainKind
{
    private static final String NAME = "SpecimenPrimaryType";
    private static final String NAMESPACE_PREFIX = "SpecimenPrimaryType";

    public static final String ROWID = "RowId";
    public static final String CONTAINER = "Container";
    public static final String EXTERNALID = "ExternalId";
    public static final String PRIMARYLDMSCODE = "PrimaryTypeLdmsCode";
    public static final String PRIMARYLABWARECODE = "PrimaryTypeLabwareCode";
    public static final String PRIMARYTYPE = "PrimaryType";

    private static final List<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> BASE_INDICES;
    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(ROWID, JdbcType.INTEGER, 0, PropertyStorageSpec.Special.PrimaryKey, false, true, null),
            new PropertyStorageSpec(CONTAINER, JdbcType.GUID, 36, false, null),
            new PropertyStorageSpec(EXTERNALID, JdbcType.INTEGER, 0, false, null),
            new PropertyStorageSpec(PRIMARYLDMSCODE, JdbcType.VARCHAR, 5),
            new PropertyStorageSpec(PRIMARYLABWARECODE, JdbcType.VARCHAR, 5),
            new PropertyStorageSpec(PRIMARYTYPE, JdbcType.VARCHAR, 100),
        };
        BASE_PROPERTIES = Arrays.asList(props);

        PropertyStorageSpec.Index[] indices =
        {
            new PropertyStorageSpec.Index(true, EXTERNALID),
            new PropertyStorageSpec.Index(false, PRIMARYTYPE)
        };
        BASE_INDICES = new HashSet<>(Arrays.asList(indices));
    }

    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        return new LinkedHashSet<>(BASE_PROPERTIES);
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
        return new HashSet<>(BASE_INDICES);
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container, SpecimenTablesProvider provider)
    {
        return Collections.EMPTY_SET;
    }

    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(SpecimenTablesTemplate template)
    {
        return Collections.EMPTY_SET;
    }
}
