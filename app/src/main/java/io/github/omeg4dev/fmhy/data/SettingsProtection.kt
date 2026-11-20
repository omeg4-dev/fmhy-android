package io.github.omeg4dev.fmhy.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_ADBLOCK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_ALLOWLIST
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_AUTO_UPDATE_LISTS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_CUSTOM_RULES
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_ELEMENT_HIDING
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_ENABLED_LISTS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_SCRIPTLETS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_SHOW_BLOCKED_COUNT
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_AUTO_RISKY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_EXPLAIN_SHOWN
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_FMHY_DOWNLOAD_STATUS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_HA_KEY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_HA_ON
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_HOLD
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_KEY_PROMPT_SHOWN
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_VT_KEY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_CHECK_VT_ON
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PRIVACY_BLOCK_THIRD_PARTY_COOKIES
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PRIVACY_CLEAR_DATA_ON_EXIT
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PRIVACY_HIDE_COOKIE_BANNERS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PRIVACY_PRIVATE_TABS_BY_DEFAULT
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PRIVACY_SEND_GPC
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_ASK_BEFORE_DOWNLOAD
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_BLOCK_APP_LINKS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_BLOCK_WITHOUT_VPN
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_POPUP_BLOCKER
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_POTENTIALLY_UNSAFE_WARNING
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_REDIRECT_GUARD
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_SAFE_BROWSING
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_SHOW_IP_COUNTRY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_SHOW_VPN_HOME_ICON
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_SHOW_VPN_STATE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_UNSAFE_WARNING
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PROTECTION_WARN_RISKY_DOWNLOADS

// DefaultSettingsRepository read/write helpers: protection, blocking, privacy, and malware check settings.

internal fun DefaultSettingsRepository.readBlockingPrefs(prefs: Preferences): BlockingPrefs {
    val default = BlockingPrefs()
    return BlockingPrefs(
        adblock = prefs[KEY_BLOCKING_ADBLOCK] ?: default.adblock,
        enabledLists = prefs[KEY_BLOCKING_ENABLED_LISTS] ?: default.enabledLists,
        customRules = prefs[KEY_BLOCKING_CUSTOM_RULES] ?: default.customRules,
        allowlist = prefs[KEY_BLOCKING_ALLOWLIST] ?: default.allowlist,
        showBlockedCount = prefs[KEY_BLOCKING_SHOW_BLOCKED_COUNT] ?: default.showBlockedCount,
        elementHiding = prefs[KEY_BLOCKING_ELEMENT_HIDING] ?: default.elementHiding,
        scriptlets = prefs[KEY_BLOCKING_SCRIPTLETS] ?: default.scriptlets,
        autoUpdateLists = prefs[KEY_BLOCKING_AUTO_UPDATE_LISTS] ?: default.autoUpdateLists,
    )
}
internal fun DefaultSettingsRepository.writeBlockingPrefs(prefs: MutablePreferences, p: BlockingPrefs) {
    prefs[KEY_BLOCKING_ADBLOCK] = p.adblock
    prefs[KEY_BLOCKING_ENABLED_LISTS] = p.enabledLists
    prefs[KEY_BLOCKING_CUSTOM_RULES] = p.customRules
    prefs[KEY_BLOCKING_ALLOWLIST] = p.allowlist
    prefs[KEY_BLOCKING_SHOW_BLOCKED_COUNT] = p.showBlockedCount
    prefs[KEY_BLOCKING_ELEMENT_HIDING] = p.elementHiding
    prefs[KEY_BLOCKING_SCRIPTLETS] = p.scriptlets
    prefs[KEY_BLOCKING_AUTO_UPDATE_LISTS] = p.autoUpdateLists
}
internal fun DefaultSettingsRepository.readProtectionPrefs(prefs: Preferences): ProtectionPrefs {
    val default = ProtectionPrefs()
    return ProtectionPrefs(
        unsafeWarning = prefs[KEY_PROTECTION_UNSAFE_WARNING] ?: default.unsafeWarning,
        potentiallyUnsafeWarning = prefs[KEY_PROTECTION_POTENTIALLY_UNSAFE_WARNING] ?: default.potentiallyUnsafeWarning,
        popupBlocker = prefs[KEY_PROTECTION_POPUP_BLOCKER] ?: default.popupBlocker,
        redirectGuard = prefs[KEY_PROTECTION_REDIRECT_GUARD] ?: default.redirectGuard,
        blockAppLinks = prefs[KEY_PROTECTION_BLOCK_APP_LINKS] ?: default.blockAppLinks,
        askBeforeDownload = prefs[KEY_PROTECTION_ASK_BEFORE_DOWNLOAD] ?: default.askBeforeDownload,
        warnRiskyDownloads = prefs[KEY_PROTECTION_WARN_RISKY_DOWNLOADS] ?: default.warnRiskyDownloads,
        safeBrowsing = prefs[KEY_PROTECTION_SAFE_BROWSING] ?: default.safeBrowsing,
        showVpnState = prefs[KEY_PROTECTION_SHOW_VPN_STATE] ?: default.showVpnState,
        showVpnHomeIcon = prefs[KEY_PROTECTION_SHOW_VPN_HOME_ICON] ?: default.showVpnHomeIcon,
        showIpCountry = prefs[KEY_PROTECTION_SHOW_IP_COUNTRY] ?: default.showIpCountry,
        blockWithoutVpn = prefs[KEY_PROTECTION_BLOCK_WITHOUT_VPN] ?: default.blockWithoutVpn,
    )
}
internal fun DefaultSettingsRepository.writeProtectionPrefs(prefs: MutablePreferences, p: ProtectionPrefs) {
    prefs[KEY_PROTECTION_UNSAFE_WARNING] = p.unsafeWarning
    prefs[KEY_PROTECTION_POTENTIALLY_UNSAFE_WARNING] = p.potentiallyUnsafeWarning
    prefs[KEY_PROTECTION_POPUP_BLOCKER] = p.popupBlocker
    prefs[KEY_PROTECTION_REDIRECT_GUARD] = p.redirectGuard
    prefs[KEY_PROTECTION_BLOCK_APP_LINKS] = p.blockAppLinks
    prefs[KEY_PROTECTION_ASK_BEFORE_DOWNLOAD] = p.askBeforeDownload
    prefs[KEY_PROTECTION_WARN_RISKY_DOWNLOADS] = p.warnRiskyDownloads
    prefs[KEY_PROTECTION_SAFE_BROWSING] = p.safeBrowsing
    prefs[KEY_PROTECTION_SHOW_VPN_STATE] = p.showVpnState
    prefs[KEY_PROTECTION_SHOW_VPN_HOME_ICON] = p.showVpnHomeIcon
    prefs[KEY_PROTECTION_SHOW_IP_COUNTRY] = p.showIpCountry
    prefs[KEY_PROTECTION_BLOCK_WITHOUT_VPN] = p.blockWithoutVpn
}
internal fun DefaultSettingsRepository.readPrivacyPrefs(prefs: Preferences): PrivacyPrefs {
    val default = PrivacyPrefs()
    return PrivacyPrefs(
        blockThirdPartyCookies = prefs[KEY_PRIVACY_BLOCK_THIRD_PARTY_COOKIES] ?: default.blockThirdPartyCookies,
        hideCookieBanners = prefs[KEY_PRIVACY_HIDE_COOKIE_BANNERS] ?: default.hideCookieBanners,
        clearDataOnExit = prefs[KEY_PRIVACY_CLEAR_DATA_ON_EXIT] ?: default.clearDataOnExit,
        privateTabsByDefault = prefs[KEY_PRIVACY_PRIVATE_TABS_BY_DEFAULT] ?: default.privateTabsByDefault,
        sendGpc = prefs[KEY_PRIVACY_SEND_GPC] ?: default.sendGpc,
    )
}
internal fun DefaultSettingsRepository.writePrivacyPrefs(prefs: MutablePreferences, p: PrivacyPrefs) {
    prefs[KEY_PRIVACY_BLOCK_THIRD_PARTY_COOKIES] = p.blockThirdPartyCookies
    prefs[KEY_PRIVACY_HIDE_COOKIE_BANNERS] = p.hideCookieBanners
    prefs[KEY_PRIVACY_CLEAR_DATA_ON_EXIT] = p.clearDataOnExit
    prefs[KEY_PRIVACY_PRIVATE_TABS_BY_DEFAULT] = p.privateTabsByDefault
    prefs[KEY_PRIVACY_SEND_GPC] = p.sendGpc
}
internal fun DefaultSettingsRepository.readCheckPrefs(prefs: Preferences): CheckPrefs {
    val default = CheckPrefs()
    return CheckPrefs(
        virusTotalOn = prefs[KEY_CHECK_VT_ON] ?: default.virusTotalOn,
        virusTotalKey = prefs[KEY_CHECK_VT_KEY] ?: default.virusTotalKey,
        hybridOn = prefs[KEY_CHECK_HA_ON] ?: default.hybridOn,
        hybridKey = prefs[KEY_CHECK_HA_KEY] ?: default.hybridKey,
        holdUntilChecked = prefs[KEY_CHECK_HOLD] ?: default.holdUntilChecked,
        autoCheckRisky = prefs[KEY_CHECK_AUTO_RISKY] ?: default.autoCheckRisky,
        keyPromptShown = prefs[KEY_CHECK_KEY_PROMPT_SHOWN] ?: default.keyPromptShown,
        explainShown = prefs[KEY_CHECK_EXPLAIN_SHOWN] ?: default.explainShown,
        fmhyDownloadStatus = prefs[KEY_CHECK_FMHY_DOWNLOAD_STATUS] ?: default.fmhyDownloadStatus,
    )
}
internal fun DefaultSettingsRepository.writeCheckPrefs(prefs: MutablePreferences, p: CheckPrefs) {
    prefs[KEY_CHECK_VT_ON] = p.virusTotalOn
    prefs[KEY_CHECK_VT_KEY] = p.virusTotalKey.trim()
    prefs[KEY_CHECK_HA_ON] = p.hybridOn
    prefs[KEY_CHECK_HA_KEY] = p.hybridKey.trim()
    prefs[KEY_CHECK_HOLD] = p.holdUntilChecked
    prefs[KEY_CHECK_AUTO_RISKY] = p.autoCheckRisky
    prefs[KEY_CHECK_KEY_PROMPT_SHOWN] = p.keyPromptShown
    prefs[KEY_CHECK_EXPLAIN_SHOWN] = p.explainShown
    prefs[KEY_CHECK_FMHY_DOWNLOAD_STATUS] = p.fmhyDownloadStatus
}
