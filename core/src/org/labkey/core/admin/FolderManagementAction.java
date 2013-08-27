/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.admin;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tika.io.IOUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriterImpl;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.PanelButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.files.FileContentService;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.Group;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.ZipFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.core.query.CoreQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: Jan 17, 2011
 * Time: 10:36:02 AM
 */
@RequiresPermissionClass(AdminPermission.class)
@ActionNames("folderManagement, folderSettings, customize")
public class FolderManagementAction extends FormViewAction<FolderManagementAction.FolderManagementForm>
{
    private ActionURL _successURL;

    public void validateCommand(FolderManagementForm form, Errors errors)
    {
        if (form.isFolderTypeTab())
        {
            boolean fEmpty = true;
            for (String module : form.activeModules)
            {
                if (module != null)
                {
                    fEmpty = false;
                    break;
                }
            }
            if (fEmpty && "None".equals(form.getFolderType()))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Error: Please select at least one module to display.");
            }
        }
        else if (form.isMessagesTab())
        {
            MessageConfigService.ConfigTypeProvider provider = MessageConfigService.getInstance().getConfigType(form.getProvider());

            if (provider != null)
                provider.validateCommand(getViewContext(), errors);
        }
    }

    public ModelAndView getView(FolderManagementForm form, boolean reshow, BindException errors) throws Exception
    {
        // In export-to-browser case, base action will attempt to reshow the view since we returned null as the success
        // URL; returning null here causes the base action to stop pestering the action.
        if (reshow && !errors.hasErrors())
            return null;

        return new FolderManagementTabStrip(getContainer(), form, errors);
    }

    public boolean handlePost(FolderManagementForm form, BindException errors) throws Exception
    {
        if (form.isFolderTypeTab())
            return handleFolderTypePost(form, errors);
        else if (form.isMvIndicatorsTab())
            return handleMvIndicatorsPost(form, errors);
        else if (form.isFullTextSearchTab())
            return handleFullTextSearchPost(form, errors);
        else if (form.isFilesTab())
            return handleFilesPost(form, errors);
        else if (form.isMessagesTab())
            return handleMessagesPost(form, errors);
        else if (form.isExportTab())
            return handleExportPost(form, errors);
        else if (form.isImportTab())
            return handleImportPost(form, errors);
        else
            return handleFolderTreePost(form, errors);
    }

    private boolean handleMvIndicatorsPost(FolderManagementForm form, BindException errors) throws SQLException
    {
        _successURL = getViewContext().getActionURL();
        if (form.isInheritMvIndicators())
        {
            MvUtil.inheritMvIndicators(getContainer());
            return true;
        }
        else
        {
            // Javascript should have enforced any constraints
            MvUtil.assignMvIndicators(getContainer(), form.getMvIndicators(), form.getMvLabels());
            return true;
        }
    }

    private boolean handleFolderTypePost(FolderManagementForm form, BindException errors) throws SQLException
    {
        Container container = getContainer();
        if (container.isRoot())
        {
            throw new NotFoundException();
        }

        String[] modules = form.getActiveModules();

        if (modules.length == 0)
        {
            errors.reject(null, "At least one module must be selected");
            return false;
        }

        Set<Module> activeModules = new HashSet<>();
        for (String moduleName : modules)
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            if (module != null)
                activeModules.add(module);
        }

        if (null == StringUtils.trimToNull(form.getFolderType()) || FolderType.NONE.getName().equals(form.getFolderType()))
        {
            container.setFolderType(FolderType.NONE, activeModules, errors);
            Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
            container.setDefaultModule(defaultModule);
        }
        else
        {
            FolderType folderType = ModuleLoader.getInstance().getFolderType(form.getFolderType());
            container.setFolderType(folderType, activeModules, errors);
        }
        if (errors.hasErrors())
            return false;

        if (form.isWizard())
        {
            _successURL = PageFlowUtil.urlProvider(SecurityUrls.class).getContainerURL(container);
            _successURL.addParameter("wizard", Boolean.TRUE.toString());
        }
        else
            _successURL = container.getFolderType().getStartURL(container, getUser());

        return true;
    }

    private boolean handleFilesPost(FolderManagementForm form, BindException errors) throws Exception
    {
        // File root settings
        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
        if (service != null)
        {
            if (form.isPipelineRootForm())
                return PipelineService.get().savePipelineSetup(getViewContext(), form, errors);
            else
            {
                setFileRootFromForm(getViewContext(), form);
            }
        }

        // Cloud settings
        setEnabledCloudStores(getViewContext(), form.getEnabledCloudStore());

        _successURL = getViewContext().getActionURL();

        return true;
    }

    public static void setFileRootFromForm(ViewContext ctx, AdminController.FileManagementForm form)
    {
        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);

        if (form.isDisableFileSharing())
            service.disableFileRoot(ctx.getContainer());
        else if (form.hasSiteDefaultRoot())
            service.setIsUseDefaultRoot(ctx.getContainer(), true);
        else
        {
            String root = StringUtils.trimToNull(form.getFolderRootPath());

            // test permissions.  only site admins are able to turn on a custom file root for a folder
            // this is only relevant if the folder is either being switched to a custom file root,
            // or if the file root is changed.
            if (!service.isUseDefaultRoot(ctx.getContainer()) && !service.getFileRoot(ctx.getContainer()).getPath().equalsIgnoreCase(form.getFolderRootPath()))
            {
                if (!ctx.getUser().isSiteAdmin())
                    throw new UnauthorizedException("Only site admins change change file roots");
            }

            if (root != null)
            {
                service.setIsUseDefaultRoot(ctx.getContainer(), false);
                service.setFileRoot(ctx.getContainer(), new File(root));
            }
            else
                service.setFileRoot(ctx.getContainer(), null);
        }
    }

    public static void setEnabledCloudStores(ViewContext ctx, String[] enabledCloudStores)
    {
        CloudStoreService cloud = ServiceRegistry.get(CloudStoreService.class);
        if (cloud != null)
        {
            Set<String> enabled = Collections.emptySet();
            if (enabledCloudStores != null)
                enabled = new HashSet(Arrays.asList(enabledCloudStores));
            cloud.setEnabledCloudStores(ctx.getContainer(), enabled);
        }
    }

    public static void setConfirmMessage(ViewContext ctx, AdminController.FileManagementForm form) throws IllegalArgumentException
    {
        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
        String confirmMessage = null;

        if (service != null)
        {
            if (service.isFileRootDisabled(ctx.getContainer()))
            {
                form.setFileRootOption(AdminController.ProjectSettingsForm.FileRootProp.disable.name());
                confirmMessage = "File sharing has been disabled for this " + ctx.getContainer().getContainerNoun();
            }
            else if (service.isUseDefaultRoot(ctx.getContainer()))
            {
                form.setFileRootOption(AdminController.ProjectSettingsForm.FileRootProp.siteDefault.name());
                File root = service.getFileRoot(ctx.getContainer());
                if (root != null && root.exists())
                    confirmMessage = "The file root is set to a default of: " + FileUtil.getAbsoluteCaseSensitiveFile(root).getAbsolutePath();
            }
            else
            {
                File root = service.getFileRoot(ctx.getContainer());

                form.setFileRootOption(AdminController.ProjectSettingsForm.FileRootProp.folderOverride.name());
                if (root != null)
                {
                    root = FileUtil.getAbsoluteCaseSensitiveFile(root);
                    form.setFolderRootPath(root.getAbsolutePath());
                    if (root.exists())
                        confirmMessage = "The file root is set to: " + root.getAbsolutePath();
                    else
                        throw new IllegalArgumentException("File root '" + root + "' does not appear to be a valid directory accessible to the server at " + ctx.getRequest().getServerName() + ".");
                }
            }
        }

        if (ctx.getActionURL().getParameter("rootSet") != null && confirmMessage != null)
            form.setConfirmMessage(confirmMessage);
    }

    private boolean handleFullTextSearchPost(FolderManagementForm form, BindException errors) throws SQLException
    {
        Container container = getContainer();
        if (container.isRoot())
        {
            throw new NotFoundException();
        }

        ContainerManager.updateSearchable(container, form.getSearchable(), getUser());
        _successURL = getViewContext().getActionURL();  // Redirect to ourselves -- this forces a reload of the Container object to get the property update

        return true;
    }

    private boolean handleMessagesPost(FolderManagementForm form, BindException errors) throws Exception
    {
        MessageConfigService.ConfigTypeProvider provider = MessageConfigService.getInstance().getConfigType(form.getProvider());

        if (provider != null)
        {
            _successURL = getViewContext().getActionURL();
            return provider.handlePost(getViewContext(), errors);
        }
        errors.reject(SpringActionController.ERROR_MSG, "Unable to find the selected config provider");
        return false;
    }

    private boolean handleExportPost(FolderManagementForm form, BindException errors) throws Exception
    {
        Container container = getContainer();
        if (container.isRoot())
        {
            throw new NotFoundException();
        }

        FolderWriterImpl writer = new FolderWriterImpl();
        FolderExportContext ctx = new FolderExportContext(getUser(), container, PageFlowUtil.set(form.getTypes()),
                form.getFormat(), form.isIncludeSubfolders(), form.isRemoveProtected(), form.isShiftDates(),
                form.isAlternateIds(), form.isMaskClinic(), new StaticLoggerGetter(Logger.getLogger(FolderWriterImpl.class)));

        switch(form.getLocation())
        {
            case 0:
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(container);
                if (root == null || !root.isValid())
                {
                    throw new NotFoundException("No valid pipeline root found");
                }
                File exportDir = root.resolvePath("export");
                try
                {
                    writer.write(container, ctx, new FileSystemFile(exportDir));
                }
                catch (Container.ContainerException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                }
                _successURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(container);
                break;
            }
            case 1:
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(container);
                if (root == null || !root.isValid())
                {
                    throw new NotFoundException("No valid pipeline root found");
                }
                File exportDir = root.resolvePath("export");
                exportDir.mkdir();
                ZipFile zip = new ZipFile(exportDir, FileUtil.makeFileNameWithTimestamp(container.getName(), "folder.zip"));
                try
                {
                    writer.write(container, ctx, zip);
                    zip.close();
                }
                catch (Container.ContainerException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                }
                _successURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(container);
                break;
            }
            case 2:
            {
                // Write to stream first, so any error can be reported properly
                OutputStream outputStream = null;
                try
                {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    outputStream = new BufferedOutputStream(baos);
                    ZipFile zip = new ZipFile(outputStream, true);
                    writer.write(container, ctx, zip);
                    zip.close();
                    PageFlowUtil.streamFileBytes(getViewContext().getResponse(), FileUtil.makeFileNameWithTimestamp(container.getName(), "folder.zip"), baos.toByteArray(), false);
                }
                catch (Container.ContainerException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                }
                finally
                {
                    IOUtils.closeQuietly(outputStream);
                }
                break;
            }
        }
        return !errors.hasErrors();
    }

    private boolean handleImportPost(FolderManagementForm form, BindException errors) throws Exception
    {
        if(form.origin == null){
            form.setOrigin("Folder");
        }
        Container container = getContainer();
        if (container.isRoot())
        {
            throw new NotFoundException();
        }

        if (!PipelineService.get().hasValidPipelineRoot(container))
        {
            errors.reject("folderImport", "Pipeline root not set or does not exist on disk");
        }
        else
        {
            // Assuming success starting the import process, redirect to pipeline status
            _successURL = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(container);

            Map<String, MultipartFile> map = getFileMap();
            if (map.isEmpty())
            {
                errors.reject("folderImport", "You must select a valid zip archive (folder or study).");
            }
            else if (map.size() > 1)
            {
                errors.reject("folderImport", "Only one file is allowed.");
            }
            else
            {
                MultipartFile file = map.values().iterator().next();

                if (0 == file.getSize() || StringUtils.isBlank(file.getOriginalFilename()))
                {
                    errors.reject("folderImport", "You must select a valid zip archive (folder or study).");
                }
                else if (!file.getOriginalFilename().endsWith(".zip"))
                {
                    errors.reject("folderImport", "You must select a valid zip archive (folder or study).");
                }
                else
                {
                    InputStream is = file.getInputStream();
                    File zipFile = File.createTempFile("folder", ".zip");
                    zipFile.deleteOnExit();
                    FileUtil.copyData(is, zipFile);

                    ViewContext context = getViewContext();
                    Container c = context.getContainer();
                    if (!PipelineService.get().hasValidPipelineRoot(c))
                    {
                        return false;
                    }

                    PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(c);

                    File folderXml;
                    boolean isStudy = false;

                    if (zipFile.getName().toLowerCase().endsWith(".zip"))
                    {
                        String dirName = "unzip";
                        File importDir = pipelineRoot.resolvePath(dirName);

                        if (importDir.exists() && !FileUtil.deleteDir(importDir))
                        {
                            errors.reject("studyImport", "Import failed: Could not delete the directory \"" + dirName + "\"");
                            return false;
                        }

                        try
                        {
                            ZipUtil.unzipToDirectory(zipFile, importDir);
                        }
                        catch (FileNotFoundException e)
                        {
                            errors.reject("folderImport", "File not found.");
                            return false;
                        }
                        catch (IOException e)
                        {
                            errors.reject("folderImport", "This file does not appear to be a valid zip archive file.");
                            return false;
                        }

                        folderXml = new File(importDir, "folder.xml");
                        if(!folderXml.exists()){
                            folderXml = new File(importDir, "study.xml");
                            isStudy = true;
                        }
                        if(!folderXml.exists()){
                            errors.reject("folderImport", "This file doesn't contain an appropriate xml.");
                        }
                    }
                    else {
                        folderXml = zipFile;
                        errors.reject("folderImport", "Please submit an appropriate zip archive file.");
                    }
                    zipFile.delete();

                    User user = context.getUser();
                    ActionURL url = context.getActionURL();

                    if(isStudy){
                       StudyService.get().runStudyImportJob(c, user, url, folderXml, file.getOriginalFilename(), errors, pipelineRoot);
                    }
                    else {
                       PipelineService.get().runFolderImportJob(c, user, url, folderXml, file.getOriginalFilename(), errors, pipelineRoot);
                    }
                }
            }
        }
        return !errors.hasErrors();
    }

    private boolean handleFolderTreePost(FolderManagementForm form, BindException errors) throws Exception
    {
        _successURL = getViewContext().getActionURL();
        return true;
    }

    public ActionURL getSuccessURL(FolderManagementForm form)
    {
        return _successURL;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Container container = getContainer();

        if (container.isRoot())
            return AdminController.appendAdminNavTrail(root, "Admin Console", AdminController.ShowAdminAction.class, container);

        root.addChild("Folder Management");
        return root;
    }

    private Container getContainer()
    {
        return getViewContext().getContainerNoTab();
    }

    private User getUser()
    {
        return getViewContext().getUser();
    }

    public static class FolderManagementForm extends SetupForm implements AdminController.FileManagementForm
    {
        // folder type settings
        private String[] activeModules = new String[ModuleLoader.getInstance().getModules().size()];
        private String defaultModule;
        private String folderType;
        private boolean wizard;
        private String tabId;
        private String origin;

        // missing value settings
        private boolean inheritMvIndicators;
        private String[] mvIndicators;
        private String[] mvLabels;

        // full-text search settings
        private boolean searchable;
        private String _provider;

        // folder export settings
        private String[] types;
        private int location;
        private String format;
        private String exportType;
        private boolean includeSubfolders;
        private boolean removeProtected;
        private boolean shiftDates;
        private boolean alternateIds;
        private boolean maskClinic;

        // file management settings
        private String _folderRootPath;
        private String _fileRootOption;

        // cloud settings
        private String[] _enabledCloudStore;

        public String[] getActiveModules()
        {
            return activeModules;
        }

        public void setActiveModules(String[] activeModules)
        {
            this.activeModules = activeModules;
        }

        public String getDefaultModule()
        {
            return defaultModule;
        }

        public void setDefaultModule(String defaultModule)
        {
            this.defaultModule = defaultModule;
        }

        public String getFolderType()
        {
            return folderType;
        }

        public void setFolderType(String folderType)
        {
            this.folderType = folderType;
        }

        public boolean isWizard()
        {
            return wizard;
        }

        public void setWizard(boolean wizard)
        {
            this.wizard = wizard;
        }

        public void setTabId(String tabId)
        {
            this.tabId = tabId;
        }

        public String getTabId()
        {
            return tabId;
        }

        public void setOrigin(String origin){
            this.origin = origin;
        }

        public String getOrigin(){
            return origin;
        }

        public boolean isFolderTreeTab()
        {
            return "folderTree".equals(getTabId());
        }

        public boolean isFolderTypeTab()
        {
            return "folderType".equals(getTabId());
        }

        public boolean isMvIndicatorsTab()
        {
            return "mvIndicators".equals(getTabId());
        }

        public boolean isFullTextSearchTab()
        {
            return "fullTextSearch".equals(getTabId());
        }

        public boolean isFilesTab()
        {
            return "files".equals(getTabId());
        }

        public boolean isMessagesTab()
        {
            return "messages".equals(getTabId());
        }

        public boolean isExportTab()
        {
            return "export".equals(getTabId());
        }

        public boolean isImportTab()
        {
            return "import".equals(getTabId());
        }

        public boolean isInformationTab()
        {
            return "info".equals(getTabId());
        }

        public boolean isInheritMvIndicators()
        {
            return inheritMvIndicators;
        }

        public void setInheritMvIndicators(boolean inheritMvIndicators)
        {
            this.inheritMvIndicators = inheritMvIndicators;
        }

        public String[] getMvIndicators()
        {
            return mvIndicators;
        }

        public void setMvIndicators(String[] mvIndicators)
        {
            this.mvIndicators = mvIndicators;
        }

        public String[] getMvLabels()
        {
            return mvLabels;
        }

        public void setMvLabels(String[] mvLabels)
        {
            this.mvLabels = mvLabels;
        }

        public boolean getSearchable()
        {
            return searchable;
        }

        public void setSearchable(boolean searchable)
        {
            this.searchable = searchable;
        }

        public String getProvider()
        {
            return _provider;
        }

        public void setProvider(String provider)
        {
            _provider = provider;
        }

        public String[] getTypes()
        {
            return types;
        }

        public void setTypes(String[] types)
        {
            this.types = types;
        }

        public int getLocation()
        {
            return location;
        }

        public void setLocation(int location)
        {
            this.location = location;
        }

        public String getFormat()
        {
            return format;
        }

        public void setFormat(String format)
        {
            this.format = format;
        }

        public AbstractFolderContext.ExportType getExportType()
        {
            if ("study".equals(exportType))
                return AbstractFolderContext.ExportType.STUDY;
            else
                return AbstractFolderContext.ExportType.ALL;
        }

        public void setExportType(String exportType)
        {
            this.exportType = exportType;
        }

        public boolean isRemoveProtected()
        {
            return removeProtected;
        }

        public void setRemoveProtected(boolean removeProtected)
        {
            this.removeProtected = removeProtected;
        }

        public boolean isIncludeSubfolders()
        {
            return includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            this.includeSubfolders = includeSubfolders;
        }

        public boolean isShiftDates()
        {
            return shiftDates;
        }

        public void setShiftDates(boolean shiftDates)
        {
            this.shiftDates = shiftDates;
        }

        public boolean isAlternateIds()
        {
            return alternateIds;
        }

        public void setAlternateIds(boolean alternateIds)
        {
            this.alternateIds = alternateIds;
        }

        //file management
        public String getFolderRootPath()
        {
            return _folderRootPath;
        }

        public void setFolderRootPath(String folderRootPath)
        {
            _folderRootPath = folderRootPath;
        }

        public String getFileRootOption()
        {
            return _fileRootOption;
        }

        public void setFileRootOption(String fileRootOption)
        {
            _fileRootOption = fileRootOption;
        }

        @Override
        public String[] getEnabledCloudStore()
        {
            return _enabledCloudStore;
        }

        @Override
        public void setEnabledCloudStore(String[] enabledCloudStore)
        {
            _enabledCloudStore = enabledCloudStore;
        }

        public boolean isDisableFileSharing()
        {
            return AdminController.ProjectSettingsForm.FileRootProp.disable.name().equals(getFileRootOption());
        }

        public boolean hasSiteDefaultRoot()
        {
            return AdminController.ProjectSettingsForm.FileRootProp.siteDefault.name().equals(getFileRootOption());
        }

        public boolean isMaskClinic()
        {
            return maskClinic;
        }

        public void setMaskClinic(boolean maskClinic)
        {
            this.maskClinic = maskClinic;
        }
    }


    private static class FolderManagementTabStrip extends TabStripView
    {
        private final Container _container;
        private FolderManagementForm _form;
        private BindException _errors;

        private FolderManagementTabStrip(Container c, FolderManagementForm form, BindException errors)
        {
            _container = c;
            _form = form;
            _errors = errors;

            // Stay on same tab if there are errors
            if (_errors.hasErrors() && null != StringUtils.trimToNull(form.getTabId()))
                setSelectedTabId(form.getTabId());
        }

        public List<NavTree> getTabList()
        {
            ActionURL url = new AdminController.AdminUrlsImpl().getFolderManagementURL(getViewContext().getContainer());
            List<NavTree> tabs = new ArrayList<>(2);

            if (!_container.isRoot())
            {
                tabs.add(new TabInfo("Folder Tree", "folderTree", url));
                tabs.add(new TabInfo("Folder Type", "folderType", url));
            }
            tabs.add(new TabInfo("Missing Values", "mvIndicators", url));

            //only show module properties tab if a module w/ properties to set is present
            boolean showProps = _container.isRoot();
            if (!showProps)
            {
                for (Module m : getViewContext().getContainer().getActiveModules())
                {
                    if(m.getModuleProperties().size() > 0)
                    {
                        showProps = true;
                        break;
                    }
                }
            }
            if (showProps)
                tabs.add(new TabInfo("Module Properties", "props", url));

            if (!_container.isRoot())
            {
                tabs.add(new TabInfo("Search", "fullTextSearch", url));
                tabs.add(new TabInfo("Notifications", "messages", url));
                tabs.add(new TabInfo("Export", "export", url));
                tabs.add(new TabInfo("Import", "import", url));
                tabs.add(new TabInfo("Files", "files", url));
                tabs.add(new TabInfo("Information", "info", url));
            }
            return tabs;
        }

        public HttpView getTabView(String tabId) throws Exception
        {
            switch (tabId)
            {
                case "folderTree":
                    assert !_container.isRoot() : "No folder tree for the root folder";
                    return new JspView<>("/org/labkey/core/admin/manageFolders.jsp", _form, _errors);
                case "folderType":
                    assert !_container.isRoot() : "No folder type settings for the root folder";
                    return new JspView<>("/org/labkey/core/admin/folderType.jsp", _form, _errors);
                case "mvIndicators":
                    return new JspView<>("/org/labkey/core/admin/mvIndicators.jsp", _form, _errors);
                case "fullTextSearch":
                    return new JspView<>("/org/labkey/core/admin/fullTextSearch.jsp", _form, _errors);
                case "files":
                    HttpView view = new JspView<>("/org/labkey/core/admin/view/filesProjectSettings.jsp", _form, _errors);

                    try
                    {
                        FolderManagementAction.setConfirmMessage(getViewContext(), _form);
                    }
                    catch (IllegalArgumentException e)
                    {
                        _errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                    }

                    return view;
                case "messages":
                    return getMessageTabView();
                case "export":
                    assert !_container.isRoot() : "No export for the root folder";
                    _form.setExportType(PageFlowUtil.filter(getViewContext().getActionURL().getParameter("exportType")));
                    return new JspView<>("/org/labkey/core/admin/exportFolder.jsp", _form, _errors);
                case "import":
                    assert !_container.isRoot() : "No import for the root folder";
                    return new JspView<>("/org/labkey/core/admin/importFolder.jsp", _form, _errors);
                case "info":
                    return AdminController.getContainerInfoView(_container);
                case "props":
                    return new JspView<>("/org/labkey/core/project/modulePropertiesAdmin.jsp", _form, _errors);
                default:
                    return null; // tabstrip.jsp will handle display for unknown tabIds

            }
        }

        private static final String DATA_REGION_NAME = "Users";
        private int realRowIndex = 0;

        private HttpView getMessageTabView() throws Exception
        {
            final String key = DataRegionSelection.getSelectionKey("core", CoreQuerySchema.USERS_MSG_SETTINGS_TABLE_NAME, null, DATA_REGION_NAME);
            DataRegionSelection.clearAll(getViewContext(), key);

            QuerySettings settings = new QuerySettings(getViewContext(), DATA_REGION_NAME, CoreQuerySchema.USERS_MSG_SETTINGS_TABLE_NAME);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn("DisplayName");

            QueryView queryView = new QueryView(new CoreQuerySchema(getViewContext().getUser(), getViewContext().getContainer()), settings, _errors)
            {
                @Override
                public List<DisplayColumn> getDisplayColumns()
                {
                    List<DisplayColumn> columns = new ArrayList<>();
                    SecurityPolicy policy = getContainer().getPolicy();
                    Set<String> assignmentSet = new HashSet<>();

                    assignmentSet.add(SecurityManager.getGroup(Group.groupAdministrators).getName());
                    assignmentSet.add(SecurityManager.getGroup(Group.groupDevelopers).getName());
                            
                    for (RoleAssignment assignment : policy.getAssignments())
                    {
                        Group g = SecurityManager.getGroup(assignment.getUserId());
                        if (g != null)
                            assignmentSet.add(g.getName());
                    }

                    for (DisplayColumn col : super.getDisplayColumns())
                    {
                        if (col.getName().equalsIgnoreCase("Groups"))
                            columns.add(new FolderGroupColumn(assignmentSet, col.getColumnInfo()));
                        else
                            columns.add(col);
                    }
                    return columns;
                }

                @Override
                protected void populateButtonBar(DataView dataView, ButtonBar bar)
                {
                    try {
                        // add the provider configuration views to the admin panel button
                        PanelButton adminButton = new PanelButton("Update Settings", getDataRegionName());
                        PanelConfig config = new PanelConfig(getViewContext().getActionURL().clone(), key);
                        for (MessageConfigService.ConfigTypeProvider provider : MessageConfigService.getInstance().getConfigTypes())
                        {
                            VBox view = new VBox();

                            view.addView(new HtmlView("<input type=\"hidden\" name=\"provider\" value=\"" + provider.getType() + "\">"));
                            view.addView(provider.createConfigPanel(getViewContext(), config));

                            adminButton.addSubPanel(provider.getName(), view);
                        }
                        bar.add(adminButton);

                        super.populateButtonBar(dataView, bar);
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            };
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            queryView.setShowDetailsColumn(false);
            queryView.setShowRecordSelectors(true);
            queryView.setFrame(WebPartView.FrameType.NONE);
            queryView.disableContainerFilterSelection();
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);

            VBox view = new VBox();

            view.addView(new JspView<Object>("/org/labkey/core/admin/view/folderSettingsHeader.jsp", Object.class, _errors));
            view.addView(queryView);

            return view;
        }
    }

    private static class FolderGroupColumn extends DataColumn
    {
        Set<String> _assignmentSet;
        public FolderGroupColumn(Set<String> assignmentSet, ColumnInfo col)
        {
            super(col);
            _assignmentSet = assignmentSet;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String value = (String)ctx.get(getBoundColumn().getDisplayField().getFieldKey());

            if (value != null)
            {
                StringBuilder sb = new StringBuilder();
                String delim = "";

                for (String name : value.split(","))
                {
                    if (_assignmentSet.contains(name))
                    {
                        sb.append(delim);
                        sb.append(name);
                        delim = ",<br>";
                    }
                }
                out.write(sb.toString());
            }
            //super.renderGridCellContents(ctx, out);  
        }
    }

    private static class PanelConfig implements MessageConfigService.PanelInfo
    {
        private ActionURL _returnUrl;
        private String _dataRegionSelectionKey;

        public PanelConfig(ActionURL returnUrl, String selectionKey)
        {
            _returnUrl = returnUrl;
            _dataRegionSelectionKey = selectionKey;
        }

        @Override
        public ActionURL getReturnUrl()
        {
            return _returnUrl;
        }

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }
    }
}
