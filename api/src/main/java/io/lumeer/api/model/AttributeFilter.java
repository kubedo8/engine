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

package io.lumeer.api.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AttributeFilter {

   private final String attributeId;
   private final String condition;
   private List<ConditionValue> values;

   public AttributeFilter(final String attributeId, final String condition, final List<ConditionValue> value) {
      this.attributeId = attributeId;
      this.condition = condition;
      this.values = value;
   }

   public String getAttributeId() {
      return attributeId;
   }

   public String getCondition() {
      return condition;
   }

   public List<ConditionValue> getValues() {
      return values;
   }

   public Object getValue() {
      return values != null && !values.isEmpty() ? values.get(0).getValue() : null;
   }

   public void setValue(final Object value) {
      this.values = Collections.singletonList(new ConditionValue(value));
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      }
      if (!(o instanceof AttributeFilter)) {
         return false;
      }
      final AttributeFilter that = (AttributeFilter) o;
      return Objects.equals(attributeId, that.attributeId) &&
            Objects.equals(condition, that.condition) &&
            Objects.equals(values, that.values);
   }

   @Override
   public int hashCode() {
      return Objects.hash(attributeId, condition, values);
   }

   @Override
   public String toString() {
      return "AttributeFilter{" +
            "attributeId='" + attributeId + '\'' +
            ", condition='" + condition + '\'' +
            ", value=" + values +
            '}';
   }
}
