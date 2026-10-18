package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.repository.WoodDepotRepositoryImpl
import com.forestry.counter.domain.model.WoodDepotDraft
import com.forestry.counter.domain.model.WoodDepotKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** True Room 42→43 migration, exclusively on labelled TEST legacy fixtures. */
@RunWith(AndroidJUnit4::class)
class Migration42To43Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: MigrationTestHelper

    @Before fun setup() {
        context.deleteDatabase(DB_NAME)
        helper = MigrationTestHelper(instrumentation, "com.forestry.counter.data.local.ForestryDatabase",
            FrameworkSQLiteOpenHelperFactory())
    }

    @After fun cleanup() { context.deleteDatabase(DB_NAME) }

    @Test fun all_legacy_rows_and_opaque_sealed_payloads_survive_without_default_depots() = runBlocking {
        val before = helper.createDatabase(DB_NAME, 42).use { db ->
            fixtures(db)
            tableNames(db).associateWith { rows(db, it) }
        }
        helper.runMigrationsAndValidate(DB_NAME, 43, true, WoodDepotMigrations.MIGRATION_42_43).use { db ->
            before.forEach { (table, values) -> assertEquals(table, values, rows(db, table)) }
            assertTrue(rows(db, "wood_depots").isEmpty())
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        val current = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL).build()
        try {
            assertEquals(SEALED_SNAPSHOT, current.inventaireSessionDao().getById("TEST-session")?.closedSnapshotJson)
            val identity = BusinessReadIdentityFixture()
            val repo = WoodDepotRepositoryImpl(current, identity) { 500 }
            repo.createDepot(ID, WoodDepotDraft("TEST new independent depot", WoodDepotKind.ROADSIDE), repo.captureAccess())
            before.forEach { (table, values) -> assertEquals(table, values, rows(current.openHelper.writableDatabase, table)) }
            assertEquals(1, rows(current.openHelper.writableDatabase, "wood_depots").size)
        } finally { current.close() }
    }

    private fun fixtures(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('TEST-forest','TEST forest','TEST owner',10,20)")
        db.execSQL("INSERT INTO parcelles(parcelleId,name,surfaceHa,foretId,createdAt,updatedAt) VALUES('TEST-parcel','TEST parcel',2.5,'TEST-forest',10,20)")
        db.execSQL("INSERT INTO essences(code,name) VALUES('TEST-oak','TEST oak')")
        db.execSQL("""
            INSERT INTO inventaire_sessions
                (sessionId,parcelleId,typeSession,dateDebut,dateFin,createdAt,martelageState,
                campaignId,activeDurationMs,closedSnapshotJson,auteur,source,version)
            VALUES('TEST-session','TEST-parcel','MARTELAGE',100,200,100,'CLOSED',
                'TEST-campaign',75,?,'TEST-author','manual',9)
        """.trimIndent(), arrayOf(SEALED_SNAPSHOT))
        db.execSQL("""
            INSERT INTO tiges
                (tigeId,uuid,parcelleOwnerId,sessionId,essenceCode,diamCm,hauteurM,gpsWkt,
                precisionM,timestamp,isTigeHabitat,auteur,source,version)
            VALUES('TEST-tree','TEST-tree-uuid','TEST-parcel','TEST-session','TEST-oak',37.5,18.25,
                'POINT(2.5 46.1)',12.5,150,0,'TEST-author','gps',4)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO survey_contexts
                (sessionId,revision,payloadJson,sealedPayloadJson,createdAt,createdBy,updatedAt,updatedBy,copiedFromSessionId)
            VALUES('TEST-session',7,?,?,100,'TEST-context-author',150,'TEST-context-editor',NULL)
        """.trimIndent(), arrayOf(SEALED_CONTEXT, SEALED_CONTEXT))
        db.execSQL("""
            INSERT INTO observation_qualifications
                (tigeId,revision,payloadJson,sealedPayloadJson,createdAt,createdBy,updatedAt,updatedBy)
            VALUES('TEST-tree',5,?,?,100,'TEST-measure-author',150,'TEST-measure-editor')
        """.trimIndent(), arrayOf(SEALED_QUALIFICATION, SEALED_QUALIFICATION))
        db.execSQL("""
            INSERT INTO parcel_sync_operations
                (operationId,parcelId,originAccountId,destinationKind,apiOrigin,operation,
                requestJson,createdAt,state,retryCount,nextAttemptAt,legacyReferenceJson)
            VALUES('TEST-parcel-operation','TEST-parcel','TEST-account','PERSONAL',
                'https://example.invalid','UPSERT',?,150,'PENDING',2,0,'{ "TEST":"legacy" }')
        """.trimIndent(), arrayOf(PARCEL_REQUEST))
        db.execSQL("""
            INSERT INTO hierarchy_sync_operations
                (operationId,forestId,accountId,apiOrigin,requestJson,requestSha256,dossierSha256,
                expectedServerVersion,createdAt,containsLocalPhotos,state,retryCount,nextAttemptAt)
            VALUES('TEST-hierarchy-operation','TEST-forest','TEST-account','https://example.invalid',
                ?,'TEST-request-sha','TEST-dossier-sha',1,160,0,'PENDING',3,0)
        """.trimIndent(), arrayOf(HIERARCHY_REQUEST))
    }

    private fun tableNames(db: SupportSQLiteDatabase): List<String> = db.query("""
        SELECT name FROM sqlite_master WHERE type = 'table'
            AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata')
        ORDER BY name
    """.trimIndent()).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
        db.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor -> buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                if (cursor.isNull(index)) null else cursor.getString(index)
            })
        } }

    private companion object {
        const val DB_NAME = "TEST-wood-depot-migration-42-43.db"
        const val ID = "a0000000-0000-0000-0000-000000000001"
        const val SEALED_SNAPSHOT = "{ \"schemaVersion\":1, \"TEST\":\"opaque original snapshot\" }"
        const val SEALED_CONTEXT = " { \"schemaVersion\":1, \"TEST\":\"opaque context\" } "
        const val SEALED_QUALIFICATION = "{ \"TEST\":\"opaque measurement qualification\", \"height\":18.2500 }"
        const val PARCEL_REQUEST = "{ \"TEST\":\"opaque parcel request\", \"height\":18.2500 }"
        const val HIERARCHY_REQUEST = "{ \"TEST\":\"opaque hierarchy request\", \"nullable\":null }"
    }
}
