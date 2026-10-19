package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.forestry.counter.data.local.entity.*

@Dao interface ReceiptDao {
    @Query("SELECT * FROM field_receipts WHERE siteId=:site AND ownerKey=:owner ORDER BY id") suspend fun receipts(site:String,owner:String):List<FieldReceiptEntity>
    @Query("SELECT * FROM field_receipts WHERE id=:id") suspend fun receipt(id:String):FieldReceiptEntity?
    @Query("SELECT * FROM field_receipt_versions WHERE receiptId=:id ORDER BY revision") suspend fun versions(id:String):List<FieldReceiptVersionEntity>
    @Query("SELECT * FROM field_receipt_versions WHERE receiptId=:id AND revision=:revision") suspend fun version(id:String,revision:Int):FieldReceiptVersionEntity?
    @Insert suspend fun insertReceipt(row:FieldReceiptEntity)
    @Insert suspend fun insertVersion(row:FieldReceiptVersionEntity)
    @Query("UPDATE field_receipts SET revision=:next WHERE id=:id AND ownerKey=:owner AND revision=:expected") suspend fun advance(id:String,owner:String,expected:Int,next:Int):Int
    @Insert suspend fun insertCredit(row:FieldReceiptCreditEntity)
    @Query("SELECT * FROM field_receipt_credits WHERE receiptId=:id") suspend fun credits(id:String):List<FieldReceiptCreditEntity>
    @Query("SELECT * FROM field_receipt_credits WHERE objectId=:id") suspend fun creditForObject(id:String):FieldReceiptCreditEntity?
    @Query("SELECT COUNT(*) FROM field_receipt_credits WHERE entryId=:id OR cancellationEntryId=:id") suspend fun protectedEntry(id:String):Int
    @Query("UPDATE field_receipt_credits SET cancellationEntryId=:entry WHERE receiptId=:id AND lineId=:line AND cancellationEntryId IS NULL") suspend fun setCancellation(id:String,line:String,entry:String):Int
    @Insert suspend fun insertCommand(row:FieldReceiptCommandEntity)
    @Query("SELECT * FROM field_receipt_commands WHERE id=:id") suspend fun command(id:String):FieldReceiptCommandEntity?
}
