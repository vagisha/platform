/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.core;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.Encryption;
import org.labkey.api.settings.NetworkDriveProps;
import org.labkey.api.settings.WriteableAppProps;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = Logger.getLogger(CoreUpgradeCode.class);

    // We don't call ContainerManager.getRoot() during upgrade code since the container table may not yet match
    // ContainerManager's assumptions. For example, older installations don't have a description column until
    // the 10.1 scripts run (see #9927).
    @SuppressWarnings("UnusedDeclaration")
    private String getRootId()
    {
        return new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT EntityId FROM core.Containers WHERE Parent IS NULL").getObject(String.class);
    }

    // Not currently invoked, but available for future scripts
    @SuppressWarnings({"UnusedDeclaration"})
    public void handleUnknownModules(ModuleContext context)
    {
        ModuleLoader.getInstance().handleUnkownModules();
    }

    /**
     * Invoked from 18.10-18.11 to migrate mapped drive settings to an encrypted property store.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void encryptMappedDrivePassword(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            WritableNetworkProps props = new WritableNetworkProps();

            String driveLetter = props.getStringValue(WritableNetworkProps.NETWORK_DRIVE_LETTER);
            String drivePath = props.getStringValue(WritableNetworkProps.NETWORK_DRIVE_PATH);
            String user = props.getStringValue(WritableNetworkProps.NETWORK_DRIVE_USER);
            String password = props.getStringValue(WritableNetworkProps.NETWORK_DRIVE_PASSWORD);

            if (StringUtils.isNotBlank(driveLetter) || StringUtils.isNotBlank(drivePath) || StringUtils.isNotBlank(user) || StringUtils.isNotBlank(password))
            {
                // we won't blow up on upgrade if the encryption key isn't specified but we will drop any
                // existing mapped drive settings and force them to re-add them
                if (Encryption.isMasterEncryptionPassPhraseSpecified())
                {
                    NetworkDriveProps.setNetworkDriveLetter(driveLetter);
                    NetworkDriveProps.setNetworkDrivePath(drivePath);
                    NetworkDriveProps.setNetworkDriveUser(user);
                    NetworkDriveProps.setNetworkDrivePassword(password);
                }
                else
                {
                    LOG.warn("Master encryption key not specified, unable to migrate saved network drive settings");
                }
                // clear out the legacy settings
                props.clearNetworkSettings();
                props.save(context.getUpgradeUser());
            }
        }
    }

    /**
     * Helper class to access legacy network settings so we can remove the old API methods immediately
     */
    private static class WritableNetworkProps extends WriteableAppProps
    {
        static final String NETWORK_DRIVE_LETTER = "networkDriveLetter";
        static final String NETWORK_DRIVE_PATH = "networkDrivePath";
        static final String NETWORK_DRIVE_USER = "networkDriveUser";
        static final String NETWORK_DRIVE_PASSWORD = "networkDrivePassword";

        public WritableNetworkProps()
        {
            super(ContainerManager.getRoot());
        }

        public void clearNetworkSettings()
        {
            storeStringValue(NETWORK_DRIVE_LETTER, "");
            storeStringValue(NETWORK_DRIVE_PATH, "");
            storeStringValue(NETWORK_DRIVE_USER, "");
            storeStringValue(NETWORK_DRIVE_PASSWORD, "");
        }

        public String getStringValue(String key)
        {
            return lookupStringValue(key, "");
        }
    }
}
