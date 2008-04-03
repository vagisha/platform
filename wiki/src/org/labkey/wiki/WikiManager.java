/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.wiki;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.common.util.Pair;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: mbellew
 * Date: Mar 10, 2005
 * Time: 1:27:36 PM
 */
public class WikiManager
{
    private static CommSchema comm = CommSchema.getInstance();
    private static CoreSchema core = CoreSchema.getInstance();

    private static final int LATEST = -1;

//    private static SessionFactory _sessionFactory = DataSourceSessionFactory.create(_schema,
//            new Class[]{Wiki.class, Attachment.class},
//            CacheMode.NORMAL);


    static class WikiAndVersion extends Pair<Wiki, WikiVersion>
    {
        public WikiAndVersion(Wiki wiki, WikiVersion version)
        {
            super(wiki, version);
        }

        public Wiki getWiki()
        {
            return getKey();
        }

        public WikiVersion getWikiVersion()
        {
            return getValue();
        }
    }


    private WikiManager()
    {
    }

    //does not include attachments
    public static List<Wiki> getWikisByParentId(String containerId, int parentRowId)
    {
        SimpleFilter filter = new SimpleFilter("container", containerId);
        filter.addCondition("Parent", parentRowId);
        try
        {
            Wiki[] wikis = Table.select(comm.getTableInfoPages(),
                    Table.ALL_COLUMNS,
                    filter,
                    new Sort("DisplayOrder,RowId"), Wiki.class);
            return Arrays.asList(wikis);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private static void buildDescendentList(List<Wiki> list, Wiki page, boolean deep) throws SQLException
    {
        List<Wiki> children = getWikisByParentId(page.getContainerId(), page.getRowId());
        list.addAll(children);
        if (deep)
        {
            for (Wiki child : children)
                buildDescendentList(list, child, deep);
        }
    }

    public static List<Wiki> getDescendents(Wiki page, boolean deep) throws SQLException
    {
        List<Wiki> descendents = new ArrayList<Wiki>();
        buildDescendentList(descendents, page, deep);
        return descendents;
    }


    // Used to verify that entityId is a wiki and belongs in the specified container
    public static Wiki getWikiByEntityId(Container c, String entityId) throws SQLException
    {
        if(null == c || c.getId().length() == 0 || null == entityId || entityId.length() == 0)
            return null;

        Wiki[] wikis = Table.select(comm.getTableInfoPages(),
                Table.ALL_COLUMNS,
                new SimpleFilter("Container", c.getId()).addCondition("EntityId", entityId),
                null, Wiki.class);
        if (null == wikis || 0 == wikis.length)
            return null;
        else
            return wikis[0];
    }

    // CONSIDER: move caching and wiki processing into this layer...
    //get wiki with attachments
    private static Wiki getWikiByName(Container c, String name)
    {
        try
        {
            if (name == null)
                return null;
            Wiki[] wikis = Table.select(comm.getTableInfoPages(),
                    Table.ALL_COLUMNS,
                    new SimpleFilter("container", c.getId()).addCondition("name", name),
                    null, Wiki.class);
            if (null == wikis || 0 == wikis.length)
            {
                //Didn't find it with case-sensitive lookup, try case-sensitive (in case the
                //underlying database is case sensitive)
                //Bug 2225
                wikis = Table.select(comm.getTableInfoPages(),
                        Table.ALL_COLUMNS,
                        new SimpleFilter("container", c.getId()).addWhereClause("LOWER(name) = LOWER(?)", new Object[] { name }),
                        null, Wiki.class);
                if (null == wikis || 0 == wikis.length)
                    return null;

            }
            Wiki wiki = wikis[0];

            Attachment[] att = AttachmentService.get().getAttachments(wiki);
            wiki.setAttachments(Arrays.asList(att));

            return wiki;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static boolean insertWiki(org.labkey.api.security.User user, Container c, Wiki wikiInsert, WikiVersion wikiversion, List<AttachmentFile> files)
            throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        DbScope scope = comm.getSchema().getScope();
        try
        {
            scope.beginTransaction();

            //transact insert of wiki page, new version, and any attachments
            wikiInsert.beforeInsert(user, c.getId());
            wikiInsert.setPageVersionId(null);
            Table.insert(user, comm.getTableInfoPages(), wikiInsert);
            String entityId = wikiInsert.getEntityId();

            //insert initial version for this page
            wikiversion.setPageEntityId(entityId);
            wikiversion.setCreated(wikiInsert.getCreated());
            wikiversion.setCreatedBy(wikiInsert.getCreatedBy());
            wikiversion.setVersion(1);
            Table.insert(user, comm.getTableInfoPageVersions(), wikiversion);

            //get rowid for newly inserted version
            wikiversion = getVersion(wikiInsert, 1);

            //store initial version reference in Pages table
            wikiInsert.setPageVersionId(wikiversion.getRowId());
            Table.update(user, comm.getTableInfoPages(), wikiInsert, wikiInsert.getEntityId(), null);

            AttachmentService.get().addAttachments(user, wikiInsert, files);

            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();
        }
        //uncache the entire container cache since wikis can refer to pages that haven't been created yet
        WikiCache.uncache(c);
        return true;
    }


    public static boolean updateWiki(org.labkey.api.security.User user, Wiki wikiUpdate, WikiVersion wikiversion)
            throws SQLException
    {
        DbScope scope = comm.getSchema().getScope();
        try
        {
            //transact wiki update and version insert
            scope.beginTransaction();

            //update Pages table
            //UNDONE: should take RowId, not EntityId
            Table.update(user, comm.getTableInfoPages(), wikiUpdate, wikiUpdate.getEntityId(), null);

            if(wikiversion != null)
            {
                String entityId = wikiUpdate.getEntityId();
                wikiversion.setPageEntityId(entityId);
                wikiversion.setCreated(new Date(System.currentTimeMillis()));
                wikiversion.setCreatedBy(user.getUserId());
                //get version number for new version
                wikiversion.setVersion(getNextVersionNumber(wikiUpdate));
                //insert initial version for this page
                wikiversion = Table.insert(user, comm.getTableInfoPageVersions(), wikiversion);

                //update version reference in Pages table.
                wikiUpdate.setPageVersionId(wikiversion.getRowId());
                Table.update(user, comm.getTableInfoPages(), wikiUpdate, wikiUpdate.getEntityId(), null);
            }
            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();
        }
        // Uncache entire container to invalidate old version of page and references to this page from other pages
        WikiCache.uncache(ContainerManager.getForId(wikiUpdate.getContainerId()));
        return true;
    }



    public static void deleteWiki(User user, Container c, Wiki wiki) throws SQLException
    {
        //shift children to new parent
        reparent(user, wiki);

        DbScope scope = comm.getSchema().getScope();

        try
        {
            //transact deletion of wiki, version, attachments, and discussions
            scope.beginTransaction();

            wiki.setPageVersionId(null);
            Table.update(user, comm.getTableInfoPages(), wiki, wiki.getEntityId(), null);
            Table.delete(comm.getTableInfoPageVersions(),
                    new SimpleFilter("pageentityId", wiki.getEntityId()));
            Table.delete(comm.getTableInfoPages(),
                    new SimpleFilter("entityId", wiki.getEntityId()));

            AttachmentService.get().deleteAttachments(wiki);

//            DiscussionService.get().unlinkDiscussions(c, wiki.getEntityId(), user);
            DiscussionService.get().deleteDiscussions(c, wiki.getEntityId(), user);

            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();
        }
        WikiCache.uncache(c);  // Uncache entire container to invalidate references to this page from other pages
    }



    private static void reparent(User user, Wiki wiki) throws SQLException
    {
        //shift any children upward so they are not orphaned

        //get page's children
        List<Wiki> children = wiki.getChildren();

        if (children.size() > 0)
        {
            Wiki parent = wiki.getParentWiki();
            int parentId = -1;
            float wikiDisplay = wiki.getDisplayOrder();
            Wiki nextWiki = null;

            //if page being deleted is not at root, get id and display order of its parent
            if (null != parent)
                parentId = parent.getRowId();

            //get pages's siblings (children of its parent)
            List<Wiki> siblings = getWikisByParentId(wiki.getContainerId(), parentId);

            //find parent wiki page in sibling list, and determine its position (based on display order)
            int wikiPosition = 0;
            for (Wiki w : siblings)
            {
                //hack: make sure we are working with the right kind of wiki object for comparison
                if (w.getEntityId().equals(wiki.getEntityId()))
                {
                    wikiPosition = siblings.indexOf(w);
                    break;
                }
            }

            //get next sibling to parent
            if(wikiPosition < siblings.size() - 1)
                nextWiki = siblings.get(wikiPosition + 1);

            //children need to fit between parent wiki and next wiki
            //increment child's order, starting with deleted page's order
            float reorder = wikiDisplay;
            for (Wiki child : children)
            {
                child.setParent(parentId);
                child.setDisplayOrder(reorder++);
                updateWiki(user, child, null);
            }

            //if there are subsequent siblings, reorder them as well.
            if (null != nextWiki)
            {
                //walk through siblings starting with page following parent
                for (int i = wikiPosition + 1; i < siblings.size(); i++)
                {
                    Wiki lowerSib = siblings.get(i);
                    lowerSib.setDisplayOrder(reorder++);
                    updateWiki(user, lowerSib, null);
                }
            }
        }
    }


    public static long getWikiCount(Container c)
            throws SQLException
    {
        return Table.executeSingleton(comm.getSchema(), "SELECT COUNT(*) FROM " + comm.getTableInfoPages() + " WHERE Container = ?", new Object[]{c.getId()}, Long.class);
    }


    public static void purgeContainer(Container c) throws SQLException
    {
        WikiCache.uncache(c);

        DbScope scope = comm.getSchema().getScope();
        try
        {
            scope.beginTransaction();
            Object[] params = { c.getId() };
            Table.execute(comm.getSchema(), "UPDATE " + comm.getTableInfoPages() + " SET PageVersionId = NULL WHERE Container = ?", params);
            Table.execute(comm.getSchema(), "DELETE FROM " + comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)", params);

            //delete stored web part information for this container (e.g., page to display in wiki web part)
            Table.execute(Portal.getSchema(),
                    "UPDATE " + Portal.getTableInfoPortalWebParts() + " SET Properties = NULL WHERE (Name LIKE '%Wiki%') AND lower(Properties) LIKE lower('%" + c.getId() + "%')",
                    null);

            ContainerUtil.purgeTable(comm.getTableInfoPages(), c, null);

            scope.commitTransaction();
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();
        }
    }


    public static int purge() throws SQLException
    {
        return ContainerUtil.purgeTable(comm.getTableInfoPages(), null);
    }


    public static MultiMap<String, String> search(Collection<String> containerIds, Search.SearchTermParser parser)
    {
        String fromClause = comm.getTableInfoPages() + " p INNER JOIN " + comm.getTableInfoPageVersions() + " v ON p.EntityId = v.PageEntityId";
        String maxVersionWhere = "v.RowId IN (SELECT MAX(RowId) FROM " + comm.getTableInfoPageVersions() + " GROUP BY PageEntityId)";
        SimpleFilter versionFilter = new SimpleFilter().addWhereClause(maxVersionWhere, null);
        SQLFragment fragment = Search.getSQLFragment("Container, Title, Name", "Container, Title, Name", fromClause, "Container", versionFilter, containerIds, parser, comm.getSchema().getSqlDialect(), "Title", "Body");

        MultiMap<String, String> map = new MultiHashMap<String, String>();
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(comm.getSchema(), fragment);

            while(rs.next())
            {
                String containerId = rs.getString(1);
                Container c = ContainerManager.getForId(containerId);
                ActionURL url = new ActionURL("Wiki", "page", c.getPath());
                url.addParameter("name", rs.getString(3));

                StringBuilder link = new StringBuilder("<a href=\"");
                link.append(url.getEncodedLocalURIString());
                link.append("\">");
                link.append(PageFlowUtil.filter(rs.getString(2)));
                link.append("</a>");

                map.put(rs.getString(1), link.toString());
            }
        }
        catch(SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(HttpView.currentRequest(), e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        return map;
    }


    private static Map<String, Wiki> generatePageMap(Container c) throws SQLException
    {
        Map<String, Wiki> tree = new TreeMap<String, Wiki>();
        List<Wiki> l = getPageList(c);
        for (Wiki wiki : l)
        {
            tree.put(wiki.getName(), wiki);
        }
        return tree;
    }

    /**
     * This method will update the parent pointers of all wiki pages in a given container.
     *
     * @param current
     */
    public static void updateWikiParenting(Container current) throws SQLException
    {
        Map<String, Wiki> pageMap = generatePageMap(current);
        for (Wiki page : pageMap.values())
        {
            int slash = page.getName().lastIndexOf("/");
            if (slash >= 0)
            {
                String parentName = page.getName().substring(0, slash);
                boolean done = false;
                while (!done)
                {
                    Wiki parent = pageMap.get(parentName);
                    if (parent != null)
                    {
                        page.setParent(parent.getRowId());
                        updateWiki(null, page, null);
                        done = true;
                    }
                    else
                    {
                        // if we couldn't find an exact match, there's probably a
                        // bad container name in our hierarchy; go up one parent and try again.
                        slash = parentName.lastIndexOf("/");
                        if (slash > 0)
                            parentName = parentName.substring(0, slash);
                        else
                            done = true;
                    }
                }
            }
        }
    }

    private static void addAllChildren(List<Wiki> pages, Wiki current)
    {
        pages.add(current);
        if (current.getChildren() != null)
        {
            for (Wiki page : current.getChildren())
                addAllChildren(pages, page);
        }
    }

    //does not include attachments, does include depth
    public static List<Wiki> getPageList(Container c)
    {
        List<Wiki> pageList = WikiCache.getCachedOrderedPageList(c);
        if (pageList != null)
            return pageList;
        else
            pageList = new ArrayList<Wiki>();
        List<Wiki> rootTopics = getWikisByParentId(c.getId(), -1);
        for (Wiki rootTopic : rootTopics)
            addAllChildren(pageList, rootTopic);

        //cache ordered page list for toc
        WikiCache.cacheOrderedPageList(c, pageList);
        return pageList;
    }

    //does not include attachments, does include depth
    public static List<Wiki> getSubTreePageList(Container c, Wiki parentPage) throws SQLException
    {
        List<Wiki> pageList = new ArrayList<Wiki>();
        addAllChildren(pageList, parentPage);

        return pageList;
    }

    public static List<String> getWikiNameList(Container c) throws SQLException
    {
        List<Wiki> pageList = getPageList(c);
        List<String> nameList = new ArrayList<String>();

        for(Wiki page : pageList)
            nameList.add(page.getName());

        return nameList;
    }

    //does not include attachments
    public static Wiki getWikiByRowId(Container c, int rowId)
    {
        List<Wiki> pages = getPageList(c);
        for (Wiki page : pages)
        {
            if (page.getRowId() == rowId)
                return page;
        }
        return null;
    }

    public static Wiki getWiki(Container c, String name)
    {
        return getWiki(c, name, false);
    }


    //get wiki with specified version, with attachments
    public static Wiki getWiki(Container c, String name, boolean forceRefresh)
    {
        WikiAndVersion wikipair = getLatestWikiAndVersion(c, name, forceRefresh);
        if (null == wikipair)
            return null;
        return wikipair.getWiki();
    }


    public static int getNextVersionNumber(Wiki wiki) throws SQLException
    {
        WikiVersion[] versions = getAllVersions(wiki);
        //get last wiki version inserted
        //note: this will break if an existing version between 0 and n is deleted
        WikiVersion wikiversion = versions[versions.length - 1];
        return wikiversion.getVersion() + 1;
    }


    // This method ignores the volatile flag -- don't cache these wikis
    public static WikiVersion getVersion(Wiki wiki, int version) throws SQLException
    {
        if (null == wiki.getEntityId())
            return null;

        //special case for latest version
        if (version == LATEST)
            return getLatestVersion(wiki);

        WikiVersion[] versions = getAllVersions(wiki);

        WikiVersion wikiversion = null;
        for (WikiVersion v : versions)
        {
            if (v.getVersion() == version)
            {
                wikiversion = v;
                break;
            }
        }
        if (null == wikiversion)
            return null;

        return wikiversion;
    }


    public static WikiVersion getLatestVersion(Wiki wiki)
    {
        return getLatestVersion(wiki, false);
    }


    public static WikiVersion getLatestVersion(Wiki wiki, boolean forceRefresh)
    {
        Container c = ContainerManager.getForId(wiki.getContainerId());

        WikiAndVersion wikipair = getLatestWikiAndVersion(c, wiki.getName(), forceRefresh);
        if (null == wikipair)
            return null;
        return wikipair.getWikiVersion();
    }


    private static WikiAndVersion nullWikiAndVersion = new WikiAndVersion(null,null);

    // UNDONE: consider exposing this method, or exposing wiki.getLatestVersion()
    private static WikiAndVersion getLatestWikiAndVersion(Container c, String name, boolean forceRefresh)
    {
        WikiAndVersion wikipair;

        if (!forceRefresh)
        {
            wikipair = (WikiAndVersion) WikiCache.getCached(c, name);
            if (null != wikipair)
            {
                if (wikipair == nullWikiAndVersion)
                    return null;
                return wikipair;
            }
        }

        Wiki wiki = getWikiByName(c, name);

        if (wiki == null)
        {
            WikiCache._cache(c, name, nullWikiAndVersion);
            return null;
        }

        try
        {
            WikiVersion wikiversion = Table.selectObject(comm.getTableInfoPageVersions(),
                        Table.ALL_COLUMNS,
                        new SimpleFilter("RowId", wiki.getPageVersionId()),
                        null,
                        WikiVersion.class);

            if (wikiversion == null)
                throw new IllegalStateException("Cannot retrieve a valid version for page " + wiki.getName());

            //make sure that wiki object that's been passed in includes attachments
            Wiki wikiAttach = getWikiByName(c, wiki.getName());
            if (wikiAttach == null)
                throw new IllegalArgumentException("Wiki page not found:" + wiki.getName());
            else
                wiki = wikiAttach;

            // always cache wiki and version -- we defer formatting until WikiVersion.getHtml() is called
            wikipair = new WikiAndVersion(wiki, wikiversion);

            WikiCache.cache(c, wikipair);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        return wikipair;
    }

    public static WikiVersion[] getAllVersions(Wiki wiki)
    {
        //fail if wiki has no entityid
        if(null == wiki.getEntityId())
            throw new IllegalStateException("Cannot retrieve version for non-existent wiki page.");

        try
        {
            WikiVersion[] versions = Table.select(comm.getTableInfoPageVersions(),
                        Table.ALL_COLUMNS,
                        new SimpleFilter("pageentityid", wiki.getEntityId()),
                        new Sort("pageentityid,version"),
                        WikiVersion.class);
            return versions;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public static int getVersionCount(Wiki wiki)
    {
        return getAllVersions(wiki).length;
    }

    public static FormattedHtml formatWiki(Container c, Wiki wiki, WikiVersion wikiversion) throws SQLException
    {
        String hrefPrefix = wiki.getWikiLink("page", "").toString();

        String attachPrefix = null;
        if (null != wiki.getEntityId())
            attachPrefix = wiki.getAttachmentLink("");

        Map<String, WikiRenderer.WikiLinkable> pages = getVersionMap(c);

        Attachment[] attachments = wiki.getAttachments() == null ? null : wiki.getAttachments().toArray(new Attachment[wiki.getAttachments().size()]);

        //get formatter specified for this version
        WikiRenderer w = wikiversion.getRenderer(hrefPrefix, attachPrefix, pages, attachments);

        return w.format(wikiversion.getBody());
    }

    public static Map<String, Wiki> getPageMap(Container c) throws SQLException
    {
        Map<String, Wiki> tree = WikiCache.getCachedPageMap(c);
        if (null != tree)
            return tree;
        tree = generatePageMap(c);

        WikiCache.cachePageMap(c, tree);
        return tree;
    }

    private static Map<String, WikiRenderer.WikiLinkable> getVersionMap(Container c) throws SQLException
    {
        Map<String, WikiRenderer.WikiLinkable> tree = WikiCache.getCachedVersionMap(c);
        if (null != tree)
            return tree;
        tree = new TreeMap<String, WikiRenderer.WikiLinkable>();

        List<Wiki> list = getPageList(c);
        for (Wiki wiki : list)
            tree.put(wiki.getName(), getLatestVersion(wiki, false));
        WikiCache.cacheVersionMap(c, tree);
        return tree;
    }

    public static boolean wikiNameExists(Container c, String wikiname)
    {
        return getWiki(c, wikiname) != null;
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


        public void testSchema()
        {
            assertNotNull("couldn't find table Pages", comm.getTableInfoPages());
            assertNotNull(comm.getTableInfoPages().getColumn("Container"));
            assertNotNull(comm.getTableInfoPages().getColumn("EntityId"));
            assertNotNull(comm.getTableInfoPages().getColumn("Name"));


            assertNotNull("couldn't find table PageVersions", comm.getTableInfoPageVersions());
            assertNotNull(comm.getTableInfoPageVersions().getColumn("PageEntityId"));
            assertNotNull(comm.getTableInfoPageVersions().getColumn("Title"));
            assertNotNull(comm.getTableInfoPageVersions().getColumn("Body"));
            assertNotNull(comm.getTableInfoPageVersions().getColumn("Version"));
        }


        private void purgePages(Container c, boolean verifyEmpty) throws SQLException
        {
            String deleteDocuments = "DELETE FROM " + core.getTableInfoDocuments() + " WHERE Container = ? AND Parent IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)";
            int docs = Table.execute(comm.getSchema(), deleteDocuments, new Object[]{c.getId(), c.getId()});

            String updatePages = "UPDATE " + comm.getTableInfoPages() + " SET PageVersionId = null WHERE Container = ?";
            Table.execute(comm.getSchema(), updatePages, new Object[]{c.getId()});

            String deletePageVersions = "DELETE FROM " + comm.getTableInfoPageVersions() + " WHERE PageEntityId IN (SELECT EntityId FROM " + comm.getTableInfoPages() + " WHERE Container = ?)";
            int pageVersions = Table.execute(comm.getSchema(), deletePageVersions, new Object[]{c.getId()});

            String deletePages = "DELETE FROM " + comm.getTableInfoPages() + " WHERE Container = ?";
            int pages = Table.execute(comm.getSchema(), deletePages, new Object[]{c.getId()});

            if (verifyEmpty)
            {
                assertEquals(0, docs);
                assertEquals(0, pageVersions);
                assertEquals(0, pages);
            }
        }

        public void testWiki()
                throws IOException, SQLException, ServletException, AttachmentService.DuplicateFilenameException
        {
            TestContext context = TestContext.get();

            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());

            Container c = JunitUtil.getTestContainer();

            purgePages(c, false);

            //
            // CREATE
            //
            Wiki wikiA = new Wiki(c, "pageA");
            WikiVersion wikiversion = new WikiVersion();
            wikiversion.setTitle("Topic A");
            wikiversion.setBody("[pageA]");

            insertWiki(user, c, wikiA, wikiversion, null);

            // verify objects
            wikiA = getWikiByName(c, "pageA");
            wikiversion = getVersion(wikiA, LATEST);
            assertEquals("Topic A", wikiversion.getTitle());

            assertNull(getWikiByName(c, "pageNA"));

            //
            // DELETE
            //
            deleteWiki(user, c, wikiA);

            // verify
            assertNull(getWikiByName(c, "pageA"));


            purgePages(c, true);
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
