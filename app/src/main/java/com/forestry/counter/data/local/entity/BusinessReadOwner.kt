package com.forestry.counter.data.local.entity

/** Lightweight Room projection. This is not a persisted table and needs no schema migration. */
data class BusinessReadOwner(val scope: String, val objectId: String, val accountId: String)
data class BusinessReadParcelParent(val parcelId: String, val forestId: String?, val legacyGroupId: String? = null)
