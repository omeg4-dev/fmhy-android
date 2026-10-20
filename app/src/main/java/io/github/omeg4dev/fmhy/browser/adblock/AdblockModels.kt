package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.data.adblock.FilterEngine
import io.github.omeg4dev.fmhy.data.adblock.FilterListUpdater
import io.github.omeg4dev.fmhy.data.adblock.ListState

data class AdblockStatus(
    val ready: Boolean = false,
    val updating: Boolean = false,
    val ruleCount: Int = 0,
    val lastUpdated: Long? = null,
    val error: String? = null,
    /** 0..1 while lists download (null when not updating). */
    val progress: Float? = null,
)

interface AdblockUpdater {
    suspend fun update(
        enabled: Set<String>,
        customRules: String,
        force: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): FilterEngine
    suspend fun rebuild(enabled: Set<String>, customRules: String): FilterEngine
    fun loadEngine(): FilterEngine?
    fun states(): Map<String, ListState>
}

class DefaultAdblockUpdater(
    private val updater: FilterListUpdater
) : AdblockUpdater {
    override suspend fun update(
        enabled: Set<String>,
        customRules: String,
        force: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): FilterEngine = updater.update(enabled, customRules, force, onProgress)

    override suspend fun rebuild(enabled: Set<String>, customRules: String): FilterEngine =
        updater.rebuild(enabled, customRules)

    override fun loadEngine(): FilterEngine? = updater.loadEngine()

    override fun states(): Map<String, ListState> = updater.states()
}
