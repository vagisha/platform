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

package org.labkey.experiment.list;

import org.apache.commons.lang.ObjectUtils;
import org.apache.log4j.Logger;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.HttpView;
import org.labkey.experiment.controllers.list.ListItemAttachmentParent;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListItemImpl implements ListItem
{
    boolean _new;
    ListDefinitionImpl _list;
    ListItm _itmOld;
    ListItm _itm;
    Map<String, Object> _properties;
    Map<String, Object> _oldProperties;
    private static final Object _autoIncrementLock = new Object();  // Consider: Synchronize each list separately
    private static final Logger _log = Logger.getLogger(ListItemImpl.class);

    public ListItemImpl(ListDefinitionImpl list, ListItm item)
    {
        _list = list;
        _itm = item;
    }

    public ListItemImpl(ListDefinitionImpl list)
    {
        _list = list;
        _itm = new ListItm();
        _itm.setEntityId(GUID.makeGUID());
        _itm.setListId(list.getListId());
        _new = true;
    }

    public Object getKey()
    {
        return _itm.getKey();
    }

    public void setKey(Object key)
    {
        edit().setKey(key);
    }

    public String getEntityId()
    {
        return _itm.getEntityId();
    }

    public void setEntityId(String entityId)
    {
        edit().setEntityId(entityId);
    }

    boolean isNew()
    {
        return _new;
    }

    public void delete(User user, Container c) throws SQLException
    {
        if (isNew())
            return;
        boolean fTransaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTransaction = true;
            }
            ensureProperties();
            addAuditEvent(user, "An existing list record was deleted", _itm.getEntityId(), _formatItemRecord(user, _properties, null, _itm.getKey()), null);

            SimpleFilter filter = new SimpleFilter("Key", _itm.getKey());
            filter.addCondition("ListId", _itm.getListId());
            Table.delete(_list.getIndexTable(), filter);
            deleteListItemContents(_itm, c, user);
            if (fTransaction)
            {
                ExperimentService.get().commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    // Used by single item delete as well as entire list delete
    static void deleteListItemContents(ListItm itm, Container c, User user) throws SQLException
    {
        DiscussionService.get().deleteDiscussions(c, itm.getEntityId(), user);
        AttachmentService.get().deleteAttachments(new ListItemAttachmentParent(itm, c));
        if (itm.getObjectId() != null)
        {
            OntologyObject object = OntologyManager.getOntologyObject(itm.getObjectId());
            if (object != null)
            {
                OntologyManager.deleteOntologyObjects(c, object.getObjectURI());
            }
        }

    }

    private OntologyObject getOntologyObject()
    {
        if (_itm.getObjectId() == null)
            return null;
        return OntologyManager.getOntologyObject(_itm.getObjectId());
    }

    public OntologyObject ensureOntologyObject() throws SQLException
    {
        if (_itm.getObjectId() == null)
        {
            edit().setObjectId(OntologyManager.ensureObject(_list.getContainer(), GUID.makeURN()));
        }
        return getOntologyObject();
    }

    private Map<String, Object> ensureProperties()
    {
        if (_properties == null)
        {
            OntologyObject obj = getOntologyObject();
            if (obj == null)
            {
                _properties = new HashMap<String, Object>();
            }
            else
            {
                try
                {
                    _properties = OntologyManager.getProperties(obj.getContainer(), obj.getObjectURI());
                }
                catch (SQLException e)
                {
                    throw UnexpectedException.wrap(e);
                }
            }
        }
        return _properties;
    }

    private Map<String, Object> editProperties()
    {
        if (_oldProperties == null)
        {
            _oldProperties = ensureProperties();
            _properties = new HashMap<String, Object>(_oldProperties);
        }
        return _properties;
    }

    public Object getProperty(DomainProperty property)
    {
        return ensureProperties().get(property.getPropertyURI());
    }

    public void save(User user) throws SQLException, IOException, AttachmentService.DuplicateFilenameException, ValidationException
    {
        boolean fTransaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTransaction = true;
            }

            String oldRecord = null;
            String newRecord = null;
            boolean isNew = _new;
            Map<String, DomainProperty> dps = new HashMap<String, DomainProperty>();
            for (DomainProperty dp : _list.getDomain().getProperties())
            {
                dps.put(dp.getPropertyURI(), dp);
            }

            if (_properties != null)
            {
                List<ValidationError> errors = new ArrayList<ValidationError>();
                for (Map.Entry<String, Object> entry : _properties.entrySet())
                {
                    DomainProperty dp = dps.get(entry.getKey());
                    if (dp != null)
                        validateProperty(dp, entry.getValue(), errors);
                }
                if (!errors.isEmpty())
                    throw new ValidationException(errors);
            }

            oldRecord = _formatItemRecord(user, _oldProperties, dps, (_itmOld != null ? _itmOld.getKey() : null));
            if (_oldProperties != null)
            {
                AttachmentParent parent = new ListItemAttachmentParent(this, _list.getContainer());
                List<AttachmentFile> newAttachments = new ArrayList<AttachmentFile>();
                OntologyObject obj = ensureOntologyObject();
                for (Map.Entry<String, Object> entry : _properties.entrySet())
                {
                    Object oldValue = _oldProperties.get(entry.getKey());
                    if (ObjectUtils.equals(oldValue, entry.getValue()))
                    {
                        continue;
                    }
                    DomainProperty dp = dps.get(entry.getKey());
                    if (dp == null)
                    {
                        continue;
                    }
                    if (oldValue != null)
                    {
                        if (dp.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
                            AttachmentService.get().deleteAttachment(parent, (String)oldValue);
                        OntologyManager.deleteProperty(obj.getObjectURI(), entry.getKey(), obj.getContainer(), obj.getContainer());
                    }
                    if (entry.getValue() != null)
                    {
                        Object value = entry.getValue();
                        String qcValue = null;
                        if (dp.isQcEnabled() && QcUtil.isQcValue(value.toString(), obj.getContainer()))
                        {
                            qcValue = value.toString();
                            value = null;
                        }
                        ObjectProperty property = new ObjectProperty(obj.getObjectURI(), obj.getContainer(), entry.getKey(), value, dp.getPropertyDescriptor().getPropertyType(), dp.getPropertyDescriptor().getName());
                        if (qcValue != null)
                            property.setQcValue(qcValue);
                        if (dp.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
                            newAttachments.add((AttachmentFile)entry.getValue());
                        OntologyManager.insertProperties(obj.getContainer(), obj.getObjectURI(), property);
                    }
                }
                _oldProperties = null;
                AttachmentService.get().addAttachments(user, parent, newAttachments);
            }
            if (_new)
            {
                if (_list.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
                {
                    synchronized(_autoIncrementLock)  // Consider: separate lock objects for each list having auto-increment key
                    {
                        TableInfo tinfo = _list.getIndexTable();
                        String keyName = tinfo.getColumn("Key").getSelectName();
                        Integer newKey = Table.executeSingleton(tinfo.getSchema(), "SELECT COALESCE(MAX(" + keyName + ") + 1, 1) FROM " + tinfo + " WHERE ListId = ?", new Object[]{_list.getListId()}, Integer.class);
                        _itm.setKey(newKey);
                        _itm = Table.insert(user, tinfo, _itm);
                    }
                }
                else
                {
                    _itm = Table.insert(user, _list.getIndexTable(), _itm);
                }

                _new = false;
            }
            else if (_itmOld != null)
            {
                _itm = Table.update(user, _list.getIndexTable(), _itm, new Object[] { _itmOld.getListId(), _itmOld.getKey()}, null);
                _itmOld = null;
            }

            newRecord = _formatItemRecord(user, _properties, dps, _itm.getKey());
            addAuditEvent(user, isNew ? "A new list record was inserted" : "An existing list record was modified",
                    _itm.getEntityId(), oldRecord, newRecord);

            if (fTransaction)
            {
                ExperimentService.get().commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    private boolean validateProperty(DomainProperty prop, Object value, List<ValidationError> errors)
    {
        boolean ret = true;
        for (IPropertyValidator validator : prop.getValidators())
        {
            if (!validator.validate(prop.getLabel(), value, errors)) ret = false;
        }
        return ret;
    }

    public void setProperty(DomainProperty property, Object value)
    {
        editProperties().put(property.getPropertyURI(), value);
    }

    private ListItm edit()
    {
        if (_new)
            return _itm;
        if (_itmOld == null)
        {
            _itmOld = _itm;
            _itm = _itmOld.clone();
        }
        return _itm;
    }

    private void addAuditEvent(User user, String comment, String entityId, String oldRecord, String newRecord)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setCreatedBy(user);
        event.setComment(comment);

        Container c = _list.getContainer();
        event.setContainerId(c.getId());
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setKey1(_list.getDomain().getTypeURI());

        event.setEventType(ListManager.LIST_AUDIT_EVENT);
        event.setIntKey1(_list.getListId());
        event.setKey3(_list.getName());
        event.setKey2(entityId);

        try
        {
            ListAuditViewFactory.getInstance().ensureDomain(user);
        }
        catch(ChangePropertyDescriptorException e)
        {
            throw new RuntimeException(e);
        }

        final Map<String, Object> dataMap = new HashMap<String, Object>();
        if (oldRecord != null) dataMap.put("oldRecordMap", oldRecord);
        if (newRecord != null) dataMap.put("newRecordMap", newRecord);

        if (!dataMap.isEmpty())
            AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(ListManager.LIST_AUDIT_EVENT));
        else
            AuditLogService.get().addEvent(event);
    }

    private String _formatItemRecord(User user, Map<String, Object> props, Map<String, DomainProperty> dps, Object keyValue)
    {
        try {
            Map<String, String> recordChangedMap = new HashMap<String, String>();
            if (props != null)
            {
                if (dps == null)
                {
                    dps = new HashMap<String, DomainProperty>();
                    for (DomainProperty dp : _list.getDomain().getProperties())
                    {
                        dps.put(dp.getPropertyURI(), dp);
                    }
                }
                Map<String, Object> rowMap = null;

                for (Map.Entry<String, Object> entry : props.entrySet())
                {
                    DomainProperty prop = dps.get(entry.getKey());
                    if (prop != null)
                    {
                        String value;
                        if (prop.getLookup() == null)
                        {
                            PropertyType type = prop.getPropertyDescriptor().getPropertyType();
                            if (type == PropertyType.ATTACHMENT && entry.getValue() instanceof AttachmentFile)
                                value = "attachment uploaded : " + ObjectUtils.toString(entry.getValue(), null);
                            else
                                value = ObjectUtils.toString(entry.getValue(), null);
                        }
                        else
                        {
                            if (rowMap == null)
                            {
                                TableInfo table = _list.getTable(user, null);
                                DetailsView details = new DetailsView(new QueryUpdateForm(table, HttpView.currentRequest()));
                                RenderContext ctx = details.getRenderContext();

                                ctx.setMode(DataRegion.MODE_DETAILS);
                                ctx.setBaseFilter(new PkFilter(table, _itm.getKey(), true));

                                ResultSet rs = Table.selectForDisplay(table, table.getColumns(), ctx.getBaseFilter(), ctx.getBaseSort(), 0, 0);
                                rs.next();
                                rowMap = ResultSetUtil.mapRow(rs, rowMap);
                                rs.close();
                            }
                            value = getFieldValue(user, prop, rowMap);
                        }
                        if (value != null)
                            recordChangedMap.put(prop.getName(), value);
                    }
                }
            }
            // audit key changes if they are not auto-increment
            if (keyValue != null && !_list.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
                recordChangedMap.put(_list.getKeyName(), ObjectUtils.toString(keyValue, ""));

            if (!recordChangedMap.isEmpty())
                return ListAuditViewFactory.encodeForDataMap(recordChangedMap, true);
        }
        catch (Exception e)
        {
            _log.error("Unable to format the audit change record", e);
        }
        return "";
    }

    String getFieldValue(User user, DomainProperty property, Map<String, Object> rowMap)
    {
        TableInfo table = _list.getTable(user, null);
        RenderContext ctx = new RenderContext(HttpView.currentContext());

        ColumnInfo col = table.getColumn(property.getName());

        if (null != col)
        {
            DataColumn dc = new DataColumn(col); 
            ctx.setRow(rowMap);
            return ObjectUtils.toString(dc.getDisplayValue(ctx), null);
        }

        return null;
    }

    private String _createChangeRecord()
    {
        if (_properties == null) return "";

        Map<String, DomainProperty> dps = new HashMap<String, DomainProperty>();
        for (DomainProperty dp : _list.getDomain().getProperties())
        {
            dps.put(dp.getPropertyURI(), dp);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        for (Map.Entry<String, Object> entry : _properties.entrySet())
        {
            DomainProperty prop = dps.get(entry.getKey());
            if (prop != null)
            {
                if (_oldProperties != null)
                {
                    _appendChange(sb, prop.getName(),
                            ObjectUtils.toString(_oldProperties.get(entry.getKey()), ""),
                            ObjectUtils.toString(entry.getValue(), ""));
                }
                else
                    _appendChange(sb, prop.getName(), ObjectUtils.toString(entry.getValue(), ""), null);
            }
        }
        sb.append("</table>");
        return sb.toString();
    }

    private void _appendChange(StringBuilder sb, String field, String from, String to)
    {
        if (!from.equals(to))
        {
            String encFrom = PageFlowUtil.filter(from);
            sb.append("<tr><td>").append(field).append("</td><td>").append(encFrom).append("</td>");
            if (to != null)
            {
                String encTo = PageFlowUtil.filter(to);
                sb.append("<td>&raquo;</td><td>").append(encTo).append("</td></tr>\n");
            }
            else
                sb.append("</tr>\n");
        }
    }
}
