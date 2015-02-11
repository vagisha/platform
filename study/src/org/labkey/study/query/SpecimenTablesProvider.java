/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.study.model.AbstractSpecimenDomainKind;
import org.labkey.study.model.AdditiveTypeDomainKind;
import org.labkey.study.model.DerivativeTypeDomainKind;
import org.labkey.study.model.LocationDomainKind;
import org.labkey.study.model.PrimaryTypeDomainKind;
import org.labkey.study.model.SpecimenDomainKind;
import org.labkey.study.model.SpecimenEventDomainKind;
import org.labkey.study.model.VialDomainKind;

public class SpecimenTablesProvider
{
    public static final String SCHEMA_NAME = "specimentables";

    public static final String SPECIMEN_TABLENAME = "Specimen";
    public static final String VIAL_TABLENAME = "Vial";
    public static final String SPECIMENEVENT_TABLENAME = "SpecimenEvent";
    public static final String LOCATION_TABLENAME = "Site";
    public static final String PRIMARYTYPE_TABLENAME = "SpecimenPrimaryType";
    public static final String DERIVATIVETYPE_TABLENAME = "SpecimenDerivative";
    public static final String ADDITIVETYPE_TABLENAME = "SpecimenAdditive";

    private final Container _container;
    private final User _user;
    private final SpecimenTablesTemplate _template;
    final private AbstractSpecimenDomainKind _specimenDomainKind = new SpecimenDomainKind();
    final private AbstractSpecimenDomainKind _vialDomainKind;
    final private AbstractSpecimenDomainKind _specimenEventDomainKind;
    final private AbstractSpecimenDomainKind _locationDomainKind = new LocationDomainKind();
    final private AbstractSpecimenDomainKind _primaryTypeDomainKind = new PrimaryTypeDomainKind();
    final private AbstractSpecimenDomainKind _derivativeTypeDomainKind = new DerivativeTypeDomainKind();
    final private AbstractSpecimenDomainKind _additiveTypeDomainKind = new AdditiveTypeDomainKind();

    public SpecimenTablesProvider(Container container, @Nullable User user, @Nullable SpecimenTablesTemplate template)
    {
        _container = container;
        _user = user;
        _template = template;

        // Vial depends on Specimen
        String specimenDomainURI = getDomainKind(SPECIMEN_TABLENAME).generateDomainURI(SCHEMA_NAME, SPECIMEN_TABLENAME, _container, _user);
        _vialDomainKind = new VialDomainKind(specimenDomainURI);

        // SpecimenEvent depends on Vial
        String vialDomainURI = getDomainKind(VIAL_TABLENAME).generateDomainURI(SCHEMA_NAME, VIAL_TABLENAME, _container, _user);
        _specimenEventDomainKind =  new SpecimenEventDomainKind(vialDomainURI);

    }

    @Nullable
    public final Domain getDomain(String tableName, boolean create)
    {
        // if the domain doesn't exist and we're asked to create, create it
        AbstractSpecimenDomainKind domainKind = getDomainKind(tableName);
        String domainURI = domainKind.generateDomainURI(SCHEMA_NAME, tableName, _container, _user);
        Domain domain = null;

        // it's possible that another thread is attempting to create the table, so we can (rarely) get a constraint violation
        // so try again if that happens
        final int MAX_TRYS = 3;
        for (int tryCount = 0; tryCount < MAX_TRYS; tryCount += 1)
        {
            domain = PropertyService.get().getDomain(_container, domainURI);
            if (null == domain && create)
            {
                try
                {
                    domain = PropertyService.get().createDomain(_container, domainURI, domainKind.getKindName());

                    // Add properties for all required fields
                    for (PropertyStorageSpec propSpec : domainKind.getBaseProperties())
                    {
                        DomainProperty prop = domain.addProperty(propSpec);
                        prop.setRequired(true);
                    }
                    if (null != _template)
                    {
                        // Add optional fields to table
                        for (PropertyStorageSpec propSpec : domainKind.getPropertySpecsFromTemplate(_template))
                        {
                            domain.addProperty(propSpec);
                        }

                    }
                    domain.setPropertyForeignKeys(domainKind.getPropertyForeignKeys(_container, this));
                    domain.save(_user);
                    break;      // Sucessfully created domain, so we're done
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new RuntimeException(e);
                }
                catch (RuntimeSQLException e)
                {
                    if (tryCount + 1 < MAX_TRYS)
                        continue;     // try again
                    throw e;
                }
            }
            else
            {
                break;  // No need to create domain so we're done
            }
        } // for MAX-TRYS
        return domain;
    }

    @NotNull
    public TableInfo createTableInfo(String tableName)
    {
        Domain domain = getDomain(tableName, true);
        if (null == domain)
            throw new IllegalStateException("Unable to create domain for table '" + tableName + "'");
        return createTableInfo(domain);
    }

    @Nullable
    public TableInfo getTableInfoIfExists(String tableName)
    {
        Domain domain = getDomain(tableName, false);
        if (null != domain)
            return createTableInfo(domain);
        return null;
    }

    public void deleteTables()
    {
        try
        {
            Domain domain = getDomain(SPECIMENEVENT_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
            domain = getDomain(VIAL_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
            domain = getDomain(SPECIMEN_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
            domain = getDomain(LOCATION_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
            domain = getDomain(PRIMARYTYPE_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
            domain = getDomain(DERIVATIVETYPE_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
            domain = getDomain(ADDITIVETYPE_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
        }
        catch (DomainNotFoundException e)
        {
        }
    }

    public void addTableIndices(String tableName)
    {
        Domain domain = getDomain(tableName, false);
        if (null != domain)
            StorageProvisioner.addOrDropTableIndices(domain, true);
    }

    public void dropTableIndices(String tableName)
    {
        Domain domain = getDomain(tableName, false);
        if (null != domain)
            StorageProvisioner.addOrDropTableIndices(domain, false);
    }

    private AbstractSpecimenDomainKind getDomainKind(String tableName)
    {
        if (SPECIMEN_TABLENAME.equalsIgnoreCase(tableName))
            return _specimenDomainKind;

        if (VIAL_TABLENAME.equalsIgnoreCase(tableName))
            return _vialDomainKind;

        if (SPECIMENEVENT_TABLENAME.equalsIgnoreCase(tableName))
            return _specimenEventDomainKind;

        if (LOCATION_TABLENAME.equalsIgnoreCase(tableName))
            return _locationDomainKind;

        if (PRIMARYTYPE_TABLENAME.equalsIgnoreCase(tableName))
            return _primaryTypeDomainKind;

        if (DERIVATIVETYPE_TABLENAME.equalsIgnoreCase(tableName))
            return _derivativeTypeDomainKind;

        if (ADDITIVETYPE_TABLENAME.equalsIgnoreCase(tableName))
            return _additiveTypeDomainKind;

        throw new IllegalStateException("Unknown domain kind: " + tableName);
    }

    @NotNull
    private TableInfo createTableInfo(@NotNull Domain domain)
    {
        return StorageProvisioner.createTableInfo(domain);
    }
}

