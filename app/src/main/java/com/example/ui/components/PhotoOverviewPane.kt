package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.exif.JpegExifEngine
import com.example.exif.OlcCodec
import com.example.exif.PresenceInfo
import com.example.ui.EditorUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalSemanticColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoOverviewPane(
    state: EditorUiState,
    onPickFromGallery: () -> Unit,
    onOpenJpegFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Photo Selector & Preview Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Photo",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.jpeg_only_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }

                if (!state.hasPhoto) {
                    // Empty state dropzone / artwork
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(semantic.surfaceAlt)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_photo_vault),
                            contentDescription = "Local photo metadata privacy inspector",
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .aspectRatio(4f / 3f)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Inspect & edit JPEG EXIF locally",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Reads, verifies, and writes in device memory only. Use Browse JPEG File to keep untouched raw MakerNotes, XMP, and trailing bytes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                        ) {
                            Button(
                                onClick = onPickFromGallery,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("choose_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose Photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onOpenJpegFile,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("browse_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse File", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else {
                    // Compact replace photo controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickFromGallery,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("choose_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Replace Photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenJpegFile,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("browse_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse File", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (state.uploadError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusBanner(
                        title = "Cannot load photo",
                        message = state.uploadError,
                        isError = true
                    )
                }

                val parsed = state.parsed
                if (parsed != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    // Preview Box + File Meta Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .background(semantic.surfaceAlt)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 260.dp)
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = state.previewBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Selected JPEG preview: ${state.filename}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    text = "Preview unavailable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetaMetricCell(
                                label = "FILENAME",
                                value = state.filename,
                                mono = false,
                                modifier = Modifier.weight(1.2f)
                            )
                            MetaMetricCell(
                                label = "DIMENSIONS",
                                value = "${parsed.width} × ${parsed.height}",
                                mono = true,
                                modifier = Modifier.weight(1f)
                            )
                            MetaMetricCell(
                                label = "SIZE",
                                value = JpegExifEngine.formatBytes(state.fileSizeBytes),
                                mono = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    PresenceBadgesRow(presence = parsed.presence)
                }
            }
        }

        // At a Glance Card
        val fields = state.initialFields
        if (fields != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag("at_a_glance_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "At a glance",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val rows = buildList {
                        val cam = listOf(fields.make, fields.model).filter { it.isNotBlank() }.joinToString(" ").trim()
                        if (cam.isNotEmpty()) add("Camera" to cam)
                        val lens = when {
                            fields.lensMake.isNotBlank() && fields.lensModel.isNotBlank() &&
                                !fields.lensModel.contains(fields.lensMake, ignoreCase = true) ->
                                "${fields.lensMake} ${fields.lensModel}"
                            fields.lensModel.isNotBlank() -> fields.lensModel
                            else -> fields.lensMake
                        }
                        if (lens.isNotBlank()) add("Lens" to lens)
                        val expParts = listOf(fields.shutter, fields.fNumber, fields.iso, fields.focalLength).filter { it.isNotBlank() }
                        if (expParts.isNotEmpty()) add("Exposure" to expParts.joinToString(" · "))
                        if (fields.dateStr.isNotBlank()) add("Date taken" to fields.dateStr)
                        if (fields.lat != null && fields.lng != null) {
                            add("GPS location" to "${"%.6f".format(java.util.Locale.US, fields.lat)}, ${"%.6f".format(java.util.Locale.US, fields.lng)} (${OlcCodec.encode(fields.lat, fields.lng, 11)})")
                        }
                        if (fields.altitude != null) {
                            add("Altitude" to "${fields.altitude} m ${if (fields.altitude < 0) "(below sea level)" else "(above sea level)"}")
                        }
                        if (fields.direction != null) {
                            add("Direction" to "${fields.direction}° ${if (fields.directionRef == "M") "Magnetic" else "True"}")
                        }
                        if (fields.software.isNotBlank()) add("Software" to fields.software)
                    }

                    if (rows.isEmpty()) {
                        Text(
                            text = "No summary EXIF fields stored in this photo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        rows.forEachIndexed { idx, (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(96.dp)
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (idx < rows.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaMetricCell(
    label: String,
    value: String,
    mono: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (mono) JetBrainsMono else MaterialTheme.typography.bodySmall.fontFamily
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresenceBadgesRow(presence: PresenceInfo) {
    val semantic = LocalSemanticColors.current
    val items = listOf(
        "EXIF" to presence.exif,
        "MakerNote" to presence.makerNote,
        "Thumbnail" to presence.thumbnail,
        "XMP" to presence.xmp,
        "IPTC" to presence.iptc,
        "Comments" to presence.comments,
        "ICC Profile" to presence.icc,
        "MPF" to presence.mpf
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag("presence_badges_row")
    ) {
        items.forEach { (label, on) ->
            PresenceChip(
                text = "$label: ${if (on) "Yes" else "No"}",
                active = on,
                warning = false
            )
        }
        if (presence.trailingBytes > 0) {
            PresenceChip(
                text = "Trailing data after EOI: ${JpegExifEngine.formatBytes(presence.trailingBytes)}",
                active = false,
                warning = true
            )
        } else {
            PresenceChip(
                text = "Trailing data: None",
                active = false,
                warning = false
            )
        }
    }
}

@Composable
private fun PresenceChip(
    text: String,
    active: Boolean,
    warning: Boolean
) {
    val semantic = LocalSemanticColors.current
    val bg: Color
    val border: Color
    val fg: Color
    when {
        warning -> {
            bg = semantic.warningContainer
            border = semantic.warning
            fg = semantic.warning
        }
        active -> {
            bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            border = MaterialTheme.colorScheme.primary
            fg = MaterialTheme.colorScheme.primary
        }
        else -> {
            bg = semantic.surfaceAlt
            border = MaterialTheme.colorScheme.outlineVariant
            fg = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusBanner(
    title: String,
    message: String,
    isError: Boolean = false,
    isWarning: Boolean = false,
    isSuccess: Boolean = false,
    bullets: List<String> = emptyList(),
    footerNote: String? = null,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val (bg, border, titleColor) = when {
        isError -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error
        )
        isWarning -> Triple(
            semantic.warningContainer,
            semantic.warning,
            semantic.warning
        )
        isSuccess -> Triple(
            semantic.verifiedContainer,
            semantic.verified,
            semantic.verified
        )
        else -> Triple(
            semantic.surfaceAlt,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.onSurface
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(1.dp, border, MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isWarning || isError) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = titleColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor
            )
        }
        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (bullets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            bullets.forEach { b ->
                Text(
                    text = "• $b",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
        if (!footerNote.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = footerNote,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
