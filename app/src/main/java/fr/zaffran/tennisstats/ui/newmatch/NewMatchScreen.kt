package fr.zaffran.tennisstats.ui.newmatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.OpponentLevel
import fr.zaffran.tennisstats.ui.components.ThemePreviews
import fr.zaffran.tennisstats.ui.components.opponentLevelLabel
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme

@Composable
fun NewMatchScreen(
    onBack: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    viewModel: NewMatchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NewMatchEvent.OpenMatch -> onOpenMatch(event.matchId)
            }
        }
    }
    NewMatchContent(
        uiState = uiState,
        onBack = onBack,
        onOpponentNameChange = viewModel::onOpponentNameChange,
        onOpponentLevelChange = viewModel::onOpponentLevelChange,
        onStart = viewModel::onStart,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMatchContent(
    uiState: NewMatchUiState,
    onBack: () -> Unit,
    onOpponentNameChange: (String) -> Unit,
    onOpponentLevelChange: (String?) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_match_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OpponentNameField(
                name = uiState.opponentName,
                isError = uiState.error == NewMatchError.OPPONENT_NAME_REQUIRED,
                enabled = !uiState.isCreating,
                onNameChange = onOpponentNameChange,
                onDone = onStart,
            )
            OpponentLevelField(
                level = uiState.opponentLevel,
                enabled = !uiState.isCreating,
                onLevelChange = onOpponentLevelChange,
            )
            when (uiState.error) {
                NewMatchError.MATCH_ALREADY_IN_PROGRESS -> FormError(R.string.error_match_already_in_progress)
                NewMatchError.UNEXPECTED -> FormError(R.string.error_unexpected)
                NewMatchError.OPPONENT_NAME_REQUIRED, null -> Unit
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                enabled = !uiState.isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.new_match_creating))
                } else {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.new_match_start))
                }
            }
        }
    }
}

@Composable
private fun OpponentNameField(
    name: String,
    isError: Boolean,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Focus and keyboard only on first display, not after a configuration change.
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoFocused) {
            focusRequester.requestFocus()
            keyboard?.show()
            autoFocused = true
        }
    }
    val errorText = stringResource(R.string.error_opponent_name_required)
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.new_match_opponent_name)) },
        placeholder = { Text(stringResource(R.string.new_match_opponent_name_placeholder)) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = if (isError) {
            { Text(errorText) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { if (isError) error(errorText) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpponentLevelField(level: String?, enabled: Boolean, onLevelChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        val levelText = level?.let { opponentLevelLabel(it) } ?: ""
        // Read-only field: recreated when the selection changes.
        val textState = remember(levelText) { TextFieldState(levelText) }
        OutlinedTextField(
            state = textState,
            readOnly = true,
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(stringResource(R.string.new_match_opponent_level)) },
            // Keeps the label above the field so that the placeholder is visible without focus.
            labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
            placeholder = { Text(stringResource(R.string.new_match_opponent_level_placeholder)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_match_opponent_level_none)) },
                onClick = {
                    onLevelChange(null)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            OpponentLevel.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(opponentLevelLabel(option.value)) },
                    onClick = {
                        onLevelChange(option.value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun FormError(textRes: Int) {
    Text(
        text = stringResource(textRes),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@ThemePreviews
@Composable
private fun NewMatchPreview() {
    TennisStatsTheme {
        NewMatchContent(
            uiState = NewMatchUiState(opponentName = "Jean Dupont", opponentLevel = "15/4"),
            onBack = {},
            onOpponentNameChange = {},
            onOpponentLevelChange = {},
            onStart = {},
        )
    }
}

@ThemePreviews
@Composable
private fun NewMatchErrorPreview() {
    TennisStatsTheme {
        NewMatchContent(
            uiState = NewMatchUiState(error = NewMatchError.OPPONENT_NAME_REQUIRED),
            onBack = {},
            onOpponentNameChange = {},
            onOpponentLevelChange = {},
            onStart = {},
        )
    }
}
