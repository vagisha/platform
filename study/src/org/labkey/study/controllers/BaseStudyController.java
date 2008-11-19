/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.controllers;

import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.HasPageConfig;
import org.labkey.api.action.NavTrailAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import static org.labkey.api.util.PageFlowUtil.jsString;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.view.BaseStudyPage;
import org.labkey.study.view.StudyNavigationView;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 13, 2007
 */
public abstract class BaseStudyController extends SpringActionController
{
    public enum SharedFormParameters
    {
        QCState,
        cohortId
    }
    static final boolean extprototype = false;

    ActionURL getPermaLink()
    {
        ActionURL url = getViewContext().cloneActionURL().setExtraPath(getContainer().getId());
        url.deleteParameter("_template");
        url.deleteParameter("_dc");
        return url;
    }

    public PageConfig defaultPageConfig()
    {
        PageConfig page =  super.defaultPageConfig();
        String template = getViewContext().getRequest().getHeader("template");
        if (null == template)
            template = getViewContext().getRequest().getParameter("_template");
        if ("custom".equals(template))
            page.setTemplate(PageConfig.Template.Custom);
        return page;
    }

    protected ModelAndView getTemplate(ViewContext context, final ModelAndView mv, final Controller action, PageConfig page)
    {
        if (!extprototype)
            return super.getTemplate(context,mv,action,page);

        if (((HasPageConfig)action).getPageConfig().getTemplate() == PageConfig.Template.Custom)
        {
            HttpView custom = new HttpView()
            {
                protected void renderInternal(Object model, PrintWriter out) throws Exception
                {
                    out.println("<!--custom-->");   // marker
                    include(mv);
                    out.println("<script type='text/javascript'>updatePageProperties({");
                    out.print("permalink:");out.println(jsString(getPermaLink().getLocalURIString()));
                    if (action instanceof NavTrailAction)
                    {
                        NavTree root = new NavTree();
                        appendNavTrail(action, root);
                        out.print(",navtrail:[");
                        String c = "";
                        NavTree last = null;
                        for (NavTree nt : root.getChildren())
                        {
                            out.print(c);c = ",";
                            out.print("{title:");out.print(jsString(nt.getKey()));out.print(',');
                            out.print("url:");out.print(jsString(nt.getValue()));out.print('}');
                            last = nt;
                        }
                        out.print("]");
                        if (null != last)
                        {
                            out.print(",title:");out.println(jsString(last.getKey()));
                        }
                    }
                    out.println("});</script>");
                }
            };
            return custom;
        }

        HttpView wrapper =  new HttpView()
        {
            protected void renderInternal(Object model, PrintWriter out) throws Exception
            {

                out.println("<div class=extContainer><div id=studyDiv><!--BODY-->");
                include(mv);
                out.println("</div></div>");
            }
        };
        ModelAndView t = super.getTemplate(context, wrapper, action, page);
        if (t instanceof HomeTemplate)
        {
            Study study = null;
            try {study = getStudy(true);}catch (ServletException x){}
            if (null != study)
                ((HttpView)t).setView("moduleNav", new StudyNavigationView(study));
        }
        return t;
    }

    public Study getStudy(boolean allowNullStudy) throws ServletException
    {
        // UNDONE: see https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=1137
        Container c = getContainer();
        Study study = StudyManager.getInstance().getStudy(c);
        if (!allowNullStudy && study == null)
        {
            // redirect to the study home page, where admins will see a 'create study' button,
            // and non-admins will simply see a message that no study exists.
            HttpView.throwRedirect(new ActionURL(StudyController.BeginAction.class, c));
        }
        return study;
    }

    public Study getStudy() throws ServletException
    {
        return getStudy(false);
    }

    protected BaseViewAction initAction(BaseViewAction parent, BaseViewAction action)
    {
        action.setViewContext(parent.getViewContext());
        action.setPageConfig(parent.getPageConfig());

        return action;
    }

    protected NavTree _appendManageStudy(NavTree root)
    {
        try {
            Study study = getStudy();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            root.addChild("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, getContainer()));
        }
        catch (ServletException e)
        {
        }
        return root;
    }

    protected NavTree _appendNavTrailDatasetAdmin(NavTree root)
    {
        _appendManageStudy(root);
        return root.addChild("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, getContainer()));
    }

    protected NavTree _appendNavTrail(NavTree root)
    {
        return _appendNavTrail(root, null);
    }

    protected NavTree _appendNavTrail(NavTree root, Integer cohortId)
    {
        try {
            Study study = getStudy();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            ActionURL overviewURL = new ActionURL(StudyController.OverviewAction.class, getContainer());
            if (cohortId != null)
                overviewURL.addParameter(SharedFormParameters.cohortId, cohortId.intValue());
            root.addChild("Study Overview", overviewURL);
        }
        catch (ServletException e)
        {
        }
        return root;
    }

    protected NavTree _appendNavTrail(NavTree root, int datasetId, int visitId)
    {
        return _appendNavTrail(root, datasetId, visitId, -1, null);
    }

    protected NavTree _appendNavTrail(NavTree root, int datasetId, int visitId, int cohortId, String qcStateSetFormValue)
    {
        try {
            Study study = getStudy();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            ActionURL overviewURL = new ActionURL(StudyController.OverviewAction.class, getContainer());
            if (cohortId >= 0)
                overviewURL.addParameter(SharedFormParameters.cohortId, cohortId);
            if (qcStateSetFormValue != null)
                overviewURL.addParameter(SharedFormParameters.QCState, qcStateSetFormValue);
            root.addChild("Study Overview", overviewURL);
            _appendDataset(root, study, datasetId, visitId, cohortId, qcStateSetFormValue);
        }
        catch (ServletException e)
        {
        }
        return root;
    }

    protected NavTree _appendDataset(NavTree root, Study study, int datasetId, int visitRowId, int cohortId, String qcStateSetFormValue)
    {
        if (datasetId > 0)
        {
            Visit visit = null;
            if (visitRowId > 0)
                visit = StudyManager.getInstance().getVisitForRowId(study, visitRowId);

            DataSetDefinition dataSet = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            if (dataSet != null)
            {
                StringBuilder label = new StringBuilder();
                label.append("Dataset: ");
                if (dataSet.getLabel() != null)
                    label.append(dataSet.getLabel());
                else
                    label.append("CRF/Assay ").append(dataSet.getDataSetId());

                if (visit != null)
                    label.append(", ").append(visit.getDisplayString());
                else
                    label.append(", All Visits");

                ActionURL datasetUrl = new ActionURL(StudyController.DatasetAction.class, getContainer()).
                        addParameter(DataSetDefinition.DATASETKEY, datasetId);
                if (cohortId >= 0)
                    datasetUrl.addParameter(SharedFormParameters.cohortId, cohortId);
                if (qcStateSetFormValue != null)
                    datasetUrl.addParameter(SharedFormParameters.QCState, qcStateSetFormValue);
                root.addChild(label.toString(), datasetUrl.getLocalURIString());
            }
        }
        return root;
    }

    protected void redirectTypeNotFound(int datasetId) throws RedirectException
    {
        HttpView.throwRedirect(new ActionURL(StudyController.TypeNotFoundAction.class, getContainer()).addParameter("id", datasetId));
    }

    public static class StudyJspView<T> extends JspView<T>
    {
        public StudyJspView(Study study, String name, T bean, BindException errors)
        {
            super("/org/labkey/study/view/" + name, bean, errors);
            if (getPage() instanceof BaseStudyPage)
                ((BaseStudyPage)getPage()).init(study);
        }
    }

    protected static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }

    public static int[] toIntArray(Collection<String> intStrings)
    {
        if (intStrings == null)
            return null;
        int[] converted = new int[intStrings.size()];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Integer.parseInt(intString);
        return converted;
    }

    public static int[] toIntArray(String[] intStrings)
    {
        if (intStrings == null)
            return null;
        int[] converted = new int[intStrings.length];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Integer.parseInt(intString);
        return converted;
    }

    public static class IdForm
    {
        public enum PARAMS
        {
            id
        }
        
        private int _id;

        public IdForm()
        {
        }

        public IdForm(int id)
        {
            _id = id;
        }

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }
    }
}
