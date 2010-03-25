/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.apache.log4j.Logger;
import org.labkey.api.security.permissions.*;
import org.labkey.api.view.ViewContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.lang.reflect.Constructor;

/*
* User: Dave
* Date: Apr 22, 2009
* Time: 2:09:39 PM
*/

/**
 * Global role manager
 */
public class RoleManager
{
    public static final Role siteAdminRole = new SiteAdminRole();

    public static final Set<Class<? extends Permission>> BasicPermissions = new HashSet<Class<? extends Permission>>();
    static
    {
        BasicPermissions.add(ReadPermission.class);
        BasicPermissions.add(InsertPermission.class);
        BasicPermissions.add(UpdatePermission.class);
        BasicPermissions.add(DeletePermission.class);
    }

    //global map from role name to Role instance
    private static final Map<String, Role> _nameToRoleMap = new ConcurrentHashMap<String,Role>();
    private static final Map<Class<? extends Role>, Role> _classToRoleMap = new ConcurrentHashMap<Class<? extends Role>, Role>();
    private static final List<Role> _roles = new CopyOnWriteArrayList<Role>();

    //register all core roles
    static
    {
        registerRole(siteAdminRole);
        registerRole(new ProjectAdminRole());
        registerRole(new FolderAdminRole());
        registerRole(new EditorRole());
        registerRole(new AuthorRole());
        registerRole(new ReaderRole());
        registerRole(new RestrictedReaderRole());
        registerRole(new SubmitterRole());
        registerRole(new NoPermissionsRole());
        registerRole(new OwnerRole());
        registerRole(new DeveloperRole());
        registerRole(new TroubleshooterRole());
    }

    public static Role getRole(String name)
    {
        Role role = _nameToRoleMap.get(name);
        if(null == role)
            Logger.getLogger(RoleManager.class).warn("Could not resolve the role " + name + "! The role may no longer exist, or may not yet be registered.");
        return role;
    }

    public static Role getRole(Class<? extends Role> clazz)
    {
        Role role = _classToRoleMap.get(clazz);
        if(null == role)
            Logger.getLogger(RoleManager.class).warn("Could not resolve the role " + clazz.getName() + "! Did you forget to register the role with RoleManager.register()?");
        return role;
    }

    public static Permission getPermission(Class<? extends Permission> clazz)
    {
        Permission perm = (Permission)_classToRoleMap.get(clazz);
        if(null == perm)
            Logger.getLogger(RoleManager.class).warn("Could not resolve the permission " + clazz.getName() + "! If this is not part of a role, you must register it separately with RoleManager.register().");
        return perm;
    }

    public static Permission getPermission(String uniqueName)
    {
        Permission perm = (Permission)_nameToRoleMap.get(uniqueName);
        if(null == perm)
            Logger.getLogger(RoleManager.class).warn("Could not resolve the permission " + uniqueName + "! The permission may no longer exist, or may not yet be registered.");
        return perm;

    }


    public static List<Role> getAllRoles()
    {
        return _roles;
    }

    public static void registerRole(Role role)
    {
        _nameToRoleMap.put(role.getUniqueName(), role);
        _classToRoleMap.put(role.getClass(), role);
        _roles.add(role);

        boolean adminRole = role instanceof SiteAdminRole || role instanceof ProjectAdminRole || role instanceof FolderAdminRole;

        //register all exposed permissions in the name and class maps
        for(Class<? extends Permission> permClass : role.getPermissions())
        {
            //add all permissions to the admin roles
            if(!adminRole)
                addPermissionToAdminRoles(permClass);

            try
            {
                Permission perm = permClass.newInstance();
                _nameToRoleMap.put(perm.getUniqueName(), perm);
                _classToRoleMap.put(perm.getClass(), perm);
            }
            catch(InstantiationException e) {}
            catch (IllegalAccessException e) {}
        }
    }

    /**
     * This method is equivalent to registerPermission(perm, true)
     * @param perm The permission singleton instance
     */
    public static void registerPermission(Permission perm)
    {
        registerPermission(perm, true);
    }

    /**
     * Registers a new permission instance.
     * If your permission is already statically defined as part of a role
     * (i.e., it is added to a defined role before that role is registered)
     * you do not need to call this method.
     * This method should be called only when your module
     * defines a new permission class that is not statically defined as part of any role.
     * This method will ensure that the singleton instance of the permission class can
     * be retrieved using the getPermission() method.
     * @param perm The permission singleton instance
     * @param addToAdminRoles true to add this to all admin roles, or false to not.
     */
    public static void registerPermission(Permission perm, boolean addToAdminRoles)
    {
        _nameToRoleMap.put(perm.getUniqueName(), perm);
        _classToRoleMap.put(perm.getClass(), perm);
        if (addToAdminRoles)
            addPermissionToAdminRoles(perm.getClass());
    }

    public static void addPermissionToAdminRoles(Class<? extends Permission> perm)
    {
        siteAdminRole.addPermission(perm);
        getRole(ProjectAdminRole.class).addPermission(perm);
        getRole(FolderAdminRole.class).addPermission(perm);
    }

    public static Set<Class<? extends Permission>> permSet(Class<? extends Permission>... perms)
    {
        //for some reason, Collections.asSet() will not compile with these kinds of generics
        Set<Class<? extends Permission>> set = new HashSet<Class<? extends Permission>>();
        set.addAll(Arrays.asList(perms));
        return set;
    }

    /**
     * Returns a set of Role objects based on the provided role classes
     * @param roleClasses The role classes
     * @return A set of Role objects corresponding to the role classes
     */
    @NotNull
    public static Set<Role> roleSet(Class<? extends Role>... roleClasses)
    {
        Set<Role> roles = new HashSet<Role>();
        if(null != roleClasses && roleClasses.length > 0)
        {
            for(Class<? extends Role> roleClass : roleClasses)
            {
                if (null != roleClass)
                    roles.add(getRole(roleClass));
            }
        }
        return roles;
    }

    /**
     * Returns a set of contextual {@link Role}s created from
     * the view context and the given role factories.
     *
     * @param context The view context
     * @param roleFactories Queried for contextual roles.
     * @param contextualRoles Any additional contextual roles.
     * @return A set of Role objects corresponding to the role classes
     */
    @NotNull
    public static Set<Role> mergeContextualRoles(ViewContext context, Class<? extends HasContextualRoles>[] roleFactories, Set<Role> contextualRoles)
    {
        if (roleFactories == null || roleFactories.length == 0)
            return contextualRoles;

        Set<Role> allContextualRoles = new HashSet<Role>();
        if (contextualRoles != null)
            allContextualRoles.addAll(contextualRoles);
        
        for (Class<? extends HasContextualRoles> roleFactory : roleFactories)
        {
            HasContextualRoles factory;
            try
            {
                Constructor ctor = roleFactory.getConstructor();
                if (ctor == null)
                    throw new IllegalStateException("Failed to find no-arg constructor.");
                factory = (HasContextualRoles)ctor.newInstance();
            }
            catch (Exception e)
            {
                if (e instanceof RuntimeException)
                    throw (RuntimeException)e;
                throw new RuntimeException(e);
            }

            if (factory != null)
            {
                Set<Role> roles = factory.getContextualRoles(context);
                if (roles != null)
                    allContextualRoles.addAll(roles);
            }
        }

        return allContextualRoles;
    }
}