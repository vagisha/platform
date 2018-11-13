/*
 * Copyright (c) 2014-2018 LabKey Corporation
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
package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.template.Warnings;

import java.util.Set;

/**
 * User: adam
 * Date: 8/5/2014
 * Time: 10:49 PM
 */
public class PostgreSql94Dialect extends PostgreSql93Dialect
{
    public PostgreSql94Dialect()
    {
    }

    public PostgreSql94Dialect(boolean standardConformingStrings)
    {
        super(standardConformingStrings);
    }

    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.remove("over");

        return words;
    }

    @Override
    public void addAdminWarningMessages(Warnings warnings)
    {
        // Override the 9.3 override... no warnings for 9.4+
    }
}
