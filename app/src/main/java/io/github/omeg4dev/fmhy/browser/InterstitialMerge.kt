package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.data.safety.readableReason

object InterstitialMerge {
    fun combine(
        host: String,
        url: String,
        verdict: Verdict?,
        unsafeWarning: Boolean,
        potentiallyUnsafeWarning: Boolean,
        hostAllowed: Boolean,
        filterRule: String?,
    ): InterstitialState? {
        val reasons = mutableListOf<WarningReason>()
        val hasUnsafe = !hostAllowed && verdict?.status == SafetyStatus.UNSAFE && unsafeWarning
        val hasPotentiallyUnsafe = !hostAllowed && verdict?.status == SafetyStatus.POTENTIALLY_UNSAFE && potentiallyUnsafeWarning

        if (hasUnsafe) {
            reasons.add(WarningReason.UNSAFE)
        } else if (hasPotentiallyUnsafe) {
            reasons.add(WarningReason.POTENTIALLY_UNSAFE)
        }

        val hasFilterBlock = filterRule != null
        if (hasFilterBlock) {
            reasons.add(WarningReason.FILTER_BLOCK)
        }

        if (reasons.isEmpty()) return null

        val note = if (hasUnsafe || hasPotentiallyUnsafe) {
            readableReason(verdict?.reason)
        } else {
            null
        }

        val rule = if (hasFilterBlock) filterRule else null

        return InterstitialState(
            host = host,
            url = url,
            reasons = reasons,
            note = note,
            filterRule = rule,
        )
    }

    fun sentence(reason: WarningReason): String = when (reason) {
        WarningReason.UNSAFE -> "FMHY lists this site as unsafe"
        WarningReason.POTENTIALLY_UNSAFE -> "FMHY flags this site as potentially unsafe"
        WarningReason.FILTER_BLOCK -> "An ad-blocking filter blocks this site"
    }
}
