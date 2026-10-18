package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.SectionItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SectionUiState(
    val isLoading: Boolean = true,
    val section: Section? = null,
    val items: ImmutableList<SectionItem> = persistentListOf(),
    val headings: ImmutableList<Heading> = persistentListOf(),
)

class SectionViewModel(
    val sectionId: String,
    val anchor: String?,
    private val store: DatasetStore,
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<SectionUiState> = combine(
        store.changes,
        _refreshTrigger,
    ) { _, _ ->
        if (!store.isAvailable) return@combine SectionUiState(isLoading = false)
        val sections = store.sections()
        val currentSection = sections.firstOrNull { it.id == sectionId }
        val sectionItems = store.sectionItems(sectionId).toImmutableList()
        val sectionHeadings = store.headings(sectionId).toImmutableList()

        SectionUiState(
            isLoading = false,
            section = currentSection,
            items = sectionItems,
            headings = sectionHeadings,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SectionUiState(isLoading = true)
    )

    fun refresh() {
        _refreshTrigger.value++
    }
}
