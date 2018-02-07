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
package io.lumeer.storage.api.dao;

import io.lumeer.api.model.User;

import java.util.List;
import java.util.Optional;

public interface UserDao {

   User createUser(String organizationId, User user);

   User updateUser(String organizationId, String userId, User user);

   void deleteUser(String organizationId, String userId);

   void deleteGroupFromUsers(String organizationId, String group);

   Optional<User> getUserByEmail(String organizationId, String email);

   List<User> getAllUsers(String organizationId);

}
