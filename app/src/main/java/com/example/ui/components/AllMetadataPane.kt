package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.exif.ExifTagDictionary
import com.example.exif.IfdGroup
import com.example.exif.JpegExifEngine
import com.example.ui.EditorUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalSemanticColors

@Composable
fun AllMetadataPane(
    state: EditorUiState,
    onUpdateTableText: (String, String) -> Unit,
    onResetTableText: (String) -> Unit,
    onToggleRemoveTag: (String) -> Unit,
    onToggleRemoveGroup: (IfdGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsed = state.parsed
    val semantic = LocalSemanticColors.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("All stored metadata", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Text tags are editable inline; numeric and binary tags can be removed individually or by group.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (parsed == null) {
                Text(
                    text = "Load a JPEG photo to inspect all IFD0, Exif, GPS, Interoperability, COM, and XMP records.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val exif = parsed.exif
            if (exif == null) {
                Text(
                    text = "No EXIF IFD blocks stored in this JPEG.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val le = exif.littleEndian
                var anyTag = false
                for (group in IfdGroup.entries) {
                    val entries = exif.ifds[group].orEmpty()
                    if (entries.isEmpty()) continue
                    anyTag = true

                    val removableKeys = entries
                        .filter { ExifTagDictionary.POINTER_TAGS[group]?.contains(it.tag) != true }
                        .map { ExifTagDictionary.tagKey(group, it.tag) }
                    val allGroupRemoved = removableKeys.isNotEmpty() && removableKeys.all { state.removedTags.contains(it) }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${group.title} (${entries.size} tags)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (removableKeys.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { onToggleRemoveGroup(group) },
                                    colors = if (allGroupRemoved) {
                                        ButtonDefaults.outlinedButtonColors()
                                    } else {
                                        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("remove_group_${group.name.lowercase()}")
                                ) {
                                    Text(
                                        text = if (allGroupRemoved) "Undo all in group" else "Remove all in group",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        ) {
                            entries.forEachIndexed { idx, entry ->
                                val name = ExifTagDictionary.tagName(group, entry.tag)
                                val hex = ExifTagDictionary.hexTag(entry.tag)
                                val key = ExifTagDictionary.tagKey(group, entry.tag)
                                val isPointer = ExifTagDictionary.POINTER_TAGS[group]?.contains(entry.tag) == true
                                val isRemoved = state.removedTags.contains(key)
                                val editObj = state.tableTextEdits[key]
                                val isChanged = !isRemoved && editObj != null && editObj.current != editObj.original

                                val rowBg = when {
                                    isRemoved -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                                    isChanged -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else -> MaterialTheme.colorScheme.surface
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(if (isRemoved) 0.55f else 1f)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        textDecoration = if (isRemoved) TextDecoration.LineThrough else TextDecoration.None
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = hex,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (isPointer) {
                                            Text(
                                                text = "Pointer",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            OutlinedButton(
                                                onClick = { onToggleRemoveTag(key) },
                                                colors = if (isRemoved) {
                                                    ButtonDefaults.outlinedButtonColors()
                                                } else {
                                                    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.testTag("toggle_remove_$key")
                                            ) {
                                                Text(
                                                    text = if (isRemoved) "Undo" else "Remove",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (editObj != null) {
                                        OutlinedTextField(
                                            value = editObj.current,
                                            onValueChange = { onUpdateTableText(key, it) },
                                            enabled = !isRemoved,
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .alpha(if (isRemoved) 0.55f else 1f)
                                                .testTag("table_edit_$key")
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Original: ${editObj.original.ifEmpty { "(empty)" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                        .clickable { onResetTableText(key) }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = JpegExifEngine.formatReadableTag(group, entry, le),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = JetBrainsMono,
                                                textDecoration = if (isRemoved) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.alpha(if (isRemoved) 0.55f else 1f)
                                        )
                                    }
                                }
                                if (idx < entries.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
                if (!anyTag) {
                    Text(
                        text = "No EXIF tags found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (parsed.comments.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(semantic.surfaceAlt)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "JPEG COM Comments (${parsed.comments.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = parsed.comments.joinToString("\n---\n"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (parsed.xmpBlocks.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(semantic.surfaceAlt)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .clickable { expanded = !expanded }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "XMP Packet (${parsed.xmpBlocks.size} segment${if (parsed.xmpBlocks.size > 1) "s" else ""})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "Collapse XMP" else "Expand XMP"
                        )
                    }
                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = parsed.xmpBlocks.joinToString("\n\n"),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
