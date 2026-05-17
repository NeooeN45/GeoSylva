package com.forestry.counter.data.sylviculture

import com.forestry.counter.data.local.dao.FertiliteEssenceSerDao
import com.forestry.counter.data.local.dao.ProjectionClimatiqueSerDao

/**
 * Seeder des données embarquées sylvicoles (fertilité + projections climatiques).
 * À appeler une seule fois au démarrage si les tables sont vides.
 */
class SylvicultureDataSeeder(
    private val fertiliteDao: FertiliteEssenceSerDao,
    private val projectionDao: ProjectionClimatiqueSerDao
) {
    suspend fun seedIfEmpty() {
        seedFertilite()
        seedProjections()
    }

    private suspend fun seedFertilite() {
        if (fertiliteDao.count() > 0) return
        val data = FertiliteEssenceSerData.buildAll()
        fertiliteDao.insertAll(data)
    }

    private suspend fun seedProjections() {
        if (projectionDao.count() > 0) return
        val data = ProjectionClimatiqueSerData.buildAll()
        projectionDao.insertAll(data)
    }
}
