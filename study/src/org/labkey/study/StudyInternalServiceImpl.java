package org.labkey.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyInternalService;
import org.labkey.api.study.model.ParticipantInfo;
import org.labkey.study.model.StudyManager;

import java.util.Map;

public class StudyInternalServiceImpl implements StudyInternalService
{
    @Override
    public void clearCaches(Container container)
    {
        StudyManager.getInstance().clearCaches(container, false);
    }

    @Override
    public Map<String, ParticipantInfo> getParticipantInfos(Study study, User user, boolean isShiftDates, boolean isAlternateIds)
    {
        return StudyManager.getInstance().getParticipantInfos(study, user, isShiftDates, isAlternateIds);
    }

    @Override
    public void generateNeededAlternateParticipantIds(Study study, User user)
    {
        StudyManager.getInstance().generateNeededAlternateParticipantIds(study, user);
    }
}
