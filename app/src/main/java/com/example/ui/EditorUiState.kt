package com.example.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.example.exif.BuildAndVerifyResult
import com.example.exif.ExifTagDictionary
import com.example.exif.FormFieldKey
import com.example.exif.ParsedJpeg
import com.example.exif.PhotoFields

enum class EditorTab(val label: String) {
    OVERVIEW("Photo"),
    EDIT("Edit"),
    ALL_TAGS("All Tags")
}

data class DateParts(
    val date: String = "",      // YYYY-MM-DD
    val hour: String = "",      // "1".."12" or ""
    val minute: String = "",    // "00".."59" or ""
    val second: String = "",    // "00".."59" or ""
    val ampm: String = "AM"
)

data class TableTextEdit(
    val key: String,
    val original: String,
    val current: String
)

enum class ConfirmActionType {
    CLEAR_ALL_FIELDS,
    REMOVE_ALL_METADATA
}

data class ConfirmDialogState(
    val type: ConfirmActionType,
    val title: String,
    val description: String,
    val summaryItems: List<String>
)

data class EditorUiState(
    val selectedTab: EditorTab = EditorTab.OVERVIEW,
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val uploadError: String? = null,

    val filename: String = "",
    val fileSizeBytes: Int = 0,
    val parsed: ParsedJpeg? = null,
    val previewBitmap: ImageBitmap? = null,
    val initialFields: PhotoFields? = null,
    val initialDateParts: DateParts = DateParts(),
    val initialPlusCode: String = "",

    // Editable form fields
    val make: String = "",
    val model: String = "",
    val lensModel: String = "",
    val software: String = "",
    val artist: String = "",
    val copyright: String = "",
    val description: String = "",
    val userComment: String = "",
    val dateParts: DateParts = DateParts(),
    val plusCode: String = "",
    val lat: String = "",
    val lng: String = "",
    val altitude: String = "",
    val direction: String = "",
    val directionRef: String = "T",

    // Scrub & Backup options
    val deepScrub: Boolean = false,
    val keepBackupJson: Boolean = false,
    val removeAllMetadata: Boolean = false,

    // Table edits & removals
    val removedTags: Set<String> = emptySet(),
    val tableTextEdits: Map<String, TableTextEdit> = emptyMap(),

    // Verification & Output
    val buildResult: BuildAndVerifyResult? = null,
    val savedPhotoConfirmed: Boolean = false,
    val savedBackupConfirmed: Boolean = false,
    val ioError: String? = null,

    // Confirmation dialog
    val confirmDialog: ConfirmDialogState? = null
) {
    val hasPhoto: Boolean get() = parsed != null && initialFields != null

    fun isDateChanged(): Boolean = dateParts != initialDateParts

    fun isFieldChanged(key: FormFieldKey): Boolean {
        val init = initialFields ?: return false
        return when (key) {
            FormFieldKey.MAKE -> make != init.make
            FormFieldKey.MODEL -> model != init.model
            FormFieldKey.LENS_MODEL -> lensModel != init.lensModel
            FormFieldKey.SOFTWARE -> software != init.software
            FormFieldKey.ARTIST -> artist != init.artist
            FormFieldKey.COPYRIGHT -> copyright != init.copyright
            FormFieldKey.DESCRIPTION -> description != init.description
            FormFieldKey.USER_COMMENT -> userComment != init.userComment
            FormFieldKey.DATE_TIME -> isDateChanged()
            FormFieldKey.PLUS_CODE -> plusCode != initialPlusCode
            FormFieldKey.LAT -> lat != (init.lat?.toString().orEmpty())
            FormFieldKey.LNG -> lng != (init.lng?.toString().orEmpty())
            FormFieldKey.ALTITUDE -> altitude != (init.altitude?.toString().orEmpty())
            FormFieldKey.DIRECTION -> {
                val initDir = init.direction?.toString().orEmpty()
                direction != initDir || (direction.trim().isNotEmpty() && directionRef != init.directionRef)
            }
        }
    }

    val changedFieldCount: Int
        get() {
            if (!hasPhoto) return 0
            var count = FormFieldKey.entries.count { isFieldChanged(it) }
            for ((tagKey, edit) in tableTextEdits) {
                if (ExifTagDictionary.TAG_TO_FORM.containsKey(tagKey)) continue
                if (edit.current != edit.original) count++
            }
            return count
        }

    val removedTagCount: Int get() = removedTags.size

    val anyModification: Boolean
        get() = changedFieldCount > 0 || removedTagCount > 0 || removeAllMetadata || deepScrub
}
