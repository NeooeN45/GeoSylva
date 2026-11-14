package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.entity.FieldControlBasisEntity
import com.forestry.counter.data.local.entity.FieldEntryEntity
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteConstraintException
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Base SQLCipher dédiée et données synthétiques uniquement.
 * Les exports archivés s'arrêtent à 46 : contrôle explicite du contrat 47,
 * puis validation par Room lors de la migration vers le schéma courant.
 */
@RunWith(AndroidJUnit4::class)
class Migration46To47Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: MigrationTestHelper

    @Before
    fun setup() {
        context.deleteDatabase(DB_NAME)
        SQLiteDatabase.loadLibs(context)
        helper = MigrationTestHelper(
            instrumentation,
            "com.forestry.counter.data.local.ForestryDatabase",
            factory(),
        )
    }

    @After
    fun cleanup() { context.deleteDatabase(DB_NAME) }

    @Test
    fun migration_preserves_legacy_bytes_without_promoting_old_controls_and_enforces_exact_contract() = runBlocking {
        helper.createDatabase(DB_NAME, 46).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            db.execSQL(
                "INSERT INTO field_entries(id,ownerKey,targetKind,targetId,payload,sha256,recordedAt,author) VALUES(?,?,?,?,?,?,?,?)",
                arrayOf("TEST-legacy-control", OWNER_A, "WOOD_OBJECT", OBJECT_ID, PAYLOAD, HASH, 10L, "TEST-author"),
            )
            ControlBasisMigrations.MIGRATION_46_47.migrate(db)

            assertEquals(0, count(db))
            assertEquals(
                listOf(
                    listOf("0", "controlEntryId", "TEXT", "1", null, "1"),
                    listOf("1", "ownerKey", "TEXT", "1", null, "0"),
                    listOf("2", "objectId", "TEXT", "1", null, "0"),
                    listOf("3", "depotId", "TEXT", "1", null, "0"),
                    listOf("4", "payload", "TEXT", "1", null, "0"),
                    listOf("5", "sha256", "TEXT", "1", null, "0"),
                ),
                rows(db, "PRAGMA table_info(field_control_bases)"),
            )
            val declaredIndices = db.query("PRAGMA index_list(field_control_bases)").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        if (!name.startsWith("sqlite_autoindex_")) {
                            add(name to cursor.getInt(cursor.getColumnIndexOrThrow("unique")))
                        }
                    }
                }
            }
            assertEquals(listOf(INDEX_NAME to 0), declaredIndices)
            assertEquals(
                listOf(listOf("0", "1", "ownerKey"), listOf("1", "2", "objectId")),
                rows(db, "PRAGMA index_info($INDEX_NAME)"),
            )
            assertEquals(
                listOf(listOf("0", "0", "field_entries", "controlEntryId", "id", "NO ACTION", "CASCADE", "NONE")),
                rows(db, "PRAGMA foreign_key_list(field_control_bases)"),
            )
            assertEquals(listOf(listOf("1")), rows(db, "PRAGMA foreign_keys"))

            val values = arrayOf<Any?>("TEST-legacy-control", OWNER_A, OBJECT_ID, "TEST-depot", PAYLOAD, HASH)
            for (column in values.indices) {
                val withNull = values.copyOf().also { it[column] = null }
                expectConstraintFailure { db.execSQL(INSERT_BASIS, withNull) }
            }
            expectConstraintFailure {
                db.execSQL(INSERT_BASIS, values.copyOf().also { it[0] = "TEST-missing-parent" })
            }
            assertEquals(0, count(db))
            assertEquals(
                listOf(listOf(PAYLOAD, HASH)),
                rows(db, "SELECT payload, sha256 FROM field_entries WHERE id = 'TEST-legacy-control'"),
            )
            assertForeignKeysValid(db)
            // Aucun export 47 n'est inventé ; Room validera le schéma courant à l'ouverture.
            db.version = 47
        }

        val current = open()
        try {
            assertEquals(49, current.openHelper.writableDatabase.version)
            assertEquals(PAYLOAD, current.fieldDao().entry("TEST-legacy-control")?.payload)
            assertEquals(HASH, current.fieldDao().entry("TEST-legacy-control")?.sha256)
            assertTrue(current.fieldControlBasisDao().forObject(OWNER_A, OBJECT_ID).isEmpty())
            assertForeignKeysValid(current.openHelper.writableDatabase)
        } finally { current.close() }
    }

    @Test
    fun dao_isolates_owners_refuses_snapshot_replacement_and_cascades_only_the_deleted_parent() = runBlocking {
        helper.createDatabase(DB_NAME, 46).use { db ->
            ControlBasisMigrations.MIGRATION_46_47.migrate(db)
            db.version = 47
        }
        var current = open()
        try {
            val first = basis("TEST-control-A-1", OWNER_A)
            val second = basis("TEST-control-A-2", OWNER_A)
            val foreign = basis("TEST-control-B", OWNER_B)
            val otherObject = basis("TEST-other-object", OWNER_A).copy(objectId = "TEST-other-object")
            for (snapshot in listOf(second, foreign, otherObject, first)) {
                current.fieldDao().insertEntry(FieldEntryEntity(
                    id = snapshot.controlEntryId,
                    ownerKey = snapshot.ownerKey,
                    targetKind = "WOOD_OBJECT",
                    targetId = snapshot.objectId,
                    payload = "TEST unchanged field entry",
                    sha256 = "TEST entry hash",
                    recordedAt = 10L,
                    author = "TEST-author",
                ))
                current.fieldControlBasisDao().insert(snapshot)
            }
            val dao = current.fieldControlBasisDao()
            assertEquals(first, dao.byControlEntryId(OWNER_A, first.controlEntryId))
            assertNull(dao.byControlEntryId(OWNER_B, first.controlEntryId))
            assertNull(dao.byControlEntryId(OWNER_A, foreign.controlEntryId))
            assertNull(dao.byControlEntryId("TEST-unknown-owner", first.controlEntryId))
            assertEquals(listOf(first, second), dao.forObject(OWNER_A, OBJECT_ID))
            assertEquals(listOf(foreign), dao.forObject(OWNER_B, OBJECT_ID))
            assertEquals(listOf(otherObject), dao.forObject(OWNER_A, otherObject.objectId))
            assertTrue(dao.forObject("TEST-unknown-owner", OBJECT_ID).isEmpty())

            expectConstraintFailure { dao.insert(first) }
            expectConstraintFailure {
                dao.insert(first.copy(ownerKey = OWNER_B, payload = "TEST forbidden replacement", sha256 = "TEST changed hash"))
            }
            expectConstraintFailure { dao.insert(first.copy(controlEntryId = "TEST-orphan")) }
            assertEquals(first, dao.byControlEntryId(OWNER_A, first.controlEntryId))
            assertEquals(listOf(foreign), dao.forObject(OWNER_B, OBJECT_ID))

            current.close()
            current = open()
            val reopened = current.fieldControlBasisDao()
            assertEquals(first, reopened.byControlEntryId(OWNER_A, first.controlEntryId))
            current.openHelper.writableDatabase.execSQL(
                "DELETE FROM field_entries WHERE id = ? AND ownerKey = ?",
                arrayOf(first.controlEntryId, OWNER_A),
            )
            assertNull(reopened.byControlEntryId(OWNER_A, first.controlEntryId))
            assertEquals(listOf(second), reopened.forObject(OWNER_A, OBJECT_ID))
            assertEquals(listOf(foreign), reopened.forObject(OWNER_B, OBJECT_ID))
            assertEquals(listOf(otherObject), reopened.forObject(OWNER_A, otherObject.objectId))
            assertForeignKeysValid(current.openHelper.writableDatabase)

            reopened.deleteAllForPurge()
            assertTrue(reopened.forObject(OWNER_A, OBJECT_ID).isEmpty())
            assertTrue(reopened.forObject(OWNER_B, OBJECT_ID).isEmpty())
            assertTrue(reopened.forObject(OWNER_A, otherObject.objectId).isEmpty())
            assertNotNull(current.fieldDao().entry(second.controlEntryId))
            assertNotNull(current.fieldDao().entry(foreign.controlEntryId))
        } finally { current.close() }
    }

    private fun factory() = SupportFactory("TEST-only-control-basis-migration".toByteArray(), null, false)

    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
        .openHelperFactory(factory())
        .addMigrations(*DatabaseMigrations.ALL)
        .build()

    private fun basis(id: String, ownerKey: String) = FieldControlBasisEntity(
        controlEntryId = id,
        ownerKey = ownerKey,
        objectId = OBJECT_ID,
        depotId = "TEST-depot",
        payload = PAYLOAD,
        sha256 = HASH,
    )

    private suspend fun expectConstraintFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("La contrainte SQL devait refuser cette insertion TEST")
        } catch (_: SQLiteConstraintException) {
            // NOT NULL, clé primaire ou parent manquant : aucune capture n'est remplacée.
        }
    }

    private fun assertForeignKeysValid(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
    }

    private fun count(db: SupportSQLiteDatabase): Int =
        db.query("SELECT COUNT(*) FROM field_control_bases").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun rows(db: SupportSQLiteDatabase, query: String): List<List<String?>> =
        db.query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { index ->
                        if (cursor.isNull(index)) null else cursor.getString(index)
                    })
                }
            }
        }

    private companion object {
        const val DB_NAME = "TEST-control-basis-46-47.db"
        const val OWNER_A = "TEST-owner-A"
        const val OWNER_B = "TEST-owner-B"
        const val OBJECT_ID = "TEST-shared-object"
        const val PAYLOAD = " { \"TEST\": \"référence figée\", \"quantity\": \"17.5000\", \"nullable\": null } "
        const val HASH = "TEST opaque hash"
        const val INDEX_NAME = "index_field_control_bases_ownerKey_objectId"
        const val INSERT_BASIS = "INSERT INTO field_control_bases(controlEntryId,ownerKey,objectId,depotId,payload,sha256) VALUES(?,?,?,?,?,?)"
    }
}
