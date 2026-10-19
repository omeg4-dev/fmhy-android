package io.github.omeg4dev.fmhy.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class Tab { BROWSE, SEARCH, SAVED }

class AppNavState(
    val stacks: Map<Tab, MutableList<NavKey>>,
    initial: Tab = Tab.BROWSE,
) {
    var current: Tab by mutableStateOf(initial)

    val currentStack: MutableList<NavKey>
        get() = stacks[current] ?: error("No stack for $current")

    private val _reselect = MutableSharedFlow<Tab>(extraBufferCapacity = 1)
    val reselect: SharedFlow<Tab> = _reselect.asSharedFlow()

    private val _searchFocusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val searchFocusRequests: SharedFlow<Unit> = _searchFocusRequests.asSharedFlow()

    private var searchFocusForced: Boolean = false

    fun requestSearchFocus() {
        searchFocusForced = true
        _searchFocusRequests.tryEmit(Unit)
    }

    fun consumeSearchFocusForced(): Boolean {
        val forced = searchFocusForced
        searchFocusForced = false
        return forced
    }

    fun select(tab: Tab) {
        if (tab != current) {
            current = tab
            if (tab == Tab.SEARCH) {
                _searchFocusRequests.tryEmit(Unit)
            }
        } else {
            val stack = currentStack
            if (stack.size > 1) {
                val root = stack.first()
                stack.clear()
                stack.add(root)
            } else {
                _reselect.tryEmit(tab)
            }
        }
    }

    fun push(key: NavKey) {
        if (currentStack.lastOrNull() == key) return
        currentStack.add(key)
    }

    fun back(): Boolean {
        return if (currentStack.size > 1) {
            currentStack.removeAt(currentStack.lastIndex)
            true
        } else if (current != Tab.BROWSE) {
            select(Tab.BROWSE)
            true
        } else {
            false
        }
    }
}

val LocalAppNav = staticCompositionLocalOf<AppNavState> { error("no nav") }
