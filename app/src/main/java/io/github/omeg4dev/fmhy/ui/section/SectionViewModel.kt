package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.newbadge.NewBadgeIndex
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class SectionUiState(
    val isLoading: Boolean = true,
    val section: Section? = null,
    val items: ImmutableList<SectionItem> = persistentListOf(),
    val headings: ImmutableList<Heading> = persistentListOf(),
    val structure: SectionStructure = SectionStructure(emptyList(), emptyList(), IntArray(0)),
    val layout: SectionLayout = SectionLayout.CONTINUOUS,
    val starredOnly: Boolean = false,
    val showIndexes: Boolean = true,
    val newBadges: Map<String, LocalDate> = emptyMap(),
)

class SectionViewModel(
    val sectionId: String,
    val anchor: String?,
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val userStore: UserStore? = null,
    newBadgeIndex: StateFlow<NewBadgeIndex> = MutableStateFlow(NewBadgeIndex.EMPTY),
    today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    init {
        viewModelScope.launch {
            runCatching {
                if (userStore != null && store.sections().any { it.id == sectionId }) {
                    userStore.recordSectionOpen(sectionId)
                }
            }
        }
    }

    private val _refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<SectionUiState> = combine(
        store.changes,
        _refreshTrigger,
        settingsRepository.appearance,
        settingsRepository.homePrefs,
        newBadgeIndex,
    ) { _, _, appearance, homePrefs, index ->
        if (!store.isAvailable) {
            return@combine SectionUiState(
                isLoading = false,
                layout = appearance.sectionLayout,
                starredOnly = appearance.starredOnly,
                showIndexes = appearance.showIndexes,
            )
        }
        withContext(Dispatchers.Default) {
            val sections = store.sections()
            val currentSection = sections.firstOrNull { it.id == sectionId }
            val rawItems = store.sectionItems(sectionId)
            val filteredItems = filterSectionItems(
                items = rawItems,
                starredOnly = appearance.starredOnly,
                showIndexes = appearance.showIndexes,
            ).toImmutableList()
            val allHeadings = store.headings(sectionId)
            val keptHeadingIds = filteredItems.filterIsInstance<SectionItem.HeadingItem>().map { it.heading.id }.toSet()
            val sectionHeadings = allHeadings.filter { it.id in keptHeadingIds }.toImmutableList()
            val structure = buildSectionStructure(filteredItems)

            val newBadges = if (homePrefs.newBadges) {
                val todayDate = today()
                val map = mutableMapOf<String, LocalDate>()
                for (item in filteredItems) {
                    if (item is SectionItem.EntryItem) {
                        index.activeDate(item.entry, todayDate)?.let { date ->
                            map[item.entry.id] = date
                        }
                    }
                }
                map
            } else {
                emptyMap()
            }

            SectionUiState(
                isLoading = false,
                section = currentSection,
                items = filteredItems,
                headings = sectionHeadings,
                structure = structure,
                layout = appearance.sectionLayout,
                starredOnly = appearance.starredOnly,
                showIndexes = appearance.showIndexes,
                newBadges = newBadges,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SectionUiState(isLoading = true)
    )

    fun setStarredOnly(starredOnly: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStarredOnly(starredOnly)
        }
    }

    fun setShowIndexes(showIndexes: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowIndexes(showIndexes)
        }
    }

    fun refresh() {
        _refreshTrigger.value++
    }
}
