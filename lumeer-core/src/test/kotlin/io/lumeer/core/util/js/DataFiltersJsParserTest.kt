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
package io.lumeer.core.util.js

import io.lumeer.api.model.*
import io.lumeer.api.model.Collection
import io.lumeer.engine.api.data.DataDocument
import org.assertj.core.api.Assertions

import org.junit.Test

class DataFiltersJsParserTest {

    private val collection1 = Collection("c1", "c1", "", "", Permissions()).apply { id = "c1" }
    private val collection2 = Collection("c2", "c2", "", "", "", Permissions(), setOf(Attribute("a1")), mapOf(), "", null).apply {
        id = "c2"
    }
    private val linkType = LinkType("lt1", listOf("c1", "c2"), listOf(), mapOf()).apply { id = "lt1" }

    @Test
    fun test() {
        val document1 = Document(DataDocument("a1", "abc")).apply {
            id = "d1"
            collectionId = collection1.id
        }
        val document2 = Document(DataDocument("a1", "abcd")).apply {
            id = "d2"
            collectionId = collection1.id
        }
        val attributeId = collection2.attributes.first().id
        val document3 = Document(DataDocument(attributeId, "lumeer")).apply {
            id = "d3"
            collectionId = collection2.id
        }
        val document4 = Document(DataDocument(attributeId, "nolumeer")).apply {
            id = "d4"
            collectionId = collection2.id
        }
        val link1 = LinkInstance(linkType.id, listOf(document1.id, document3.id)).apply { id = "li1" }
        val link2 = LinkInstance(linkType.id, listOf(document2.id, document4.id)).apply { id = "li2" }
        val filter = CollectionAttributeFilter.createFromValue(collection2.id, attributeId, ConditionType.EQUALS.toString(), "lumeer")
        val query = Query(QueryStem(collection1.id, listOf(linkType.id), setOf(), setOf(filter), setOf()))
        val permissions = AllowedPermissions(true, true, true)
        val collectionsPermissions = mapOf(collection1.id to permissions, collection2.id to permissions)
        val linkTypPermissions = mapOf(linkType.id to permissions)
        val constraintData = ConstraintData(listOf(), null, mapOf(), CurrencyData(listOf(), listOf()))

        val result = DataFiltersJsParser.filterDocumentsAndLinksByQuery(
                listOf(document1, document2, document3, document4),
                listOf(collection1, collection2),
                listOf(linkType),
                listOf(link1, link2),
                query, collectionsPermissions, linkTypPermissions, constraintData, true
        )

        Assertions.assertThat(result.first).containsOnly(document1, document3)
        Assertions.assertThat(result.second).containsOnly(link1)
    }
}