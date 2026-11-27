package io.github.omeg4dev.fmhy.ui.settings

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.browser.BrowserActivity
import io.github.omeg4dev.fmhy.data.fmhyx.FmhyXAccountState
import io.github.omeg4dev.fmhy.data.fmhyx.FmhyXSyncError
import io.github.omeg4dev.fmhy.data.fmhyx.SyncLimit
import io.github.omeg4dev.fmhy.ui.shell.formatDataDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FmhyXAccountPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val accountState by viewModel.fmhyXAccountState.collectAsStateWithLifecycle()
    val signInBusy by viewModel.fmhyXSignInBusy.collectAsStateWithLifecycle()
    val signInError by viewModel.fmhyXSignInError.collectAsStateWithLifecycle()
    val deleteBusy by viewModel.fmhyXDeleteBusy.collectAsStateWithLifecycle()
    val pendingMobileAction by viewModel.pendingMobileAction.collectAsStateWithLifecycle()

    var tokenInput by rememberSaveable { mutableStateOf("") }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val eToken = SettingsCatalog.fmhyxAccountToken
    val eSyncNow = SettingsCatalog.fmhyxAccountSyncNow
    val eCopyToken = SettingsCatalog.fmhyxAccountCopyToken
    val eSignOut = SettingsCatalog.fmhyxAccountSignOut
    val eDeleteAccount = SettingsCatalog.fmhyxAccountDeleteAccount

    val signedInState = accountState as? FmhyXAccountState.SignedIn
    val showSignInForm = signedInState == null || signedInState.error is FmhyXSyncError.TokenInvalid

    val sectionSettingIds = remember(signedInState != null, showSignInForm) {
        buildList {
            if (signedInState != null) {
                add(listOf(eSyncNow.id, eCopyToken.id, eSignOut.id, eDeleteAccount.id))
            }
            if (showSignInForm) {
                add(listOf(eToken.id))
            }
        }
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    fun submitSignIn() {
        if (tokenInput.isBlank() || signInBusy) return
        focusManager.clearFocus()
        viewModel.signInFmhyX(tokenInput) {
            tokenInput = ""
            tokenVisible = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FMHY X account") },
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
            if (signedInState != null) {
                item {
                    val statusText = formatFmhyXStatusLine(signedInState)
                    val isErrorStatus = !signedInState.running && signedInState.error != null

                    SettingsSection(title = "Account") {
                        ListItem(
                            headlineContent = {
                                Text("Status", style = MaterialTheme.typography.bodyLarge)
                            },
                            supportingContent = {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isErrorStatus) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                        )
                        SettingsDivider()
                        ButtonRow(
                            text = eSyncNow.title,
                            loading = signedInState.running,
                            enabled = !signedInState.running &&
                                signedInState.error !is FmhyXSyncError.TokenInvalid,
                            settingId = eSyncNow.id,
                            onClick = { viewModel.syncFmhyXNow() },
                        )
                        SettingsDivider()
                        ListItem(
                            headlineContent = {
                                Text(eCopyToken.title, style = MaterialTheme.typography.bodyLarge)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .settingHighlight(eCopyToken.id)
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(role = Role.Button) {
                                    scope.launch {
                                        val token = viewModel.readFmhyXToken()
                                        if (!token.isNullOrEmpty()) {
                                            copySensitiveToken(context, token)
                                            onShowSnackbar("Token copied")
                                        }
                                    }
                                },
                        )
                        SettingsDivider()
                        ListItem(
                            headlineContent = {
                                Text(eSignOut.title, style = MaterialTheme.typography.bodyLarge)
                            },
                            supportingContent = {
                                Text(
                                    text = "Saved sites stay on this phone",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .settingHighlight(eSignOut.id)
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(role = Role.Button) {
                                    viewModel.signOutFmhyX()
                                },
                        )
                        SettingsDivider()
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = eDeleteAccount.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .settingHighlight(eDeleteAccount.id)
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(enabled = !deleteBusy, role = Role.Button) {
                                    showDeleteConfirm = true
                                },
                        )
                    }
                }
            }

            if (showSignInForm) {
                item {
                    val sectionTitle = if (signedInState != null) "Replace token" else "Sign in"
                    SettingsSection(title = sectionTitle) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = {
                                    tokenInput = it
                                    if (signInError != null) {
                                        viewModel.clearFmhyXSignInError()
                                    }
                                },
                                label = { Text("Account token") },
                                placeholder = { Text("fmhyx_…") },
                                singleLine = true,
                                isError = signInError != null,
                                supportingText = signInError?.let { err ->
                                    { Text(err) }
                                },
                                visualTransformation = if (tokenVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                        Icon(
                                            imageVector = if (tokenVisible) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = if (tokenVisible) "Hide token" else "Show token",
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    capitalization = KeyboardCapitalization.None,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitSignIn() },
                                ),
                                modifier = Modifier
                                    .settingHighlight(eToken.id)
                                    .fillMaxWidth(),
                            )

                            Text(
                                text = "Your saved sites and folders are stored on x.fmhy.bid, a service separate from this app. You can delete them there any time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Text(
                                text = "Copy the token from Account on x.fmhy.bid. Keep it: it is the only way to sign in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            FilledTonalButton(
                                onClick = ::submitSignIn,
                                enabled = tokenInput.isNotBlank() && !signInBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                if (signInBusy) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Sign in")
                            }

                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        BrowserActivity.intent(context, FMHYX_ACCOUNT_URL, null)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text("Create account")
                            }
                        }
                    }
                }
            }

            extraRows()
        }

        MobileDataConfirmationDialog(
            visible = pendingMobileAction != null,
            onConfirm = viewModel::confirmMobileAction,
            onDismiss = viewModel::dismissMobileActionDialog,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete FMHY X account?") },
            text = {
                Text("Everything synced on x.fmhy.bid is deleted. Saved sites stay on this phone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteFmhyXAccount {
                            onShowSnackbar("Could not delete the account")
                        }
                    },
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

internal fun formatFmhyXStatusLine(
    state: FmhyXAccountState.SignedIn,
    nowMs: Long = System.currentTimeMillis(),
): String {
    if (state.running) return "Syncing…"
    state.error?.let { err ->
        return formatFmhyXError(err)
    }
    val syncedAt = state.lastSyncedAt
    return if (syncedAt != null) {
        "Last synced ${formatFmhyXSyncTime(syncedAt, nowMs)}"
    } else {
        "Not synced yet"
    }
}

internal fun formatFmhyXError(error: FmhyXSyncError): String = when (error) {
    FmhyXSyncError.Offline -> "Not synced: offline"
    FmhyXSyncError.WaitingForWifi -> "Not synced: waiting for Wi-Fi"
    FmhyXSyncError.TokenInvalid -> "Token not valid"
    FmhyXSyncError.TooMany -> "Too many tries, wait a moment"
    is FmhyXSyncError.Limit -> when (error.limit) {
        SyncLimit.SAVED_ITEMS -> "Not synced: over the FMHY X limit of 5,000 saved items"
        SyncLimit.FOLDERS -> "Not synced: over the FMHY X limit of 50 folders"
        SyncLimit.FOLDER_ITEMS -> "Not synced: over the FMHY X limit of 500 sites per folder"
        SyncLimit.BODY_SIZE -> "Not synced: over the FMHY X limit of 20 MB per sync"
    }
    FmhyXSyncError.Failed -> "Not synced: sync failed"
}

internal fun formatFmhyXSyncTime(
    epochMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): String {
    val diffMs = (nowMs - epochMs).coerceAtLeast(0L)
    val secs = diffMs / 1000L
    if (secs < 60L) return "just now"
    val mins = secs / 60L
    if (mins < 60L) return "${mins}m ago"
    val hours = mins / 60L
    if (hours < 24L) return "${hours}h ago"
    val days = hours / 24L
    if (days < 7L) return "${days}d ago"
    return formatDataDate(epochMs, nowMs)
}

private fun copySensitiveToken(context: Context, token: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("FMHY X token", token)
    val extraKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }
    clip.description.extras = PersistableBundle().apply {
        putBoolean(extraKey, true)
    }
    clipboard.setPrimaryClip(clip)
}

private const val FMHYX_ACCOUNT_URL = "https://x.fmhy.bid"
