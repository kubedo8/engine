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

package io.lumeer.core.adapter

import io.lumeer.api.model.*
import io.lumeer.api.model.Collection
import io.lumeer.api.model.common.Resource
import io.lumeer.api.util.PermissionUtils
import io.lumeer.core.exception.NoDocumentPermissionException
import io.lumeer.core.exception.NoPermissionException
import io.lumeer.core.exception.NoResourcePermissionException
import io.lumeer.core.util.DocumentUtils
import io.lumeer.core.util.QueryUtils
import io.lumeer.storage.api.dao.*

class PermissionAdapter(private val userDao: UserDao,
                        private val groupDao: GroupDao,
                        private val viewDao: ViewDao,
                        private val linkTypeDao: LinkTypeDao,
                        private val collectionDao: CollectionDao) {

   private val usersCache = mutableMapOf<String, List<User>>()
   private val hasRoleCache = mutableMapOf<String, Boolean>()
   private val viewCache = mutableMapOf<String, View>()
   private val collectionCache = mutableMapOf<String, Collection>()
   private val userCache = mutableMapOf<String, User>()
   private val groupsCache = mutableMapOf<String, List<Group>>()
   private val linkTypes = lazy { linkTypeDao.allLinkTypes }

   private var currentViewId: String? = null

   fun setViewId(viewId: String) {
      currentViewId = viewId
   }

   fun getViewId() = currentViewId

   fun activeView(): View? {
      if (currentViewId.orEmpty().isNotEmpty()) {
         return getView(currentViewId!!)
      }

      return null
   }

   fun invalidateCache(resource: Resource) {
      for (role in RoleType.values()) {
         hasRoleCache.remove(resource.id + ":" + role.toString())
      }
   }

   fun isPublic(organization: Organization?, project: Project?) = project?.isPublic ?: false

   fun canReadAllInWorkspace(organization: Organization, project: Project?, userId: String): Boolean {
      val user = getUser(userId)
      val groups = PermissionUtils.getUserGroups(organization, user)
      if (PermissionUtils.getUserRolesInResource(organization, user, groups).any { role -> role.isTransitive && role.roleType === RoleType.Read }) {
         return true
      }
      return project?.let { PermissionUtils.getUserRolesInResource(it, user, groups).any { role -> role.isTransitive && role.roleType === RoleType.Read } } ?: false
   }

   fun getOrganizationUsersByRole(organization: Organization, roleType: RoleType): Set<String> {
      return PermissionUtils.getOrganizationUsersByRole(organization, getUsers(organization.id), roleType)
   }

   fun getOrganizationReadersDifference(organization1: Organization, organization2: Organization): RolesDifference {
      return PermissionUtils.getOrganizationUsersDifferenceByRole(organization1, organization2, getUsers(organization1.id), RoleType.Read)
   }

   fun getProjectUsersByRole(organization: Organization, project: Project?, roleType: RoleType): Set<String> {
      return PermissionUtils.getProjectUsersByRole(organization, project, getUsers(organization.id), roleType)
   }

   fun getProjectReadersDifference(organization: Organization, project1: Project, project2: Project): RolesDifference {
      return PermissionUtils.getProjectUsersDifferenceByRole(organization, project1, project2, getUsers(organization.id), RoleType.Read)
   }

   fun <T : Resource> getResourceUsersByRole(organization: Organization, project: Project?, resource: T, roleType: RoleType): Set<String> {
      return PermissionUtils.getResourceUsersByRole(organization, project, resource, getUsers(organization.id), roleType)
   }

   fun getLinkTypeUsersByRole(organization: Organization, project: Project?, linkType: LinkType, roleType: RoleType): Set<String> {
      return PermissionUtils.getLinkTypeUsersByRole(organization, project, linkType, getLinkTypeCollections(linkType), getUsers(organization.id), roleType)
   }

   fun <T : Resource> getResourceReadersDifference(organization: Organization, project: Project?, resource1: T, resource2: T): RolesDifference {
      return PermissionUtils.getResourceUsersDifferenceByRole(organization, project, resource1, resource2, getUsers(organization.id), RoleType.Read)
   }

   fun <T : Resource> getUserRolesInResource(organization: Organization?, project: Project?, resource: T, userId: String): Set<RoleType> {
      return getUserRolesInResource(organization, project, resource, getUser(userId))
   }

   fun <T : Resource> getUserRolesInResource(organization: Organization?, project: Project?, resource: T, user: User): Set<RoleType> {
      return PermissionUtils.getUserRolesInResource(organization, project, resource, user)
   }

   fun getUserRolesInCollectionWithView(organization: Organization?, project: Project?, collection: Collection, user: User): Set<RoleType> {
      val view = activeView()
      if (view != null) {
         val viewRoles = getUserRolesInResource(organization, project, view, user)
         val authorId = view.authorId.orEmpty()
         val collectionIds = QueryUtils.getQueryCollectionIds(view.query, linkTypes.value)
         if (collectionIds.contains(collection.id) && authorId.isNotEmpty()) { // does the view contain the collection?
            val authorRoles = getUserRolesInResource(organization, project, collection, authorId)
            return viewRoles.intersect(authorRoles)
         }
      }
      return emptySet()
   }

   fun getUserRolesInLinkTypeWithView(organization: Organization?, project: Project?, linkType: LinkType, user: User): Set<RoleType> {
      val view = activeView()
      if (view != null) {
         val viewRoles = getUserRolesInResource(organization, project, view, user)
         val authorId = view.authorId.orEmpty()
         val linkTypeIds = view.query?.linkTypeIds.orEmpty()
         if (linkTypeIds.contains(linkType.id) && authorId.isNotEmpty()) { // does the view contain the linkType?
            val authorRoles = getUserRolesInLinkType(organization, project, linkType, getUser(authorId))
            return viewRoles.intersect(authorRoles)
         }
      }
      return emptySet()
   }

   fun getUserRolesInLinkType(organization: Organization?, project: Project?, linkType: LinkType, user: User): Set<RoleType> {
      return getUserRolesInLinkType(organization, project, linkType, getLinkTypeCollections(linkType), user)
   }

   fun getUserRolesInLinkType(organization: Organization?, project: Project?, linkType: LinkType, collections: List<Collection>, userId: String): Set<RoleType> {
      return getUserRolesInLinkType(organization, project, linkType, collections, getUser(userId))
   }

   fun getUserRolesInLinkType(organization: Organization?, project: Project?, linkType: LinkType, collections: List<Collection>, user: User): Set<RoleType> {
      return PermissionUtils.getUserRolesInLinkType(organization, project, linkType, collections, user)
   }

   fun checkRole(organization: Organization?, project: Project?, resource: Resource, role: RoleType, userId: String) {
      if (!hasRole(organization, project, resource, role, userId)) {
         throw NoResourcePermissionException(resource)
      }
   }

   fun hasAnyRoleInResource(organization: Organization?, project: Project?, resource: Resource, roles: Set<RoleType>, userId: String): Boolean {
      return roles.any { hasRole(organization, project, resource, it, userId) }
   }

   fun checkRoleInCollectionWithView(organization: Organization?, project: Project?, collection: Collection, role: RoleType, viewRole: RoleType, userId: String) {
      if (!hasRoleInCollectionWithView(organization, project, collection, role, viewRole, userId)) {
         throw NoResourcePermissionException(collection)
      }
   }

   fun hasRoleInCollectionWithView(organization: Organization?, project: Project?, collection: Collection, role: RoleType, viewRole: RoleType, userId: String): Boolean {
      return hasRole(organization, project, collection, role, userId) || hasRoleInCollectionViaView(organization, project, collection, role, viewRole, userId, activeView())
   }

   private fun hasRoleInCollectionViaView(organization: Organization?, project: Project?, collection: Collection, role: RoleType, viewRole: RoleType, userId: String, view: View?): Boolean {
      if (view != null && hasRole(organization, project, view, viewRole, userId)) { // does user have access to the view?
         val authorId = view.authorId.orEmpty()
         val collectionIds = QueryUtils.getQueryCollectionIds(view.query, linkTypes.value)
         if (collectionIds.contains(collection.id) && authorId.isNotEmpty()) { // does the view contain the collection?
            if (hasRole(organization, project, collection, role, authorId)) { // has the view author access to the collection?
               return true // grant access
            }
         }
      }
      return false
   }

   fun checkCanCreateDocuments(organization: Organization?, project: Project?, collection: Collection, userId: String) {
      if (!canCreateDocuments(organization, project, collection, userId)) {
         throw NoResourcePermissionException(collection)
      }
   }

   fun canCreateDocuments(organization: Organization?, project: Project?, collection: Collection, userId: String): Boolean {
      return hasRoleInCollectionWithView(organization, project, collection, RoleType.Contribute, RoleType.Contribute, userId)
   }

   fun checkCanEditDocument(organization: Organization?, project: Project?, document: Document, collection: Collection, userId: String) {
      if (!canEditDocument(organization, project, document, collection, userId)) {
         throw NoDocumentPermissionException(document)
      }
   }

   fun canEditDocument(organization: Organization?, project: Project?, document: Document, collection: Collection, userId: String): Boolean {
      return hasRoleInCollectionWithView(organization, project, collection, RoleType.Write, RoleType.Write, userId)
            || isDocumentOwner(organization, project, document, collection, userId)
   }

   fun checkCanDeleteDocument(organization: Organization?, project: Project?, document: Document, collection: Collection, userId: String) {
      if (!canDeleteDocument(organization, project, document, collection, userId)) {
         throw NoDocumentPermissionException(document)
      }
   }

   fun canDeleteDocument(organization: Organization?, project: Project?, document: Document, collection: Collection, userId: String): Boolean {
      return hasRoleInCollectionWithView(organization, project, collection, RoleType.Delete, RoleType.Delete, userId)
            || isDocumentOwner(organization, project, document, collection, userId)
   }

   private fun isDocumentOwner(organization: Organization?, project: Project?, document: Document, collection: Collection, userId: String): Boolean {
      return isDocumentContributor(organization, project, document, collection, userId) || isDocumentOwnerByPurpose(organization, project, document, collection, userId)
   }

   private fun isDocumentContributor(organization: Organization?, project: Project?, document: Document, collection: Collection, userId: String): Boolean {
      return hasRoleInCollectionWithView(organization, project, collection, RoleType.Contribute, RoleType.Contribute, userId) && DocumentUtils.isDocumentOwner(collection, document, userId)
   }

   private fun isDocumentOwnerByPurpose(organization: Organization?, project: Project?, document: Document, collection: Collection, userId: String): Boolean {
      return hasRoleInCollectionWithView(organization, project, collection, RoleType.Read, RoleType.Read, userId) && DocumentUtils.isDocumentOwnerByPurpose(collection, document, getUser(userId))
   }

   fun checkRoleInLinkTypeWithView(organization: Organization?, project: Project?, linkType: LinkType, role: RoleType, userId: String) {
      if (!hasRoleInLinkTypeWithView(organization, project, linkType, role, userId)) {
         throw NoPermissionException(ResourceType.LINK_TYPE.toString())
      }
   }

   fun hasRoleInLinkTypeWithView(organization: Organization?, project: Project?, linkType: LinkType, role: RoleType, userId: String): Boolean {
      val collections = getLinkTypeCollections(linkType)
      return hasRole(organization, project, linkType, collections, role, userId) || hasRoleInLinkTypeViaView(organization, project, linkType, collections, role, role, userId, activeView())
   }

   private fun getLinkTypeCollections(linkType: LinkType) = linkType.collectionIds.orEmpty().subList(0, 2).map { getCollection(it) }

   private fun hasRoleInLinkTypeViaView(organization: Organization?, project: Project?, linkType: LinkType, collections: List<Collection>, role: RoleType, viewRole: RoleType, userId: String, view: View?): Boolean {
      if (view != null && hasRole(organization, project, view, viewRole, userId)) { // does user have access to the view?
         val authorId = view.authorId.orEmpty()
         val linkTypeIds = view.query?.linkTypeIds.orEmpty()
         if (linkTypeIds.contains(linkType.id) && authorId.isNotEmpty()) { // does the view contain the linkType?
            if (hasRole(organization, project, linkType, collections, role, authorId)) { // has the view author access to the linkType?
               return true // grant access
            }
         }
      }
      return false
   }

   fun hasRoleInLinkType(organization: Organization?, project: Project?, linkType: LinkType, role: RoleType, userId: String): Boolean {
      return hasRoleInLinkType(organization, project, linkType, getLinkTypeCollections(linkType), role, userId)
   }

   fun hasRoleInLinkType(organization: Organization?, project: Project?, linkType: LinkType, collections: List<Collection>, role: RoleType, userId: String): Boolean {
      return hasRole(organization, project, linkType, collections, role, userId)
   }

   fun hasRole(organization: Organization?, project: Project?, resource: Resource, role: RoleType, userId: String): Boolean {
      return hasRoleCache.computeIfAbsent("${resource.type}:${resource.id}:$role") { getUserRolesInResource(organization, project, resource, userId).contains(role) }
   }

   fun hasRole(organization: Organization?, project: Project?, linkType: LinkType, collection: List<Collection>, role: RoleType, userId: String): Boolean {
      return hasRoleCache.computeIfAbsent("${ResourceType.LINK_TYPE}:${linkType.id}:$role") { getUserRolesInLinkType(organization, project, linkType, collection, userId).contains(role) }
   }

   fun getUser(userId: String): User {
      return userCache.computeIfAbsent(userId) { userDao.getUserById(userId) }
   }

   fun getUsers(organizationId: String): List<User> {
      return usersCache.computeIfAbsent(organizationId) { userDao.getAllUsers(organizationId) }
   }

   fun getView(viewId: String): View {
      return viewCache.computeIfAbsent(viewId) { viewDao.getViewById(viewId) }
   }

   fun getCollection(collectionId: String): Collection {
      return collectionCache.computeIfAbsent(collectionId) { collectionDao.getCollectionById(collectionId) }
   }

   fun getGroups(organizationId: String): List<Group> {
      return groupsCache.computeIfAbsent(organizationId) { groupDao.allGroups }
   }

}
