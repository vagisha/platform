package org.labkey.announcements.query;

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AbstractBeanQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.wiki.WikiRendererDisplayColumn;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/**
 * User: jeckels
 * Date: Feb 5, 2012
 */
public class AnnouncementTable extends FilteredTable
{
    private final AnnouncementSchema _schema;
    private Boolean _secure;

    public AnnouncementTable(AnnouncementSchema schema)
    {
        super(CommSchema.getInstance().getTableInfoAnnouncements(), schema.getContainer());
        _schema = schema;
        wrapAllColumns(true);
        removeColumn(getColumn("Container"));
        ColumnInfo folderColumn = wrapColumn("Folder", getRealTable().getColumn("Container"));
        folderColumn.setFk(new ContainerForeignKey(_schema));
        addColumn(folderColumn);
        setDescription("Contains one row per announcement or reply");
        getColumn("Parent").setFk(new LookupForeignKey("EntityId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                AnnouncementTable result = new AnnouncementTable(_schema);
                result.addCondition(new SimpleFilter(new CompareType.CompareClause("Parent", CompareType.ISBLANK, null)));
                return result;
            }
        });
        final ColumnInfo renderTypeColumn = getColumn("RendererType");
        renderTypeColumn.setFk(new LookupForeignKey("Value")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return QueryService.get().getUserSchema(_schema.getUser(), _schema.getContainer(), WikiService.SCHEMA_NAME).getTable(WikiService.RENDERER_TYPE_TABLE_NAME);
            }
        });

        ColumnInfo bodyColumn = getColumn("Body");
        bodyColumn.setHidden(true);
        bodyColumn.setShownInDetailsView(false);

        ColumnInfo formattedBodyColumn = wrapColumn("FormattedBody", getRealTable().getColumn("Body"));
        formattedBodyColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new WikiRendererDisplayColumn(colInfo, renderTypeColumn.getName(), WikiRendererType.TEXT_WITH_LINKS);
            }
        });
        addColumn(formattedBodyColumn);
        formattedBodyColumn.setReadOnly(true);
        formattedBodyColumn.setUserEditable(false);
        formattedBodyColumn.setShownInInsertView(false);
        formattedBodyColumn.setShownInDetailsView(true);

        getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(_schema.getUser(), getContainer()));
        getColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(_schema.getUser(), getContainer()));
        getColumn("AssignedTo").setFk(new UserIdQueryForeignKey(_schema.getUser(), getContainer()));

        setName(AnnouncementSchema.ANNOUNCEMENT_TABLE_NAME);
        setPublicSchemaName(AnnouncementSchema.SCHEMA_NAME);
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return _schema.getContainer().hasPermission(user, perm);
    }

    private boolean isSecure()
    {
        if (_secure == null)
        {
            _secure = DiscussionService.get().getSettings(_schema.getContainer()).isSecure();
        }
        return _secure.booleanValue();
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new AnnouncementUpdateService();
    }

    private class AnnouncementUpdateService extends AbstractBeanQueryUpdateService<AnnouncementModel, Integer>
    {
        protected AnnouncementUpdateService()
        {
            super(AnnouncementTable.this);
        }

        @Override
        protected AnnouncementModel createNewBean()
        {
            return new AnnouncementModel();
        }

        @Override
        protected Integer keyFromMap(Map<String, Object> map) throws InvalidKeyException
        {
            Object rowId = map.get("RowId");
            if (rowId != null)
            {
                return (Integer)ConvertUtils.convert(rowId.toString(), Integer.class);
            }
            Object entityId = map.get("EntityId");
            if (entityId != null)
            {
                AnnouncementModel model = AnnouncementManager.getAnnouncement(getContainer(), entityId.toString());
                if (model != null)
                {
                    return model.getRowId();
                }
            }
            return null;
        }

        @Override
        protected AnnouncementModel get(User user, Container container, Integer key) throws QueryUpdateServiceException, SQLException
        {
            ensureNotSecure();
            return AnnouncementManager.getAnnouncement(container, key);
        }

        @Override
        protected AnnouncementModel insert(User user, Container container, AnnouncementModel bean) throws ValidationException, DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            ensureNotSecure();
            try
            {
                return AnnouncementManager.insertAnnouncement(container, user, bean, Collections.<AttachmentFile>emptyList());
            }
            catch (IOException e)
            {
                throw new QueryUpdateServiceException(e);
            }
            catch (MessagingException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }

        @Override
        protected AnnouncementModel update(User user, Container container, AnnouncementModel bean, Integer oldKey) throws ValidationException, QueryUpdateServiceException, SQLException
        {
            ensureNotSecure();
            try
            {
                return AnnouncementManager.updateAnnouncement(user, bean, Collections.<AttachmentFile>emptyList());
            }
            catch (IOException e)
            {
                throw new QueryUpdateServiceException(e);
            }

        }

        private void ensureNotSecure() throws QueryUpdateServiceException
        {
            if (isSecure())
            {
                throw new QueryUpdateServiceException("Not supported for secure message boards");
            }
        }

        @Override
        protected void delete(User user, Container container, Integer key) throws QueryUpdateServiceException, SQLException
        {
            ensureNotSecure();
            AnnouncementManager.deleteAnnouncement(container, key.intValue());
        }
    }
}
