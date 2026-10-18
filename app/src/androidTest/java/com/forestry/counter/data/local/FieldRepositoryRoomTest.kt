package com.forestry.counter.data.local

import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.repository.*
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FieldRepositoryRoomTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db:ForestryDatabase
    private lateinit var identity:BusinessReadIdentityFixture
    private lateinit var repository:FieldRepositoryImpl
    private lateinit var access:FieldAccess
    private val depot=UUID.randomUUID()
    private val wood=UUID.randomUUID()
    private val target get()=FieldTarget(FieldTargetKind.WOOD_OBJECT,wood.toString())
    @Before fun setup()=runBlocking<Unit> {
        context.deleteDatabase(DB)
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        db=open();identity=BusinessReadIdentityFixture()
        val depots=WoodDepotRepositoryImpl(db,identity)
        depots.createDepot(depot.toString(),WoodDepotDraft("TEST dépôt terrain",WoodDepotKind.ROADSIDE),depots.captureAccess())
        val woods=WoodObjectRepositoryImpl(db,identity)
        woods.execute(WoodObjectCommand.Create(command(),WoodObjectKind.PILE,WoodObjectHeader("TEST Pile 01")),woods.captureAccess())
        repository=repo();access=repository.captureAccess()
    }
    private fun open()=Room.databaseBuilder(context,ForestryDatabase::class.java,DB).openHelperFactory(net.sqlcipher.database.SupportFactory("TEST-only-field-storage".toByteArray())).addMigrations(*DatabaseMigrations.ALL).build()
    private fun repo()=FieldRepositoryImpl(db,identity,BusinessReadAccess(identity,db.parcelSyncDao()))
    private fun command()=WoodCommandContext(UUID.randomUUID(),depot,wood,WoodOwner(WoodDepotOwnerScope.LOCAL_DEVICE),"anonymous")
    private fun quantity(kind:FieldEntryKind,amount:String,time:String="18/10/2026 10:00")=FieldEntryForm(kind,title="TEST",observed=time,zone="Europe/Paris",quantity=amount,unit=FieldQuantityUnit.APPARENT_M3,convention="TEST enveloppe")
    private suspend fun add(form:FieldEntryForm)=repository.append(UUID.randomUUID().toString(),target,form,access)
    private suspend fun reject(code:FieldError,action:suspend ()->Unit) {
        try {action();fail("Expected $code")} catch(e:FieldException) {assertEquals(code,e.code)}
    }
    @After fun cleanup() {db.close();context.deleteDatabase(DB)}

    @Test fun partial_pickups_retries_and_count_preserve_one_physical_balance()=runBlocking {
        add(quantity(FieldEntryKind.INITIAL_STOCK,"30"))
        val removal=add(quantity(FieldEntryKind.REMOVAL,"7,5","18/10/2026 11:00"))
        repository.append(removal.id,target,removal.form,access)
        add(quantity(FieldEntryKind.REMOVAL,"5","18/10/2026 12:00"))
        val count=add(quantity(FieldEntryKind.REMAINING,"17","18/10/2026 13:00").copy(remainder=FieldRemainder.BOTTOM))
        val all=repository.entries(target,access)
        assertEquals(4,all.size)
        val balance=FieldBalance.at(all,checkNotNull(count.form.observedInstant()),FieldQuantityUnit.APPARENT_M3,"TEST enveloppe")
        assertEquals(0,checkNotNull(balance.expected).compareTo(java.math.BigDecimal("17.5")))
        assertEquals(0,checkNotNull(balance.difference).compareTo(java.math.BigDecimal("-0.5")))
    }
    @Test fun late_base_correction_cannot_orphan_prior_removals_in_another_ledger()=runBlocking {
        val base=add(quantity(FieldEntryKind.INITIAL_STOCK,"30"));add(quantity(FieldEntryKind.REMOVAL,"5","18/10/2026 11:00"))
        reject(FieldError.INVALID) {add(base.form.copy(unit=FieldQuantityUnit.TONNE,correctionOf=base.id,correctionReason="TEST units"))}
        assertEquals(2,repository.entries(target,access).size)
    }
    @Test fun negative_stock_and_duplicate_base_are_rejected_atomically()=runBlocking {
        add(quantity(FieldEntryKind.INITIAL_STOCK,"10"))
        reject(FieldError.INVALID) {add(quantity(FieldEntryKind.REMOVAL,"11","18/10/2026 12:00"))}
        reject(FieldError.INVALID) {add(quantity(FieldEntryKind.INITIAL_STOCK,"10"))}
        assertEquals(1,repository.entries(target,access).size)
    }
    @Test fun draft_reopen_and_competing_editor_do_not_overwrite_each_other()=runBlocking {
        val draft=repository.saveDraft(target,"test-form",null,"TEST incomplete raw 2,",access)
        db.close();db=open();repository=repo();access=repository.captureAccess()
        assertEquals(draft,repository.loadDraft(target,"test-form",access))
        repository.saveDraft(target,"test-form",draft.revision,"TEST edit A",access)
        reject(FieldError.CONFLICT) {repository.saveDraft(target,"test-form",draft.revision,"TEST edit B",access)}
        assertEquals("TEST edit A",repository.loadDraft(target,"test-form",access)?.content)
    }
    @Test fun account_switch_cannot_replay_a_local_form_or_read_local_photo()=runBlocking {
        val old=access
        identity.login("TEST B")
        reject(FieldError.ACCESS_CHANGED) {repository.append(UUID.randomUUID().toString(),target,FieldEntryForm(title="TEST stale"),old)}
        val b=repository.captureAccess()
        reject(FieldError.UNAVAILABLE) {repository.entries(target,b)}
    }
    @Test fun multiple_parcels_links_archive_and_restore_retain_history_and_block_writes()=runBlocking {
        val form=WorksiteForm("TEST chantier",parcels=listOf(WorksiteParcel("ONF","A-1","TEST ONF"),WorksiteParcel("CADASTRE","79000:AB:12","TEST cadastre")))
        val site=repository.saveSite(UUID.randomUUID().toString(),form,0,false,access)
        repository.link(site.id,FieldTarget(FieldTargetKind.WOOD_DEPOT,depot.toString()),access)
        repository.link(site.id,target,access)
        add(FieldEntryForm(title="TEST avant"))
        val archived=repository.saveSite(site.id,site.form,site.revision,true,access)
        assertFalse(repository.writable(target,access));assertEquals(1,repository.entries(target,access).size)
        reject(FieldError.UNAVAILABLE) {add(FieldEntryForm(title="TEST forbidden"))}
        val woods=WoodObjectRepositoryImpl(db,identity)
        try {woods.execute(WoodObjectCommand.UpdateHeader(command(),1,WoodObjectHeader("TEST forbidden")),woods.captureAccess());fail()} catch(e:WoodRepositoryException) {assertEquals(WoodRepositoryError.UNAVAILABLE,e.code)}
        repository.saveSite(site.id,archived.form,archived.revision,false,access)
        assertTrue(repository.writable(target,access));assertEquals(2,repository.linked(site.id,access).size)
    }
    @Test fun forestry_journal_is_separate_from_wood_and_preserves_legacy_identifiers()=runBlocking {
        db.openHelper.writableDatabase.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('legacy-forest','TEST forêt','TEST owner',10,20)")
        repository=repo();access=repository.captureAccess()
        val forest=FieldTarget(FieldTargetKind.FOREST,"legacy-forest")
        repository.append(UUID.randomUUID().toString(),forest,FieldEntryForm(FieldEntryKind.ACCESS,title="TEST accès",restriction="TEST barrière",latitude="46.1",longitude="-0.4"),access)
        assertEquals("TEST barrière",repository.entries(forest,access).single().form.restriction)
        assertTrue(repository.entries(target,access).isEmpty())
        try {repository.append(UUID.randomUUID().toString(),forest,quantity(FieldEntryKind.INITIAL_STOCK,"20"),access);fail()} catch(_:IllegalArgumentException) { }
    }
    @Test fun encrypted_original_photo_survives_reopen_and_purge_invalidates_old_access()=runBlocking {
        val bitmap=Bitmap.createBitmap(1200,1000,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(1200*1000) {index->0xff000000.toInt() or ((index*7919) and 0x00ffffff)}
        bitmap.setPixels(pixels,0,1200,0,0,1200,1000)
        val bytes=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.JPEG,96,it)}.toByteArray();bitmap.recycle()
        assertTrue(bytes.size>128*1024)
        val photo=repository.addPhoto(target,bytes,"image/jpeg","TEST état avant",access)
        add(FieldEntryForm(FieldEntryKind.BEFORE,title="TEST état",photoIds=listOf(photo.id)))
        db.close();db=open();repository=repo();access=repository.captureAccess()
        assertArrayEquals(bytes,repository.photo(photo.id,target,access).second)
        val before=access
        db.woodDataGate.purge {db.withTransaction {
            db.fieldDao().deleteChunks();db.fieldDao().deletePhotos();db.fieldDao().deleteDrafts();db.fieldDao().deleteEntries();db.fieldDao().deleteLinks();db.fieldDao().deleteSites()
        }}
        reject(FieldError.ACCESS_CHANGED) {repository.photo(photo.id,target,before)}
        assertTrue(db.fieldDao().chunks(photo.id).isEmpty())
    }
    @Test fun same_correction_cannot_be_forked_or_reused_on_another_object()=runBlocking {
        val original=add(FieldEntryForm(title="TEST original"))
        add(original.form.copy(title="TEST correction",correctionOf=original.id,correctionReason="TEST typo"))
        reject(FieldError.CONFLICT) {add(original.form.copy(title="TEST conflicting",correctionOf=original.id,correctionReason="TEST other"))}
        assertEquals(2,repository.entries(target,access).size)
    }
    companion object {private const val DB="TEST-field-repository.db"}
}
