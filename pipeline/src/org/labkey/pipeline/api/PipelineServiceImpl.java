package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.browse.BrowseForm;
import org.labkey.api.pipeline.browse.BrowseView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.pipeline.PipelineModule;
import org.labkey.pipeline.mule.EPipelineQueueImpl;
import org.labkey.pipeline.browse.BrowseViewImpl;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

public class PipelineServiceImpl extends PipelineService
        implements PipelineJobService.ApplicationProperties
{
    public static String PARAM_Provider = "provider";
    public static String PARAM_Action = "action";
    public static String PROP_Mirror = "mirror-containers";
    public static String PREF_LASTPATH = "lastpath";
    public static String PREF_LASTPROTOCOL = "lastprotocol";
    public static String KEY_PREFERENCES = "pipelinePreferences";

    private static Logger _log = Logger.getLogger(PipelineService.class);

    private Map<String, PipelineProvider> _mapPipelineProviders = new TreeMap<String, PipelineProvider>();
    private PipelineQueue _queue = null;

    public void registerPipelineProvider(PipelineProvider provider, String... aliases)
    {
        _mapPipelineProviders.put(provider.getName(), provider);
        for (String alias : aliases)
            _mapPipelineProviders.put(alias, provider);
        provider.register();
    }


    public PipeRoot findPipelineRoot(Container container) throws SQLException
    {
        PipelineRoot pipelineRoot = PipelineManager.findPipelineRoot(container);
        if (null != pipelineRoot)
        {
            try
            {
                return new PipeRootImpl(pipelineRoot);
            }
            catch (URISyntaxException x)
            {
                _log.error("unexpected error", x);
            }
        }
        return null;
    }


    public PipeRoot[] getAllPipelineRoots()
    {
        PipelineRoot[] pipelines;
        try
        {
            pipelines = PipelineManager.getPipelineRoots(PipelineRoot.PRIMARY_ROOT);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        ArrayList<PipeRoot> pipes = new ArrayList<PipeRoot>(pipelines.length);
        for (PipelineRoot pipeline : pipelines)
        {
            try
            {
                PipeRoot p = new PipeRootImpl(pipeline);
                if (p.getContainer() != null)
                    pipes.add(new PipeRootImpl(pipeline));
            }
            catch (URISyntaxException x)
            {
                _log.error("unexpected error", x);
            }
        }
        return pipes.toArray(new PipeRoot[pipes.size()]);
    }


    public PipeRoot getPipelineRootSetting(Container container) throws SQLException
    {
        try
        {
            return new PipeRootImpl(PipelineManager.getPipelineRootObject(container, PipelineRoot.PRIMARY_ROOT));
        }
        catch (URISyntaxException x)
        {
            _log.error("unexpected error", x);
        }

        return null;
    }



    public URI getPipelineRootSetting(Container container, final String type) throws SQLException
    {
        String root = PipelineManager.getPipelineRoot(container, type);
        if (root == null)
            return null;

        try
        {
            return new URI(root);
        }
        catch (URISyntaxException use)
        {
            _log.error("Invalid pipeline root '" + root + "'.", use);
            return null;
        }
    }

    public PipeRoot[] getOverlappingRoots(Container c) throws SQLException
    {
        PipelineRoot[] roots = PipelineManager.getOverlappingRoots(c, PipelineRoot.PRIMARY_ROOT);
        List<PipeRoot> rootsList = new ArrayList<PipeRoot>();
        for (PipelineRoot root : roots)
        {
            Container container = ContainerManager.getForId(root.getContainerId());
            if (container == null)
                continue;

            try
            {
                rootsList.add(new PipeRootImpl(root));
            }
            catch (URISyntaxException e)
            {
                _log.error("Invalid pipeline root '" + root + "'.", e);
            }
        }
        return rootsList.toArray(new PipeRoot[rootsList.size()]);
    }

    public void setPipelineRoot(User user, Container container, URI root, String type) throws SQLException
    {
        PipelineManager.setPipelineRoot(user, container, root == null ? "" : root.toString(), type);
    }

    public void setPipelineRoot(User user, Container container, URI root) throws SQLException
    {
        setPipelineRoot(user, container, root , PipelineRoot.PRIMARY_ROOT);
    }

    public boolean canModifyPipelineRoot(User user, Container container)
    {
        return container != null && !container.isRoot() && container.hasPermission(user, ACL.PERM_ADMIN);
    }

    public File ensureSystemDirectory(URI root)
    {
        return PipeRootImpl.ensureSystemDirectory(root);
    }

    public List<PipelineProvider> getPipelineProviders()
    {
        // Get a list of unique providers
        return new ArrayList<PipelineProvider>(new HashSet<PipelineProvider>(_mapPipelineProviders.values()));
    }

    public PipelineProvider getPipelineProvider(String name)
    {
        if (name == null)
            return null;
        return _mapPipelineProviders.get(name);
    }

    public String getButtonHtml(String text, ActionURL href)
    {
        return "<a href=\"" + PageFlowUtil.filter(href.toString()) + "\">" +
                "<img border=\"0\" alt=\"" + PageFlowUtil.filter(text) + "\" src=\"" + PageFlowUtil.filter(PageFlowUtil.buttonSrc(text)) + "\"></a>";
    }

    public boolean isEnterprisePipeline()
    {
        return PipelineModule.isEnterprisePipeline();
    }

    public synchronized PipelineQueue getPipelineQueue()
    {
        if (_queue == null)
        {
            if (isEnterprisePipeline())
                _queue = new EPipelineQueueImpl();
            else
                _queue = new PipelineQueueImpl();
        }
        return _queue;
    }

    public void queueJob(PipelineJob job) throws IOException
    {
        getPipelineQueue().addJob(job);
    }

    public void queueJob(PipelineJob job, String initialState) throws IOException
    {
        getPipelineQueue().addJob(job, initialState);
    }

    public void setPipelineProperty(Container container, String name, String value) throws SQLException
    {
        PipelineManager.setPipelineProperty(container, name, value);
    }

    public String getPipelineProperty(Container container, String name) throws SQLException
    {
        return PipelineManager.getPipelineProperty(container, name);
    }

    public BrowseView getBrowseView(BrowseForm form)
    {
        return new BrowseViewImpl(form);
    }

    private String getLastProtocolKey(PipelineProtocolFactory factory)
    {
        return PREF_LASTPROTOCOL + "-" + factory.getName();
    }
    // TODO: This should be on PipelineProtocolFactory
    public String getLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        try
        {
            Map<String, String> props = PropertyManager.getProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES, false);
            if (props != null)
                return props.get(getLastProtocolKey(factory));
        }
        catch (Exception e)
        {
            _log.error("Error", e);
        }
        return "";
    }

    // TODO: This should be on PipelineProtocolFactory
    public void rememberLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user,
                                            String protocolName)
    {
        if (user.isGuest())
            return;
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES, true);
        map.put(getLastProtocolKey(factory), protocolName);
        PropertyManager.saveProperties(map);
    }


    public PipelineStatusFile getStatusFile(String path) throws SQLException
    {
        return PipelineStatusManager.getStatusFile(path);
    }

    public void setStatusFile(ViewBackgroundInfo info, PipelineStatusFile sf) throws Exception
    {
        PipelineStatusManager.setStatusFile(info, sf);
    }

    public void setStatusFile(ViewBackgroundInfo info, PipelineJob job,
                              String status, String statusInfo) throws Exception
    {
        setStatusFile(info, new PipelineStatusFileImpl(job, status, statusInfo));
    }

    public String getToolsDirectory()
    {
        return AppProps.getInstance().getPipelineToolsDirectory();
    }
}
