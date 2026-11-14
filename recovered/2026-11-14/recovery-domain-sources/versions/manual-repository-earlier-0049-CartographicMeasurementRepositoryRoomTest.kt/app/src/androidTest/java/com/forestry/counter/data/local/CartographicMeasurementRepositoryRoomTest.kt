package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.repository.CartographicMeasurementRepositoryImpl
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.calculation.cartography.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real encrypted Room, synthetic coordinates/identity and a dedicated disposable DB only. */
@RunWith(AndroidJUnit4::class)
class CartographicMeasurementRepositoryRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ForestryDatabase
    private lateinit var identity: BusinessReadIdentityFixture
    private lateinit var repository: CartographicMeasurementRepositoryImpl
    private lateinit var access: CartographicMeasurementAccess

    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB)
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        identity = BusinessReadIdentityFixture()
        db = open()
        repository = repo()
        access = success(repository.captureAccess(MapDataScope.Free))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(DB) }
    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB)
        .openHelperFactory(net.sqlcipher.database.SupportFactory("TEST-only-cartographic-store".toByteArray()))
        .addMigrations(*DatabaseMigrations.ALL).build()
    private fun repo() = CartographicMeasurementRepositoryImpl(db, identity, BusinessReadAccess(identity, db.parcelSyncDao()), clock = { 1_234L })
    private fun input(name: String? = null) = CartographicMeasurementInput(UUID.randomUUID().toString(), name,
        CartographicMeasureKind.DISTANCE, listOf(CartographicCoordinate(45.0, 5.0), CartographicCoordinate(45.001, 5.001)))
    private fun <T> success(result: CartographicStoreResult<T>): T = when (result) {
        is CartographicStoreResult.Success -> result.value
        is CartographicStoreResult.Failure -> throw AssertionError("Unexpected ${result.reason}")
    }
    private fun failure(expected: CartographicStoreError, result: CartographicStoreResult<*>) {
        assertEquals(CartographicStoreResult.Failure(expected), result)
    }

    @Test fun encrypted_reopen_retries_and_unicode_names_preserve_one_immutable_snapshot() = runBlocking {
        val request = input("  TEST / nord \"chêne\"  ")
        val saved = success(repository.save(request, access))
        assertEquals("TEST / nord \"chêne\"", saved.name)
        assertEquals(1_234L, saved.recordedAt)
        assertTrue(saved.valueSi > 0)
        assertEquals(saved, success(repository.save(request, access)))
        val originalKey = access.restorationKey
        db.close(); db = open(); repository = repo()
        access = success(repository.captureAccess(MapDataScope.Free))
        assertEquals(originalKey, access.restorationKey)
        assertEquals(saved, success(repository.read(saved.id, access)))
        assertEquals(listOf(saved), success(repository.list(access)))
    }

    @Test fun duplicate_id_cannot_replace_geometry_name_or_copy_provenance() = runBlocking {
        val request = input("TEST original")
        val saved = success(repository.save(request, access))
        failure(CartographicStoreError.CONFLICT, repository.save(request.copy(name = "TEST overwrite"), access))
        failure(CartographicStoreError.CONFLICT, repository.save(request.copy(points = listOf(CartographicCoordinate(45.0, 5.0), CartographicCoordinate(45.002, 5.0))), access))
        val copy = success(repository.save(input("TEST copy").copy(sourceMeasurementId = saved.id), access))
        assertEquals(saved.id, copy.sourceMeasurementId)
        assertEquals("manual_map_copy", copy.source)
        assertEquals(saved, success(repository.read(saved.id, access)))
        failure(CartographicStoreError.INVALID_INPUT, repository.save(request.copy(sourceMeasurementId = request.id), access))
    }

    @Test fun invalid_geometries_and_oversized_input_never_become_saved_zeroes() = runBlocking {
        val request = input()
        failure(CartographicStoreError.INVALID_INPUT, repository.save(request.copy(points = listOf(CartographicCoordinate(Double.NaN, 5.0), CartographicCoordinate(45.0, 5.0))), access))
        failure(CartographicStoreError.INVALID_INPUT, repository.save(request.copy(kind = CartographicMeasureKind.AREA,
            points = listOf(CartographicCoordinate(45.0, 5.0), CartographicCoordinate(45.001, 5.001), CartographicCoordinate(45.0, 5.001), CartographicCoordinate(45.001, 5.0))), access))
        failure(CartographicStoreError.INVALID_INPUT, repository.save(request.copy(points = List(CartographicMeasureCalculator.MAX_POINTS + 1) { CartographicCoordinate(45.0, 5.0) }), access))
        assertTrue(success(repository.list(access)).isEmpty())
    }

    @Test fun local_account_and_scope_partitions_cannot_read_or_replay_each_other() = runBlocking {
        val request = input("TEST local")
        val local = success(repository.save(request, access))
        val previous = access
        identity.login("TEST-account-A")
        failure(CartographicStoreError.ACCESS_CHANGED, repository.save(input(), previous))
        access = success(repository.captureAccess(MapDataScope.Free))
        assertFalse(access.localDevice)
        assertNotEquals(previous.restorationKey, access.restorationKey)
        assertTrue(success(repository.list(access)).isEmpty())
        failure(CartographicStoreError.UNAVAILABLE, repository.read(local.id, access))
        failure(CartographicStoreError.UNAVAILABLE, repository.save(input().copy(sourceMeasurementId = local.id), access))
        val own = success(repository.save(input(), access))
        val otherScope = success(repository.captureAccess(MapDataScope.AllForests))
        failure(CartographicStoreError.UNAVAILABLE, repository.read(own.id, otherScope))
        identity.login("TEST-account-B")
        val b = success(repository.captureAccess(MapDataScope.Free))
        failure(CartographicStoreError.UNAVAILABLE, repository.read(own.id, b))
    }

    @Test fun deleted_context_is_unavailable_and_never_reassigned_to_free_map() = runBlocking {
        db.openHelper.writableDatabase.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('TEST-forest','TEST forest','TEST owner',10,20)")
        val forest = success(repository.captureAccess(MapDataScope.Forest("TEST-forest")))
        val saved = success(repository.save(input(), forest))
        failure(CartographicStoreError.UNAVAILABLE, repository.read(saved.id, access))
        db.foretDao().delete("TEST-forest", 50)
        failure(CartographicStoreError.UNAVAILABLE, repository.read(saved.id, forest))
        failure(CartographicStoreError.UNAVAILABLE, repository.captureAccess(MapDataScope.Forest("TEST-forest")))
        assertTrue(success(repository.list(access)).isEmpty())
    }

    @Test fun purge_revokes_pending_access_and_rotates_restoration_partition() = runBlocking {
        success(repository.save(input(), access))
        val old = access
        db.woodDataGate.purge { db.withTransaction {
            db.cartographicMeasurementDao().deleteAll()
            db.cartographicMeasurementDao().deleteStoreIdentity()
        } }
        assertFalse(repository.isCurrent(old))
        failure(CartographicStoreError.ACCESS_CHANGED, repository.save(input(), old))
        access = success(repository.captureAccess(MapDataScope.Free))
        assertNotEquals(old.restorationKey, access.restorationKey)
        assertTrue(success(repository.list(access)).isEmpty())
    }

    @Test fun corrupt_geometry_is_an_explicit_failure_instead_of_an_empty_or_zero_measurement() = runBlocking {
        val saved = success(repository.save(input(), access))
        db.openHelper.writableDatabase.execSQL("UPDATE cartographic_measurements SET geometryJson='TEST corrupt' WHERE id=?", arrayOf(saved.id))
        failure(CartographicStoreError.CORRUPT, repository.read(saved.id, access))
        failure(CartographicStoreError.CORRUPT, repository.list(access))
    }

    companion object { private const val DB = "TEST-cartographic-measurement-repository.db" }
}
