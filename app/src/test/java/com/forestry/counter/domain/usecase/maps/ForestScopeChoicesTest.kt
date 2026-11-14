package com.forestry.counter.domain.usecase.maps

import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.model.Parcelle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ForestScopeChoicesTest {
    @Test fun `canonical forest created without any group remains a navigable forest choice`() {
        val result=forestScopeChoices(listOf(forest("canonical","TEST local forest")),emptyList())
        assertEquals(listOf(ForestScopeChoice("canonical","TEST local forest",ForestScopeKind.CANONICAL)),result)
    }
    @Test fun `same id prefers canonical identity while equal labels never merge different ids`() {
        val result=forestScopeChoices(listOf(forest("canonical","TEST shared label")),listOf(
            Group("canonical","TEST old label"),Group("legacy","TEST shared label")))
        assertEquals(2,result.size)
        assertEquals("TEST shared label",result.single { it.id == "canonical" }.name)
        assertEquals(ForestScopeKind.CANONICAL,result.single { it.id == "canonical" }.kind)
        assertEquals(ForestScopeKind.LEGACY_GROUP,result.single { it.id == "legacy" }.kind)
    }
    @Test fun `a known canonical tombstone cannot be relabelled as a historical group`() {
        assertTrue(forestScopeChoices(listOf(forest("same","TEST deleted",1)),listOf(Group("same","TEST historical"))).isEmpty())
    }
    @Test fun `canonical parent masks an unrelated historical link but null canonical keeps the historical fallback`() {
        val migrated=parcel("canonical-A","legacy-B")
        assertTrue(migrated.belongsToForestScope("canonical-A"));assertFalse(migrated.belongsToForestScope("legacy-B"))
        // Historical forestId may point at a canonical forest without a Group row. No table identity is inferred here.
        assertTrue(parcel(null,"canonical-without-group").belongsToForestScope("canonical-without-group"))
        assertTrue(parcel(null,"legacy-B").belongsToForestScope("legacy-B"))
        assertFalse(parcel(null,null).belongsToForestScope("any"))
        assertFalse(parcel("","legacy-B").belongsToForestScope("legacy-B"))
    }
    private fun forest(id: String,name: String,deleted: Long?=null)=mockk<Foret> {
        every { foretId } returns id;every { nom } returns name;every { deletedAt } returns deleted
    }
    private fun parcel(canonical: String?,legacy: String?)=mockk<Parcelle> {
        every { foretId } returns canonical;every { forestId } returns legacy
    }
}
