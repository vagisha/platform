/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.exp.pipeline;

import org.labkey.api.util.FileType;

import java.util.Map;
import java.io.IOException;

/**
 * <code>XarGeneratorId</code> is the TaskId interface for the XarTemplateSubstitutionTask
 */
public interface XarTemplateSubstitutionId
{
    interface Factory
    {
        FileType[] getInputTypes();

        FileType getOutputType();
    }

    /**
     * Interface for support required from the PipelineJob to run this task,
     * beyond the base PipelineJob methods.
     */
    interface JobSupport
    {
        /**
         * Returns a description of the search.
         */
        String getDescription();

        /**
         * Returns a classpath-relative path to the template resource.
         */
        String getXarTemplateResource();

        /**
         * Returns a map of string replacements to be made in the template.
         */
        Map<String, String> getXarTemplateReplacements() throws IOException;
    }
}
