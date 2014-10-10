/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.api.exp.property;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: May 4, 2007
 * Time: 10:55:27 AM
 * <p/>
 * Base class for building GWT editors that edit domains
 *
 * @see org.labkey.api.gwt.client.ui.PropertiesEditor in InternalGWT
 */
public class DomainEditorServiceBase extends BaseRemoteService
{
    public DomainEditorServiceBase(ViewContext context)
    {
        super(context);
    }


    // paths
    public List<String> getContainers()
    {
        try
        {
            List<Container> set = ContainerManager.getAllChildren(ContainerManager.getRoot(), getUser());
            List<String> list = new ArrayList<>();
            for (Container c : set)
            {
                if (c.isRoot())
                    continue;
                list.add(c.getPath());
            }
            Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
            return list;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    public List<String> getSchemas(String containerId)
    {
        try
        {
            Container c = getContainer(containerId);
            if (c == null)
            {
                return Collections.emptyList();
            }
            Set<SchemaKey> schemaPaths = DefaultSchema.get(getUser(), c).getUserSchemaPaths(false);

            List<String> names = new ArrayList<>();
            for (SchemaKey schemaPath : schemaPaths)
            {
                names.add(schemaPath.toString());
            }

            return new ArrayList<>(names);
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    public Map<String, GWTPropertyDescriptor> getTablesForLookup(String containerId, String schemaName)
    {
        try
        {
            Map<String, GWTPropertyDescriptor> availableQueries = new HashMap<>();  //  GWT: TreeMap does not work
            Container c = getContainer(containerId);
            if (c == null)
            {
                return availableQueries;
            }
            UserSchema schema = QueryService.get().getUserSchema(getUser(), c, schemaName);
            if (schema == null)
                return availableQueries;

            for (String name : schema.getTableAndQueryNames(false))
            {
                TableInfo table;
                try
                {
                    table = schema.getTable(name);
                }
                catch (QueryException x)
                {
                    continue;
                }
                if (table == null)
                    continue;

                // Accept lookups with 1 PK, but if a second PK is Container, that's acceptable, too
                List<ColumnInfo> pkColumns = table.getPkColumns();
                if (pkColumns.size() < 1 || pkColumns.size() > 2)
                    continue;
                ColumnInfo pk = pkColumns.get(0);
                if (pkColumns.size() == 2)
                {
                    if (pk.getName().equalsIgnoreCase("container"))
                        pk = pkColumns.get(1);
                    else if (!pkColumns.get(1).getName().equalsIgnoreCase("container"))
                        continue;
                }
                PropertyType type = PropertyType.getFromClass(pk.getJavaObjectClass());
                GWTPropertyDescriptor pd = new GWTPropertyDescriptor(pk.getName(), type.getTypeUri());
                availableQueries.put(name, pd);
            }
            return availableQueries;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    @Nullable
    private Container getContainer(String containerId)
    {
        Container container = null;
        if (containerId == null || containerId.length() == 0)
            container = getContainer();
        else
        {
            if (GUID.isGUID(containerId))
                container = ContainerManager.getForId(containerId);
            if (null == container)
                container = ContainerManager.getForPath(containerId);
        }

        if (container != null && !container.hasPermission(getUser(), ReadPermission.class))
        {
            throw new UnauthorizedException();
        }

        return container;
    }

    public GWTDomain getDomainDescriptor(String typeURI)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, getContainer());
    }

    public GWTDomain getDomainDescriptor(String typeURI, String domainContainerId)
    {
        Container domainContainer = ContainerManager.getForId(domainContainerId);
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, domainContainer);
    }

    /** @return Errors encountered during the save attempt */
    @NotNull
    public List<String> updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update)
    {
        return DomainUtil.updateDomainDescriptor(orig, update, getContainer(), getUser());
    }

    protected GWTDomain<? extends GWTPropertyDescriptor> getDomainDescriptor(String typeURI, Container domainContainer)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, domainContainer);
    }
}
