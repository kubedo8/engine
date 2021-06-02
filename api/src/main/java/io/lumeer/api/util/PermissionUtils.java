/*
 * Lumeer: Modern Data Definition and Processing Platform
 *
 * Copyright (C) since 2017 Lumeer.io, s.r.o. and/or its affiliates.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.lumeer.api.util;

import static java.util.stream.Collectors.toSet;

import io.lumeer.api.model.Collection;
import io.lumeer.api.model.LinkType;
import io.lumeer.api.model.Organization;
import io.lumeer.api.model.Permission;
import io.lumeer.api.model.Permissions;
import io.lumeer.api.model.Project;
import io.lumeer.api.model.ResourceType;
import io.lumeer.api.model.Role;
import io.lumeer.api.model.RoleType;
import io.lumeer.api.model.RolesDifference;
import io.lumeer.api.model.User;
import io.lumeer.api.model.common.Resource;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class PermissionUtils {

   private PermissionUtils() {
   }

   public static boolean hasRole(Organization organization, @Nullable Project project, Resource resource, RoleType role, User user) {
      return getUserRolesInResource(organization, project, resource, user).contains(role);
   }

   public static Set<String> getOrganizationUsersByRole(Organization organization, List<User> users, RoleType roleType) {
      return getResourceUsersByRole(organization, null, organization, users, roleType);
   }

   public static Set<String> getProjectUsersByRole(Organization organization, Project project, List<User> users, RoleType roleType) {
      return getResourceUsersByRole(organization, project, project, users, roleType);
   }

   public static Set<String> getResourceUsersByRole(Organization organization, @Nullable Project project, Resource resource, List<User> users, RoleType roleType) {
      return users.stream().filter(user -> getUserRolesInResource(organization, project, resource, user).contains(roleType)).map(User::getId).collect(Collectors.toSet());
   }

   public static Set<String> getLinkTypeUsersByRole(Organization organization, @Nullable Project project, LinkType linkType, java.util.Collection<Collection> collections, List<User> users, RoleType roleType) {
      return users.stream().filter(user -> getUserRolesInLinkType(organization, project, linkType, collections, user).contains(roleType)).map(User::getId).collect(Collectors.toSet());
   }

   public static Set<RoleType> getUserRolesInResource(Organization organization, @Nullable Project project, Resource resource, User user) {
      return getUserRolesInResource(organization, project, resource.getType(), resource.getPermissions(), user);
   }

   public static Set<RoleType> getUserRolesInLinkType(Organization organization, @Nullable Project project, LinkType linkType, java.util.Collection<Collection> collections, User user) {
      if (!canReadLinkTypeCollections(organization, project, linkType, collections, user)) {
         return Collections.emptySet();
      }
      return getUserRolesInResource(organization, project, ResourceType.LINK_TYPE, linkType.getPermissions(), user);
   }

   private static boolean canReadLinkTypeCollections(Organization organization, Project project, LinkType linkType, java.util.Collection<Collection> collections, User user) {
      var linkTypeCollections = collections.stream().filter(collection -> linkType.getCollectionIds().contains(collection.getId())).collect(Collectors.toList());
      var hasPermissions = linkTypeCollections.size() == 2;
      for (Collection collection : linkTypeCollections) {
         hasPermissions = hasPermissions && hasRole(organization, project, collection, RoleType.Read, user);
      }
      return hasPermissions;
   }

   private static Set<RoleType> getUserRolesInResource(Organization organization, @Nullable Project project, ResourceType resourceType, Permissions permissions, User user) {
      final Set<String> groups = getUserGroups(organization, user);

      final Set<Role> actualRoles = getUserRolesInResource(permissions, user, groups);
      final Set<Role> organizationRoles = resourceType == ResourceType.ORGANIZATION ? actualRoles : getUserRolesInResource(organization, user, groups);

      if (organization != null && resourceType != ResourceType.ORGANIZATION) {
         // It's necessary to have read permission in organization in order to process resource (project, collection, view, link)
         if (organizationRoles.stream().noneMatch(role -> role.getRoleType() == RoleType.Read)) {
            return Collections.emptySet();
         }

         final Set<Role> organizationTransitiveRoles = organizationRoles.stream().filter(Role::isTransitive).collect(Collectors.toSet());
         actualRoles.addAll(organizationTransitiveRoles);
      }

      if (project != null && resourceType != ResourceType.PROJECT) {
         final Set<Role> projectRoles = getUserRolesInResource(project, user, groups);

         // It's necessary to have read permission in project (or transitive in organization) in order to process resource (collection, view, link)
         if (organizationRoles.stream().noneMatch(role -> role.getRoleType() == RoleType.Read && role.isTransitive())
               && projectRoles.stream().noneMatch(role -> role.getRoleType() == RoleType.Read)) {
            return Collections.emptySet();
         }

         final Set<Role> projectTransitiveRoles = projectRoles.stream().filter(Role::isTransitive).collect(Collectors.toSet());
         actualRoles.addAll(projectTransitiveRoles);
      }

      return actualRoles.stream().map(Role::getRoleType).collect(Collectors.toSet());
   }

   public static RolesDifference getOrganizationUsersDifferenceByRole(Organization organization1, Organization organization2, List<User> users, RoleType roleType) {
      Set<String> users1 = getOrganizationUsersByRole(organization1, users, roleType);
      Set<String> users2 = getOrganizationUsersByRole(organization2, users, roleType);

      return getUsersDifference(users1, users2);
   }

   public static RolesDifference getProjectUsersDifferenceByRole(Organization organization, Project project1, Project project2, List<User> users, RoleType roleType) {
      Set<String> users1 = getProjectUsersByRole(organization, project1, users, roleType);
      Set<String> users2 = getProjectUsersByRole(organization, project2, users, roleType);

      return getUsersDifference(users1, users2);
   }

   public static RolesDifference getResourceUsersDifferenceByRole(Organization organization, @Nullable Project project, Resource resource1, Resource resource2, List<User> users, RoleType roleType) {
      Set<String> users1 = getResourceUsersByRole(organization, project, resource1, users, roleType);
      Set<String> users2 = getResourceUsersByRole(organization, project, resource2, users, roleType);

      return getUsersDifference(users1, users2);
   }

   private static RolesDifference getUsersDifference(Set<String> users1, Set<String> users2) {
      Set<String> addedUsers = new HashSet<>(users2);
      addedUsers.removeAll(users1);

      Set<String> removedUsers = new HashSet<>(users1);
      removedUsers.removeAll(users2);

      return new RolesDifference(addedUsers, removedUsers);
   }

   public static Set<Role> getUserRolesInResource(Resource resource, User user, Set<String> groups) {
      return getUserRolesInResource(resource.getPermissions(), user, groups);
   }

   public static Set<Role> getUserRolesInResource(Permissions permissions, User user, Set<String> groups) {
      Permissions notNullPermissions = Objects.requireNonNullElse(permissions, new Permissions());
      Set<Permission> userPermissions = Objects.requireNonNullElse(notNullPermissions.getUserPermissions(), Collections.emptySet());
      Set<Permission> groupPermissions = Objects.requireNonNullElse(notNullPermissions.getGroupPermissions(), Collections.emptySet());

      final Set<Role> actualRoles = getRolesByUser(userPermissions, user.getId());
      actualRoles.addAll(getRolesByGroups(groupPermissions, groups));
      return actualRoles;
   }

   public static Set<Role> getRolesByUser(Set<Permission> userRoles, String userId) {
      return userRoles.stream()
                      .filter(entity -> entity.getId() != null && entity.getId().equals(userId))
                      .flatMap(entity -> entity.getRoles().stream())
                      .collect(toSet());
   }

   public static Set<Role> getRolesByGroups(Set<Permission> groupRoles, Set<String> groupIds) {
      return groupRoles.stream()
                       .filter(entity -> groupIds.contains(entity.getId()))
                       .flatMap(entity -> entity.getRoles().stream())
                       .collect(toSet());
   }

   public static Set<String> getUserGroups(final Organization organization, final User user) {
      if (organization == null || user == null || "".equals(user.getId())) {
         return Collections.emptySet();
      }

      return Objects.requireNonNullElse(user.getGroups().get(organization.getId()), Collections.emptySet());
   }
}
