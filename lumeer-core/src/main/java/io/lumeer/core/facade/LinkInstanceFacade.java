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

import io.lumeer.api.model.Constraint;
import io.lumeer.api.model.ConstraintType;
import io.lumeer.api.model.Document;
import io.lumeer.api.model.DocumentLinks;
import io.lumeer.api.model.FileAttachment;
import io.lumeer.api.model.LinkInstance;
import io.lumeer.api.model.LinkType;
import io.lumeer.api.model.Project;
import io.lumeer.api.model.ResourceType;
import io.lumeer.api.model.Role;
import io.lumeer.api.util.ResourceUtils;
import io.lumeer.core.adapter.LinkInstanceAdapter;
import io.lumeer.core.constraint.ConstraintManager;
import io.lumeer.core.exception.BadFormatException;
import io.lumeer.core.facade.configuration.DefaultConfigurationProducer;
import io.lumeer.core.util.Tuple;
import io.lumeer.engine.api.data.DataDocument;
import io.lumeer.engine.api.event.CreateLinkInstance;
import io.lumeer.engine.api.event.ImportLinkTypeContent;
import io.lumeer.engine.api.event.SetDocumentLinks;
import io.lumeer.engine.api.event.UpdateLinkInstance;
import io.lumeer.storage.api.dao.DocumentDao;
import io.lumeer.storage.api.dao.LinkDataDao;
import io.lumeer.storage.api.dao.LinkInstanceDao;
import io.lumeer.storage.api.dao.LinkTypeDao;
import io.lumeer.storage.api.dao.ResourceCommentDao;
import io.lumeer.storage.api.exception.ResourceNotFoundException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

@RequestScoped
public class LinkInstanceFacade extends AbstractFacade {

   @Inject
   private LinkTypeDao linkTypeDao;

   @Inject
   private LinkInstanceDao linkInstanceDao;

   @Inject
   private LinkDataDao linkDataDao;

   @Inject
   private DocumentDao documentDao;

   @Inject
   private DefaultConfigurationProducer configurationProducer;

   @Inject
   private Event<CreateLinkInstance> createLinkInstanceEvent;

   @Inject
   private Event<UpdateLinkInstance> updateLinkInstanceEvent;

   @Inject
   private Event<ImportLinkTypeContent> importLinkTypeContentEvent;

   @Inject
   private Event<SetDocumentLinks> setDocumentLinksEvent;

   @Inject
   private FileAttachmentFacade fileAttachmentFacade;

   @Inject
   private ResourceCommentDao resourceCommentDao;

   @Inject
   private TaskProcessingFacade taskProcessingFacade;

   private ConstraintManager constraintManager;

   private LinkInstanceAdapter adapter;

   @PostConstruct
   public void init() {
      constraintManager = ConstraintManager.getInstance(configurationProducer);
      adapter = new LinkInstanceAdapter(resourceCommentDao);
   }

   public LinkInstanceAdapter getAdapter() {
      return adapter;
   }

   public LinkInstance createLinkInstance(final LinkInstance linkInstance) {
      checkDocumentsExists(linkInstance.getDocumentIds());
      var linkType = checkLinkTypeWritePermissions(linkInstance.getLinkTypeId());

      var linkInstanceData = createLinkInstance(linkType, linkInstance);

      if (createLinkInstanceEvent != null) {
         createLinkInstanceEvent.fire(new CreateLinkInstance(linkInstanceData.getFirst()));
      }

      return linkInstanceData.getSecond();
   }

   public Tuple<LinkInstance, LinkInstance> createLinkInstance(final LinkType linkType, final LinkInstance linkInstance) {
      linkInstance.setCreatedBy(getCurrentUserId());
      linkInstance.setCreationDate(ZonedDateTime.now());
      LinkInstance createdLinkInstance = linkInstanceDao.createLinkInstance(linkInstance);

      var data = constraintManager.encodeDataTypes(linkType, linkInstance.getData());

      var storedData = linkDataDao.createData(linkInstance.getLinkTypeId(), createdLinkInstance.getId(), data);
      createdLinkInstance.setData(storedData);

      var createdLinkInstanceCopy = new LinkInstance(createdLinkInstance);

      createdLinkInstance.setData(constraintManager.decodeDataTypes(linkType, storedData));

      return new Tuple<>(createdLinkInstanceCopy, createdLinkInstance);
   }

   public List<LinkInstance> createLinkInstances(final List<LinkInstance> linkInstances, final boolean sendIndividualNotifications) {
      if (linkInstances.size() > 0) {
         final String linkTypeId = linkInstances.get(0).getLinkTypeId();
         var linkType = checkLinkTypeWritePermissions(linkTypeId);

         checkLinkDocumentsExists(linkInstances);

         if (linkInstances.stream().anyMatch(linkInstance -> !linkInstance.getLinkTypeId().equals(linkTypeId))) {
            throw new BadFormatException("Cannot create link instances of multiple link types at once.");
         }

         final List<LinkInstance> storedLinkInstances = createLinkInstances(linkType, linkInstances, sendIndividualNotifications);

         if (importLinkTypeContentEvent != null) {
            importLinkTypeContentEvent.fire(new ImportLinkTypeContent(linkType));
         }

         return storedLinkInstances;
      }

      return linkInstances;
   }

   private List<LinkInstance> createLinkInstances(final LinkType linkType, final List<LinkInstance> linkInstances, boolean sendIndividualNotifications) {
      linkInstances.forEach(linkInstance -> {
         linkInstance.setCreatedBy(getCurrentUserId());
         linkInstance.setCreationDate(ZonedDateTime.now());
      });

      final List<LinkInstance> storedInstances = linkInstanceDao.createLinkInstances(linkInstances, sendIndividualNotifications);
      final Map<String, LinkInstance> storedInstancesMap = new HashMap<>();

      storedInstances.forEach(linkInstance -> {
         storedInstancesMap.put(linkInstance.getId(), linkInstance);

         var data = constraintManager.encodeDataTypes(linkType, linkInstance.getData());
         data.setId(linkInstance.getId());
         linkInstance.setData(data);
      });

      final List<DataDocument> storedData = linkDataDao.createData(linkType.getId(), storedInstances.stream().map(LinkInstance::getData).collect(Collectors.toList()));
      storedData.forEach(data -> {
         storedInstancesMap.get(data.getId()).setData(constraintManager.decodeDataTypes(linkType, data));
      });

      return storedInstances;
   }

   public LinkInstance updateLinkInstance(final String linkInstanceId, final LinkInstance linkInstance) {
      checkDocumentsExists(linkInstance.getDocumentIds());
      final LinkInstance stored = linkInstanceDao.getLinkInstance(linkInstanceId);
      final LinkInstance originalLinkInstance = new LinkInstance(stored);
      final String linkTypeId = Objects.requireNonNullElse(linkInstance.getLinkTypeId(), stored.getLinkTypeId());

      final LinkType linkType = checkLinkTypeWritePermissions(linkTypeId);

      updateLinkTypeMetadata(linkType, Collections.emptySet(), Collections.emptySet());

      final DataDocument data = linkDataDao.getData(linkType.getId(), linkInstanceId);
      stored.setDocumentIds(linkInstance.getDocumentIds());
      stored.setLinkTypeId(linkTypeId);

      final LinkInstance updatedLinkInstance = updateLinkInstance(stored, data, originalLinkInstance);

      updatedLinkInstance.setData(constraintManager.decodeDataTypes(linkType, data));

      return updatedLinkInstance;
   }

   public LinkInstance updateLinkInstanceData(final String linkInstanceId, final DataDocument updateData) {
      final LinkInstance stored = linkInstanceDao.getLinkInstance(linkInstanceId);
      final LinkInstance originalLinkInstance = new LinkInstance(stored);
      final LinkType linkType = checkLinkTypeWritePermissions(stored.getLinkTypeId());

      final DataDocument data = constraintManager.encodeDataTypes(linkType, updateData);

      final DataDocument oldData = linkDataDao.getData(linkType.getId(), linkInstanceId);
      originalLinkInstance.setData(oldData);
      final Set<String> attributesIdsToAdd = new HashSet<>(data.keySet());
      attributesIdsToAdd.removeAll(oldData.keySet());

      final Set<String> attributesIdsToDec = new HashSet<>(oldData.keySet());
      attributesIdsToDec.removeAll(data.keySet());
      updateLinkTypeMetadata(linkType, attributesIdsToAdd, attributesIdsToDec);

      final DataDocument updatedData = linkDataDao.updateData(linkType.getId(), linkInstanceId, data);

      final LinkInstance updatedLinkInstance = updateLinkInstance(stored, updatedData, originalLinkInstance);

      updatedLinkInstance.setData(constraintManager.decodeDataTypes(linkType, updatedData));

      return updatedLinkInstance;
   }

   private LinkInstance updateLinkInstance(LinkInstance linkInstance, DataDocument newData, final LinkInstance originalLinkInstance) {
      linkInstance.setData(newData);
      linkInstance.setUpdateDate(ZonedDateTime.now());
      linkInstance.setUpdatedBy(getCurrentUserId());

      LinkInstance updatedLinkInstance = linkInstanceDao.updateLinkInstance(linkInstance.getId(), linkInstance);
      updatedLinkInstance.setData(newData);

      fireLinkInstanceUpdate(updatedLinkInstance, originalLinkInstance);

      return updatedLinkInstance;
   }

   private void fireLinkInstanceUpdate(final LinkInstance updatedLinkInstance, final LinkInstance originalLinkInstance) {
      if (updateLinkInstanceEvent != null) {
         LinkInstance updatedLinkInstanceWithData = new LinkInstance(updatedLinkInstance);
         updateLinkInstanceEvent.fire(new UpdateLinkInstance(updatedLinkInstanceWithData, originalLinkInstance));
      }
   }

   private void updateLinkTypeMetadata(LinkType linkType, Set<String> attributesIdsToInc, Set<String> attributesIdsToDec) {
      linkType.setAttributes(new ArrayList<>(ResourceUtils.incOrDecAttributes(linkType.getAttributes(), attributesIdsToInc, attributesIdsToDec)));
      linkTypeDao.updateLinkType(linkType.getId(), linkType, new LinkType(linkType));
   }

   public LinkInstance patchLinkInstanceData(final String linkInstanceId, final DataDocument updateData) {
      final LinkInstance stored = linkInstanceDao.getLinkInstance(linkInstanceId);
      final LinkInstance originalLinkInstance = new LinkInstance(stored);
      final LinkType linkType = checkLinkTypeWritePermissions(stored.getLinkTypeId());

      final DataDocument data = constraintManager.encodeDataTypes(linkType, updateData);

      final DataDocument oldData = linkDataDao.getData(linkType.getId(), linkInstanceId);
      originalLinkInstance.setData(oldData);
      final Set<String> attributesIdsToAdd = new HashSet<>(data.keySet());
      attributesIdsToAdd.removeAll(oldData.keySet());

      updateLinkTypeMetadata(linkType, attributesIdsToAdd, Collections.emptySet());

      final DataDocument updatedData = linkDataDao.patchData(linkType.getId(), linkInstanceId, data);

      final LinkInstance updatedLinkInstance = updateLinkInstance(stored, updatedData, originalLinkInstance);

      updatedLinkInstance.setData(constraintManager.decodeDataTypes(linkType, updatedData));

      return updatedLinkInstance;
   }

   public void deleteLinkInstance(String id) {
      LinkInstance stored = linkInstanceDao.getLinkInstance(id);
      final LinkType linkType = checkLinkTypeWritePermissions(stored.getLinkTypeId());

      linkInstanceDao.deleteLinkInstance(id, linkDataDao.getData(stored.getLinkTypeId(), id));
      linkDataDao.deleteData(stored.getLinkTypeId(), id);

      linkType.getAttributes().forEach(attribute -> {
         if (attribute.getConstraint() != null && attribute.getConstraint().getType().equals(ConstraintType.FileAttachment)) {
            fileAttachmentFacade.removeAllFileAttachments(linkType.getId(), stored.getId(), attribute.getId(), FileAttachment.AttachmentType.LINK);
         }
      });
   }

   public LinkInstance getLinkInstance(String linkTypeId, String linkInstanceId) {
      LinkType linkType = checkLinkTypeReadPermissions(linkTypeId);
      return getLinkInstance(linkType, linkInstanceId);
   }

   private LinkInstance getLinkInstance(LinkType linkType, String linkInstanceId) {
      LinkInstance stored = linkInstanceDao.getLinkInstance(linkInstanceId);
      final DataDocument data = constraintManager.decodeDataTypes(linkType, linkDataDao.getData(linkType.getId(), linkInstanceId));
      stored.setData(data);

      return stored;
   }

   public List<LinkInstance> getLinkInstances(Set<String> ids) {
      checkProjectRole(Role.READ);

      var linkComments = getCommentsCounts(ids);
      var linksMap = linkInstanceDao.getLinkInstances(ids)
                                    .stream()
                                    .collect(Collectors.groupingBy(LinkInstance::getLinkTypeId));

      linksMap.forEach((linkTypeId, value) -> {
         var linkType = linkTypeDao.getLinkType(linkTypeId);
         if (permissionsChecker.hasLinkTypePermissions(linkType, Role.READ)) {

            var dataMap = linkDataDao.getData(linkTypeId, value.stream().map(LinkInstance::getId).collect(Collectors.toSet()))
                                     .stream()
                                     .collect(Collectors.toMap(DataDocument::getId, d -> d));

            value.forEach(linkInstance -> {
               var data = dataMap.get(linkInstance.getId());
               if (data != null) {
                  linkInstance.setData(constraintManager.decodeDataTypes(linkType, data));
                  linkInstance.setCommentsCount((long) linkComments.getOrDefault(linkInstance.getId(), 0));
               }
            });
         }
      });

      return linksMap.entrySet().stream()
                     .flatMap(entry -> entry.getValue().stream())
                     .collect(Collectors.toList());
   }

   public List<LinkInstance> duplicateLinkInstances(final String originalDocumentId, final String newDocumentId, final Set<String> linkInstanceIds, final Map<String, String> documentMap) {
      final List<LinkInstance> linkInstances = linkInstanceDao.getLinkInstances(linkInstanceIds);
      if (linkInstances.size() <= 0 || linkInstances.stream().map(LinkInstance::getLinkTypeId).distinct().count() != 1) {
         return null;
      }

      final String linkTypeId = linkInstances.get(0).getLinkTypeId();
      checkLinkTypeWritePermissions(linkTypeId);

      final List<LinkInstance> newLinks = linkInstanceDao.duplicateLinkInstances(linkInstances, originalDocumentId, newDocumentId, documentMap);
      final Map<String, LinkInstance> linkInstancesDirectory = new HashMap<>();
      final Map<String, String> linkMap = new HashMap<>();
      newLinks.forEach(link -> {
         linkInstancesDirectory.put(link.getId(), link);
         linkMap.put(link.getOriginalLinkInstanceId(), link.getId());
      });

      final List<DataDocument> data = linkDataDao.duplicateData(linkTypeId, linkMap);
      data.forEach(l -> {
         if (linkInstancesDirectory.containsKey(l.getId())) {
            linkInstancesDirectory.get(l.getId()).setData(l);
         }
      });

      fileAttachmentFacade.duplicateFileAttachments(linkTypeId, linkMap, FileAttachment.AttachmentType.LINK);

      return newLinks;
   }

   @SuppressWarnings("unchecked")
   public void runRule(final String linkTypeId, String linkInstanceId, String attributeId, final String actionName) {
      LinkType linkType = linkTypeDao.getLinkType(linkTypeId);
      Constraint constraint = ResourceUtils.findConstraint(linkType.getAttributes(), attributeId);
      if (constraint != null) {
         var config = (Map<String, Object>) constraint.getConfig();
         var rule = config.get("rule").toString();
         if (!linkType.getRules().containsKey(rule)) {
            throw new IllegalStateException("Rule not found");
         }
         var roleString = config.get("role").toString();
         var role = Role.fromString(roleString);

         permissionsChecker.checkLinkTypePermissions(linkType, role, true);
         LinkInstance linkInstance = getLinkInstance(linkType, linkInstanceId);
         taskProcessingFacade.runRule(linkType, rule, linkInstance, actionName);
      }
   }

   public LinkInstance mapLinkInstanceData(final LinkInstance linkInstance) {
      return adapter.mapLinkInstanceData(linkInstance);
   }

   public java.util.Collection<LinkInstance> mapLinkInstancesData(final java.util.Collection<LinkInstance> linkInstances) {
      Set<String> linkInstanceIds = linkInstances.stream().map(LinkInstance::getId).collect(Collectors.toSet());
      Map<String, Integer> commentCounts = getCommentsCounts(linkInstanceIds);
      linkInstances.forEach(document -> {
         document.setCommentsCount((long) commentCounts.getOrDefault(document.getId(), 0));
      });
      return linkInstances;
   }

   public List<LinkInstance> setDocumentLinks(final String linkTypeId, final DocumentLinks documentLinks) {
      LinkType linkType = checkLinkTypeWritePermissions(linkTypeId);
      checkDocumentsExists(Collections.singletonList(documentLinks.getDocumentId()));

      List<LinkInstance> deletedLinkInstances = linkInstanceDao.getLinkInstances(new HashSet<>(documentLinks.getRemovedLinkInstancesIds()));
      linkInstanceDao.deleteLinkInstances(documentLinks.getRemovedLinkInstancesIds());

      List<LinkInstance> linkInstances = documentLinks.getCreatedLinkInstances().stream()
                                                      .peek(linkInstance -> linkInstance.setLinkTypeId(linkTypeId)).collect(Collectors.toList());

      List<LinkInstance> createdLinkInstances = Collections.emptyList();
      if (linkInstances.size() > 0) {
         createdLinkInstances = createLinkInstances(linkType, linkInstances, false);
      }

      if (setDocumentLinksEvent != null) {
         setDocumentLinksEvent.fire(new SetDocumentLinks(documentLinks.getDocumentId(), createdLinkInstances, deletedLinkInstances));
      }

      return createdLinkInstances;
   }

   public long getCommentsCount(final String linkInstanceId) {
      return adapter.getCommentsCount(linkInstanceId);
   }

   public Map<String, Integer> getCommentsCounts(final Set<String> linkInstanceIds) {
      return adapter.getCommentsCounts(linkInstanceIds);
   }

   private LinkType checkLinkTypeWritePermissions(String linkTypeId) {
      return checkLinkTypePermissions(linkTypeId, Role.WRITE);
   }

   private LinkType checkLinkTypeReadPermissions(String linkTypeId) {
      return checkLinkTypePermissions(linkTypeId, Role.READ);
   }

   private LinkType checkLinkTypePermissions(String linkTypeId, Role role) {
      LinkType linkType = linkTypeDao.getLinkType(linkTypeId);
      permissionsChecker.checkLinkTypePermissions(linkType, role, false);
      return linkType;
   }

   private void checkProjectRole(Role role) {
      Project project = getCurrentProject();
      permissionsChecker.checkRole(project, role);
   }

   private Project getCurrentProject() {
      if (workspaceKeeper.getProject().isEmpty()) {
         throw new ResourceNotFoundException(ResourceType.PROJECT);
      }
      return workspaceKeeper.getProject().get();
   }

   private void checkDocumentsExists(final List<String> documentIds) {
      List<Document> documents = documentDao.getDocumentsByIds(documentIds.toArray(new String[0]));
      if (documents.size() != documentIds.size()) {
         throw new BadFormatException("Invalid number of document ids in Link: " + documentIds);
      }
   }

   private void checkLinkDocumentsExists(final List<LinkInstance> linkInstances) {
      final List<String> documentIds = new ArrayList<>();
      linkInstances.forEach(linkInstance -> documentIds.addAll(linkInstance.getDocumentIds()));
      if (linkInstances.size() * 2 != documentIds.size()) {
         throw new BadFormatException(String.format("Invalid number of document ids (%d) in links (%d).", documentIds.size(), linkInstances.size()));
      }

      List<Document> documents = documentDao.getDocumentsByIds(documentIds.toArray(new String[0]));
      if (documents.size() != new HashSet<>(documentIds).size()) {
         throw new BadFormatException("Invalid number of document ids in Link: " + documentIds);
      }
   }
}
