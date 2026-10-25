package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.data.adblock.FilterEngine
import io.github.omeg4dev.fmhy.data.adblock.FilterListProgress
import io.github.omeg4dev.fmhy.data.adblock.FilterListUpdater
import io.github.omeg4dev.fmhy.data.adblock.ListState

data class FailedList(val title: String, val lastFetchedAt: Long?)

data class AdblockStatus(
    val ready: Boolean = false,
    val updating: Boolean = false,
    val ruleCount: Int = 0,
    val lastUpdated: Long? = null,
    val error: String? = null,
    val step: FilterListProgress? = null,
    val failedLists: List<FailedList> = emptyList(),
    val cancelled: Boolean = false,
    val userStarted: Boolean = false,
    val totalLists: Int = 0,
)

interface AdblockUpdater {
    suspend fun update(
        enabled: Set<String>,
        customRules: String,
        force: Boolean,
        onProgress: (FilterListProgress) -> Unit = {},
    ): FilterEngine
    suspend fun rebuild(
        enabled: Set<String>,
        customRules: String,
        onProgress: (FilterListProgress) -> Unit = {},
    ): FilterEngine
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
        onProgress: (FilterListProgress) -> Unit,
    ): FilterEngine = updater.update(enabled, customRules, force, onProgress)

    override suspend fun rebuild(
        enabled: Set<String>,
        customRules: String,
        onProgress: (FilterListProgress) -> Unit,
    ): FilterEngine = updater.rebuild(enabled, customRules, onProgress)

    override fun loadEngine(): FilterEngine? = updater.loadEngine()

    override fun states(): Map<String, ListState> = updater.states()
}
