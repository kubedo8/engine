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

package io.lumeer.api.model;

import io.lumeer.api.util.AttributeUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class LinkType {

   public static final String ID = "id";
   public static final String NAME = "name";
   public static final String COLLECTION_IDS = "collectionIds";
   public static final String ATTRIBUTES = "attributes";

   public static String ATTRIBUTE_PREFIX = "a";

   private String id;
   private String name;
   private long version;
   private List<String> collectionIds;
   private List<Attribute> attributes;
   private Integer lastAttributeNum;

   @JsonCreator
   public LinkType(@JsonProperty(ID) final String id,
         @JsonProperty(NAME) final String name,
         @JsonProperty(COLLECTION_IDS) final List<String> collectionIds,
         @JsonProperty(ATTRIBUTES) final List<Attribute> attributes) {
      this.id = id;
      this.name = name;
      this.collectionIds = collectionIds;
      this.attributes = attributes;
   }

   public LinkType(LinkType linkType) {
      this.id = linkType.getId();
      this.name = linkType.getName();
      this.collectionIds = linkType.getCollectionIds();
      this.version = linkType.getVersion();
      this.attributes = linkType.getAttributes();
      this.lastAttributeNum = linkType.getLastAttributeNum();
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<String> getCollectionIds() {
      return Collections.unmodifiableList(collectionIds);
   }

   public void setCollectionIds(List<String> collectionIds) {
      this.collectionIds = collectionIds;
   }

   public List<Attribute> getAttributes() {
      return Collections.unmodifiableList(attributes);
   }

   public void setAttributes(final List<Attribute> attributes) {
      this.attributes = attributes != null ? new LinkedList<>(attributes) : new LinkedList<>();
   }

   public void createAttribute(final Attribute attribute) {
      if (attributes != null) {
         attributes.add(attribute);
      } else {
         attributes = new ArrayList<>(Collections.singletonList(attribute));
      }
   }

   public void updateAttribute(final String attributeId, final Attribute attribute) {
      Optional<Attribute> oldAttribute = attributes.stream().filter(attr -> attr.getId().equals(attributeId)).findFirst();
      attributes.removeIf(a -> a.getId().equals(attributeId));

      oldAttribute.ifPresent((a) -> attribute.setUsageCount(a.getUsageCount()));
      attributes.add(attribute);

      if (oldAttribute.isPresent() && !oldAttribute.get().getName().equals(attribute.getName())) {
         AttributeUtil.renameChildAttributes(attributes, oldAttribute.get().getName(), attribute.getName());
      }
   }

   public void deleteAttribute(final String attributeId) {
      Optional<Attribute> toDelete = attributes.stream().filter(attribute -> attribute.getId().equals(attributeId)).findFirst();
      toDelete.ifPresent(jsonAttribute -> attributes.removeIf(attribute -> AttributeUtil.isEqualOrChild(attribute, jsonAttribute.getName())));
   }

   public long getVersion() {
      return version;
   }

   public void setVersion(final long version) {
      this.version = version;
   }

   public Integer getLastAttributeNum() {
      return lastAttributeNum;
   }

   public void setLastAttributeNum(final Integer lastAttributeNum) {
      this.lastAttributeNum = lastAttributeNum;
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      }
      if (!(o instanceof LinkType)) {
         return false;
      }

      final LinkType linkType = (LinkType) o;

      return Objects.equals(id, linkType.id);
   }

   @Override
   public int hashCode() {
      return id != null ? id.hashCode() : 0;
   }

   @Override
   public String toString() {
      return "LinkType{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", version=" + version +
            ", collectionIds=" + collectionIds +
            ", attributes=" + attributes +
            ", lastAttributeNum=" + lastAttributeNum +
            '}';
   }
}
