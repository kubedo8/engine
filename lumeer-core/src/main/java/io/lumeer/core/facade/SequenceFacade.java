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
package io.lumeer.core.facade;

import io.lumeer.api.model.Project;
import io.lumeer.api.model.ResourceType;
import io.lumeer.api.model.RoleOld;
import io.lumeer.api.model.Sequence;
import io.lumeer.storage.api.dao.SequenceDao;
import io.lumeer.storage.api.exception.ResourceNotFoundException;

import java.util.List;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;

@RequestScoped
public class SequenceFacade extends AbstractFacade {

   @Inject
   private SequenceDao sequenceDao;

   public int getNextSequenceNumber(final String sequenceName) {
      return sequenceDao.getNextSequenceNo(sequenceName);
   }

   public List<Sequence> getAllSequences() {
      checkProjectManageRole();

      return sequenceDao.getAllSequences();
   }

   public Sequence updateSequence(final String id, final Sequence sequence) {
      checkProjectManageRole();

      return sequenceDao.updateSequence(id, sequence);
   }

   public void deleteSequence(final String id) {
      checkProjectManageRole();
      sequenceDao.deleteSequence(id);
   }

   private void checkProjectManageRole() {
      Project project = getCurrentProject();
      permissionsChecker.checkRole(project, RoleOld.MANAGE);
   }

   private Project getCurrentProject() {
      if (!workspaceKeeper.getProject().isPresent()) {
         throw new ResourceNotFoundException(ResourceType.PROJECT);
      }
      return workspaceKeeper.getProject().get();
   }

}
