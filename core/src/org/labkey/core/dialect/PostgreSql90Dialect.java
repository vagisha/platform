/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
import org.labkey.api.collections.CsvSet;

import java.util.Set;

/*
* User: adam
* Date: May 22, 2011
* Time: 9:28:29 PM
*/
public class PostgreSql90Dialect extends PostgreSql84Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();

        words.removeAll(new CsvSet("between, new, off, old"));

        return words;
    }

    @Override  // 9.0 and above show no warning
    public String getAdminWarningMessage()
    {
        return null;
    }
}
