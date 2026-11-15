package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.components.AllMetadataPane
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EditMetadataPane
import com.example.ui.components.PhotoOverviewPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShamailEditorApp(
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadFromUri(context.contentResolver, uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadFromUri(context.contentResolver, uri)
        }
    }

    val createPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        if (uri != null) {
            viewModel.saveVerifiedPhotoToUri(context.contentResolver, uri)
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.saveBackupJsonToUri(context.contentResolver, uri)
        }
    }

    val launchGalleryPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val launchFileBrowser = {
        openDocumentLauncher.launch(arrayOf("image/jpeg"))
    }

    val launchSavePhoto = {
        val suggested = state.filename.ifEmpty { "edited_photo.jpg" }
        createPhotoLauncher.launch(suggested)
    }

    val launchSaveBackup = {
        val base = state.filename.ifEmpty { "photo.jpg" }
        createBackupLauncher.launch("$base.metadata-backup.json")
    }

    if (state.hasPhoto && state.selectedTab != EditorTab.OVERVIEW) {
        BackHandler {
            viewModel.selectTab(EditorTab.OVERVIEW)
        }
    }

    state.confirmDialog?.let { dialogState ->
        ConfirmActionDialog(
            state = dialogState,
            onDismiss = viewModel::dismissConfirmDialog,
            onConfirm = viewModel::confirmDialogAction
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp
        val showBottomNav = !isExpanded && state.hasPhoto

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.on_device_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = viewModel::clearSession,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("clear_session_button")
                        ) {
                            Text(
                                text = stringResource(R.string.clear_session),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (showBottomNav) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        EditorTab.entries.forEach { tab ->
                            val selected = state.selectedTab == tab
                            val icon = when (tab) {
                                EditorTab.OVERVIEW -> if (selected) Icons.Filled.Image else Icons.Outlined.Image
                                EditorTab.EDIT -> if (selected) Icons.Filled.Edit else Icons.Outlined.Edit
                                EditorTab.ALL_TAGS -> if (selected) Icons.AutoMirrored.Filled.ListAlt else Icons.AutoMirrored.Outlined.ListAlt
                            }
                            val badgeText = when (tab) {
                                EditorTab.EDIT -> {
                                    val totalMods = state.changedFieldCount + state.removedTagCount
                                    if (totalMods > 0) " ($totalMods)" else ""
                                }
                                EditorTab.ALL_TAGS -> {
                                    val count = state.parsed?.exif?.ifds?.values?.sumOf { it.size } ?: 0
                                    if (count > 0) " ($count)" else ""
                                }
                                EditorTab.OVERVIEW -> ""
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(imageVector = icon, contentDescription = tab.label)
                                },
                                label = {
                                    Text(
                                        text = "${tab.label}$badgeText",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        PhotoOverviewPane(
                            state = state,
                            onPickFromGallery = launchGalleryPicker,
                            onOpenJpegFile = launchFileBrowser
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EditMetadataPane(
                            state = state,
                            onUpdateText = viewModel::updateTextField,
                            onUpdatePlusCode = viewModel::updatePlusCode,
                            onUpdateLat = { viewModel.updateLatLng(latValue = it) },
                            onUpdateLng = { viewModel.updateLatLng(lngValue = it) },
                            onUpdateDirectionRef = viewModel::updateDirectionRef,
                            onUpdateDateParts = viewModel::updateDateParts,
                            onClearField = viewModel::clearField,
                            onResetField = viewModel::resetField,
                            onClearCameraSection = viewModel::clearCameraSection,
                            onClearDateSection = { viewModel.clearField(com.example.exif.FormFieldKey.DATE_TIME) },
                            onClearLocationSection = viewModel::clearLocationSection,
                            onRequestClearAll = viewModel::requestClearAllFieldsDialog,
                            onRequestRemoveAllMetadata = viewModel::requestRemoveAllMetadataDialog,
                            onRevertOriginal = viewModel::revertToOriginal,
                            onToggleDeepScrub = viewModel::toggleDeepScrub,
                            onToggleKeepBackup = viewModel::toggleKeepBackupJson,
                            onApplyAndVerify = viewModel::applyChangesAndVerify,
                            onSavePhoto = launchSavePhoto,
                            onSaveBackupJson = launchSaveBackup
                        )
                        if (state.hasPhoto) {
                            AllMetadataPane(
                                state = state,
                                onUpdateTableText = viewModel::updateTableTextEdit,
                                onResetTableText = viewModel::resetTableTextEdit,
                                onToggleRemoveTag = viewModel::toggleRemoveTag,
                                onToggleRemoveGroup = viewModel::toggleRemoveGroup
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp)
                    ) {
                        when {
                            !state.hasPhoto || state.selectedTab == EditorTab.OVERVIEW -> {
                                PhotoOverviewPane(
                                    state = state,
                                    onPickFromGallery = launchGalleryPicker,
                                    onOpenJpegFile = launchFileBrowser
                                )
                                if (state.hasPhoto) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.medium,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Ready to edit or scrub EXIF",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            OutlinedButton(
                                                onClick = { viewModel.selectTab(EditorTab.EDIT) },
                                                modifier = Modifier.testTag("go_to_edit_button")
                                            ) {
                                                Text("Open Editor")
                                            }
                                        }
                                    }
                                }
                            }
                            state.selectedTab == EditorTab.EDIT -> {
                                EditMetadataPane(
                                    state = state,
                                    onUpdateText = viewModel::updateTextField,
                                    onUpdatePlusCode = viewModel::updatePlusCode,
                                    onUpdateLat = { viewModel.updateLatLng(latValue = it) },
                                    onUpdateLng = { viewModel.updateLatLng(lngValue = it) },
                                    onUpdateDirectionRef = viewModel::updateDirectionRef,
                                    onUpdateDateParts = viewModel::updateDateParts,
                                    onClearField = viewModel::clearField,
                                    onResetField = viewModel::resetField,
                                    onClearCameraSection = viewModel::clearCameraSection,
                                    onClearDateSection = { viewModel.clearField(com.example.exif.FormFieldKey.DATE_TIME) },
                                    onClearLocationSection = viewModel::clearLocationSection,
                                    onRequestClearAll = viewModel::requestClearAllFieldsDialog,
                                    onRequestRemoveAllMetadata = viewModel::requestRemoveAllMetadataDialog,
                                    onRevertOriginal = viewModel::revertToOriginal,
                                    onToggleDeepScrub = viewModel::toggleDeepScrub,
                                    onToggleKeepBackup = viewModel::toggleKeepBackupJson,
                                    onApplyAndVerify = viewModel::applyChangesAndVerify,
                                    onSavePhoto = launchSavePhoto,
                                    onSaveBackupJson = launchSaveBackup
                                )
                            }
                            state.selectedTab == EditorTab.ALL_TAGS -> {
                                AllMetadataPane(
                                    state = state,
                                    onUpdateTableText = viewModel::updateTableTextEdit,
                                    onResetTableText = viewModel::resetTableTextEdit,
                                    onToggleRemoveTag = viewModel::toggleRemoveTag,
                                    onToggleRemoveGroup = viewModel::toggleRemoveGroup
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
