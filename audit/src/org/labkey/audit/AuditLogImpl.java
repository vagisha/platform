/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.audit;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.ViewContext;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.ValidationException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Pair;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.audit.model.LogManager;
import org.labkey.audit.query.AuditQuerySchema;
import org.labkey.audit.query.AuditQueryViewImpl;

import javax.servlet.ServletContext;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 4, 2007
 */
public class AuditLogImpl implements AuditLogService.I, StartupListener
{
    private static final AuditLogImpl _instance = new AuditLogImpl();

    private static final Logger _log = Logger.getLogger(AuditLogImpl.class);
    private static final String OBJECT_XML_KEY = "objectXML";
    private static Map<String, AuditLogService.AuditViewFactory> _auditViewFactories = new HashMap<String, AuditLogService.AuditViewFactory>();

    private Queue<Pair<User, AuditLogEvent>> _eventQueue = new LinkedList<Pair<User, AuditLogEvent>>();
    private boolean _logToDatabase = false;
    private static final Object STARTUP_LOCK = new Object();

    public static AuditLogImpl get()
    {
        return _instance;
    }

    private AuditLogImpl()
    {
        ContextListener.addStartupListener(this);
    }

    public void moduleStartupComplete(ServletContext servletContext)
    {
        synchronized (STARTUP_LOCK)
        {
            _logToDatabase = true;

            while (!_eventQueue.isEmpty())
            {
                Pair<User, AuditLogEvent> event = _eventQueue.remove();
                _addEvent(event.first, event.second);
            }
        }
    }

    public boolean isViewable()
    {
        return true;
    }

    public AuditLogEvent addEvent(ViewContext context, String eventType, String key, String message)
    {
        AuditLogEvent event = _createEvent(context);
        event.setEventType(eventType);
        event.setKey1(key);
        event.setComment(message);

        return _addEvent(context.getUser(), event);
    }

    public AuditLogEvent addEvent(User user, Container c, String eventType, String key, String message)
    {
        AuditLogEvent event = _createEvent(user, c);
        event.setEventType(eventType);
        event.setKey1(key);
        event.setComment(message);

        return _addEvent(user, event);
    }

    public AuditLogEvent addEvent(User user, Container c, String eventType, String key1, String key2, String message)
    {
        AuditLogEvent event = _createEvent(user, c);
        event.setEventType(eventType);
        event.setKey1(key1);
        event.setKey2(key2);
        event.setComment(message);

        return _addEvent(user, event);
    }
    
    public AuditLogEvent addEvent(User user, Container c, String eventType, int key, String message)
    {
        AuditLogEvent event = _createEvent(user, c);
        event.setEventType(eventType);
        event.setIntKey1(key);
        event.setComment(message);

        return _addEvent(user, event);
    }

    public AuditLogEvent addEvent(AuditLogEvent event)
    {
        User user = event.getCreatedBy();
        if (user == null)
        {
            _log.warn("user was not specified, defaulting to guest user.");
            user = UserManager.getGuestUser();
            event.setCreatedBy(user);
        }

/*
        BeanObjectFactory factory = new BeanObjectFactory(event.getClass());
        Map<String, Object> map = new HashMap();
        factory.toMap(event, map);

        if (!event.getClass().equals(AuditLogEvent.class))
            event.setObjectXML(getObjectDescriptor(map));
*/
        return _addEvent(user, event);
    }

    /**
     * Factory method to get an audit event from a property map.
     * @param clz - the event class to create.
     * @return
     */
    public <K extends AuditLogEvent> K createFromMap(Map<String, Object> map, Class<K> clz)
    {
        if (map.containsKey(OBJECT_XML_KEY))
        {
            map = new CaseInsensitiveHashMap<Object>(map);
            addObjectProperties((String)map.get(OBJECT_XML_KEY), map);
        }
        return ObjectFactory.Registry.getFactory(clz).fromMap(map);
    }

    private void addObjectProperties(String objectXML, Map<String, Object> map)
    {
        map.putAll(decodeFromXML(objectXML));
    }

    private String getObjectDescriptor(Map<String, Object> properties)
    {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            XMLEncoder enc = new XMLEncoder(new BufferedOutputStream(baos));
            enc.writeObject(properties);
            enc.close();
            return baos.toString();
        }
        catch (Exception e)
        {
            _log.error("error serializing object properties to XML", e);
        }
        return null;
    }

    private AuditLogEvent _addEvent(User user, AuditLogEvent event)
    {
        try
        {
            assert event.getContainerId() != null : "Container cannot be null";

            if (event.getContainerId() == null)
            {
                _log.warn("container was not specified, defaulting to root container.");
                Container root = ContainerManager.getRoot();
                event.setContainerId(root.getId());
            }
            /**
             * This is necessary because audit log service needs to be registered in the constructor
             * of the audit module, but the schema may not be created or updated at that point.  Events
             * that occur before startup is complete are therefore queued up and recorded after startup.
             */
            synchronized (STARTUP_LOCK)
            {
                if (_logToDatabase)
                    return LogManager.get().insertEvent(user, event);
                else
                    _eventQueue.add(new Pair<User, AuditLogEvent>(user, event));
            }
        }
        catch (SQLException e)
        {
            _log.error("Failed to insert audit log event", e);
            throw new RuntimeSQLException(e);
        }
        return null;
    }

    private AuditLogEvent _createEvent(ViewContext context)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreated(new Date());
        event.setCreatedBy(context.getUser());
        Container c = context.getContainer();
        event.setContainerId(c.getId());
        if (null != c.getProject())
            event.setProjectId(c.getProject().getId());

        return event;
    }

    private AuditLogEvent _createEvent(User user, Container c)
    {
        if (user == null)
        {
            _log.warn("user was not specified, defaulting to guest user.");
            user = UserManager.getGuestUser();
        }
        AuditLogEvent event = new AuditLogEvent();
        event.setCreated(new Date());
        event.setCreatedBy(user);

        if (c != null)
        {
            event.setContainerId(c.getId());
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
        }

        return event;
    }

    private Map<String, Object> decodeFromXML(String objectXML)
    {
        try {
            XMLDecoder dec = new XMLDecoder(new ByteArrayInputStream(objectXML.getBytes("UTF-8")));
            Object o = dec.readObject();
            if (Map.class.isAssignableFrom(o.getClass()))
                return (Map<String, Object>)o;

            dec.close();
        }
        catch (Exception e)
        {
            _log.error("An error occurred parsing the object xml", e);
        }
        return Collections.emptyMap();
    }

    public AuditLogQueryView createQueryView(ViewContext context, SimpleFilter filter)
    {
        return createQueryView(context, filter, AuditQuerySchema.AUDIT_TABLE_NAME);
    }

    public AuditLogQueryView createQueryView(ViewContext context, @Nullable SimpleFilter filter, String viewFactoryName)
    {
        AuditQuerySchema schema = new AuditQuerySchema(context.getUser(), context.getContainer());
        QuerySettings settings = schema.getSettings(context, AuditQuerySchema.AUDIT_TABLE_NAME, viewFactoryName);
        settings.setBaseFilter(filter);

        // if the user is an admin, they should see everything, else default to current folder
        if (context.getUser().isAdministrator())
            settings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());
        AuditQueryViewImpl view = new AuditQueryViewImpl(schema, settings, filter);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        return view;
    }

    public String getTableName()
    {
        return AuditQuerySchema.AUDIT_TABLE_NAME;
    }

    public TableInfo getTable(ViewContext context, String name)
    {
        UserSchema schema = createSchema(context.getUser(), context.getContainer());
        return schema.getTable(name);
    }

    public UserSchema createSchema(User user, Container container)
    {
        return new AuditQuerySchema(user, container);
    }

    public List<AuditLogEvent> getEvents(String eventType, String key)
    {
        SimpleFilter filter = new SimpleFilter("EventType", eventType);
        filter.addCondition("Key1", key);

        return getEvents(filter);
    }

    public List<AuditLogEvent> getEvents(String eventType, int key)
    {
        SimpleFilter filter = new SimpleFilter("EventType", eventType);
        filter.addCondition("IntKey1", key);

        return getEvents(filter);
    }

    public List<AuditLogEvent> getEvents(SimpleFilter filter)
    {
        return getEvents(filter, null);
    }

    public List<AuditLogEvent> getEvents(SimpleFilter filter, Sort sort)
    {
        try {
            if (LogManager.get().getTinfoAuditLog().getTableType() != DatabaseTableType.NOT_IN_DB)
                return Arrays.asList(LogManager.get().getEvents(filter, sort));

            return Collections.emptyList();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public AuditLogEvent getEvent(int rowId)
    {
        /**
         * need to check for the physical table to be in existence because the audit log service needs
         * to be registered in the constructor of the audit module.
         */
        if (LogManager.get().getTinfoAuditLog().getTableType() != DatabaseTableType.NOT_IN_DB)
            return LogManager.get().getEvent(rowId);

        return null;
    }

    public synchronized void addAuditViewFactory(AuditLogService.AuditViewFactory factory)
    {
        _auditViewFactories.put(factory.getEventType(), factory);
    }

    public synchronized AuditLogService.AuditViewFactory getAuditViewFactory(String eventType)
    {
        return _auditViewFactories.get(eventType);
    }

    public AuditLogService.AuditViewFactory[] getAuditViewFactories()
    {
        List<AuditLogService.AuditViewFactory> factories = new ArrayList<AuditLogService.AuditViewFactory>(_auditViewFactories.values());

        Collections.sort(factories, new Comparator<AuditLogService.AuditViewFactory>(){
            public int compare(AuditLogService.AuditViewFactory o1, AuditLogService.AuditViewFactory o2)
            {
                return (o1.getName().compareToIgnoreCase(o2.getName()));
            }
        });
        return factories.toArray(new AuditLogService.AuditViewFactory[factories.size()]);
    }

    public AuditLogEvent addEvent(AuditLogEvent event, Map<String, Object> dataMap, String domainURI)
    {
        DbSchema schema = AuditSchema.getInstance().getSchema();

        try {
            schema.getScope().ensureTransaction();

            event = addEvent(event);
            if (event != null)
            {
                String parentLsid = domainURI + ':' + event.getRowId();

                SQLFragment updateSQL = new SQLFragment();
                updateSQL.append("UPDATE " + LogManager.get().getTinfoAuditLog() + " SET lsid = ? WHERE rowid = ?");
                updateSQL.add(parentLsid);
                updateSQL.add(event.getRowId());
                Table.execute(LogManager.get().getSchema(), updateSQL);

                addEventProperties(parentLsid, domainURI, dataMap);
            }
            schema.getScope().commitTransaction();

            return event;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            schema.getScope().closeConnection();
        }
    }

    public static void addEventProperties(String parentLsid, String domainURI, Map<String, Object> dataMap)
    {
        Container c = ContainerManager.getSharedContainer();
        Domain domain = PropertyService.get().getDomain(c, domainURI);

        if (domain == null)
            throw new IllegalStateException("Domain does not exist: " + domainURI);

        ObjectProperty[] properties = new ObjectProperty[dataMap.size()];
        int i=0;
        for (Map.Entry<String, Object> entry : dataMap.entrySet())
        {
            DomainProperty prop = domain.getPropertyByName(entry.getKey());
            if (prop != null)
            {
                properties[i++] = new ObjectProperty(null, c, prop.getPropertyURI(), entry.getValue());
            }
            else
            {
                throw new IllegalStateException("Specified property: " + entry.getKey() + " is not available in domain: " + domainURI);
            }
        }
        for (ObjectProperty prop : properties)
            prop.setObjectURI(parentLsid);

        try {
            OntologyManager.insertProperties(c, parentLsid, properties);
        }
        catch (ValidationException e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public String getDomainURI(String eventType)
    {
        return new Lsid("AuditLogService", eventType).toString();
    }

    public String getPropertyURI(String eventType, String propertyName)
    {
        return new Lsid("AuditLogService", eventType).toString() + '#' + propertyName;
    }
}
