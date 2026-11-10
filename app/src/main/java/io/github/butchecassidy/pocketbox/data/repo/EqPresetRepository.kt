// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.repo

import io.github.butchecassidy.pocketbox.data.db.EqPresetEntity
import io.github.butchecassidy.pocketbox.data.db.PocketBoxDatabase
import io.github.butchecassidy.pocketbox.domain.audio.EqPreset
import io.github.butchecassidy.pocketbox.domain.audio.EqPresets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Built-in equalizer presets followed by the user's saved ones. */
class EqPresetRepository(db: PocketBoxDatabase, private val io: CoroutineDispatcher) {
    private val dao = db.playbackDao()

    val presets: Flow<List<EqPreset>> = dao.eqPresets().map { saved ->
        EqPresets.builtIn + saved.mapNotNull { entity ->
            val bands = entity.bandsDb.split(',').mapNotNull { it.toFloatOrNull() }
            if (bands.size == EqPresets.bandCentersHz.size) EqPreset(entity.name, bands, isBuiltIn = false, id = entity.id) else null
        }
    }

    suspend fun save(name: String, bandsDb: List<Float>) = withContext(io) {
        val clean = name.trim().take(MAX_NAME).ifEmpty { return@withContext }
        dao.saveEqPreset(EqPresetEntity(name = clean, bandsDb = bandsDb.joinToString(",")))
    }

    suspend fun delete(id: Long) = withContext(io) { dao.deleteEqPreset(id) }

    private companion object {
        const val MAX_NAME = 40
    }
}
