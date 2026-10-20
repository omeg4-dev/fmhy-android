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
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SectionLayout
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
    LOOK,
    LINKS,
    PROTECTION,
    LISTS,
    STAY_UPDATED,
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
    val openLinksWith: OpenLinksWith = OpenLinksWith.IN_APP,
    val adblockLevel: AdblockLevel = AdblockLevel.BALANCED,
    val blockPopupsAndRedirects: Boolean = true,
    val hideCookieBanners: Boolean = false,
    val starredOnly: Boolean = false,
    val newBadgeDays: Int = 7,
    val sectionLayout: SectionLayout = SectionLayout.CONTINUOUS,
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
            privacyPrefs: PrivacyPrefs,
            homePrefs: HomePrefs,
            notificationPrefs: NotificationPrefs,
            follows: Set<String> = emptySet(),
        ): OnboardingState {
            return OnboardingState(
                themeMode = appearance.themeMode,
                colorSource = appearance.colorSource,
                accentSeed = appearance.accentSeed,
                openLinksWith = browserPrefs.openLinksWith,
                adblockLevel = AdblockLevel.fromBlockingPrefs(blockingPrefs),
                blockPopupsAndRedirects = protectionPrefs.popupBlocker && protectionPrefs.redirectGuard,
                hideCookieBanners = privacyPrefs.hideCookieBanners,
                starredOnly = appearance.starredOnly,
                newBadgeDays = homePrefs.newBadgeDays,
                sectionLayout = appearance.sectionLayout,
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
        val privacyPrefs = settingsRepository.privacyPrefs.first()
        val homePrefs = settingsRepository.homePrefs.first()
        val notificationPrefs = settingsRepository.notificationPrefs.first()
        val follows = try {
            userStore.follows().map { it.sectionId }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        _state.value = OnboardingState.fromCurrent(
            appearance = appearance,
            browserPrefs = browserPrefs,
            blockingPrefs = blockingPrefs,
            protectionPrefs = protectionPrefs,
            privacyPrefs = privacyPrefs,
            homePrefs = homePrefs,
            notificationPrefs = notificationPrefs,
            follows = follows,
        )
    }

    val visiblePages: List<OnboardingPage>
        get() = if (_state.value.openLinksWith == OpenLinksWith.IN_APP) {
            OnboardingPage.entries
        } else {
            OnboardingPage.entries.filter { it != OnboardingPage.PROTECTION }
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

    fun setOpenLinksWith(openLinksWith: OpenLinksWith) {
        _state.update { it.copy(openLinksWith = openLinksWith) }
    }

    fun setAdblockLevel(level: AdblockLevel) {
        _state.update { it.copy(adblockLevel = level) }
    }

    fun setBlockPopupsAndRedirects(block: Boolean) {
        _state.update { it.copy(blockPopupsAndRedirects = block) }
    }

    fun setHideCookieBanners(hide: Boolean) {
        _state.update { it.copy(hideCookieBanners = hide) }
    }

    fun setStarredOnly(starredOnly: Boolean) {
        _state.update { it.copy(starredOnly = starredOnly) }
    }

    fun setNewBadgeDays(days: Int) {
        _state.update { it.copy(newBadgeDays = days) }
    }

    fun setSectionLayout(layout: SectionLayout) {
        _state.update { it.copy(sectionLayout = layout) }
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

    suspend fun persistPage(page: OnboardingPage) {
        val s = _state.value
        when (page) {
            OnboardingPage.WELCOME -> {}
            OnboardingPage.LOOK -> {
                settingsRepository.setThemeMode(s.themeMode)
                settingsRepository.setColorSource(s.colorSource)
                settingsRepository.setAccentSeed(s.accentSeed)
            }
            OnboardingPage.LINKS -> {
                settingsRepository.updateBrowser { it.copy(openLinksWith = s.openLinksWith) }
            }
            OnboardingPage.PROTECTION -> {
                if (s.openLinksWith == OpenLinksWith.IN_APP) {
                    settingsRepository.updateBlocking { b ->
                        when (s.adblockLevel) {
                            AdblockLevel.OFF -> b.copy(adblock = false)
                            AdblockLevel.BALANCED -> b.copy(
                                adblock = true,
                                enabledLists = if (s.hideCookieBanners) {
                                    FilterListCatalog.balancedIds + FilterListCatalog.COOKIE_LIST_ID
                                } else {
                                    FilterListCatalog.balancedIds
                                },
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
                    settingsRepository.updatePrivacy { p ->
                        p.copy(hideCookieBanners = s.hideCookieBanners)
                    }
                }
            }
            OnboardingPage.LISTS -> {
                settingsRepository.setStarredOnly(s.starredOnly)
                settingsRepository.updateHome { it.copy(newBadgeDays = s.newBadgeDays) }
                settingsRepository.setSectionLayout(s.sectionLayout)
            }
            OnboardingPage.STAY_UPDATED -> {
                settingsRepository.updateNotifications { it.copy(enabled = s.notifyChanges) }
                settingsRepository.updateHome { it.copy(personalHome = s.personalHome) }
                try {
                    val existingFollows = userStore.follows()
                    val existingSectionIds = existingFollows.map { it.sectionId }.toSet()
                    for (secId in s.followedSections) {
                        if (secId !in existingSectionIds) {
                            userStore.follow(secId, "", "")
                        }
                    }
                    for (f in existingFollows) {
                        if (f.sectionId !in s.followedSections && f.headingId.isEmpty()) {
                            userStore.unfollow(f.sectionId, f.headingId)
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
            if (_state.value.openLinksWith == OpenLinksWith.IN_APP && _state.value.adblockLevel != AdblockLevel.OFF) {
                _state.update { it.copy(filterDownloadStarted = true) }
                appContainer?.startFilterDownload()
            }
            settingsRepository.setOnboardingVersion(ONBOARDING_VERSION)
            onComplete()
        }
    }

    fun skip(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            persistAll()
            settingsRepository.setOnboardingVersion(ONBOARDING_VERSION)
            onComplete()
        }
    }

    companion object {
        fun shouldShowOnboarding(version: Int): Boolean = version < ONBOARDING_VERSION
    }
}

typealias OnboardingViewModel = OnboardingModel
