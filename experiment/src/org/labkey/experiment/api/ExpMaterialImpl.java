/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.ActionResource;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpMaterialImpl extends AbstractProtocolOutputImpl<Material> implements ExpMaterial
{
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("material", "Material/Sample");

    static public List<ExpMaterialImpl> fromMaterials(List<Material> materials)
    {
        List<ExpMaterialImpl> ret = new ArrayList<>(materials.size());
        for (Material material : materials)
        {
            ret.add(new ExpMaterialImpl(material));
        }
        return ret;
    }

    public ExpMaterialImpl(Material material)
    {
        super(material);
    }


    public void setName(String name)
    {
        if (null != getLSID() && !name.equals(new Lsid(getLSID()).getObjectId()))
            throw new IllegalStateException("name="+name + " lsid="+getLSID());
        super.setName(name);
    }

    @Override
    public void setLSID(String lsid)
    {
        if (null != getName() && !getName().equals(new Lsid(lsid).getObjectId()))
            throw new IllegalStateException("name="+getName() + " lsid="+lsid);
        super.setLSID(lsid);
    }

    @Override
    public void setLSID(Lsid lsid)
    {
        if (null != getName() && !getName().equals(lsid.getObjectId()))
            throw new IllegalStateException("name="+getName() + " lsid="+lsid.toString());
        super.setLSID(lsid);
    }


    public URLHelper detailsURL()
    {
        ActionURL ret = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    @Nullable
    public ExpSampleSet getSampleSet()
    {
        String type = _object.getCpasType();
        if (!"Material".equals(type) && !"Sample".equals(type))
        {
            return ExperimentService.get().getSampleSet(type);
        }
        else
        {
            return null;
        }
    }

    public Map<PropertyDescriptor, Object> getPropertyValues()
    {
        ExpSampleSet sampleSet = getSampleSet();
        if (sampleSet == null)
        {
            return Collections.emptyMap();
        }
        Map<PropertyDescriptor, Object> values = new HashMap<>();
        for (DomainProperty pd : sampleSet.getType().getProperties())
        {
            values.put(pd.getPropertyDescriptor(), getProperty(pd));
        }
        return values;
    }

    public List<ExpProtocolApplicationImpl> getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter(FieldKey.fromParts("MaterialId"), getRowId()), ExperimentServiceImpl.get().getTinfoMaterialInput());
    }

    public String getCpasType()
    {
        String result = _object.getCpasType();
        return result == null ? "Material" : result;
    }

    public void save(User user)
    {
        save(user, ExperimentServiceImpl.get().getTinfoMaterial());
        index(null);
    }

    public void delete(User user)
    {
        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, getContainer(), Collections.singleton(getRowId()));
        // Deleting from search index is handled inside deleteMaterialByRowIds()
    }

    public List<ExpRunImpl> getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoMaterialInput(), "MaterialId");
    }

    public void index(SearchService.IndexTask task)
    {
        // Big hack to prevent study specimens from being indexed as
        if (StudyService.SPECIMEN_NAMESPACE_PREFIX.equals(getLSIDNamespacePrefix()))
        {
            return;
        }
        if (task == null)
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
                return;
            task = ss.defaultTask();
        }

        // do the least possible amount of work here
        final SearchService.IndexTask indexTask = task;
        final ExpMaterialImpl me = this;
        indexTask.addRunnable(
                () -> {
                    ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getMaterialDetailsURL(me);
                    ActionURL sourceURL = new ActionURL(ExperimentController.ShowMaterialSimpleAction.class, getContainer()).addParameter("rowId", getRowId());

                    ActionResource r = new ActionResource(searchCategory, getDocumentId(), url, sourceURL)
                    {
                        @Override
                        public void setLastIndexed(long ms, long modified)
                        {
                            new SqlExecutor(ExperimentService.get().getSchema()).execute("UPDATE " + ExperimentService.get().getTinfoMaterial() + " SET LastIndexed = ? WHERE RowId = ?",
                                    new Timestamp(ms), getRowId());
                        }
                    };
                    r.getMutableProperties().put(SearchService.PROPERTY.title.toString(), "Sample - " + getName());
                    ExpSampleSet ss = getSampleSet();
                    if (null != ss)
                    {
                        //ActionURL resolve = new ActionURL(ExperimentController.ResolveLSIDAction.class,getContainer()).addParameter("lsid",ss.getLSID());
                        ActionURL show = new ActionURL(ExperimentController.ShowMaterialSourceAction.class,getContainer()).addParameter("rowId",ss.getRowId());
                        NavTree t = new NavTree("SampleSet - " + ss.getName(), show);
                        String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
                        r.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
                    }
                    indexTask.addResource(r, SearchService.PRIORITY.item);
                }
                , SearchService.PRIORITY.bulk
        );
    }

    public String getDocumentId()
    {
        return "material:" + getRowId();
    }

}
