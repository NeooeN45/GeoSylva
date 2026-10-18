package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI

/** Real Room/SQLite exclusively in-memory; no pilot DB, encryption key or migration. */
@RunWith(AndroidJUnit4::class)
class PlacetteBatchCreationDaoTest {
    private lateinit var database: ForestryDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            ForestryDatabase::class.java,
        ).build()
    }

    @After fun cleanup() { database.close() }

    @Test fun duplicate_second_plot_rolls_back_first_and_preserves_original() = runBlocking {
        withTimeout(15_000L) {
            database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
            val existing = point("fixture-existing").toPlacetteEntity()
            database.placetteDao().insertNewPlacettes(listOf(existing))
            val failure = runCatching {
                database.placetteDao().insertNewPlacettes(listOf(
                    point("fixture-first").toPlacetteEntity(),
                    point("fixture-existing").copy(name = "Should not replace").toPlacetteEntity(),
                ))
            }
            assertTrue(failure.isFailure)
            assertNull(database.placetteDao().getPlacetteById("fixture-first"))
            assertEquals(existing, database.placetteDao().getPlacetteById("fixture-existing"))
        }
    }

    @Test fun invalid_second_foreign_key_rolls_back_entire_list() = runBlocking {
        withTimeout(15_000L) {
            database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
            val failure = runCatching {
                database.placetteDao().insertNewPlacettes(listOf(
                    point("fixture-first").toPlacetteEntity(),
                    point("fixture-orphan").copy(parcelleId = "fixture-missing-parent").toPlacetteEntity(),
                ))
            }
            assertTrue(failure.isFailure)
            assertTrue(database.placetteDao().getAllPlacettesNow().isEmpty())
        }
    }

    @Test fun repository_inserts_complete_batch_with_one_author_without_linking_parcel() = runBlocking {
        withTimeout(15_000L) {
            database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
            var reads = 0
            val repository = PlacetteRepositoryImpl(database.placetteDao(), MetadataService {
                reads++
                "fixture-account-a"
            })
            repository.insertPlacettesForParcelle(listOf(point("fixture-one"), point("fixture-two")))
            val stored = database.placetteDao().getAllPlacettesNow()
            assertEquals(2, stored.size)
            assertTrue(reads >= 2) // Identity guard reads do not create a second author snapshot.
            assertEquals(setOf("fixture-account-a"), stored.map { it.auteur }.toSet())
            assertTrue(database.parcelSyncDao().getAccountIdsForParcel("fixture-parent").isEmpty())
        }
    }

    @Test fun soft_deleted_parent_prevents_repository_insert() = runBlocking {
        withTimeout(15_000L) {
            database.parcelleDao().insertParcelle(parcel().copy(deletedAt = 100L).toParcelleEntity())
            val repository = PlacetteRepositoryImpl(database.placetteDao(), MetadataService { null })
            assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(point("fixture-one"))) }.isFailure)
            assertTrue(database.placetteDao().getAllPlacettesNow().isEmpty())
        }
    }

    private fun point(id: String) = Placette(
        id = id, parcelleId = "fixture-parent", name = "Synthetic plot fixture", type = "CIRC",
        rayonM = 10.0, surfaceM2 = PI * 100.0, centerWkt = "POINT (2.3 46.3)",
    )

    private fun parcel() = Parcelle(
        id = "fixture-parent", forestId = null, name = "Synthetic parcel fixture",
        surfaceHa = 1.25, shape = "POLYGON ((2 46, 3 46, 3 47, 2 47, 2 46))",
        slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null,
        samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null,
        srid = 4326, remarks = null,
    )
}
