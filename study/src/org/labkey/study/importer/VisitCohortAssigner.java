/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.importer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * User: adam
 * Date: May 21, 2009
 * Time: 3:22:31 PM
 */
public class VisitCohortAssigner
{
    // Parses the whole visit map again to retrieve the cohort assigments; not ideal...
    void process(Study study, ImportContext ctx, File root) throws SQLException
    {
        StudyDocument.Study.Visits visitsXml = ctx.getStudyXml().getVisits();

        if (null != visitsXml)
        {
            File visitMap = new File(root, visitsXml.getFile());

            if (visitMap.exists())
            {
                StudyManager studyManager = StudyManager.getInstance();
                VisitManager visitManager = studyManager.getVisitManager(study);
                Container c = ctx.getContainer();
                User user = ctx.getUser();

                VisitMapImporter.Format vmFormat = VisitMapImporter.Format.getFormat(visitMap);
                String contents = PageFlowUtil.getFileContentsAsString(visitMap);
                List<VisitMapRecord> records = vmFormat.getReader().getRecords(contents);

                for (VisitMapRecord record : records)
                {
                    Visit visit = visitManager.findVisitBySequence(record.getSequenceNumMin());

                    String oldCohortLabel = null != visit.getCohort() ? visit.getCohort().getLabel() : null;

                    if (!PageFlowUtil.nullSafeEquals(oldCohortLabel, record.getCohort()))
                    {
                        Cohort cohort = studyManager.getCohortByLabel(c, user, record.getCohort());
                        Visit mutable = visit.createMutable();
                        mutable.setCohortId(cohort.getRowId());
                        StudyManager.getInstance().updateVisit(ctx.getUser(), mutable);
                    }
                }
            }
        }
    }
}
