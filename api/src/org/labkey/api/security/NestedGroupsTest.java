/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.security;

import org.apache.commons.lang.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: adam
 * Date: 10/24/11
 * Time: 10:01 AM
 */
public class NestedGroupsTest extends Assert
{
    private static final String ALL = "All Employees";
    private static final String DIV_A = "Division A";
    private static final String DIV_B = "Division B";
    private static final String DIV_C = "Division C";
    private static final String CODERS = "Coders";
    private static final String TESTERS = "Testers";
    private static final String WRITERS = "Writers";
    private static final String PROJECT_X = "Project X";
    private static final String SITE_GROUP = "TestSiteGroup";

    private static final String ADD_TO_GUESTS = "Can't add a member to the Guests group";
    private static final String ADD_TO_USERS = "Can't add a member to the Users group";
    private static final String[] GROUP_NAMES = new String[] {ALL, DIV_A, DIV_B, DIV_C, CODERS, TESTERS, WRITERS, PROJECT_X};

    private Container _project;

    @Before
    public void setUp()
    {
        cleanup();
        _project = JunitUtil.getTestContainer().getProject();
    }

    @Test
    public void test() throws SQLException
    {
        User user = TestContext.get().getUser();

        // Grab the first group (if there is one) in the home project
        Container home = ContainerManager.getHomeContainer();
        Group[] homeGroups = SecurityManager.getGroups(home, false);
        @Nullable Group homeGroup = homeGroups.length > 0 ? homeGroups[0] : null;

        Group all = create(ALL);
        Group divA = create(DIV_A);
        Group divB = create(DIV_B);
        Group divC = create(DIV_C);
        Group coders = create(CODERS);
        Group testers = create(TESTERS);
        Group writers = create(WRITERS);
        Group projectX = create(PROJECT_X);

        addMember(all, divA);
        addMember(all, divB);
        addMember(all, divC);
        addMember(all, coders);
        addMember(all, testers);
        addMember(all, writers);

        addMember(coders, user);
        addMember(divA, user);
        addMember(divA, projectX);
        addMember(projectX, user);

        expected(all, divA, divB, divC, coders, testers, writers);

        int[] groups = GroupMembershipCache.getGroupsForPrincipal(user.getUserId());
        expected(groups, projectX, coders, divA);
        notExpected(groups, user, all, divB, divC, testers, writers);

        int[] allGroups = GroupManager.getAllGroupsForPrincipal(user);
        expected(allGroups, projectX, coders, divA, user, all);
        notExpected(allGroups, divB, divC, testers, writers);

        // TODO: Create another group, add directly to "all", add user to new group, validate
        // TODO: Check permissions

        Group administrators = SecurityManager.getGroup(Group.groupAdministrators);
        Group developers = SecurityManager.getGroup(Group.groupDevelopers);
        Group users = SecurityManager.getGroup(Group.groupUsers);
        Group guests = SecurityManager.getGroup(Group.groupGuests);

        failAddMember(null, user, SecurityManager.NULL_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, null, SecurityManager.NULL_PRINCIPAL_ERROR_MESSAGE);

        failAddMember(testers, testers, SecurityManager.ADD_GROUP_TO_ITSELF_ERROR_MESSAGE);
        failAddMember(administrators, administrators, SecurityManager.ADD_GROUP_TO_ITSELF_ERROR_MESSAGE);
        failAddMember(developers, developers, SecurityManager.ADD_GROUP_TO_ITSELF_ERROR_MESSAGE);

        failAddMember(coders, user, SecurityManager.ALREADY_A_MEMBER_ERROR_MESSAGE);
        failAddMember(all, divA, SecurityManager.ALREADY_A_MEMBER_ERROR_MESSAGE);
        failAddMember(divA, user, SecurityManager.ALREADY_A_MEMBER_ERROR_MESSAGE);

        failAddMember(divA, all, SecurityManager.CIRCULAR_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, all, SecurityManager.CIRCULAR_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, divA, SecurityManager.CIRCULAR_GROUP_ERROR_MESSAGE);

        failAddMember(guests, user, ADD_TO_GUESTS);
        failAddMember(guests, projectX, ADD_TO_GUESTS);
        failAddMember(guests, users, ADD_TO_GUESTS);
        failAddMember(users, user, ADD_TO_USERS);
        failAddMember(users, projectX, ADD_TO_USERS);
        failAddMember(users, guests, ADD_TO_USERS);

        failAddMember(administrators, projectX, SecurityManager.ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(developers, projectX, SecurityManager.ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(administrators, guests, SecurityManager.ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(developers, users, SecurityManager.ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE);

        failAddMember(projectX, administrators, SecurityManager.ADD_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, developers, SecurityManager.ADD_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, users, SecurityManager.ADD_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, guests, SecurityManager.ADD_SYSTEM_GROUP_ERROR_MESSAGE);

        if (null != homeGroup)
            failAddMember(projectX, homeGroup, SecurityManager.DIFFERENT_PROJECTS_ERROR_MESSAGE);

        Group siteGroup = SecurityManager.createGroup(ContainerManager.getRoot(), SITE_GROUP);
        assertTrue(!siteGroup.isProjectGroup());
        addMember(projectX, siteGroup);
    }

    private Group create(String name)
    {
        return SecurityManager.createGroup(_project, name);
    }

    private void addMember(Group group, UserPrincipal principal)
    {
        SecurityManager.addMember(group, principal);
    }

    // Adding this principal should fail
    private void failAddMember(@Nullable Group group, @Nullable UserPrincipal principal, String expectedMessage) throws SQLException
    {
        Set<UserPrincipal> members = getMembers(group);

        try
        {
            SecurityManager.addMember(group, principal);
            assertTrue("Expected failure when adding principal \"" + principal.getName() + "\" to group \"" + group.getName() + "\"", false);
        }
        catch (IllegalStateException e)
        {
            assertEquals(expectedMessage, e.getMessage());
        }

        // Membership should not have changed
        assertEquals(members, getMembers(group));
    }

    private Set<UserPrincipal> getMembers(@Nullable Group group)
    {
        return null != group ? SecurityManager.getGroupMembers(group, SecurityManager.GroupMemberType.Both) : Collections.<UserPrincipal>emptySet();
    }

    private void expected(int[] actualIds, UserPrincipal... expectedMembers)
    {
        validate(true, actualIds, expectedMembers);
    }

    private void notExpected(int[] actualIds, UserPrincipal... testMembers)
    {
        validate(false, actualIds, testMembers);
    }

    private void expected(Group group, UserPrincipal... expectedMembers) throws SQLException
    {
        validate(true, group, expectedMembers);
    }

    private void notExpected(Group group, UserPrincipal... expectedMembers) throws SQLException
    {
        validate(false, group, expectedMembers);
    }

    private void validate(boolean expected, int[] actualIds, UserPrincipal... testMembers)
    {
        Set<Integer> actual = new HashSet<Integer>(Arrays.asList(ArrayUtils.toObject(actualIds)));

        validate(expected, actual, testMembers);
    }

    private void validate(boolean expected, Set<Integer> actual, UserPrincipal... testMembers)
    {
        for (UserPrincipal member : testMembers)
        {
            if (expected)
                assertTrue("Expected member \"" + member.getName() + "\" (" + member.getUserId() + ") not present in " + actual, actual.contains(member.getUserId()));
            else
                assertFalse("Member \"" + member.getName() + "\" (" + member.getUserId() + ") was found but was not expected in " + actual, actual.contains(member.getUserId()));
        }
    }

    private void validate(boolean expected, Group group, UserPrincipal... members)
    {
        Set<Integer> set = new HashSet<Integer>();

        for (UserPrincipal userPrincipal : SecurityManager.getGroupMembers(group, SecurityManager.GroupMemberType.Both))
            set.add(userPrincipal.getUserId());

        validate(expected, set, members);
    }

    private void cleanup()
    {
        Container project = JunitUtil.getTestContainer().getProject();

        for (String groupName : GROUP_NAMES)
        {
            Integer groupId = SecurityManager.getGroupId(project, groupName, false);

            if (null != groupId)
                SecurityManager.deleteGroup(groupId);
        }

        Integer groupId = SecurityManager.getGroupId(ContainerManager.getRoot(), SITE_GROUP, false);

        if (null != groupId)
            SecurityManager.deleteGroup(groupId);
    }
}
