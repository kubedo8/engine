/*
 * Lumeer: Modern Data Definition and Processing Platform
 *
 * Copyright (C) since 2017 Answer Institute, s.r.o. and/or its affiliates.
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
package io.lumeer.storage.mongodb.dao.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lumeer.api.dto.JsonPermission;
import io.lumeer.api.dto.JsonPermissions;
import io.lumeer.api.dto.JsonProject;
import io.lumeer.api.model.Organization;
import io.lumeer.api.model.Permission;
import io.lumeer.api.model.Project;
import io.lumeer.api.model.ResourceType;
import io.lumeer.api.model.Role;
import io.lumeer.storage.api.exception.ResourceNotFoundException;
import io.lumeer.storage.api.exception.StorageException;
import io.lumeer.storage.api.query.DatabaseQuery;
import io.lumeer.storage.mongodb.MongoDbTestBase;
import io.lumeer.storage.mongodb.util.MongoFilters;

import org.assertj.core.api.SoftAssertions;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MongoProjectDaoTest extends MongoDbTestBase {

   private static final String ORGANIZATION_ID = "596e3b86d412bc5a3caaa22a";

   private static final String USER = "testUser";
   private static final String USER2 = "testUser2";

   private static final String GROUP = "testGroup";
   private static final String GROUP2 = "testGroup2";

   private static final String CODE1 = "TPROJ1";
   private static final String CODE2 = "TPROJ2";
   private static final String NOT_EXISTING_ID = "598323f5d412bc7a51b5a460";
   private static final String NAME = "Testing project";
   private static final String COLOR = "#cccccc";
   private static final String ICON = "fa-search";
   private static final JsonPermissions PERMISSIONS;
   private static final JsonPermission GROUP_PERMISSION;

   static {
      JsonPermission userPermission = new JsonPermission(USER, Project.ROLES.stream().map(Role::toString).collect(Collectors.toSet()));

      GROUP_PERMISSION = new JsonPermission(GROUP, Collections.singleton(Role.READ.toString()));

      PERMISSIONS = new JsonPermissions();
      PERMISSIONS.updateUserPermissions(userPermission);
      PERMISSIONS.updateGroupPermissions(GROUP_PERMISSION);
   }

   private MongoProjectDao projectDao;

   @Before
   public void initProjectDao() {
      Organization organization = Mockito.mock(Organization.class);
      Mockito.when(organization.getId()).thenReturn(ORGANIZATION_ID);

      projectDao = new MongoProjectDao();
      projectDao.setDatabase(database);

      projectDao.setOrganization(organization);
      projectDao.createProjectsRepository(organization);
   }

   private JsonProject prepareProject(String code) {
      JsonProject project = new JsonProject();
      project.setCode(code);
      project.setName(NAME);
      project.setColor(COLOR);
      project.setIcon(ICON);
      project.setPermissions(new JsonPermissions(PERMISSIONS));
      return project;
   }

   @Test
   public void testCreateProject() {
      Project project = prepareProject(CODE1);

      String id = projectDao.createProject(project).getId();
      assertThat(id).isNotNull().isNotEmpty();
      assertThat(ObjectId.isValid(id)).isTrue();

      JsonProject storedProject = projectDao.databaseCollection().find(MongoFilters.idFilter(id)).first();
      assertThat(storedProject).isNotNull();

      SoftAssertions assertions = new SoftAssertions();
      assertions.assertThat(storedProject.getCode()).isEqualTo(CODE1);
      assertions.assertThat(storedProject.getName()).isEqualTo(NAME);
      assertions.assertThat(storedProject.getColor()).isEqualTo(COLOR);
      assertions.assertThat(storedProject.getIcon()).isEqualTo(ICON);
      assertions.assertThat(storedProject.getPermissions()).isEqualTo(PERMISSIONS);
      assertions.assertAll();
   }

   @Test
   public void testCreateProjectExistingCode() {
      JsonProject project = prepareProject(CODE1);
      projectDao.databaseCollection().insertOne(project);

      Project project2 = prepareProject(CODE1);
      assertThatThrownBy(() -> projectDao.createProject(project2))
            .isInstanceOf(StorageException.class);
   }

   @Test
   public void testGetProjectByCode() {
      JsonProject project = prepareProject(CODE1);
      projectDao.databaseCollection().insertOne(project);

      JsonProject storedProject = (JsonProject) projectDao.getProjectByCode(CODE1);
      assertThat(storedProject).isNotNull();
      assertThat(storedProject.getId()).isNotNull().isNotEmpty();

      SoftAssertions assertions = new SoftAssertions();
      assertions.assertThat(storedProject.getCode()).isEqualTo(CODE1);
      assertions.assertThat(storedProject.getName()).isEqualTo(NAME);
      assertions.assertThat(storedProject.getColor()).isEqualTo(COLOR);
      assertions.assertThat(storedProject.getIcon()).isEqualTo(ICON);
      assertions.assertThat(storedProject.getPermissions()).isEqualTo(PERMISSIONS);
      assertions.assertAll();
   }

   @Test
   public void testGetProjectByCodeNotExisting() {
      assertThatThrownBy(() -> projectDao.getProjectByCode("notExistingCode"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasFieldOrPropertyWithValue("resourceType", ResourceType.PROJECT);
   }

   @Test
   public void testGetProjects() {
      JsonProject project = prepareProject(CODE1);
      projectDao.databaseCollection().insertOne(project);

      JsonProject project2 = prepareProject(CODE2);
      projectDao.databaseCollection().insertOne(project2);

      DatabaseQuery query = DatabaseQuery.createBuilder(USER).build();
      List<Project> projects = projectDao.getProjects(query);
      assertThat(projects).extracting(Project::getCode).containsOnly(CODE1, CODE2);
   }

   @Test
   public void testGetProjectsNoReadRole() {
      JsonProject project = prepareProject(CODE1);
      Permission userPermission = new JsonPermission(USER2, Collections.singleton(Role.CLONE.toString()));
      project.getPermissions().updateUserPermissions(userPermission);
      projectDao.databaseCollection().insertOne(project);

      JsonProject project2 = prepareProject(CODE2);
      Permission groupPermission = new JsonPermission(GROUP2, Collections.singleton(Role.SHARE.toString()));
      project2.getPermissions().updateGroupPermissions(groupPermission);
      projectDao.databaseCollection().insertOne(project2);

      DatabaseQuery query = DatabaseQuery.createBuilder(USER2).groups(Collections.singleton(GROUP2)).build();
      List<Project> projects = projectDao.getProjects(query);
      assertThat(projects).isEmpty();
   }

   @Test
   public void testGetProjectsGroupRole() {
      JsonProject project = prepareProject(CODE1);
      projectDao.databaseCollection().insertOne(project);

      JsonProject project2 = prepareProject(CODE2);
      projectDao.databaseCollection().insertOne(project2);

      DatabaseQuery query = DatabaseQuery.createBuilder(USER2).groups(Collections.singleton(GROUP)).build();
      List<Project> projects = projectDao.getProjects(query);
      assertThat(projects).extracting(Project::getCode).containsOnly(CODE1, CODE2);
   }

   @Test
   public void testDeleteProject() {
      JsonProject project = prepareProject(CODE1);
      projectDao.databaseCollection().insertOne(project);
      assertThat(project.getId()).isNotNull();

      projectDao.deleteProject(project.getId());

      JsonProject storedProject = projectDao.databaseCollection().find(MongoFilters.idFilter(project.getId())).first();
      assertThat(storedProject).isNull();
   }

   @Test
   public void testDeleteProjectNotExisting() {
      assertThatThrownBy(() -> projectDao.deleteProject(NOT_EXISTING_ID))
            .isInstanceOf(StorageException.class);
   }

   @Test
   public void testUpdateProjectCode() {
      JsonProject project = prepareProject(CODE1);
      String id = projectDao.createProject(project).getId();
      assertThat(id).isNotNull().isNotEmpty();

      project.setCode(CODE2);
      projectDao.updateProject(id, project);

      JsonProject storedProject = projectDao.databaseCollection().find(MongoFilters.idFilter(id)).first();
      assertThat(storedProject).isNotNull();
      assertThat(storedProject.getCode()).isEqualTo(CODE2);
   }

   @Test
   public void testUpdateProjectPermissions() {
      JsonProject project = prepareProject(CODE1);
      String id = projectDao.createProject(project).getId();
      assertThat(id).isNotNull().isNotEmpty();

      project.getPermissions().removeUserPermission(USER);
      project.getPermissions().updateGroupPermissions(GROUP_PERMISSION);
      projectDao.updateProject(id, project);

      JsonProject storedProject = projectDao.databaseCollection().find(MongoFilters.idFilter(id)).first();
      assertThat(storedProject).isNotNull();
      assertThat(storedProject.getPermissions().getUserPermissions()).isEmpty();
      assertThat(storedProject.getPermissions().getGroupPermissions()).containsExactly(GROUP_PERMISSION);
   }

   @Test
   public void testUpdateProjectExistingCode() {
      JsonProject project = prepareProject(CODE1);
      projectDao.databaseCollection().insertOne(project);

      JsonProject project2 = prepareProject(CODE2);
      projectDao.databaseCollection().insertOne(project2);

      project2.setCode(CODE1);
      assertThatThrownBy(() -> projectDao.updateProject(project2.getId(), project2))
            .isInstanceOf(StorageException.class);
   }

}