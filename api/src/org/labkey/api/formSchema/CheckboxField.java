/*
 * Copyright (c) 2021 LabKey Corporation
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

package org.labkey.api.formSchema;

/**
 * Used to render a checkbox input in the client.
 */
public class CheckboxField extends AbstractField<Boolean>
{
    public static final String TYPE = "checkbox";

    public CheckboxField(String name, String label, Boolean required, Boolean defaultValue)
    {
        // Checkbox inputs to not support placeholders, so we set to null
        super(name, label, null, required, defaultValue);
    }

    public CheckboxField(String name, String label, Boolean required, Boolean defaultValue, String helpText)
    {
        super(name, label, null, required, defaultValue, helpText);
    }

    public CheckboxField(String name, String label, Boolean required, Boolean defaultValue, String helpText, String helpTextHref)
    {
        super(name, label, null, required, defaultValue, helpText, helpTextHref);
    }

    @Override
    public String getType()
    {
        return TYPE;
    }
}
