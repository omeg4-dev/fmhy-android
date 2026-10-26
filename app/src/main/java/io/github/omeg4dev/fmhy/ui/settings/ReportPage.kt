package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onReportProblem: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val reportOfferCrashes by viewModel.reportOfferCrashes.collectAsStateWithLifecycle()

    val eReportProblem = SettingsCatalog.reportReportProblem
    val eOfferToReportCrashes = SettingsCatalog.reportOfferToReportCrashes

    val reportIds = listOf(eReportProblem.id, eOfferToReportCrashes.id)
    val sectionSettingIds = remember { listOf(reportIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report a problem") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsSection(title = "Problem reports") {
                    NavRow(
                        title = eReportProblem.title,
                        summary = "Tell us what went wrong or what is missing",
                        settingId = eReportProblem.id,
                        onClick = onReportProblem,
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eOfferToReportCrashes.title,
                        summary = eOfferToReportCrashes.description,
                        checked = reportOfferCrashes,
                        settingId = eOfferToReportCrashes.id,
                        onChange = { checked ->
                            viewModel.setReportOfferCrashes(checked)
                        },
                    )
                }
            }

            extraRows()
        }
    }
}
