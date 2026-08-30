package com.forestry.counter.domain.model

data class Group(
    val id: String,
    val name: String,
    val color: String? = null,
    val sortIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val counterCount: Int = 0,
    val totalValue: Double = 0.0,
    /** ID de la Forêt liée (null pour les groupes de martelage classiques). */
    val foretId: String? = null,
)
