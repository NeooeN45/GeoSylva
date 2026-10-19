package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName="field_receipts", foreignKeys=[ForeignKey(entity=FieldWorksiteEntity::class,
    parentColumns=["id"],childColumns=["siteId"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["siteId"]),Index(value=["ownerKey"])])
data class FieldReceiptEntity(@PrimaryKey val id: String,val ownerKey: String,val siteId: String,val revision: Int)

@Entity(tableName="field_receipt_versions",primaryKeys=["receiptId","revision"],
    foreignKeys=[ForeignKey(entity=FieldReceiptEntity::class,parentColumns=["id"],childColumns=["receiptId"],onDelete=ForeignKey.RESTRICT)])
data class FieldReceiptVersionEntity(val receiptId: String,val revision: Int,val payload: String,val sha256: String)

@Entity(tableName="field_receipt_credits",primaryKeys=["receiptId","revision","lineId"],
    foreignKeys=[
        ForeignKey(entity=FieldReceiptVersionEntity::class,parentColumns=["receiptId","revision"],childColumns=["receiptId","revision"],onDelete=ForeignKey.RESTRICT),
        ForeignKey(entity=FieldEntryEntity::class,parentColumns=["id"],childColumns=["entryId"],onDelete=ForeignKey.RESTRICT),
        ForeignKey(entity=FieldEntryEntity::class,parentColumns=["id"],childColumns=["cancellationEntryId"],onDelete=ForeignKey.RESTRICT),
        ForeignKey(entity=WoodObjectEntity::class,parentColumns=["objectId"],childColumns=["objectId"],onDelete=ForeignKey.RESTRICT),
    ],indices=[Index(value=["entryId"],unique=true),Index(value=["cancellationEntryId"],unique=true),Index(value=["objectId"],unique=true)])
data class FieldReceiptCreditEntity(val receiptId: String,val revision: Int,val lineId: String,val objectId: String,
    val entryId: String,val cancellationEntryId: String?)

@Entity(tableName="field_receipt_commands",foreignKeys=[ForeignKey(entity=FieldReceiptVersionEntity::class,
    parentColumns=["receiptId","revision"],childColumns=["receiptId","resultRevision"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["receiptId","resultRevision"])])
data class FieldReceiptCommandEntity(@PrimaryKey val id: String,val ownerKey: String,val receiptId: String,
    val payload: String,val sha256: String,val resultRevision: Int)
