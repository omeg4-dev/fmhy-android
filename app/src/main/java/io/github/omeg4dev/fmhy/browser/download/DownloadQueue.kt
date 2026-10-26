package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkPolicy

const val MAX_RUNNING = 3

data class Transition(val id: Long, val to: DownloadStatus)

object DownloadQueue {

    fun transitions(items: List<DownloadItem>, wifiOnly: Boolean, network: NetworkKind): List<Transition> {
        val candidates = items.filter {
            it.status == DownloadStatus.WAITING ||
                it.status == DownloadStatus.WAITING_WIFI ||
                it.status == DownloadStatus.RUNNING
        }.sortedWith(compareBy({ it.createdAt }, { it.id }))

        val result = mutableListOf<Transition>()
        var runningCount = 0

        for (item in candidates) {
            val netOk = network != NetworkKind.NONE && NetworkPolicy.allowed(wifiOnly, network, item.allowMobile)

            if (item.status == DownloadStatus.RUNNING) {
                if (!netOk) {
                    val target = if (network != NetworkKind.NONE) DownloadStatus.WAITING_WIFI else DownloadStatus.WAITING
                    result.add(Transition(item.id, target))
                } else {
                    runningCount++
                }
            } else {
                // status is WAITING or WAITING_WIFI
                if (!netOk) {
                    val target = if (network != NetworkKind.NONE) DownloadStatus.WAITING_WIFI else DownloadStatus.WAITING
                    if (item.status != target) {
                        result.add(Transition(item.id, target))
                    }
                } else {
                    if (runningCount < MAX_RUNNING) {
                        result.add(Transition(item.id, DownloadStatus.RUNNING))
                        runningCount++
                    } else {
                        if (item.status != DownloadStatus.WAITING) {
                            result.add(Transition(item.id, DownloadStatus.WAITING))
                        }
                    }
                }
            }
        }

        return result
    }

    fun retry(item: DownloadItem): DownloadItem {
        return if (!item.resumable) {
            item.copy(
                status = DownloadStatus.WAITING,
                error = null,
                speedBps = null,
                etaSeconds = null,
                downloadedBytes = 0L,
                restartNote = true,
            )
        } else {
            item.copy(
                status = DownloadStatus.WAITING,
                error = null,
                speedBps = null,
                etaSeconds = null,
            )
        }
    }
}
