package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.exif.BuildAndVerifyResult
import com.example.exif.FormFieldKey
import com.example.exif.JpegExifEngine
import com.example.ui.EditorUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalSemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataPane(
    state: EditorUiState,
    onUpdateText: (FormFieldKey, String) -> Unit,
    onUpdatePlusCode: (String) -> Unit,
    onUpdateLat: (String) -> Unit,
    onUpdateLng: (String) -> Unit,
    onUpdateDirectionRef: (String) -> Unit,
    onUpdateDateParts: (date: String?, hour: String?, minute: String?, second: String?, ampm: String?) -> Unit,
    onClearField: (FormFieldKey) -> Unit,
    onResetField: (FormFieldKey) -> Unit,
    onClearCameraSection: () -> Unit,
    onClearDateSection: () -> Unit,
    onClearLocationSection: () -> Unit,
    onRequestClearAll: () -> Unit,
    onRequestRemoveAllMetadata: () -> Unit,
    onRevertOriginal: () -> Unit,
    onToggleDeepScrub: (Boolean) -> Unit,
    onToggleKeepBackup: (Boolean) -> Unit,
    onApplyAndVerify: () -> Unit,
    onSavePhoto: () -> Unit,
    onSaveBackupJson: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val enabled = state.hasPhoto
    val init = state.initialFields

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val initialMillis = remember(state.dateParts.date) {
            if (state.dateParts.date.isNotEmpty()) {
                try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(state.dateParts.date)?.time
                } catch (_: Exception) {
                    null
                }
            } else null
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(Date(millis))
                            onUpdateDateParts(formatted, null, null, null, null)
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit metadata", style = MaterialTheme.typography.titleMedium)
                val hint = when {
                    state.parsed?.exif != null ->
                        "Byte order: ${if (state.parsed.exif.littleEndian) "Little-Endian (II)" else "Big-Endian (MM)"}"
                    state.hasPhoto -> "No existing EXIF (MM)"
                    else -> "Load a photo to begin"
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bulk toolbar
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRequestClearAll,
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("clear_all_fields_button")
                ) {
                    Text("Clear all fields", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onRequestRemoveAllMetadata,
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("remove_all_metadata_button")
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remove ALL metadata", style = MaterialTheme.typography.labelMedium)
                }
                if (state.anyModification) {
                    OutlinedButton(
                        onClick = onRevertOriginal,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("revert_original_button")
                    ) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Revert to original", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            AnimatedVisibility(visible = state.removeAllMetadata) {
                StatusBanner(
                    title = "All metadata marked for removal",
                    message = stringResource(R.string.remove_all_banner),
                    isWarning = true
                )
            }

            // SECTION 1: Camera & Text
            SubSectionCard(
                title = "Camera & text",
                enabled = enabled,
                onClearSection = onClearCameraSection,
                clearTag = "clear_camera_section_button"
            ) {
                EditableMetadataField(
                    fieldKey = FormFieldKey.MAKE,
                    label = "Camera Make",
                    value = state.make,
                    originalDisplay = init?.make.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.MAKE),
                    enabled = enabled,
                    onValueChange = { onUpdateText(FormFieldKey.MAKE, it) },
                    onClear = { onClearField(FormFieldKey.MAKE) },
                    onReset = { onResetField(FormFieldKey.MAKE) }
                )
                EditableMetadataField(
                    fieldKey = FormFieldKey.MODEL,
                    label = "Camera Model",
                    value = state.model,
                    originalDisplay = init?.model.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.MODEL),
                    enabled = enabled,
                    onValueChange = { onUpdateText(FormFieldKey.MODEL, it) },
                    onClear = { onClearField(FormFieldKey.MODEL) },
                    onReset = { onResetField(FormFieldKey.MODEL) }
                )
                EditableMetadataField(
                    fieldKey = FormFieldKey.LENS_MODEL,
                    label = "Lens Model",
                    value = state.lensModel,
                    originalDisplay = init?.lensModel.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.LENS_MODEL),
                    enabled = enabled,
                    onValueChange = { onUpdateText(FormFieldKey.LENS_MODEL, it) },
                    onClear = { onClearField(FormFieldKey.LENS_MODEL) },
                    onReset = { onResetField(FormFieldKey.LENS_MODEL) }
                )
                EditableMetadataField(
                    fieldKey = FormFieldKey.SOFTWARE,
                    label = "Software",
                    value = state.software,
                    originalDisplay = init?.software.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.SOFTWARE),
                    enabled = enabled,
                    onValueChange = { onUpdateText(FormFieldKey.SOFTWARE, it) },
                    onClear = { onClearField(FormFieldKey.SOFTWARE) },
                    onReset = { onResetField(FormFieldKey.SOFTWARE) }
                )
                EditableMetadataField(
                    fieldKey = FormFieldKey.ARTIST,
                    label = "Artist",
                    value = state.artist,
                    originalDisplay = init?.artist.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.ARTIST),
                    enabled = enabled,
                    onValueChange = { onUpdateText(FormFieldKey.ARTIST, it) },
                    onClear = { onClearField(FormFieldKey.ARTIST) },
                    onReset = { onResetField(FormFieldKey.ARTIST) }
                )
                EditableMetadataField(
                    fieldKey = FormFieldKey.COPYRIGHT,
                    label = "Copyright",
                    value = state.copyright,
                    originalDisplay = init?.copyright.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.COPYRIGHT),
                    enabled = enabled,
                    onValueChange = { onUpdateText(FormFieldKey.COPYRIGHT, it) },
                    onClear = { onClearField(FormFieldKey.COPYRIGHT) },
                    onReset = { onResetField(FormFieldKey.COPYRIGHT) }
                )
                EditableMetadataField(
                    fieldKey = FormFieldKey.DESCRIPTION,
                    label = "Image Description / Caption",
                    value = state.description,
                    originalDisplay = init?.description.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.DESCRIPTION),
                    enabled = enabled,
                    onValueChange = { onUpdateText(FormFieldKey.DESCRIPTION, it) },
                    onClear = { onClearField(FormFieldKey.DESCRIPTION) },
                    onReset = { onResetField(FormFieldKey.DESCRIPTION) }
                )
                EditableMetadataField(
                    fieldKey = FormFieldKey.USER_COMMENT,
                    label = "User Comment",
                    value = state.userComment,
                    originalDisplay = init?.userComment.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.USER_COMMENT),
                    enabled = enabled,
                    singleLine = false,
                    onValueChange = { onUpdateText(FormFieldKey.USER_COMMENT, it) },
                    onClear = { onClearField(FormFieldKey.USER_COMMENT) },
                    onReset = { onResetField(FormFieldKey.USER_COMMENT) }
                )
            }

            // SECTION 2: Date & Time
            SubSectionCard(
                title = "Date & time",
                enabled = enabled,
                onClearSection = onClearDateSection,
                clearTag = "clear_date_section_button",
                extraHeaderAction = {
                    OutlinedButton(
                        onClick = onClearDateSection,
                        enabled = enabled,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("remove_date_button")
                    ) {
                        Text("Remove date/time", style = MaterialTheme.typography.labelSmall)
                    }
                }
            ) {
                FieldEnvelope(
                    label = "Timestamp (DateTimeOriginal / Digitized / ModifyDate)",
                    isChanged = state.isFieldChanged(FormFieldKey.DATE_TIME),
                    originalDisplay = init?.dateStr.orEmpty(),
                    enabled = enabled,
                    clearTag = "clear_date_time",
                    resetTag = "reset_date_time",
                    onClear = { onClearField(FormFieldKey.DATE_TIME) },
                    onReset = { onResetField(FormFieldKey.DATE_TIME) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            enabled = enabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("date_picker_button")
                        ) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.dateParts.date.ifEmpty { "Select calendar date (YYYY-MM-DD)" },
                                fontFamily = if (state.dateParts.date.isNotEmpty()) JetBrainsMono else MaterialTheme.typography.bodyMedium.fontFamily
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TimeDropdownSelector(
                                label = "Hour",
                                value = state.dateParts.hour.ifEmpty { "--" },
                                options = listOf("") + (1..12).map { it.toString() },
                                optionLabel = { if (it.isEmpty()) "--" else it.padStart(2, '0') },
                                enabled = enabled,
                                onSelect = { onUpdateDateParts(null, it, null, null, null) },
                                modifier = Modifier.weight(1f)
                            )
                            TimeDropdownSelector(
                                label = "Min",
                                value = state.dateParts.minute.ifEmpty { "--" },
                                options = listOf("") + (0..59).map { it.toString().padStart(2, '0') },
                                optionLabel = { it.ifEmpty { "--" } },
                                enabled = enabled,
                                onSelect = { onUpdateDateParts(null, null, it, null, null) },
                                modifier = Modifier.weight(1f)
                            )
                            TimeDropdownSelector(
                                label = "Sec",
                                value = state.dateParts.second.ifEmpty { "--" },
                                options = listOf("") + (0..59).map { it.toString().padStart(2, '0') },
                                optionLabel = { it.ifEmpty { "--" } },
                                enabled = enabled,
                                onSelect = { onUpdateDateParts(null, null, null, it, null) },
                                modifier = Modifier.weight(1f)
                            )
                            TimeDropdownSelector(
                                label = "AM/PM",
                                value = state.dateParts.ampm,
                                options = listOf("AM", "PM"),
                                optionLabel = { it },
                                enabled = enabled,
                                onSelect = { onUpdateDateParts(null, null, null, null, it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // SECTION 3: Location
            SubSectionCard(
                title = "Location",
                enabled = enabled,
                onClearSection = onClearLocationSection,
                clearTag = "clear_location_section_button"
            ) {
                EditableMetadataField(
                    fieldKey = FormFieldKey.PLUS_CODE,
                    label = "Google Plus Code (10+ digits)",
                    value = state.plusCode,
                    originalDisplay = state.initialPlusCode,
                    isChanged = state.isFieldChanged(FormFieldKey.PLUS_CODE),
                    enabled = enabled,
                    mono = true,
                    onValueChange = onUpdatePlusCode,
                    onClear = { onClearField(FormFieldKey.PLUS_CODE) },
                    onReset = { onResetField(FormFieldKey.PLUS_CODE) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EditableMetadataField(
                        fieldKey = FormFieldKey.LAT,
                        label = "Latitude (-90..90)",
                        value = state.lat,
                        originalDisplay = init?.lat?.toString().orEmpty(),
                        isChanged = state.isFieldChanged(FormFieldKey.LAT),
                        enabled = enabled,
                        mono = true,
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = onUpdateLat,
                        onClear = { onClearField(FormFieldKey.LAT) },
                        onReset = { onResetField(FormFieldKey.LAT) },
                        modifier = Modifier.weight(1f)
                    )
                    EditableMetadataField(
                        fieldKey = FormFieldKey.LNG,
                        label = "Longitude (-180..180)",
                        value = state.lng,
                        originalDisplay = init?.lng?.toString().orEmpty(),
                        isChanged = state.isFieldChanged(FormFieldKey.LNG),
                        enabled = enabled,
                        mono = true,
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = onUpdateLng,
                        onClear = { onClearField(FormFieldKey.LNG) },
                        onReset = { onResetField(FormFieldKey.LNG) },
                        modifier = Modifier.weight(1f)
                    )
                }

                EditableMetadataField(
                    fieldKey = FormFieldKey.ALTITUDE,
                    label = "Altitude (m)",
                    value = state.altitude,
                    originalDisplay = init?.altitude?.let { "$it m" }.orEmpty(),
                    isChanged = state.isFieldChanged(FormFieldKey.ALTITUDE),
                    enabled = enabled,
                    mono = true,
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = { onUpdateText(FormFieldKey.ALTITUDE, it) },
                    onClear = { onClearField(FormFieldKey.ALTITUDE) },
                    onReset = { onResetField(FormFieldKey.ALTITUDE) }
                )

                FieldEnvelope(
                    label = "Direction (0–360°)",
                    isChanged = state.isFieldChanged(FormFieldKey.DIRECTION),
                    originalDisplay = init?.direction?.let { "$it° ${init.directionRef}" }.orEmpty(),
                    enabled = enabled,
                    clearTag = "clear_direction",
                    resetTag = "reset_direction",
                    onClear = { onClearField(FormFieldKey.DIRECTION) },
                    onReset = { onResetField(FormFieldKey.DIRECTION) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.direction,
                            onValueChange = { onUpdateText(FormFieldKey.DIRECTION, it) },
                            enabled = enabled,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("field_direction")
                        )
                        TimeDropdownSelector(
                            label = "Ref",
                            value = if (state.directionRef == "M") "Magnetic (M)" else "True (T)",
                            options = listOf("T", "M"),
                            optionLabel = { if (it == "M") "Magnetic (M)" else "True (T)" },
                            enabled = enabled,
                            onSelect = onUpdateDirectionRef,
                            modifier = Modifier.width(124.dp)
                        )
                    }
                }
            }

            // Deep Scrub Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(semantic.surfaceAlt)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Deep Scrub", style = MaterialTheme.typography.titleSmall)
                    }
                    Switch(
                        checked = state.deepScrub,
                        onCheckedChange = onToggleDeepScrub,
                        enabled = enabled,
                        modifier = Modifier.testTag("deep_scrub_switch")
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Off (default): Keep all existing EXIF tags and modify only fields marked Changed. XMP & IPTC are dropped whenever any edit is made.\n" +
                        "On: Erase all EXIF, MakerNotes, XMP, IPTC, comments, other APPn blocks, and JFXX thumbnails, writing back only form fields plus Orientation.\n" +
                        "JFIF, ICC profile, and Adobe APP14 are always preserved. Trailing data after EOI, IFD1 thumbnails, and MPF APP2 are always removed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Optional Separate Backup Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(semantic.surfaceAlt)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Original metadata backup", style = MaterialTheme.typography.titleSmall)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = if (state.keepBackupJson) "Separate backup file enabled" else "Don't keep any backup (default)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = enabled) { onToggleKeepBackup(!state.keepBackupJson) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = state.keepBackupJson,
                        onCheckedChange = onToggleKeepBackup,
                        enabled = enabled,
                        modifier = Modifier.testTag("backup_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Also save a backup of the original metadata",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = stringResource(R.string.backup_warning_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!state.keepBackupJson) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.no_backup_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Apply, Verify & Save Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.hasPhoto) {
                        Surface(
                            color = semantic.surfaceAlt,
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "${state.changedFieldCount} field${if (state.changedFieldCount == 1) "" else "s"} changed, ${state.removedTagCount} tag${if (state.removedTagCount == 1) "" else "s"} removed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("live_counter_badge")
                            )
                        }
                    }

                    Button(
                        onClick = onApplyAndVerify,
                        enabled = enabled && !state.isApplying,
                        modifier = Modifier.testTag("apply_changes_button")
                    ) {
                        Text(if (state.isApplying) "Verifying..." else "Apply changes")
                    }
                }

                when (val res = state.buildResult) {
                    is BuildAndVerifyResult.ValidationError -> {
                        StatusBanner(
                            title = "Validation error",
                            message = res.message,
                            isError = true
                        )
                    }
                    is BuildAndVerifyResult.VerificationBlocked -> {
                        StatusBanner(
                            title = "Save blocked by output verification",
                            message = "Original or removed data still appears in the output bytes:",
                            bullets = res.violations,
                            footerNote = "Remove the listed tag/segment in All Tags or enable Deep Scrub, then try again.",
                            isError = true,
                            modifier = Modifier.testTag("verification_blocked_banner")
                        )
                    }
                    is BuildAndVerifyResult.VerifiedReady -> {
                        StatusBanner(
                            title = "Verified & ready to save",
                            message = "Original size: ${JpegExifEngine.formatBytes(res.originalSize)} → New size: ${JpegExifEngine.formatBytes(res.newSize)}",
                            bullets = res.changes,
                            isSuccess = true,
                            modifier = Modifier.testTag("verified_ready_banner")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onSavePhoto,
                                modifier = Modifier.testTag("save_photo_button")
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save photo")
                            }
                            if (res.backupJson != null) {
                                OutlinedButton(
                                    onClick = onSaveBackupJson,
                                    modifier = Modifier.testTag("save_backup_button")
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save metadata backup (.json)")
                                }
                            }
                        }
                    }
                    null -> Unit
                }

                if (state.ioError != null) {
                    StatusBanner(
                        title = "File write error",
                        message = state.ioError,
                        isError = true
                    )
                }

                if (state.savedPhotoConfirmed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(semantic.verifiedContainer)
                            .border(1.dp, semantic.verified, MaterialTheme.shapes.medium)
                            .padding(12.dp)
                            .testTag("saved_confirmation_banner"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = semantic.verified,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.saved_photo_confirmation),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (state.savedBackupConfirmed) {
                    StatusBanner(
                        title = "Backup saved separately",
                        message = "Metadata backup JSON saved. Remember to store it privately.",
                        isSuccess = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SubSectionCard(
    title: String,
    enabled: Boolean,
    onClearSection: () -> Unit,
    clearTag: String,
    extraHeaderAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val semantic = LocalSemanticColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(semantic.surfaceAlt)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (extraHeaderAction != null) extraHeaderAction()
                OutlinedButton(
                    onClick = onClearSection,
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag(clearTag)
                ) {
                    Text("Clear section", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        content()
    }
}

@Composable
private fun EditableMetadataField(
    fieldKey: FormFieldKey,
    label: String,
    value: String,
    originalDisplay: String,
    isChanged: Boolean,
    enabled: Boolean,
    mono: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keySlug = fieldKey.name.lowercase(Locale.ROOT)
    FieldEnvelope(
        label = label,
        isChanged = isChanged,
        originalDisplay = originalDisplay,
        enabled = enabled,
        clearTag = "clear_$keySlug",
        resetTag = "reset_$keySlug",
        onClear = onClear,
        onReset = onReset,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            maxLines = if (singleLine) 1 else 4,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (mono) JetBrainsMono else MaterialTheme.typography.bodyMedium.fontFamily
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("field_$keySlug")
        )
    }
}

@Composable
private fun FieldEnvelope(
    label: String,
    isChanged: Boolean,
    originalDisplay: String,
    enabled: Boolean,
    clearTag: String,
    resetTag: String,
    onClear: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    inputContent: @Composable () -> Unit
) {
    val bgColor = if (isChanged) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
    val accentBarColor = if (isChanged) MaterialTheme.colorScheme.primary else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (isChanged) 96.dp else 0.dp)
                .background(accentBarColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isChanged) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "CHANGED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onClear,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(clearTag)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear $label", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }

            inputContent()

            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (originalDisplay.isNotEmpty()) "Original: $originalDisplay" else "Original: (none)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isChanged) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        ),
                        modifier = Modifier
                            .clickable(onClick = onReset)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag(resetTag)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeDropdownSelector(
    label: String,
    value: String,
    options: List<String>,
    optionLabel: (String) -> String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(opt),
                                fontFamily = JetBrainsMono
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(opt)
                        }
                    )
                }
            }
        }
    }
}
