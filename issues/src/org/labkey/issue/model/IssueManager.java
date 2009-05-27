/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
package org.labkey.issue.model;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.*;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.Cache;
import org.labkey.issue.IssuesController;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


/**
 * User: mbellew
 * Date: Mar 11, 2005
 * Time: 11:07:27 AM
 */
public class IssueManager
{
    // UNDONE: Keywords, Summary, etc.

    private static IssuesSchema _issuesSchema = IssuesSchema.getInstance();
    
    private static Logger _log = Logger.getLogger(IssueManager.class);

    public static final int NOTIFY_ASSIGNEDTO_OPEN = 1;     // if a bug is assigned to me
    public static final int NOTIFY_ASSIGNEDTO_UPDATE = 2;   // if a bug assigned to me is modified
    public static final int NOTIFY_CREATED_UPDATE = 4;      // if a bug I created is modified
    public static final int NOTIFY_SELF_SPAM = 8;           // spam me when I enter/edit a bug
    public static final int DEFAULT_EMAIL_PREFS = NOTIFY_ASSIGNEDTO_OPEN | NOTIFY_ASSIGNEDTO_UPDATE | NOTIFY_CREATED_UPDATE;

    private static final String ISSUES_PREF_MAP = "IssuesPreferencesMap";
    private static final String ISSUES_REQUIRED_FIELDS = "IssuesRequiredFields";

    private static final String CAT_ENTRY_TYPE_NAMES = "issueEntryTypeNames";
    private static final String PROP_ENTRY_TYPE_NAME_SINGULAR = "issueEntryTypeNameSingular";
    private static final String PROP_ENTRY_TYPE_NAME_PLURAL = "issueEntryTypeNamePlural";

    private IssueManager()
    {
    }

    public static Issue getIssue(Object s, Container c, int issueId) throws SQLException
    {
        Issue[] issues = Table.selectForDisplay(
                _issuesSchema.getTableInfoIssues(),
                Table.ALL_COLUMNS,
                new SimpleFilter("issueId", new Integer(issueId))
                        .addCondition("container", c.getId()),
                null, Issue.class);
        if (null == issues || issues.length < 1)
            return null;
        Issue issue = issues[0];

        Issue.Comment[] comments = Table.select(
                _issuesSchema.getTableInfoComments(),
                Table.ALL_COLUMNS,
                new SimpleFilter("issueId", new Integer(issue.getIssueId())),
                new Sort("CommentId"), Issue.Comment.class);
        issue.setComments(new ArrayList<Issue.Comment>(Arrays.asList(comments)));
        return issue;
    }


    public static void saveIssue(Object s, User user, Container c, Issue issue) throws SQLException
    {
        if (issue.assignedTo == null)
            issue.assignedTo = new Integer(0);

        if (issue.issueId == 0)
        {
            issue.beforeInsert(user, c.getId());
            Table.insert(user, _issuesSchema.getTableInfoIssues(), issue);
        }
        else
        {
            issue.beforeUpdate(user);
            Table.update(user, _issuesSchema.getTableInfoIssues(), issue, new Integer(issue.getIssueId()), null);
        }
        saveComments(user, issue);
    }


    protected static void saveComments(User user, Issue issue) throws SQLException
    {
        Collection<Issue.Comment> comments = issue._added;
        if (null == comments)
            return;
        for (Issue.Comment comment : comments)
        {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("issueId", new Integer(issue.getIssueId()));
            m.put("comment", comment.getComment());
            m.put("entityId", comment.getEntityId());
            Table.insert(user, _issuesSchema.getTableInfoComments(), m);
        }
        issue._added = null;
    }


    public static void addKeyword(Container c, int type, HString keyword)
    {
        try
        {
            Table.execute(_issuesSchema.getSchema(),
                    "INSERT INTO " + _issuesSchema.getTableInfoIssueKeywords() + " (Container, Type, Keyword) VALUES (?, ?, ?)",
                    new Object[]{c.getId(), new Integer(type), keyword});
            DbCache.clear(_issuesSchema.getTableInfoIssueKeywords());
        }
        catch (SQLException x)
        {
            _log.error(x);
            //probably primary key violation
        }
    }


    public static class Keyword
    {
        HString _keyword;
        boolean _default = false;

        public boolean isDefault()
        {
            return _default;
        }

        public void setDefault(boolean def)
        {
            _default = def;
        }

        public HString getKeyword()
        {
            return _keyword;
        }

        public void setKeyword(HString keyword)
        {
            _keyword = keyword;
        }
    }


    public static Keyword[] getKeywords(String container, int type)
    {
        Keyword[] keywords = null;
        SimpleFilter filter = new SimpleFilter("Container", container).addCondition("Type", type);
        Sort sort = new Sort("Keyword");

        try
        {
            keywords = Table.select(_issuesSchema.getTableInfoIssueKeywords(), PageFlowUtil.set("Keyword", "Default", "Container", "Type"), filter, sort, Keyword.class);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }

        return keywords;
    }


    public static Map<Integer, HString> getAllDefaults(Container container) throws SQLException
    {
        ResultSet rs = null;

        try
        {
            SimpleFilter filter = new SimpleFilter("container", container.getId()).addCondition("Default", true);
            rs = Table.select(_issuesSchema.getTableInfoIssueKeywords(), PageFlowUtil.set("Type", "Keyword", "Container", "Default"), filter, null);

            Map<Integer, HString> defaults = new HashMap<Integer, HString>();

            while (rs.next())
                defaults.put(rs.getInt("Type"), new HString(rs.getString("Keyword")));

            return defaults;
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    // Clear old default value and set new one
    public static void setKeywordDefault(Container c, int type, HString keyword)
    {
        clearKeywordDefault(c, type);

        String selectName = _issuesSchema.getTableInfoIssueKeywords().getColumn("Default").getSelectName();

        try
        {
            Table.execute(_issuesSchema.getSchema(),
                    "UPDATE " + _issuesSchema.getTableInfoIssueKeywords() + " SET " + selectName + "=? WHERE Container=? AND Type=? AND Keyword=?",
                    new Object[]{Boolean.TRUE, c.getId(), type, keyword});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    // Clear existing default value
    public static void clearKeywordDefault(Container c, int type)
    {
        String selectName = _issuesSchema.getTableInfoIssueKeywords().getColumn("Default").getSelectName();

        try
        {
            Table.execute(_issuesSchema.getSchema(),
                    "UPDATE " + _issuesSchema.getTableInfoIssueKeywords() + " SET " + selectName + "=? WHERE Container=? AND Type=?",
                    new Object[]{Boolean.FALSE, c.getId(), type});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static void deleteKeyword(Container c, int type, HString keyword)
    {
        try
        {
            Table.execute(_issuesSchema.getSchema(),
                    "DELETE FROM " + _issuesSchema.getTableInfoIssueKeywords() + " WHERE Container=? AND Type=? AND Keyword=?",
                    new Object[]{c.getId(), new Integer(type), keyword});
            DbCache.clear(_issuesSchema.getTableInfoIssueKeywords());
        }
        catch (SQLException x)
        {
            _log.error("deleteKeyword", x);
        }
    }


    private static final String CUSTOM_COLUMN_CONFIGURATION = "IssuesCaptions";

    public static CustomColumnConfiguration getCustomColumnConfiguration(Container c)
    {
        Map<String, String> map = PropertyManager.getProperties(c.getId(), CUSTOM_COLUMN_CONFIGURATION, false);

        return new CustomColumnConfiguration(map);
    }


    public static void saveCustomColumnConfiguration(Container c, CustomColumnConfiguration ccc) throws SQLException
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(0, c.getId(), CUSTOM_COLUMN_CONFIGURATION, true);

        map.clear();
        map.putAll(ccc.getColumnCaptions());
        map.put(CustomColumnConfiguration.PICK_LIST_NAME, StringUtils.join(ccc.getPickListColumns().iterator(), ","));

        PropertyManager.saveProperties(map);
    }


    public static class CustomColumnConfiguration
    {
        public static final String PICK_LIST_NAME = "pickListColumns";
        private static String[] _tableColumns = new String[]{"int1", "int2", "string1", "string2"};
        private Map<String, String> _columnCaptions = new CaseInsensitiveHashMap<String>(5);
        private Set<String> _pickListColumns = new HashSet<String>(2);

        public CustomColumnConfiguration(Map<String, ? extends Object> map)
        {
            if (null == map)
                return;

            setColumnCaptions(map);
            setPickListColumns(map);
        }

        private void setColumnCaptions(Map<String, ? extends Object> map)
        {
            for (String tableColumn : _tableColumns)
            {
                String caption = (String)map.get(tableColumn);

                if (!StringUtils.isEmpty(caption))
                    _columnCaptions.put(tableColumn, caption);
            }
        }

        public Map<String, String> getColumnCaptions()
        {
            return _columnCaptions;
        }

        private void setPickListColumns(Map<String, ? extends Object> map)
        {
            Object pickListColumnNames = map.get(PICK_LIST_NAME);

            if (null == pickListColumnNames)
                return;

            String[] columns;

            if (pickListColumnNames.getClass().equals(String.class))
                columns = ((String)pickListColumnNames).split(",");
            else
                columns = ((List<String>)pickListColumnNames).toArray(new String[]{});

            for (String column : columns)
                if (null != _columnCaptions.get(column))
                    _pickListColumns.add(column);
        }

        public Set<String> getPickListColumns()
        {
            return _pickListColumns;
        }
    }


    public static Map[] getSummary(Container c) throws SQLException
    {
        return Table.executeQuery(_issuesSchema.getSchema(),
                "SELECT DisplayName, SUM(CASE WHEN Status='open' THEN 1 ELSE 0 END) AS " + _issuesSchema.getSqlDialect().getTableSelectName("Open") + ", SUM(CASE WHEN Status='resolved' THEN 1 ELSE 0 END) AS " + _issuesSchema.getSqlDialect().getTableSelectName("Resolved") + "\n" +
                        "FROM " + _issuesSchema.getTableInfoIssues() + " LEFT OUTER JOIN " + CoreSchema.getInstance().getTableInfoUsers() + " ON AssignedTo = UserId\n" +
                        "WHERE Status in ('open', 'resolved') AND Container = ?\n" +
                        "GROUP BY DisplayName",
                new Object[]{c.getId()},
                Map.class);
    }

    public static User[] getAssignedToList(Container c, Issue issue)
    {
        final TableInfo table = CoreSchema.getInstance().getTableInfoActiveUsers();
        final String cacheKey = getCacheKey(c);

        Map<String, User> assignedToMap = (Map<String, User>) DbCache.get(table, cacheKey);
        if (assignedToMap == null)
        {
            assignedToMap = new TreeMap<String, User>();
            for (User user : SecurityManager.getProjectMembers(c.getProject()))
            {
                assignedToMap.put(user.getEmail(), user);
            }
            DbCache.put(table, cacheKey, assignedToMap, Cache.HOUR);
        }

        //add the user who opened this issue, unless they are a guest, or already in the list.
        if (issue != null)
        {
            User createdByUser = UserManager.getUser(issue.getCreatedBy());
            if (createdByUser != null && !createdByUser.isGuest() && !assignedToMap.containsKey(createdByUser.getEmail()))
            {
                assignedToMap.put(createdByUser.getEmail(), createdByUser);
                DbCache.put(table, cacheKey, assignedToMap, Cache.HOUR);
            }
        }
        return assignedToMap.values().toArray(new User[0]);
    }

    public static String getCacheKey(Container c)
    {
        String key = "AssignedTo";
        return null != c ? key + c.getProject().getName() : key;
    }

    public static int getUserEmailPreferences(Container c, int userId)
    {
        Integer[] emailPreference;

        //if the user is inactive, don't send email
        User user = UserManager.getUser(userId);
        if(null != user && !user.isActive())
            return 0;

        try
        {
            emailPreference = Table.executeArray(
                    _issuesSchema.getSchema(),
                    "SELECT EmailOption FROM " + _issuesSchema.getTableInfoEmailPrefs() + " WHERE Container=? AND UserId=?",
                    new Object[]{c.getId(), userId},
                    Integer.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (emailPreference.length == 0)
        {
            if (userId == UserManager.getGuestUser().getUserId())
            {
                return 0; 
            }
            return DEFAULT_EMAIL_PREFS;
        }
        return emailPreference[0].intValue();
    }

    public static class EntryTypeNames
    {
        public HString singularName = new HString("Issue", false);
        public HString pluralName = new HString("Issues", false);
    }

    public static EntryTypeNames getEntryTypeNames(Container container)
    {
        Map<String,String> props = PropertyManager.getProperties(container.getId(), CAT_ENTRY_TYPE_NAMES, false);
        EntryTypeNames ret = new EntryTypeNames();
        if(null != props && props.containsKey(PROP_ENTRY_TYPE_NAME_SINGULAR))
            ret.singularName = new HString(props.get(PROP_ENTRY_TYPE_NAME_SINGULAR));
        if(null != props && props.containsKey(PROP_ENTRY_TYPE_NAME_PLURAL))
            ret.pluralName = new HString(props.get(PROP_ENTRY_TYPE_NAME_PLURAL));
        return ret;
    }

    public static void saveEntryTypeNames(Container container, EntryTypeNames names)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(container.getId(), CAT_ENTRY_TYPE_NAMES, true);
        props.put(PROP_ENTRY_TYPE_NAME_SINGULAR, names.singularName.getSource());
        props.put(PROP_ENTRY_TYPE_NAME_PLURAL, names.pluralName.getSource());
        PropertyManager.saveProperties(props);
    }

    public static void setUserEmailPreferences(Container c, int userId, int emailPrefs, int currentUser)
    {
        try
        {
            int ret = Table.execute(_issuesSchema.getSchema(),
                    "UPDATE " + _issuesSchema.getTableInfoEmailPrefs() + " SET EmailOption=? WHERE Container=? AND UserId=?",
                    new Object[]{emailPrefs, c.getId(), userId});


            if (ret == 0)
            {
                // record doesn't exist yet...
                Table.execute(_issuesSchema.getSchema(),
                        "INSERT INTO " + _issuesSchema.getTableInfoEmailPrefs() + " (Container, UserId, EmailOption ) VALUES (?, ?, ?)",
                        new Object[]{c.getId(), userId, emailPrefs});
            }
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
    }

    public static void deleteUserEmailPreferences(User user) throws SQLException
    {
        Table.delete(_issuesSchema.getTableInfoEmailPrefs(), new SimpleFilter("UserId", user.getUserId()));
    }

    public static long getIssueCount(Container c)
    {
        try
        {
            Long l = Table.executeSingleton(_issuesSchema.getSchema(), "SELECT COUNT(*) FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?", new Object[]{c.getId()}, Long.class);
            return l.longValue();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public static void uncache(Container c)
    {
        if (c != null)
            DbCache.remove(CoreSchema.getInstance().getTableInfoActiveUsers(), getCacheKey(c));
        else
            DbCache.removeUsingPrefix(CoreSchema.getInstance().getTableInfoActiveUsers(), getCacheKey(null));
    }

    public static void purgeContainer(Container c)
    {
        try
        {
            _issuesSchema.getSchema().getScope().beginTransaction();
            String deleteComments = "DELETE FROM " + _issuesSchema.getTableInfoComments() + " WHERE IssueId IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?)";
            Table.execute(_issuesSchema.getSchema(), deleteComments, new Object[]{c.getId()});
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssues(), c, null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssueKeywords(), c, null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoEmailPrefs(), c, null);
            _issuesSchema.getSchema().getScope().commitTransaction();
        }
        catch (SQLException x)
        {
        }
        finally
        {
            _issuesSchema.getSchema().getScope().closeConnection();
        }
    }


    public static String purge() throws SQLException
    {
        String message = "";

        try
        {
            _issuesSchema.getSchema().getScope().beginTransaction();
            String deleteComments =
                    "DELETE FROM " + _issuesSchema.getTableInfoComments() + " WHERE IssueId IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container NOT IN (SELECT EntityId FROM core.Containers))";
            int commentsDeleted = Table.execute(_issuesSchema.getSchema(), deleteComments, null);
            String deleteOrphanedComments =
                    "DELETE FROM " + _issuesSchema.getTableInfoComments() + " WHERE IssueId NOT IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + ")";
            commentsDeleted += Table.execute(_issuesSchema.getSchema(), deleteOrphanedComments, null);
            int issuesDeleted = ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssues(), null);
            ContainerUtil.purgeTable(_issuesSchema.getTableInfoIssueKeywords(), null);
            _issuesSchema.getSchema().getScope().commitTransaction();

            message = "deleted " + issuesDeleted + " issues<br>\ndeleted " + commentsDeleted + " comments<br>\n";
        }
        finally
        {
            if (_issuesSchema.getSchema().getScope().isTransactionActive())
                _issuesSchema.getSchema().getScope().rollbackTransaction();
        }

        return message;
    }

    public static HString getRequiredIssueFields(Container container)
    {
        String requiredFields = IssuesController.DEFAULT_REQUIRED_FIELDS;
        Map<String, String> map = PropertyManager.getProperties(container.getId(), ISSUES_PREF_MAP, false);
        if (map != null)
            requiredFields = map.get(ISSUES_REQUIRED_FIELDS);
        return new HString(requiredFields,true);
    }

    public static void setRequiredIssueFields(Container container, String requiredFields) throws SQLException
    {
        Map<String, String> map = PropertyManager.getWritableProperties(0, container.getId(), ISSUES_PREF_MAP, true);

        if (!StringUtils.isEmpty(requiredFields))
            requiredFields = requiredFields.toLowerCase();
        map.put(ISSUES_REQUIRED_FIELDS, requiredFields);
        PropertyManager.saveProperties(map);
    }

    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testIssues()
                throws IOException, SQLException, ServletException
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            int issueId;

            //
            // INSERT
            //
            {
                Issue issue = new Issue();
                issue.Open(c, user);
                issue.setAssignedTo(new Integer(user.getUserId()));
                issue.setTitle(new HString("This is a junit test bug",false));
                issue.setTag(new HString("junit",false));
                issue.addComment(user, new HString("new issue",false));
                issue.setPriority(3);

                IssueManager.saveIssue(null, user, c, issue);
                issueId = issue.getIssueId();
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertEquals("This is a junit test bug", issue.getTitle().getSource());
                assertEquals(user.getUserId(), issue.getCreatedBy());
                assertTrue(issue.getCreated().getTime() != 0);
                assertTrue(issue.getModified().getTime() != 0);
                assertEquals(user.getUserId(), issue.getAssignedTo().intValue());
                assertEquals(Issue.statusOPEN, issue.getStatus());
                assertEquals(1, issue.getComments().size());
				HString comment = (issue.getComments().iterator().next()).getComment();
                assertTrue(HString.eq("new issue", comment));
            }

            //
            // ADD COMMENT
            //
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                issue.addComment(user, new HString("what was I thinking"));
                IssueManager.saveIssue(null, user, c, issue);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertEquals(2, issue.getComments().size());
                Iterator it = issue.getComments().iterator();
                assertEquals("new issue", ((Issue.Comment) it.next()).getComment().getSource());
                assertEquals("what was I thinking", ((Issue.Comment) it.next()).getComment().getSource());
            }

            //
            // RESOLVE
            //
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertNotNull("issue not found", issue);
                issue.Resolve(user);

                Issue copy = (Issue) JunitUtil.copyObject(issue);
                copy.setResolution(new HString("fixed"));
                copy.addComment(user, new HString("fixed it"));
                IssueManager.saveIssue(null, user, c, copy);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertEquals(Issue.statusRESOLVED, issue.getStatus());
                assertEquals(3, issue.getComments().size());
            }

            //
            // CLOSE
            //
            {
                Issue issue = getIssue(null, c, issueId);
                assertNotNull("issue not found", issue);
                issue.Close(user);

                Issue copy = (Issue) JunitUtil.copyObject(issue);
                copy.addComment(user, new HString("closed"));
                IssueManager.saveIssue(null, user, c, copy);
            }

            // verify
            {
                Issue issue = IssueManager.getIssue(null, c, issueId);
                assertEquals(Issue.statusCLOSED, issue.getStatus());
                assertEquals(4, issue.getComments().size());
            }
        }


        protected void tearDown() throws Exception
        {
            Container c = JunitUtil.getTestContainer();

            String deleteComments = "DELETE FROM " + _issuesSchema.getTableInfoComments() + " WHERE IssueId IN (SELECT IssueId FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?)";
            Table.execute(_issuesSchema.getSchema(), deleteComments, new Object[]{c.getId()});
            String deleteIssues = "DELETE FROM " + _issuesSchema.getTableInfoIssues() + " WHERE Container = ?";
            Table.execute(_issuesSchema.getSchema(), deleteIssues, new Object[]{c.getId()});
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
