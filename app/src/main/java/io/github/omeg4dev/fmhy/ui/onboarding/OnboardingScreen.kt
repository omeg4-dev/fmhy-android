package io.github.omeg4dev.fmhy.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.home.FmhyWordmark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingModel = viewModel {
        OnboardingModel(
            settingsRepository = container.settingsRepository,
            userStore = container.userStore,
            appContainer = container,
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visiblePages = viewModel.visiblePages
    val pagerState = rememberPagerState(pageCount = { visiblePages.size })
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // Persist previous page choices when moving between pages
    var lastActiveIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        val newIndex = pagerState.currentPage
        if (newIndex != lastActiveIndex && lastActiveIndex < visiblePages.size) {
            viewModel.persistPage(visiblePages[lastActiveIndex])
        }
        lastActiveIndex = newIndex
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    val currentPage = visiblePages.getOrElse(pagerState.currentPage) { OnboardingPage.WELCOME }
    val isDonePage = currentPage == OnboardingPage.DONE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    if (!isDonePage) {
                        TextButton(
                            onClick = {
                                haptics.click()
                                viewModel.skip(onFinished)
                            },
                        ) {
                            Text("Skip")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                currentPage = pagerState.currentPage,
                pageCount = visiblePages.size,
                isWelcome = currentPage == OnboardingPage.WELCOME,
                isDone = isDonePage,
                onBack = {
                    haptics.tick()
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNext = {
                    haptics.tick()
                    if (isDonePage) {
                        viewModel.finish(onFinished)
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { pageIndex ->
            val page = visiblePages.getOrElse(pageIndex) { OnboardingPage.WELCOME }
            when (page) {
                OnboardingPage.WELCOME -> WelcomePageContent(
                    onSetUp = {
                        haptics.click()
                        scope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                )
                OnboardingPage.LOOK -> LookPageContent(
                    state = state,
                    model = viewModel,
                )
                OnboardingPage.LINKS -> LinksPageContent(
                    state = state,
                    model = viewModel,
                )
                OnboardingPage.PROTECTION -> ProtectionPageContent(
                    state = state,
                    model = viewModel,
                )
                OnboardingPage.LISTS -> ListsPageContent(
                    state = state,
                    model = viewModel,
                )
                OnboardingPage.STAY_UPDATED -> StayUpdatedPageContent(
                    state = state,
                    model = viewModel,
                    container = container,
                )
                OnboardingPage.DONE -> DonePageContent(
                    state = state,
                    onStart = {
                        haptics.click()
                        viewModel.finish(onFinished)
                    },
                )
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    pageCount: Int,
    isWelcome: Boolean,
    isDone: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = navBarInset),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            if (currentPage > 0 && !isDone) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous page",
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Pager dot indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { index ->
                    val isSelected = index == currentPage
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                    )
                }
            }

            // Next / Set up / Start button
            if (isWelcome) {
                Button(onClick = onNext) {
                    Text("Set up")
                }
            } else if (isDone) {
                Button(onClick = onNext) {
                    Text("Start")
                }
            } else {
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next page",
                    )
                }
            }
        }
    }
}

// ==========================================
// 1. WELCOME PAGE
// ==========================================
@Composable
private fun WelcomePageContent(
    onSetUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FmhyWordmark()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "FMHY for Android",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 3 highlight lines
        val highlights = listOf(
            "Follow categories and get told what's new",
            "Browse with uBlock Origin's filter lists built in",
            "Check any link against FMHY's lists",
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            highlights.forEach { text ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = onSetUp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Set up")
        }
    }
}

// ==========================================
// 2. LOOK PAGE
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPageContent(
    state: OnboardingState,
    model: OnboardingModel,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val isMaterialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Look",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose your theme and accent colour",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Live preview (~40% height)
        LookPreview(
            themeMode = state.themeMode,
            colorSource = state.colorSource,
            accentSeed = state.accentSeed,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Theme options
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Triple(ThemeMode.SYSTEM, "System", true),
                Triple(ThemeMode.LIGHT, "Light", false),
                Triple(ThemeMode.DARK, "Dark", false),
            ).forEach { (mode, label, rec) ->
                val isSelected = state.themeMode == mode
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                haptics.tick()
                                model.setThemeMode(mode)
                            },
                        ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (rec) {
                            Spacer(modifier = Modifier.height(4.dp))
                            RecommendedPill()
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accent colours
        Text(
            text = "Accent",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (isMaterialYouAvailable) {
            val isMaterialYouSelected = state.colorSource == ColorSource.MATERIAL_YOU
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isMaterialYouSelected,
                        role = Role.RadioButton,
                        onClick = {
                            haptics.tick()
                            model.setColorSource(ColorSource.MATERIAL_YOU)
                        },
                    )
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    if (isMaterialYouSelected) 2.dp else 1.dp,
                    if (isMaterialYouSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMaterialYouSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Material You", style = MaterialTheme.typography.titleSmall)
                        Text("Match system wallpaper colours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isMaterialYouSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Swatches
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ACCENT_PRESETS.forEach { preset ->
                val swatch = Color(preset.seed)
                val isSelected = state.colorSource == ColorSource.FMHY && state.accentSeed == preset.seed
                val isVioletRec = preset.name == "Violet"

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .background(swatch, CircleShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                haptics.tick()
                                model.setAccentSeed(preset.seed)
                            },
                        )
                        .semantics { contentDescription = "${preset.name} accent ${if (isVioletRec) "(Recommended)" else ""}" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (swatch.luminance() > 0.4f) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 3. LINKS PAGE
// ==========================================
@Composable
private fun LinksPageContent(
    state: OnboardingState,
    model: OnboardingModel,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Links",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Where should links open when you tap them?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Live Preview (~40% height)
        LinksPreview(openLinksWith = state.openLinksWith)
        Spacer(modifier = Modifier.height(16.dp))

        OptionCard(
            title = "In-app browser",
            subtitle = "Ad blocking, site bar, and media controls built in",
            selected = state.openLinksWith == OpenLinksWith.IN_APP,
            isRecommended = true,
            icon = Icons.Default.Shield,
            onClick = {
                haptics.tick()
                model.setOpenLinksWith(OpenLinksWith.IN_APP)
            },
        )
        Spacer(modifier = Modifier.height(10.dp))

        OptionCard(
            title = "Custom tab",
            subtitle = "Opens in a fast Chrome or browser tab over the app",
            selected = state.openLinksWith == OpenLinksWith.CUSTOM_TAB,
            icon = Icons.Default.Tab,
            onClick = {
                haptics.tick()
                model.setOpenLinksWith(OpenLinksWith.CUSTOM_TAB)
            },
        )
        Spacer(modifier = Modifier.height(10.dp))

        OptionCard(
            title = "External browser",
            subtitle = "Hands links off to your default browser app",
            selected = state.openLinksWith == OpenLinksWith.EXTERNAL,
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = {
                haptics.tick()
                model.setOpenLinksWith(OpenLinksWith.EXTERNAL)
            },
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 4. PROTECTION PAGE
// ==========================================
@Composable
private fun ProtectionPageContent(
    state: OnboardingState,
    model: OnboardingModel,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Protection",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose your ad blocking level and protections",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Live preview (~40% height)
        ProtectionPreview(
            adblockLevel = state.adblockLevel,
            blockPopupsAndRedirects = state.blockPopupsAndRedirects,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Options: Balanced, Strict, Off
        OptionCard(
            title = "Balanced",
            subtitle = "about 6 MB download",
            extraContent = {
                Text(
                    text = "uBlock filters, Badware risks, Privacy, Quick fixes, Unbreak, EasyList, EasyPrivacy, Peter Lowe's, FMHY unsafe sites",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            selected = state.adblockLevel == AdblockLevel.BALANCED,
            isRecommended = true,
            onClick = {
                haptics.tick()
                model.setAdblockLevel(AdblockLevel.BALANCED)
            },
        )
        Spacer(modifier = Modifier.height(10.dp))

        OptionCard(
            title = "Strict",
            subtitle = "about 7 MB download",
            extraContent = {
                Text(
                    text = "All Balanced lists + Cookie notices, Annoyances",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            selected = state.adblockLevel == AdblockLevel.STRICT,
            onClick = {
                haptics.tick()
                model.setAdblockLevel(AdblockLevel.STRICT)
            },
        )
        Spacer(modifier = Modifier.height(10.dp))

        OptionCard(
            title = "Off",
            subtitle = "0 MB",
            extraContent = {
                Text(
                    text = "Ad blocking disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            selected = state.adblockLevel == AdblockLevel.OFF,
            onClick = {
                haptics.tick()
                model.setAdblockLevel(AdblockLevel.OFF)
            },
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Switches
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Block pop-ups and redirects")
                    RecommendedPill(text = "On")
                }
            },
            supportingContent = { Text("Blocks intrusive new windows and redirects") },
            trailingContent = {
                Switch(
                    checked = state.blockPopupsAndRedirects,
                    onCheckedChange = {
                        haptics.tick()
                        model.setBlockPopupsAndRedirects(it)
                    },
                )
            },
        )

        ListItem(
            headlineContent = { Text("Hide cookie banners") },
            supportingContent = { Text("Dismiss cookie consent dialogues (rec. off)") },
            trailingContent = {
                Switch(
                    checked = state.hideCookieBanners,
                    onCheckedChange = {
                        haptics.tick()
                        model.setHideCookieBanners(it)
                    },
                )
            },
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 5. LISTS PAGE
// ==========================================
@Composable
private fun ListsPageContent(
    state: OnboardingState,
    model: OnboardingModel,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Lists",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Customize layout, badges, and filters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Live preview (~40% height)
        ListsPreview(
            starredOnly = state.starredOnly,
            newBadgeDays = state.newBadgeDays,
            sectionLayout = state.sectionLayout,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Starred only switch
        ListItem(
            headlineContent = { Text("Starred only") },
            supportingContent = { Text("Show only sites FMHY recommends (⭐ / 🌟, rec. off)") },
            trailingContent = {
                Switch(
                    checked = state.starredOnly,
                    onCheckedChange = {
                        haptics.tick()
                        model.setStarredOnly(it)
                    },
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        // NEW badges
        Text(
            text = "NEW badges",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Pair(0, "Off"),
                Pair(3, "3 days"),
                Pair(7, "7 days"),
                Pair(14, "14 days"),
            ).forEach { (days, label) ->
                val isSelected = state.newBadgeDays == days
                val isRec = days == 7
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                haptics.tick()
                                model.setNewBadgeDays(days)
                            },
                        ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isRec) {
                            Spacer(modifier = Modifier.height(4.dp))
                            RecommendedPill()
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section layout
        Text(
            text = "Section layout",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(
                Triple(SectionLayout.CONTINUOUS, "Continuous", true),
                Triple(SectionLayout.CATEGORIES, "Categories", false),
            ).forEach { (layout, label, isRec) ->
                val isSelected = state.sectionLayout == layout
                OptionCard(
                    title = label,
                    subtitle = if (layout == SectionLayout.CONTINUOUS) "All on one page" else "Drill down by category",
                    selected = isSelected,
                    isRecommended = isRec,
                    onClick = {
                        haptics.tick()
                        model.setSectionLayout(layout)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 6. STAY UPDATED PAGE
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StayUpdatedPageContent(
    state: OnboardingState,
    model: OnboardingModel,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<Section>>(emptyList()) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(container.datasetStore.changes) {
        val loaded = try {
            if (container.datasetStore.isAvailable) container.datasetStore.sections() else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        sections = if (loaded.isNotEmpty()) {
            loaded
        } else {
            listOf(
                Section("video", "Video", "", 0, 0),
                Section("audio", "Audio", "", 1, 0),
                Section("gaming", "Gaming", "", 2, 0),
                Section("reading", "Reading", "", 3, 0),
                Section("downloading", "Downloading", "", 4, 0),
                Section("tools", "Tools", "", 5, 0),
                Section("ai", "Artificial Intelligence", "", 6, 0),
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            model.setNotifyChanges(true)
            permissionDeniedMessage = null
        } else {
            model.setNotifyChanges(false)
            permissionDeniedMessage = "You can turn this on later in Settings"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Stay updated",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Follow sections and receive notifications",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Live preview (~40% height)
        StayUpdatedPreview()
        Spacer(modifier = Modifier.height(16.dp))

        // Multi-select section chips
        Text(
            text = "Follow sections",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEach { section ->
                val isSelected = section.id in state.followedSections
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        haptics.tick()
                        model.toggleSectionFollowed(section.id)
                    },
                    label = { Text(section.title) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notify me switch
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notify me about changes")
                    if (state.followedSections.isNotEmpty()) {
                        RecommendedPill(text = "On")
                    }
                }
            },
            supportingContent = {
                Column {
                    Text("Get notified when followed sections update")
                    if (permissionDeniedMessage != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = permissionDeniedMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            trailingContent = {
                Switch(
                    checked = state.notifyChanges,
                    onCheckedChange = { turningOn ->
                        haptics.tick()
                        if (turningOn) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    model.setNotifyChanges(true)
                                    permissionDeniedMessage = null
                                } else {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                model.setNotifyChanges(true)
                                permissionDeniedMessage = null
                            }
                        } else {
                            model.setNotifyChanges(false)
                        }
                    },
                )
            },
        )

        // Personal Home switch
        ListItem(
            headlineContent = { Text("Personal Home") },
            supportingContent = { Text("Pin your favourite categories to the top of Home (rec. off)") },
            trailingContent = {
                Switch(
                    checked = state.personalHome,
                    onCheckedChange = {
                        haptics.tick()
                        model.setPersonalHome(it)
                    },
                )
            },
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 7. DONE PAGE
// ==========================================
@Composable
private fun DonePageContent(
    state: OnboardingState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAdblockOn = state.openLinksWith == OpenLinksWith.IN_APP && state.adblockLevel != AdblockLevel.OFF

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "All set",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Here is a summary of your choices",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Summary items (one line each)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Look summary
                val themeName = when (state.themeMode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
                val accentName = if (state.colorSource == ColorSource.MATERIAL_YOU) {
                    "Material You"
                } else {
                    ACCENT_PRESETS.firstOrNull { it.seed == state.accentSeed }?.name ?: "Custom"
                }
                SummaryRow(title = "Look", value = "$themeName theme · $accentName accent")

                // Links summary
                val linksName = when (state.openLinksWith) {
                    OpenLinksWith.IN_APP -> "In-app browser"
                    OpenLinksWith.CUSTOM_TAB -> "Custom tab"
                    OpenLinksWith.EXTERNAL -> "External browser"
                }
                SummaryRow(title = "Links", value = linksName)

                // Protection summary
                if (state.openLinksWith == OpenLinksWith.IN_APP) {
                    val adblockName = when (state.adblockLevel) {
                        AdblockLevel.BALANCED -> "Balanced (about 6 MB)"
                        AdblockLevel.STRICT -> "Strict (about 7 MB)"
                        AdblockLevel.OFF -> "Ad blocking off"
                    }
                    val popupText = if (state.blockPopupsAndRedirects) "pop-ups blocked" else "pop-ups allowed"
                    SummaryRow(title = "Protection", value = "$adblockName · $popupText")
                } else {
                    SummaryRow(title = "Protection", value = "Standard browser protection")
                }

                // Lists summary
                val layoutName = if (state.sectionLayout == SectionLayout.CONTINUOUS) "Continuous" else "Categories"
                val badgesName = if (state.newBadgeDays > 0) "${state.newBadgeDays}-day NEW badges" else "No NEW badges"
                val starredText = if (state.starredOnly) "Starred only" else "All sites"
                SummaryRow(title = "Lists", value = "$layoutName · $badgesName · $starredText")

                // Stay updated summary
                val followCount = state.followedSections.size
                val followText = if (followCount > 0) "$followCount sections followed" else "No sections followed"
                val notifText = if (state.notifyChanges) "Notifications on" else "Notifications off"
                SummaryRow(title = "Stay updated", value = "$followText · $notifText")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Adblock filter download notice
        if (isAdblockOn) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Filter lists are downloading in the background.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Start")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "$title: ",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
