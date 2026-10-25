package io.github.omeg4dev.fmhy.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.enterStagger
import io.github.omeg4dev.fmhy.ui.shell.adblockStep
import kotlin.math.floor
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // Persist previous page choices and track visited pages
    var lastActiveIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) {
        val newIndex = pagerState.currentPage
        if (newIndex != lastActiveIndex && lastActiveIndex < visiblePages.size) {
            viewModel.persistPage(visiblePages[lastActiveIndex])
        }
        lastActiveIndex = newIndex
        if (newIndex in visiblePages.indices) {
            viewModel.markPageVisited(visiblePages[newIndex])
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    val currentPageIndex = pagerState.currentPage
    val isDonePage = currentPageIndex == visiblePages.size - 1

    Scaffold(
        topBar = {
            OnboardingTopBar(
                currentPage = currentPageIndex,
                pageCount = visiblePages.size,
                onSkip = {
                    viewModel.skip(onComplete = onFinished)
                },
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                currentPage = currentPageIndex,
                pageCount = visiblePages.size,
                onBack = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNext = {
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
        contentWindowInsets = WindowInsets(0),
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
                OnboardingPage.WELCOME -> WelcomePageContent()
                OnboardingPage.HELLO -> HelloPageContent(
                    state = state,
                    model = viewModel,
                )
                OnboardingPage.BROWSING -> BrowsingPageContent(
                    state = state,
                    model = viewModel,
                )
                OnboardingPage.FOLLOW -> FollowPageContent(
                    state = state,
                    model = viewModel,
                    container = container,
                )
                OnboardingPage.DONE -> DonePageContent(
                    state = state,
                    container = container,
                )
            }
        }
    }
}

/**
 * Top bar: 5-segment progress bar (4 dp tall, 4 dp gaps, rounded, 16 dp side padding)
 * with animated fill as you move.
 * "Skip" text button at top-right on pages 1-3.
 */
@Composable
private fun OnboardingTopBar(
    currentPage: Int,
    pageCount: Int,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp),
    ) {
        // Segmented progress bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(pageCount) { index ->
                val fill by animateFloatAsState(
                    targetValue = if (index <= currentPage) 1f else 0f,
                    animationSpec = Motion.standard(),
                    label = "segment_fill_$index",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        // Top right Skip text button on pages 1-3 (indices 0, 1, 2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(48.dp),
        ) {
            if (currentPage < pageCount - 1) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(48.dp),
                ) {
                    Text("Skip")
                }
            }
        }
    }
}

/**
 * Bottom bar: One full-width primary Button (56 dp tall, 16 dp margins)
 * whose label changes per page with AnimatedContent (slide up + fade):
 * "Get started" -> "Next" -> "Next" -> "Start using FMHY".
 * A small "Back" text button to its left on pages 2-3 (indices 1 & 2).
 */
@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonLabel = when (currentPage) {
        0 -> "Get started"
        pageCount - 1 -> "Start using FMHY"
        else -> "Next"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Small "Back" text button on pages 2-3 (indices 1 and 2)
            AnimatedVisibility(
                visible = currentPage in 1 until (pageCount - 1),
                enter = fadeIn(Motion.standard()) + expandHorizontally(Motion.standard()),
                exit = fadeOut(Motion.standard()) + shrinkHorizontally(Motion.standard()),
            ) {
                Row {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text("Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                AnimatedContent(
                    targetState = buttonLabel,
                    transitionSpec = {
                        (slideInVertically(Motion.standard()) { it / 2 } + fadeIn(Motion.standard()))
                            .togetherWith(slideOutVertically(Motion.standard()) { -it / 2 } + fadeOut(Motion.standard()))
                    },
                    label = "bottom_button_label",
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. BROWSING PAGE (Merges LINKS + PROTECTION)
// ==========================================
@Composable
private fun BrowsingPageContent(
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
        // Title (index 0)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 0, key = OnboardingPage.BROWSING),
        ) {
            Text(
                text = "Where should links open?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose how web pages and safety checks are handled.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live preview (index 1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 1, key = OnboardingPage.BROWSING),
        ) {
            BrowsingPreview(
                openLinksWith = state.openLinksWith,
                adblockLevel = state.adblockLevel,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Link destination options (index 2)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 2, key = OnboardingPage.BROWSING),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
        }

        // Expandable Ad blocking section when In-app is selected
        AnimatedVisibility(
            visible = state.openLinksWith == OpenLinksWith.IN_APP,
            enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
            exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.standard()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(
                    text = "Ad blocking",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pop-up switch
                ListItem(
                    headlineContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Block pop-ups and redirects")
                            RecommendedPill(text = "On")
                        }
                    },
                    supportingContent = { Text("Blocks intrusive new windows and redirects") },
                    trailingContent = {
                        Switch(
                            checked = state.blockPopupsAndRedirects,
                            onCheckedChange = {
                                haptics.toggle(it)
                                model.setBlockPopupsAndRedirects(it)
                            },
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// 3. FOLLOW PAGE
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowPageContent(
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
        // Title (index 0)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 0, key = OnboardingPage.FOLLOW),
        ) {
            Text(
                text = "Follow what you care about",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Get a quiet heads-up when FMHY adds sites there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Notification preview card with bounce (index 1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 1, key = OnboardingPage.FOLLOW),
        ) {
            StayUpdatedPreview(
                followedSections = state.followedSections,
                sections = sections,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section chips with section icons, entering staggered (index 2)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 2, key = OnboardingPage.FOLLOW),
        ) {
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
                sections.forEachIndexed { chipIndex, section ->
                    val isSelected = section.id in state.followedSections
                    FilterChip(
                        modifier = Modifier.enterStaggerChip(index = chipIndex, key = OnboardingPage.FOLLOW),
                        selected = isSelected,
                        onClick = {
                            haptics.toggle(!isSelected)
                            model.toggleSectionFollowed(section.id)
                        },
                        label = { Text(section.title) },
                        leadingIcon = {
                            Icon(
                                imageVector = sectionIcon(section.id),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications switch (index 3)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 3, key = OnboardingPage.FOLLOW),
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                            haptics.toggle(turningOn)
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
        }

        // Personal Home switch (index 4)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 4, key = OnboardingPage.FOLLOW),
        ) {
            ListItem(
                headlineContent = { Text("Personal Home") },
                supportingContent = { Text("Pin your favourite sections on Home") },
                trailingContent = {
                    Switch(
                        checked = state.personalHome,
                        onCheckedChange = {
                            haptics.toggle(it)
                            model.setPersonalHome(it)
                        },
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Custom stagger for chips: 30 ms delay per chip.
 */
private fun Modifier.enterStaggerChip(index: Int, key: Any?): Modifier = composed {
    val progress = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(key) {
        delay(index * 30L)
        progress.animateTo(1f, Motion.enter(Motion.MEDIUM))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * with(density) { 16.dp.toPx() }
    }
}

// ==========================================
// 4. DONE PAGE
// ==========================================
@Composable
private fun DonePageContent(
    state: OnboardingState,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val isAdblockOn = state.openLinksWith == OpenLinksWith.IN_APP && state.adblockLevel != AdblockLevel.OFF
    val adblockStatus = runCatching { container.adblockService.status }
        .getOrNull()
        ?.collectAsStateWithLifecycle()
        ?.value

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 96 dp primary circle with check that draws itself + confetti burst (index 0)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enterStagger(index = 0, key = OnboardingPage.DONE)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            DoneCelebration()
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Title: "You're set" (index 1)
        Text(
            text = "You're set",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.enterStagger(index = 1, key = OnboardingPage.DONE),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Filter download progress indicator (index 2)
        if (isAdblockOn) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .enterStagger(index = 2, key = OnboardingPage.DONE),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val step = adblockStatus?.step?.let(::adblockStep)
                        val isReady = adblockStatus?.ready == true && adblockStatus.step == null && !adblockStatus.updating

                        if (isReady) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Ad blocking is ready",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            val text = if (step != null) {
                                if (step.measured) "${step.label} · ${floor(step.fraction * 100).toInt()}%" else step.label
                            } else {
                                "Downloading ad-block lists…"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (step != null && step.measured) {
                                LinearProgressIndicator(
                                    progress = { step.fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3 Compact tip cards (icon + one line), entering staggered (indices 3, 4, 5)
        val tips = listOf(
            Icons.Outlined.TouchApp to "Tap a site to open it, hold it for more options",
            Icons.Default.Share to "Share a link to FMHY to check it",
            Icons.Default.NewReleases to "Open What's new to see the latest changes",
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tips.forEachIndexed { i, (icon, text) ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .enterStagger(index = 3 + i, key = OnboardingPage.DONE),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
