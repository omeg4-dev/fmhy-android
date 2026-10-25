package io.github.omeg4dev.fmhy.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ONBOARDING_VERSION = 1

enum class OnboardingPage {
    WELCOME,
    HELLO,
    BROWSING,
    FOLLOW,
    DONE,
}

enum class AdblockLevel {
    OFF,
    BALANCED,
    STRICT;

    companion object {
        fun fromBlockingPrefs(prefs: BlockingPrefs): AdblockLevel {
            return when {
                !prefs.adblock -> OFF
                prefs.enabledLists.containsAll(FilterListCatalog.strictIds) ||
                        prefs.enabledLists == FilterListCatalog.strictIds -> STRICT
                else -> BALANCED
            }
        }
    }
}

data class OnboardingState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.FMHY,
    val accentSeed: Int = 0xFF7C5CFF.toInt(),
    val websiteStyle: Boolean = false,
    val openLinksWith: OpenLinksWith = OpenLinksWith.IN_APP,
    val adblockLevel: AdblockLevel = AdblockLevel.BALANCED,
    val blockPopupsAndRedirects: Boolean = true,
    val followedSections: Set<String> = emptySet(),
    val notifyChanges: Boolean = false,
    val personalHome: Boolean = false,
    val filterDownloadStarted: Boolean = false,
    val isInitialized: Boolean = false,
) {
    companion object {
        fun fromCurrent(
            appearance: Appearance,
            browserPrefs: BrowserPrefs,
            blockingPrefs: BlockingPrefs,
            protectionPrefs: ProtectionPrefs,
            homePrefs: HomePrefs,
            notificationPrefs: NotificationPrefs,
            follows: Set<String> = emptySet(),
        ): OnboardingState {
            return OnboardingState(
                themeMode = appearance.themeMode,
                colorSource = appearance.colorSource,
                accentSeed = appearance.accentSeed,
                websiteStyle = appearance.websiteStyle,
                openLinksWith = browserPrefs.openLinksWith,
                adblockLevel = AdblockLevel.fromBlockingPrefs(blockingPrefs),
                blockPopupsAndRedirects = protectionPrefs.popupBlocker && protectionPrefs.redirectGuard,
                followedSections = follows,
                notifyChanges = notificationPrefs.enabled,
                personalHome = homePrefs.personalHome,
                isInitialized = true,
            )
        }
    }
}

class OnboardingModel(
    private val settingsRepository: SettingsRepository,
    private val userStore: UserStore,
    private val appContainer: AppContainer? = null,
    initialState: OnboardingState? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState ?: OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _visitedPages = mutableSetOf(OnboardingPage.WELCOME)
    val visitedPages: Set<OnboardingPage> get() = _visitedPages

    init {
        if (initialState == null) {
            viewModelScope.launch {
                loadCurrent()
            }
        }
    }

    suspend fun loadCurrent() {
        val appearance = settingsRepository.appearance.first()
        val browserPrefs = settingsRepository.browserPrefs.first()
        val blockingPrefs = settingsRepository.blockingPrefs.first()
        val protectionPrefs = settingsRepository.protectionPrefs.first()
        val homePrefs = settingsRepository.homePrefs.first()
        val notificationPrefs = settingsRepository.notificationPrefs.first()
        val follows = try {
            userStore.follows().filter { !it.excluded }.map { it.sectionId }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        _state.value = OnboardingState.fromCurrent(
            appearance = appearance,
            browserPrefs = browserPrefs,
            blockingPrefs = blockingPrefs,
            protectionPrefs = protectionPrefs,
            homePrefs = homePrefs,
            notificationPrefs = notificationPrefs,
            follows = follows,
        )
    }

    val visiblePages: List<OnboardingPage>
        get() = OnboardingPage.entries

    fun markPageVisited(page: OnboardingPage) {
        _visitedPages.add(page)
    }

    fun nextPage(from: OnboardingPage): OnboardingPage? {
        val pages = visiblePages
        val idx = pages.indexOf(from)
        if (idx == -1 || idx >= pages.size - 1) return null
        return pages[idx + 1]
    }

    fun previousPage(from: OnboardingPage): OnboardingPage? {
        val pages = visiblePages
        val idx = pages.indexOf(from)
        if (idx <= 0) return null
        return pages[idx - 1]
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
    }

    fun setColorSource(source: ColorSource) {
        _state.update { it.copy(colorSource = source) }
    }

    fun setAccentSeed(seed: Int) {
        _state.update { it.copy(accentSeed = seed, colorSource = ColorSource.FMHY) }
    }

    fun setWebsiteStyle(enabled: Boolean) {
        _state.update { it.copy(websiteStyle = enabled) }
    }

    fun setOpenLinksWith(openLinksWith: OpenLinksWith) {
        _state.update { it.copy(openLinksWith = openLinksWith) }
    }

    fun setAdblockLevel(level: AdblockLevel) {
        _state.update { it.copy(adblockLevel = level) }
    }

    fun setBlockPopupsAndRedirects(block: Boolean) {
        _state.update { it.copy(blockPopupsAndRedirects = block) }
    }

    fun toggleSectionFollowed(sectionId: String) {
        _state.update { curr ->
            val updated = if (sectionId in curr.followedSections) {
                curr.followedSections - sectionId
            } else {
                curr.followedSections + sectionId
            }
            val notify = if (updated.isNotEmpty() && curr.followedSections.isEmpty() && !curr.notifyChanges) {
                true
            } else if (updated.isEmpty()) {
                false
            } else {
                curr.notifyChanges
            }
            curr.copy(followedSections = updated, notifyChanges = notify)
        }
    }

    fun setNotifyChanges(notify: Boolean) {
        _state.update { it.copy(notifyChanges = notify) }
    }

    fun setPersonalHome(personalHome: Boolean) {
        _state.update { it.copy(personalHome = personalHome) }
    }

    fun startFilterDownloadIfNeeded() {
        val s = _state.value
        if (!s.filterDownloadStarted && s.openLinksWith == OpenLinksWith.IN_APP && s.adblockLevel != AdblockLevel.OFF) {
            _state.update { it.copy(filterDownloadStarted = true) }
            appContainer?.startFilterDownload()
        }
    }

    suspend fun persistPage(page: OnboardingPage) {
        val s = _state.value
        when (page) {
            OnboardingPage.WELCOME -> Unit
            OnboardingPage.HELLO -> {
                settingsRepository.setThemeMode(s.themeMode)
                settingsRepository.setColorSource(s.colorSource)
                settingsRepository.setAccentSeed(s.accentSeed)
                settingsRepository.setWebsiteStyle(s.websiteStyle)
            }
            OnboardingPage.BROWSING -> {
                settingsRepository.updateBrowser { it.copy(openLinksWith = s.openLinksWith) }
                if (s.openLinksWith == OpenLinksWith.IN_APP) {
                    settingsRepository.updateBlocking { b ->
                        when (s.adblockLevel) {
                            AdblockLevel.OFF -> b.copy(adblock = false)
                            AdblockLevel.BALANCED -> b.copy(
                                adblock = true,
                                enabledLists = FilterListCatalog.balancedIds,
                            )
                            AdblockLevel.STRICT -> b.copy(
                                adblock = true,
                                enabledLists = FilterListCatalog.strictIds,
                            )
                        }
                    }
                    settingsRepository.updateProtection { p ->
                        p.copy(
                            popupBlocker = s.blockPopupsAndRedirects,
                            redirectGuard = s.blockPopupsAndRedirects,
                        )
                    }
                    startFilterDownloadIfNeeded()
                }
            }
            OnboardingPage.FOLLOW -> {
                settingsRepository.updateNotifications { it.copy(enabled = s.notifyChanges) }
                settingsRepository.updateHome { it.copy(personalHome = s.personalHome) }
                try {
                    val existingFollows = userStore.follows()
                    val existingSectionIds = existingFollows.map { it.sectionId }.toSet()
                    val wholeFollowedSecIds = existingFollows
                        .filter { it.headingId.isEmpty() && !it.excluded }
                        .map { it.sectionId }
                        .toSet()
                    val onlySubFollowedSecIds = existingSectionIds - existingFollows.filter { it.headingId.isEmpty() }.map { it.sectionId }.toSet()

                    val titles = runCatching { appContainer?.datasetStore?.sections() }.getOrNull()
                        ?.associate { it.id to it.title }
                        ?: emptyMap()

                    for (secId in s.followedSections) {
                        if (secId in onlySubFollowedSecIds) {
                            continue
                        }
                        if (secId !in wholeFollowedSecIds) {
                            val title = titles[secId] ?: existingFollows.firstOrNull { it.sectionId == secId }?.title?.substringBefore(" › ") ?: secId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            userStore.followSection(secId, title)
                        }
                    }

                    for (secId in existingSectionIds) {
                        if (secId !in s.followedSections) {
                            userStore.unfollowSection(secId)
                        }
                    }
                } catch (_: Exception) {}
            }
            OnboardingPage.DONE -> {}
        }
    }

    suspend fun persistAll() {
        for (page in OnboardingPage.entries) {
            persistPage(page)
        }
    }

    fun finish(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            persistAll()
            startFilterDownloadIfNeeded()
            settingsRepository.setOnboardingVersion(ONBOARDING_VERSION)
            settingsRepository.setGestureHintShown(true)
            onComplete()
        }
    }

    fun skip(visited: Set<OnboardingPage> = _visitedPages, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            for (page in visited) {
                persistPage(page)
            }
            settingsRepository.setOnboardingVersion(ONBOARDING_VERSION)
            settingsRepository.setGestureHintShown(true)
            onComplete()
        }
    }

    companion object {
        fun shouldShowOnboarding(version: Int): Boolean = version < ONBOARDING_VERSION
    }
}

typealias OnboardingViewModel = OnboardingModel
