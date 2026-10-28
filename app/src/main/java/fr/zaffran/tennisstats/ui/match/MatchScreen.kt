package fr.zaffran.tennisstats.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.input.InputAction
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.navigation.MatchTab
import fr.zaffran.tennisstats.ui.components.MatchStatusBadge
import fr.zaffran.tennisstats.ui.components.opponentLevelLabel
import fr.zaffran.tennisstats.ui.match.feed.ShotFeed
import fr.zaffran.tennisstats.ui.match.finish.DeleteMatchDialog
import fr.zaffran.tennisstats.ui.match.finish.FinishMatchSheet
import fr.zaffran.tennisstats.ui.match.input.ShotTracker
import fr.zaffran.tennisstats.ui.match.stats.MatchStatsContent

@Composable
fun MatchScreen(onBack: () -> Unit, onMatchDeleted: () -> Unit, viewModel: MatchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MatchEvent.ShowMessage -> snackbarHostState.showSnackbar(
                    resources.getString(
                        when (event.message) {
                            MatchMessage.SHOT_NOT_SAVED -> R.string.match_error_shot_not_saved
                            MatchMessage.UNEXPECTED -> R.string.error_unexpected
                        },
                    ),
                )
            }
        }
    }
    val currentOnMatchDeleted by rememberUpdatedState(onMatchDeleted)
    LaunchedEffect(uiState.isMissing, uiState.isLoading) {
        if (uiState.isMissing && !uiState.isLoading) currentOnMatchDeleted()
    }
    // Confirms each recorded shot without looking at the screen.
    val shotVibrator = rememberShotVibrator()
    LaunchedEffect(uiState.feedback?.id) {
        if (uiState.feedback != null) shotVibrator.vibrate()
    }
    KeepScreenOn(enabled = uiState.isInProgress)

    MatchContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTabSelected = viewModel::onTabSelected,
        onInputAction = viewModel::onInputAction,
        onUndo = viewModel::onUndo,
        onDeleteShot = viewModel::onDeleteShot,
        onToggleWinner = viewModel::onToggleWinner,
        onFinishClick = viewModel::onFinishClick,
        onDeleteClick = viewModel::onDeleteClick,
    )

    if (uiState.isFinishSheetVisible) {
        FinishMatchSheet(
            isBusy = uiState.isBusy,
            onFinish = viewModel::onFinish,
            onCancelAndDelete = viewModel::onDeleteClick,
            onDismiss = viewModel::onFinishSheetDismiss,
        )
    }
    if (uiState.isDeleteDialogVisible) {
        DeleteMatchDialog(
            isBusy = uiState.isBusy,
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDeleteDismiss,
        )
    }
}

/** Keeps the screen on while [enabled] (match in progress on screen). */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchContent(
    uiState: MatchUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTabSelected: (MatchTab) -> Unit,
    onInputAction: (InputAction) -> Unit,
    onUndo: () -> Unit,
    onDeleteShot: (shotId: String) -> Unit,
    onToggleWinner: () -> Unit,
    onFinishClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // The bottom inset is handled by the input bar / stats list so that they can draw behind it.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { uiState.match?.let { MatchTitle(it) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val match = uiState.match
                    when {
                        match == null -> Unit

                        match.isInProgress -> TextButton(onClick = onFinishClick) {
                            Text(stringResource(R.string.match_finish), fontWeight = FontWeight.Bold)
                        }

                        else -> MoreMenu(onDeleteClick)
                    }
                },
            )
        },
    ) { innerPadding ->
        val match = uiState.match
        if (uiState.isLoading || match == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                MatchTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = {
                            Text(
                                stringResource(
                                    if (tab == MatchTab.INPUT) R.string.match_tab_input else R.string.match_tab_stats,
                                ),
                            )
                        },
                    )
                }
            }
            when (uiState.selectedTab) {
                MatchTab.INPUT -> InputTab(uiState, onInputAction, onUndo, onDeleteShot, onToggleWinner)

                MatchTab.STATS -> MatchStatsContent(
                    stats = uiState.stats,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun InputTab(
    uiState: MatchUiState,
    onInputAction: (InputAction) -> Unit,
    onUndo: () -> Unit,
    onDeleteShot: (shotId: String) -> Unit,
    onToggleWinner: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Scrolls on small screens; the input bar below never scrolls.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            ShotFeed(
                shotCount = uiState.shotCount,
                recentShots = uiState.recentShots,
                canDelete = uiState.canDeleteShots,
                onDeleteShot = { onDeleteShot(it.id) },
                showDeleteButtons = uiState.isInProgress,
                modifier = Modifier
                    .padding(16.dp)
                    .then(
                        if (uiState.isInProgress) {
                            Modifier
                        } else {
                            Modifier.padding(
                                WindowInsets.navigationBars.asPaddingValues(),
                            )
                        },
                    ),
            )
        }
        if (uiState.isInProgress) {
            ShotTracker(
                input = uiState.input,
                isRecording = uiState.isRecording,
                feedback = uiState.feedback,
                canUndo = uiState.canUndo,
                qualifiableShot = uiState.qualifiableShot,
                canQualify = uiState.canDeleteShots,
                onAction = onInputAction,
                onUndo = onUndo,
                onToggleWinner = onToggleWinner,
            )
        }
    }
}

@Composable
private fun MatchTitle(match: Match) {
    Column {
        Text(
            text = stringResource(R.string.opponent_vs, match.opponentName),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            match.opponentLevel?.let {
                Text(
                    text = opponentLevelLabel(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MatchStatusBadge(match.status, match.result)
        }
    }
}

@Composable
private fun MoreMenu(onDeleteClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.match_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.match_delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    expanded = false
                    onDeleteClick()
                },
            )
        }
    }
}
