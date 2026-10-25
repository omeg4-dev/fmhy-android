package io.github.omeg4dev.fmhy.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.github.omeg4dev.fmhy.MainActivity
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_ENTRY_ID
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_HEADING_ID
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_OPEN
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_SECTION_ID

const val EXTRA_SELECT_TAB = "select_tab"
const val EXTRA_SHOW_TABS = "show_tabs"
/** Open a fresh new-tab page (address bar focused). Handled once tab restore is done. */
const val EXTRA_NEW_TAB_PAGE = "new_tab_page"
/** With [EXTRA_NEW_TAB_PAGE]: make the new tab private. */
const val EXTRA_PRIVATE_TAB = "private_tab"

/** Brings the existing browser instance to the front without opening a new tab. */
fun bringBrowserToFront(context: Context, selectTabId: Long? = null, showTabs: Boolean = false) {
    val intent = Intent(context, BrowserActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (selectTabId != null) putExtra(EXTRA_SELECT_TAB, selectTabId)
        if (showTabs) putExtra(EXTRA_SHOW_TABS, true)
    }
    context.startActivity(intent)
}

/** Brings the browser to the front on a new, empty tab (private when [private]). */
fun openBrowserNewTab(context: Context, private: Boolean = false) {
    val intent = Intent(context, BrowserActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(EXTRA_NEW_TAB_PAGE, true)
        if (private) putExtra(EXTRA_PRIVATE_TAB, true)
    }
    context.startActivity(intent)
}

/** Brings FMHY (MainActivity) to the front. The browser and its tabs stay alive. */
fun bringFmhyToFront(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/** "Show in section": brings FMHY to the front at [entry]; the browser and its tabs stay alive. */
fun showEntryInApp(context: Context, entry: Entry) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(EXTRA_OPEN, "entry")
        putExtra(EXTRA_ENTRY_ID, entry.id)
        putExtra(EXTRA_SECTION_ID, entry.sectionId)
        entry.headingId?.let { putExtra(EXTRA_HEADING_ID, it) }
    }
    context.startActivity(intent)
}
