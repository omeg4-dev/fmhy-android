package io.github.omeg4dev.fmhy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.MainActivity
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_ENTRY_ID
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_HEADING_ID
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_OPEN
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_SECTION_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the random-pick row shows; kept in SharedPreferences so taps open the entry that is on screen. */
data class WidgetPick(val entryId: String, val name: String, val sectionTitle: String)

/** 4x1 shows search + random side by side; from two rows up the layout stacks them and adds pins. */
fun widgetUsesLargeLayout(minHeightDp: Int): Boolean = minHeightDp >= 110

/**
 * Home-screen widget: a search pill, a random starred site (refresh button) and, at 4x2, up to
 * three Personal Home pins. Plain RemoteViews; no Glance.
 */
class FmhyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // Periodic (daily) update and first placement both pick a fresh site.
        refresh(context, manager, appWidgetIds, newPick = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refresh(context, manager, intArrayOf(appWidgetId), newPick = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FmhyWidgetProvider::class.java))
            refresh(context, manager, ids, newPick = true)
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray, newPick: Boolean) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as FmhyApp
                val container = app.container
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

                var pick = if (newPick) null else readPick(prefs)
                if (pick == null) {
                    pick = runCatching {
                        val store = container.datasetStore
                        val entry = store.randomTopPick()
                        entry?.let {
                            val title = store.sections().firstOrNull { s -> s.id == it.sectionId }?.title ?: it.sectionId
                            WidgetPick(it.id, it.name, title)
                        }
                    }.getOrNull() ?: readPick(prefs)
                    pick?.let { writePick(prefs, it) }
                }

                val pins = runCatching {
                    if (container.settingsRepository.homePrefs.first().personalHome) {
                        container.userStore.pins().sortedBy { it.ord }.take(MAX_PINS)
                    } else emptyList()
                }.getOrDefault(emptyList())

                for (id in ids) {
                    val minHeight = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                    val large = widgetUsesLargeLayout(minHeight)
                    val views = RemoteViews(
                        context.packageName,
                        if (large) R.layout.widget_fmhy_large else R.layout.widget_fmhy_small,
                    )
                    views.setOnClickPendingIntent(R.id.widget_search, activity(context, 1, "search"))
                    views.setOnClickPendingIntent(R.id.widget_random, activity(context, 2, "random") {
                        pick?.let { putExtra(EXTRA_ENTRY_ID, it.entryId) }
                    })
                    views.setTextViewText(R.id.widget_random_name, pick?.name ?: context.getString(R.string.widget_random_empty))
                    views.setTextViewText(R.id.widget_random_section, pick?.sectionTitle ?: "")
                    views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
                    if (large) {
                        val pinIds = intArrayOf(R.id.widget_pin1, R.id.widget_pin2, R.id.widget_pin3)
                        pinIds.forEachIndexed { i, viewId ->
                            val pin = pins.getOrNull(i)
                            if (pin == null) {
                                views.setViewVisibility(viewId, View.GONE)
                            } else {
                                views.setViewVisibility(viewId, View.VISIBLE)
                                views.setTextViewText(viewId, pin.title)
                                views.setOnClickPendingIntent(viewId, activity(context, 10 + i, "pin") {
                                    putExtra(EXTRA_SECTION_ID, pin.sectionId)
                                    putExtra(EXTRA_HEADING_ID, pin.headingId)
                                })
                            }
                        }
                        views.setViewVisibility(R.id.widget_pins, if (pins.isEmpty()) View.GONE else View.VISIBLE)
                    }
                    manager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                Log.e("FmhyWidget", "Widget update failed: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun activity(context: Context, requestCode: Int, open: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, open)
            extras()
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, FmhyWidgetProvider::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun readPick(prefs: android.content.SharedPreferences): WidgetPick? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return WidgetPick(id, prefs.getString(KEY_NAME, "") ?: "", prefs.getString(KEY_SECTION, "") ?: "")
    }

    private fun writePick(prefs: android.content.SharedPreferences, pick: WidgetPick) {
        prefs.edit()
            .putString(KEY_ID, pick.entryId)
            .putString(KEY_NAME, pick.name)
            .putString(KEY_SECTION, pick.sectionTitle)
            .apply()
    }

    companion object {
        const val ACTION_REFRESH = "io.github.omeg4dev.fmhy.widget.REFRESH"
        private const val PREFS = "fmhy_widget"
        private const val KEY_ID = "pick_id"
        private const val KEY_NAME = "pick_name"
        private const val KEY_SECTION = "pick_section"
        private const val MAX_PINS = 3
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
