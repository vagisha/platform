/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.CohortController;

/**
 * User: jgarms
 * Date: Jul 17, 2008
 * Time: 1:37:53 PM
 */
public class CohortDomainKind extends BaseStudyDomainKind
{

    protected Class<? extends ExtensibleStudyEntity> getSupportedClass()
    {
        return CohortImpl.class;
    }

    protected TableInfo getTableInfo()
    {
        return StudySchema.getInstance().getTableInfoCohort();
    }

    public ActionURL urlEditDefinition(Domain domain)
    {
        // This isn't really the edit url, but instead the destination after editing
        return new ActionURL(CohortController.ManageCohortsAction.class, domain.getContainer());
    }

    public ActionURL urlShowData(Domain domain)
    {
        return new ActionURL(CohortController.ManageCohortsAction.class, domain.getContainer());
    }
}
