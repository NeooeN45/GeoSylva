package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive storage. Measuring wood never inserts a stock movement. */
object WoodObjectMigrations {
    val MIGRATION_43_44 = object : Migration(43, 44) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `wood_objects` (
                `objectId` TEXT NOT NULL, `depotId` TEXT NOT NULL, `ownerScope` TEXT NOT NULL, `ownerAccountId` TEXT,
                `kind` TEXT NOT NULL, `headerJson` TEXT NOT NULL, `headerSha256` TEXT NOT NULL, `revision` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL,
                `updatedBy` TEXT NOT NULL, `archivedAt` INTEGER, PRIMARY KEY(`objectId`),
                FOREIGN KEY(`depotId`) REFERENCES `wood_depots`(`depotId`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wood_objects_depotId` ON `wood_objects` (`depotId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wood_objects_ownerScope_ownerAccountId_archivedAt` ON `wood_objects` (`ownerScope`,`ownerAccountId`,`archivedAt`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `wood_observations` (
                `observationId` TEXT NOT NULL, `objectId` TEXT NOT NULL, `rootObservationId` TEXT NOT NULL,
                `observationRevision` INTEGER NOT NULL, `correctionOfObservationId` TEXT, `correctionReason` TEXT,
                `payloadJson` TEXT NOT NULL, `payloadSha256` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL,
                `recordedZoneId` TEXT NOT NULL, `recordedOffsetSeconds` INTEGER NOT NULL, `recordedBy` TEXT NOT NULL,
                PRIMARY KEY(`observationId`),
                FOREIGN KEY(`objectId`) REFERENCES `wood_objects`(`objectId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`objectId`,`rootObservationId`) REFERENCES `wood_observations`(`objectId`,`observationId`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
                FOREIGN KEY(`objectId`,`correctionOfObservationId`) REFERENCES `wood_observations`(`objectId`,`observationId`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wood_observations_objectId_observationId` ON `wood_observations` (`objectId`,`observationId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wood_observations_objectId_rootObservationId_observationRevision` ON `wood_observations` (`objectId`,`rootObservationId`,`observationRevision`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wood_observations_correctionOfObservationId` ON `wood_observations` (`correctionOfObservationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wood_observations_objectId_correctionOfObservationId` ON `wood_observations` (`objectId`,`correctionOfObservationId`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `wood_effective_observations` (
                `objectId` TEXT NOT NULL, `observationId` TEXT NOT NULL, `selectedAt` INTEGER NOT NULL,
                `selectedBy` TEXT NOT NULL, `commandId` TEXT NOT NULL, PRIMARY KEY(`objectId`),
                FOREIGN KEY(`objectId`) REFERENCES `wood_objects`(`objectId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`objectId`,`observationId`) REFERENCES `wood_observations`(`objectId`,`observationId`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wood_effective_observations_objectId_observationId` ON `wood_effective_observations` (`objectId`,`observationId`)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `wood_commands` (
                `commandId` TEXT NOT NULL, `depotId` TEXT NOT NULL, `objectId` TEXT NOT NULL, `ownerScope` TEXT NOT NULL,
                `ownerAccountId` TEXT, `actorId` TEXT NOT NULL, `commandJson` TEXT NOT NULL, `commandSha256` TEXT NOT NULL,
                `appliedObjectRevision` INTEGER NOT NULL, `createdObservationId` TEXT, `effectiveObservationId` TEXT,
                `appliedAt` INTEGER NOT NULL, PRIMARY KEY(`commandId`),
                FOREIGN KEY(`objectId`) REFERENCES `wood_objects`(`objectId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`depotId`) REFERENCES `wood_depots`(`depotId`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wood_commands_objectId` ON `wood_commands` (`objectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wood_commands_depotId` ON `wood_commands` (`depotId`)")
        }
    }
}
