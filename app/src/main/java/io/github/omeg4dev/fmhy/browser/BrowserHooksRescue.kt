package io.github.omeg4dev.fmhy.browser

/** Dead-site rescue decision and state builders for [DefaultBrowserHooks]. */

import android.net.Uri
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun DefaultBrowserHooks.decideRescue(record: TabRecord, failed: FailedLoad) {
    val tm = tabManager
    val inGrace = tm?.inRestoreGrace(record) == true
    val elapsed = tm?.graceElapsedMs(record) ?: 0L
    val networkReady = tm?.isOnline() ?: true
    val isLookup = failed.isLookupFailure()

    val decision = RescueDecision.decideWithNetworkGrace(
        isForMainFrame = true,
        failingUrl = failed.url,
        currentUrls = listOf(record.url, record.sourceUrl),
        contentShown = record.contentShown,
        retriesDone = record.silentRetries,
        inGraceWindow = inGrace,
        networkReady = networkReady,
        graceElapsedMs = elapsed,
        isLookupFailure = isLookup,
    )
    val action = if (failed.kind == LoadFailureKind.PROXY && decision != RescueDecision.Action.IGNORE) {
        RescueDecision.Action.SHOW
    } else {
        decision
    }
    rescueLog("decide $action url=${failed.url} tab=${record.url} shown=${record.contentShown} retries=${record.silentRetries} grace=$inGrace ($failed)")
    when (action) {
        RescueDecision.Action.IGNORE -> {
            record.isWaitingRetry = false
            record.isWaitingGraceRetry = false
        }
        RescueDecision.Action.RETRY -> {
            val scope = tabManager?.appScope ?: return
            val insideGrace = inGrace && isLookup && elapsed < RescueDecision.GRACE_MS
            record.isWaitingRetry = true
            record.isWaitingGraceRetry = insideGrace
            if (insideGrace && record.rescueState != null) {
                tabManager?.setRescueState(record.id, null)
            }
            record.rescueJob = scope.launch(Dispatchers.Main) {
                val delayMs = if (insideGrace) {
                    RescueDecision.graceRetryDelayMs(networkReady, elapsed)
                } else {
                    RescueDecision.retryDelayMs(record.silentRetries)
                }
                delay(delayMs)
                if (!insideGrace) {
                    record.silentRetries++
                    record.silentRetryUrl = failed.url
                }
                record.isWaitingRetry = false
                record.isWaitingGraceRetry = false
                record.webView?.reload()
            }
        }
        RescueDecision.Action.SHOW -> {
            record.isWaitingRetry = false
            record.isWaitingGraceRetry = false
            buildRescueState(
                record = record,
                failingUrl = failed.url,
                reason = failed.reason,
                kind = failed.kind,
            )
        }
    }
}

private fun DefaultBrowserHooks.adblockActive(record: TabRecord): Boolean {
    val tm = tabManager ?: return false
    return SiteFix.adblockActive(tm.adblockService.currentBlockingPrefs.adblock, record.sitePrefs)
}

internal fun DefaultBrowserHooks.buildRescueState(
    record: TabRecord,
    failingUrl: String,
    reason: String,
    kind: LoadFailureKind = LoadFailureKind.UNKNOWN,
    ignoreContentShown: Boolean = false,
) {
    val canSkipBlocking = kind != LoadFailureKind.OFFLINE && kind != LoadFailureKind.PROXY && adblockActive(record)
    // Same address already rescued (e.g. the user pressed Retry): update it in place, no flash.
    val current = record.rescueState
    if (current != null && RescueDecision.sameAddress(current.failingUrl, failingUrl)) {
        tabManager?.setRescueState(
            record.id,
            current.copy(
                reason = reason,
                retrying = false,
                kind = kind,
                canSkipBlocking = canSkipBlocking,
            )
        )
        return
    }
    val host = try { Uri.parse(failingUrl).host ?: failingUrl } catch (_: Exception) { failingUrl }
    if (kind == LoadFailureKind.PROXY) {
        tabManager?.setRescueState(
            record.id,
            RescueState(
                failingUrl = failingUrl,
                host = host,
                reason = reason,
                mirrors = emptyList(),
                alternatives = emptyList(),
                kind = LoadFailureKind.PROXY,
                canSkipBlocking = false,
            )
        )
        return
    }
    val entryId = record.entryId
    record.rescueJob = tabManager?.appScope?.launch(Dispatchers.IO) {
        val store = datasetStore
        val entry = if (entryId != null) {
            store?.entry(entryId)
        } else {
            safeGuard?.check(failingUrl)?.matches?.firstOrNull()
        }
        val mirrors = entry?.mirrors.orEmpty()
        val alternatives = if (store != null && entry?.headingId != null) {
            store.entriesForSection(entry.sectionId)
                .filter { it.headingId == entry.headingId && it.id != entry.id }
                .sortedWith(
                    compareByDescending<Entry> { it.tier == Tier.STAR || it.starredAt != null }
                        .thenBy { it.ord }
                )
                .take(8)
        } else {
            emptyList()
        }

        withContext(Dispatchers.Main) {
            // The page may have loaded or the tab moved on while the lookup ran.
            if (!ignoreContentShown && record.contentShown) return@withContext
            if (listOf(record.url, record.sourceUrl).none { RescueDecision.sameAddress(it, failingUrl) }) return@withContext
            rescueLog("SHOW rescue url=$failingUrl ($reason)")
            tabManager?.setRescueState(
                record.id,
                RescueState(
                    failingUrl = failingUrl,
                    host = host,
                    reason = reason,
                    mirrors = mirrors,
                    alternatives = alternatives,
                    kind = kind,
                    canSkipBlocking = canSkipBlocking,
                )
            )
        }
    }
}

/** User pressed Retry: keep the rescue up while loading; no extra silent rounds. */
fun DefaultBrowserHooks.retryRescue(record: TabRecord) {
    val rescue = record.rescueState ?: return
    record.silentRetries = RescueDecision.MAX_SILENT_RETRIES
    record.silentRetryUrl = rescue.failingUrl
    tabManager?.setRescueState(record.id, rescue.copy(retrying = true))
    tabManager?.loadUrl(record.id, rescue.failingUrl)
}

fun DefaultBrowserHooks.reloadWithoutBlocking(record: TabRecord) {
    val rescue = record.rescueState ?: return
    record.skipBlockingOnceUrl = rescue.failingUrl
    record.silentRetries = RescueDecision.MAX_SILENT_RETRIES
    record.silentRetryUrl = rescue.failingUrl
    tabManager?.setRescueState(record.id, rescue.copy(retrying = true))
    tabManager?.loadUrl(record.id, rescue.failingUrl)
}

internal fun rescueLog(msg: String) {
    if (io.github.omeg4dev.fmhy.BuildConfig.DEBUG) {
        try {
            android.util.Log.d("FmhyRescue", msg)
        } catch (_: Throwable) {}
    }
}
