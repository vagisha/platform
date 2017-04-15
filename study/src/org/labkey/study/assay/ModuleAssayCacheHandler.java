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

package org.labkey.study.assay;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.study.assay.xml.ProviderDocument;
import org.labkey.study.assay.xml.ProviderType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * User: kevink
 * Date: Dec 10, 2008 1:34:33 PM
 */

public class ModuleAssayCacheHandler implements ModuleResourceCacheHandler<Collection<ModuleAssayProvider>>
{
    public static final String DOMAINS_DIR_NAME = "domains";

    @Override
    public Collection<ModuleAssayProvider> load(@Nullable Resource dir, Module module)
    {
        Collection<ModuleAssayProvider> ret = new LinkedList<>();
        if (dir != null && dir.isCollection())
        {
            for (Resource assayProviderDir : dir.list())
            {
                if (!assayProviderDir.isCollection())
                    continue;

                // A config.xml file is required to define the file-based module assay.
                Resource configFile = assayProviderDir.find("config.xml");
                if (configFile == null || !configFile.isFile())
                    continue;

                try
                {
                    assayProviderDir.list(); // This adds a file watcher in this directory
                    ret.add(loadAssayProvider(module, assayProviderDir, configFile));
                }
                catch (Exception e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
        }
        return Collections.unmodifiableCollection(ret);
    }

    @Nullable
    @Override
    public FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return new FileSystemDirectoryListener()
        {
            @Override
            public void entryCreated(Path directory, Path entry)
            {
                AssayManager.get().clearModuleAssayCollections();
            }

            @Override
            public void entryDeleted(Path directory, Path entry)
            {
                AssayManager.get().clearModuleAssayCollections();
            }

            @Override
            public void entryModified(Path directory, Path entry)
            {
                AssayManager.get().clearModuleAssayCollections();
            }

            @Override
            public void overflow()
            {
            }
        };
    }

    private ModuleAssayProvider loadAssayProvider(Module module, Resource assayProviderDir, Resource configFile) throws IOException, ModuleResourceLoadException
    {
        String assayName = assayProviderDir.getName();

        ProviderType providerConfig = parseProvider(configFile);
        if (providerConfig == null)
            providerConfig = ProviderDocument.Factory.newInstance().addNewProvider();

        if (providerConfig.isSetName())
            assayName = providerConfig.getName();
        else
            providerConfig.setName(assayName);

        return new ModuleAssayProvider(assayName, module, assayProviderDir, providerConfig);
    }

    private ProviderType parseProvider(Resource configFile) throws IOException, ModuleResourceLoadException
    {
        try
        {
            ProviderDocument doc = ProviderDocument.Factory.parse(configFile.getInputStream());
            if (doc != null)
                return doc.getProvider();
        }
        catch (XmlException e)
        {
            throw new ModuleResourceLoadException(e);
        }
        return null;
    }
}