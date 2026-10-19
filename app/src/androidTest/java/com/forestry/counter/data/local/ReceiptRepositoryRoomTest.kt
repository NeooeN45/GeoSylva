package com.forestry.counter.data.local

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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Synthetic TEST data only. Actual SQLCipher, gate and Room transactions. */
@RunWith(AndroidJUnit4::class)
class ReceiptRepositoryRoomTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db:ForestryDatabase
    private lateinit var identity:BusinessReadIdentityFixture
    private lateinit var repo:FieldRepositoryImpl
    private lateinit var access:FieldAccess
    private lateinit var site:SavedWorksite
    private val depot=UUID.randomUUID()
    private val wood=UUID.randomUUID()
    private val observationId=UUID.randomUUID()
    private val target get()=FieldTarget(FieldTargetKind.WOOD_OBJECT,wood.toString())
    private fun id()=UUID.randomUUID().toString()
    private fun command()=WoodCommandContext(UUID.randomUUID(),depot,wood,WoodOwner(WoodDepotOwnerScope.LOCAL_DEVICE),"anonymous")
    private fun observation(id:UUID=observationId,length:String="10")=NewWoodObservation(id,WoodObservationPayload(
        RawWoodMeasurement.Pile(RawWoodDimension(length,"m"),RawWoodDimension("5","m"),RawWoodDimension("2","m"),
            RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE,"TEST enveloppe",RawWoodDimension("2","m")),
        WoodObservedTime(Instant.parse("2026-10-18T08:00:00Z"),"Europe/Paris",7200)))
    private fun open()=Room.databaseBuilder(context,ForestryDatabase::class.java,DB)
        .openHelperFactory(net.sqlcipher.database.SupportFactory("TEST-only-receipts-key".toByteArray()))
        .addMigrations(*DatabaseMigrations.ALL).build()
    private fun repository()=FieldRepositoryImpl(db,identity,BusinessReadAccess(identity,db.parcelSyncDao()))
    @Before fun setup()=runBlocking<Unit> {
        context.deleteDatabase(DB);net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        db=open();identity=BusinessReadIdentityFixture()
        val depots=WoodDepotRepositoryImpl(db,identity)
        depots.createDepot(depot.toString(),WoodDepotDraft("TEST dépôt",WoodDepotKind.ROADSIDE),depots.captureAccess())
        val woods=WoodObjectRepositoryImpl(db,identity)
        woods.execute(WoodObjectCommand.Create(command(),WoodObjectKind.PILE,WoodObjectHeader("TEST pile"),observation(),true),woods.captureAccess())
        repo=repository();access=repo.captureAccess()
        site=repo.saveSite(id(),WorksiteForm("TEST chantier",parcels=listOf(
            WorksiteParcel("ONF","TEST-P1","TEST parcelle ONF"),WorksiteParcel("CADASTRE","TEST-P2","TEST cadastre"))),0,false,access)
        repo.link(site.id,target,access)
    }
    @After fun cleanup() {db.close();context.deleteDatabase(DB)}
    private fun form()=ReceiptForm("TEST réception","18/10/2026 10:00","Europe/Paris",lines=listOf(
        ReceiptLine(objectId=wood.toString(),depotId=depot.toString(),observationId=observationId.toString(),observationRevision=1,
            quantity="100",unit=FieldQuantityUnit.APPARENT_M3,convention="TEST enveloppe",conventionVersion="1")))
    private suspend fun ready()=repo.saveReceipt(id(),site.id,form(),0,true,access)
    private suspend fun validate(r:SavedReceipt)=repo.validateReceipt(id(),r.id,r.revision,access)
    private suspend fun quantity(q:String,time:String="18/10/2026 11:00")=repo.append(id(),target,
        FieldEntryForm(FieldEntryKind.REMOVAL,title="TEST enlèvement",observed=time,zone="Europe/Paris",quantity=q,
            unit=FieldQuantityUnit.APPARENT_M3,convention="TEST enveloppe [v:1]"),access)
    private suspend fun reject(code:FieldError,block:suspend ()->Unit) {
        try {block();fail("Expected $code")} catch(e:FieldException) {assertEquals(code,e.code)}
    }
    private suspend fun balance()=FieldBalance.at(repo.entries(target,access),Instant.parse("2026-10-19T12:00:00Z").toEpochMilli(),
        FieldQuantityUnit.APPARENT_M3,"TEST enveloppe [v:1]").expected

    @Test fun draft_and_sources_survive_reopen_without_any_stock_effect()=runBlocking {
        val draft=repo.saveReceipt(id(),site.id,form().copy(lines=form().lines.map {it.copy(quantity="2,")}),0,false,access)
        assertTrue(repo.entries(target,access).isEmpty())
        db.close();db=open();repo=repository();access=repo.captureAccess()
        assertEquals(draft,repo.receipt(draft.id,access));assertEquals(2,repo.sites(access).single().form.parcels.size)
        assertEquals("2,",repo.receipt(draft.id,access).form.lines.single().quantity)
    }
    @Test fun validation_is_atomic_and_replay_survives_reopen_but_changed_command_conflicts()=runBlocking {
        val draft=ready();val command=id()
        val result=repo.validateReceipt(command,draft.id,draft.revision,access)
        db.close();db=open();repo=repository();access=repo.captureAccess()
        assertEquals(result,repo.validateReceipt(command,draft.id,draft.revision,access))
        assertEquals(1,repo.entries(target,access).size);assertEquals(BigDecimal("100"),balance())
        reject(FieldError.CONFLICT) {repo.validateReceipt(command,draft.id,result.revision,access)}
        assertEquals(2,repo.receiptHistory(draft.id,access).size)
    }
    @Test fun remesure_does_not_change_validated_receipt_or_stock()=runBlocking {
        val receipt=validate(ready())
        val woods=WoodObjectRepositoryImpl(db,identity)
        woods.execute(WoodObjectCommand.AddObservation(command(),1,observation(UUID.randomUUID(),"9.8"),true),woods.captureAccess())
        assertEquals(receipt,repo.receipt(receipt.id,access));assertEquals(BigDecimal("100"),balance())
        assertEquals(2,repo.exportSnapshot(site.id,access).objects.single().observations.size)
    }
    @Test fun partial_removals_and_replay_end_at_zero_and_overdraw_is_rejected()=runBlocking {
        validate(ready());val first=quantity("25")
        repo.append(first.id,target,first.form,access);assertEquals(BigDecimal("75"),balance())
        quantity("75","18/10/2026 12:00");assertEquals(BigDecimal.ZERO,balance())
        reject(FieldError.INVALID) {quantity("1","18/10/2026 13:00")}
        assertEquals(3,repo.entries(target,access).size)
    }
    @Test fun two_concurrent_receipts_can_only_credit_the_pile_once()=runBlocking {
        val first=ready();val second=ready()
        val results=listOf(first,second).map {r->async(Dispatchers.Default) {runCatching {validate(r)}}}.awaitAll()
        assertEquals(1,results.count {it.isSuccess});assertEquals(1,repo.entries(target,access).size)
    }
    @Test fun concurrent_overdrawing_removals_commit_only_one()=runBlocking {
        validate(ready())
        val results=(1..2).map {async(Dispatchers.Default) {runCatching {quantity("60")}}}.awaitAll()
        assertEquals(1,results.count {it.isSuccess});assertEquals(BigDecimal("40"),balance())
    }
    @Test fun existing_historical_base_is_not_received_again()=runBlocking {
        repo.append(id(),target,FieldEntryForm(FieldEntryKind.INITIAL_STOCK,title="TEST historical",observed="18/10/2026 10:00",zone="Europe/Paris",
            quantity="100",unit=FieldQuantityUnit.APPARENT_M3,convention="TEST old convention"),access)
        val r=ready();reject(FieldError.CONFLICT) {validate(r)}
        assertEquals(ReceiptStatus.READY,repo.receipt(r.id,access).status);assertEquals(1,repo.entries(target,access).size)
    }
    @Test fun receipt_credit_cannot_be_edited_via_generic_journal_correction()=runBlocking {
        validate(ready());val entry=repo.entries(target,access).single()
        reject(FieldError.CONFLICT) {repo.append(id(),target,entry.form.copy(quantity="200",correctionOf=entry.id,correctionReason="TEST bypass"),access)}
        assertEquals(BigDecimal("100"),balance())
    }
    @Test fun cancellation_keeps_history_zeroes_stock_and_cannot_be_replayed_with_another_reason()=runBlocking {
        val r=validate(ready());val command=id()
        val cancelled=repo.cancelReceipt(command,r.id,r.revision,"TEST mauvaise pile",access)
        assertEquals(ReceiptStatus.CANCELLED,cancelled.status);assertEquals(BigDecimal.ZERO,balance())
        assertEquals(cancelled,repo.cancelReceipt(command,r.id,r.revision,"TEST mauvaise pile",access))
        reject(FieldError.CONFLICT) {repo.cancelReceipt(command,r.id,r.revision,"TEST autre motif",access)}
        assertEquals(2,repo.entries(target,access).size)
        val correction=repo.entries(target,access).single {it.form.correctionOf != null}
        reject(FieldError.CONFLICT) {repo.append(id(),target,correction.form.copy(quantity="200",correctionOf=correction.id),access)}
    }
    @Test fun cancellation_after_removal_reports_dependencies_without_mutation()=runBlocking {
        val r=validate(ready());quantity("25")
        reject(FieldError.DEPENDENCIES) {repo.cancelReceipt(id(),r.id,r.revision,"TEST undo",access)}
        assertEquals(r,repo.receipt(r.id,access));assertEquals(BigDecimal("75"),balance())
    }
    @Test fun changed_source_requires_new_control_before_validation()=runBlocking {
        val r=ready();val woods=WoodObjectRepositoryImpl(db,identity)
        woods.execute(WoodObjectCommand.UpdateHeader(command(),1,WoodObjectHeader("TEST changed")),woods.captureAccess())
        reject(FieldError.CONFLICT) {validate(r)}
        assertTrue(repo.entries(target,access).isEmpty())
    }
    @Test fun archive_allows_read_and_export_but_rejects_new_validation()=runBlocking {
        val r=ready();repo.saveSite(site.id,site.form,site.revision,true,access)
        reject(FieldError.UNAVAILABLE) {validate(r)}
        assertEquals(r,repo.receipt(r.id,access));assertTrue(repo.exportSnapshot(site.id,access).site.archived)
    }
    @Test fun snapshot_has_exact_versions_observations_commands_physical_balance_and_account_isolation()=runBlocking {
        val r=validate(ready());quantity("25")
        val snapshot=repo.exportSnapshot(site.id,access)
        assertEquals(2,snapshot.receipts.size);assertEquals(r,snapshot.receipts.last())
        assertEquals("75",snapshot.balances.single().expected)
        assertEquals(2,snapshot.entries.size);assertEquals(1,snapshot.objects.single().history.size)
        identity.login("TEST another account")
        reject(FieldError.ACCESS_CHANGED) {repo.exportSnapshot(site.id,access)}
        reject(FieldError.UNAVAILABLE) {repo.exportSnapshot(site.id,repo.captureAccess())}
    }
    @Test fun purge_removes_receipts_before_restricting_ledger_and_invalidates_access()=runBlocking {
        val r=validate(ready());val old=access
        db.woodDataGate.purge {db.withTransaction {
            db.fieldDao().deleteReceiptCommands();db.fieldDao().deleteReceiptCredits()
            db.fieldDao().deleteReceiptVersions();db.fieldDao().deleteReceipts()
            db.fieldDao().deleteEntries();db.fieldDao().deleteLinks();db.fieldDao().deleteSites()
        }}
        reject(FieldError.ACCESS_CHANGED) {repo.receipt(r.id,old)}
        assertTrue(db.receiptDao().versions(r.id).isEmpty())
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
    }
    @Test fun one_invalid_line_rolls_back_every_credit_and_validation_version()=runBlocking {
        val otherWood=UUID.randomUUID();val otherObservation=UUID.randomUUID()
        val woods=WoodObjectRepositoryImpl(db,identity)
        woods.execute(WoodObjectCommand.Create(command().copy(objectId=otherWood),WoodObjectKind.PILE,
            WoodObjectHeader("TEST already stocked"),observation(otherObservation),true),woods.captureAccess())
        val otherTarget=FieldTarget(FieldTargetKind.WOOD_OBJECT,otherWood.toString());repo.link(site.id,otherTarget,access)
        repo.append(id(),otherTarget,FieldEntryForm(FieldEntryKind.INITIAL_STOCK,title="TEST historical",observed="18/10/2026 10:00",
            zone="Europe/Paris",quantity="10",unit=FieldQuantityUnit.TONNE,convention="TEST weight"),access)
        val baseForm=form()
        val two=baseForm.copy(lines=baseForm.lines+baseForm.lines.single().copy(id=id(),objectId=otherWood.toString(),observationId=otherObservation.toString()))
        val r=repo.saveReceipt(id(),site.id,two,0,true,access)
        reject(FieldError.CONFLICT) {validate(r)}
        assertTrue(repo.entries(target,access).isEmpty());assertEquals(ReceiptStatus.READY,repo.receipt(r.id,access).status)
        assertEquals(1,repo.receiptHistory(r.id,access).size)
    }
    @Test fun declared_new_unit_cannot_bypass_receipt_credit_protection()=runBlocking {
        validate(ready())
        reject(FieldError.CONFLICT) {repo.append(id(),target,FieldEntryForm(FieldEntryKind.INITIAL_STOCK,title="TEST other ledger",observed="18/10/2026 10:00",
            zone="Europe/Paris",quantity="80",unit=FieldQuantityUnit.TONNE,convention="TEST weight"),access)}
        assertEquals(1,repo.entries(target,access).size)
    }
    @Test fun gate_wait_does_not_allow_caller_to_mutate_frozen_draft_lines()=runBlocking {
        val mutableLines=form().lines.toMutableList();val input=form().copy(lines=mutableLines)
        lateinit var pending:Deferred<SavedReceipt>
        db.woodDataGate.withAccess(db.woodDataGate.capture()) {
            pending=async(start=CoroutineStart.UNDISPATCHED) {repo.saveReceipt(id(),site.id,input,0,false,access)}
            mutableLines.clear()
        }
        assertEquals(1,pending.await().form.lines.size)
    }
    @Test fun identity_change_inside_validation_rolls_back_credits_and_command()=runBlocking {
        val r=ready()
        val changing=FieldRepositoryImpl(db,identity,BusinessReadAccess(identity,db.parcelSyncDao())) {
            identity.login("TEST changed during transaction");System.currentTimeMillis()
        }
        val captured=changing.captureAccess()
        reject(FieldError.ACCESS_CHANGED) {changing.validateReceipt(id(),r.id,r.revision,captured)}
        identity.login(null);access=repo.captureAccess()
        assertEquals(r,repo.receipt(r.id,access));assertTrue(repo.entries(target,access).isEmpty())
    }
    @Test fun shared_depot_journal_is_not_inferred_to_belong_to_the_exported_worksite()=runBlocking {
        repo.link(site.id,FieldTarget(FieldTargetKind.WOOD_DEPOT,depot.toString()),access)
        repo.append(id(),FieldTarget(FieldTargetKind.WOOD_DEPOT,depot.toString()),FieldEntryForm(title="TEST another site detail"),access)
        val snapshot=repo.exportSnapshot(site.id,access)
        assertEquals(1,snapshot.depots.size);assertTrue(snapshot.entries.isEmpty())
    }
    @Test fun protected_ids_include_credit_and_cancellation_but_are_scoped_to_authorized_target()=runBlocking {
        val r=validate(ready())
        assertEquals(setOf(r.credits.single().entryId),repo.protectedEntries(target,access))
        val cancelled=repo.cancelReceipt(id(),r.id,r.revision,"TEST cancel",access)
        assertEquals(setOf(cancelled.credits.single().entryId,cancelled.credits.single().cancellationEntryId),repo.protectedEntries(target,access))
        identity.login("TEST another account")
        reject(FieldError.UNAVAILABLE) {repo.protectedEntries(target,repo.captureAccess())}
    }
    @Test fun inconsistent_object_owner_scope_is_rejected_even_under_local_depot()=runBlocking {
        db.openHelper.writableDatabase.execSQL("UPDATE wood_objects SET ownerScope='ORGANIZATION' WHERE objectId=?",arrayOf(wood.toString()))
        reject(FieldError.UNAVAILABLE) {repo.receiptWoodChoices(site.id,access)}
        reject(FieldError.UNAVAILABLE) {repo.exportSnapshot(site.id,access)}
        reject(FieldError.UNAVAILABLE) {repo.protectedEntries(target,access)}
    }
    companion object {private const val DB="TEST-receipts-room.db"}
}
