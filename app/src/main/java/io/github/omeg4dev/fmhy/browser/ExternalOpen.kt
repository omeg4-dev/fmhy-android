package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** How "Open in another browser" reaches a browser for a page address. */
sealed interface ExternalOpenPlan {
    /** The phone's default browser, started directly. */
    data class Direct(val packageName: String) : ExternalOpenPlan

    /** No usable default: the Android chooser, listing these browsers (never this app). */
    data class Chooser(val packageNames: List<String>) : ExternalOpenPlan

    /** No browser other than this app can open the address. */
    data object NoneFound : ExternalOpenPlan
}

object ExternalOpen {
    /**
     * @param defaultPackage package the system resolves a plain web link to; may be this app,
     *   the system resolver ("android") or null when there is no default.
     * @param browserPackages every package that can open a web link, in system order.
     */
    fun plan(defaultPackage: String?, browserPackages: List<String>, ownPackage: String): ExternalOpenPlan {
        val others = browserPackages.filter { it != ownPackage }.distinct()
        if (others.isEmpty()) return ExternalOpenPlan.NoneFound
        if (defaultPackage != null && defaultPackage in others) return ExternalOpenPlan.Direct(defaultPackage)
        return ExternalOpenPlan.Chooser(others)
    }

    /** True for an address another browser or the share sheet can use. */
    fun isWebAddress(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")
}

/** The tab shows a real web page (not a blank tab, block page or notice page). */
fun TabState.hasWebAddress(): Boolean =
    ExternalOpen.isWebAddress(url) && interstitialState == null && !showsBlockPage && !showsNoticePage

private fun webProbe() = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
    .addCategory(Intent.CATEGORY_BROWSABLE)

/** Starts the share sheet with the address as text and the page title as subject. False when it failed. */
fun shareWebAddress(context: Context, url: String, title: String): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

enum class ExternalOpenResult { OPENED, NONE_FOUND, FAILED }

/** Opens [url] in the default browser, or in the Android chooser without this app. */
fun openInExternalBrowser(context: Context, url: String): ExternalOpenResult {
    val pm = context.packageManager
    val browsers = pm.queryIntentActivities(webProbe(), PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName }
    val default = pm.resolveActivity(webProbe(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    fun view(pkg: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        setPackage(pkg)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        when (val plan = ExternalOpen.plan(default, browsers, context.packageName)) {
            is ExternalOpenPlan.NoneFound -> ExternalOpenResult.NONE_FOUND
            is ExternalOpenPlan.Direct -> {
                context.startActivity(view(plan.packageName))
                ExternalOpenResult.OPENED
            }
            is ExternalOpenPlan.Chooser -> {
                val targets = plan.packageNames.map { view(it) }
                val chooser = Intent.createChooser(targets.first(), null).apply {
                    if (targets.size > 1) putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                ExternalOpenResult.OPENED
            }
        }
    } catch (_: Exception) {
        ExternalOpenResult.FAILED
    }
}
