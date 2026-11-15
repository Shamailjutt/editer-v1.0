package com.example.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exif.BuildAndVerifyResult
import com.example.exif.EditPlan
import com.example.exif.ExifTagDictionary
import com.example.exif.FormFieldKey
import com.example.exif.IfdGroup
import com.example.exif.JpegExifEngine
import com.example.exif.OlcCodec
import com.example.exif.ParsedJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun selectTab(tab: EditorTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadFromUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, uploadError = null, ioError = null) }
            try {
                val (filename, bytes) = withContext(Dispatchers.IO) {
                    val name = queryFilename(contentResolver, uri) ?: "photo.jpg"
                    val mime = contentResolver.getType(uri)
                    val isJpegExt = name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true)
                    val isJpegMime = mime == null || mime.equals("image/jpeg", ignoreCase = true) || mime.equals("image/jpg", ignoreCase = true)
                    val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Failed to read file from device.")

                    val hasJpegMagic = rawBytes.size >= 2 &&
                        (rawBytes[0].toInt() and 0xFF) == 0xFF &&
                        (rawBytes[1].toInt() and 0xFF) == 0xD8

                    if (!hasJpegMagic || (!isJpegExt && !isJpegMime)) {
                        throw IllegalArgumentException("Unsupported file type. Only JPEG / JPG files are supported.")
                    }
                    Pair(name, rawBytes)
                }
                processLoadedBytes(bytes, filename)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        uploadError = e.message ?: "Unable to parse this JPEG file."
                    )
                }
            }
        }
    }

    suspend fun loadFromBytes(bytes: ByteArray, filename: String) {
        _uiState.update { it.copy(isLoading = true, uploadError = null, ioError = null) }
        try {
            processLoadedBytes(bytes, filename)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    uploadError = e.message ?: "Unable to parse this JPEG file."
                )
            }
        }
    }

    private suspend fun processLoadedBytes(bytes: ByteArray, filename: String) {
        val parsed = withContext(Dispatchers.Default) {
            JpegExifEngine.parseJpeg(bytes)
        }
        val fields = withContext(Dispatchers.Default) {
            JpegExifEngine.extractPhotoFields(parsed)
        }
        val bitmap = withContext(Dispatchers.Default) {
            decodePreviewBitmap(bytes, parsed.orientation)
        }
        val dateParts = parseDateParts(fields.dateStr)
        val plusStr = if (fields.lat != null && fields.lng != null) {
            OlcCodec.encode(fields.lat, fields.lng, 11)
        } else ""
        val latStr = fields.lat?.toString().orEmpty()
        val lngStr = fields.lng?.toString().orEmpty()
        val altStr = fields.altitude?.toString().orEmpty()
        val dirStr = fields.direction?.toString().orEmpty()
        val tableEdits = buildInitialTableEdits(parsed)

        _uiState.update {
            it.copy(
                isLoading = false,
                uploadError = null,
                filename = filename,
                fileSizeBytes = bytes.size,
                parsed = parsed,
                previewBitmap = bitmap,
                initialFields = fields,
                initialDateParts = dateParts,
                initialPlusCode = plusStr,
                make = fields.make,
                model = fields.model,
                lensModel = fields.lensModel,
                software = fields.software,
                artist = fields.artist,
                copyright = fields.copyright,
                description = fields.description,
                userComment = fields.userComment,
                dateParts = dateParts,
                plusCode = plusStr,
                lat = latStr,
                lng = lngStr,
                altitude = altStr,
                direction = dirStr,
                directionRef = fields.directionRef,
                deepScrub = false,
                removeAllMetadata = false,
                removedTags = emptySet(),
                tableTextEdits = tableEdits,
                buildResult = null,
                savedPhotoConfirmed = false,
                savedBackupConfirmed = false,
                ioError = null
            )
        }
    }

    private fun buildInitialTableEdits(parsed: ParsedJpeg): Map<String, TableTextEdit> {
        val ex = parsed.exif ?: return emptyMap()
        val map = LinkedHashMap<String, TableTextEdit>()
        for (group in IfdGroup.entries) {
            for (entry in ex.ifds[group].orEmpty()) {
                if (JpegExifEngine.isEditableTextTag(group, entry)) {
                    val key = ExifTagDictionary.tagKey(group, entry.tag)
                    val value = JpegExifEngine.extractEditableText(group, entry, ex.littleEndian)
                    map[key] = TableTextEdit(key = key, original = value, current = value)
                }
            }
        }
        return map
    }

    private fun parseDateParts(dateStr: String): DateParts {
        val regex = Regex("""^(\d{4}):(\d{2}):(\d{2})\s+(\d{2}):(\d{2}):(\d{2})""")
        val m = regex.find(dateStr) ?: return DateParts()
        val h24 = m.groupValues[4].toIntOrNull() ?: 12
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return DateParts(
            date = "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}",
            hour = h12.toString(),
            minute = m.groupValues[5],
            second = m.groupValues[6],
            ampm = if (h24 >= 12) "PM" else "AM"
        )
    }

    fun updateTextField(key: FormFieldKey, value: String) {
        _uiState.update { s ->
            val updatedEdits = syncFormToTableMap(s.tableTextEdits, key, value)
            val clearedOutput = s.invalidateBuild()
            when (key) {
                FormFieldKey.MAKE -> clearedOutput.copy(make = value, tableTextEdits = updatedEdits)
                FormFieldKey.MODEL -> clearedOutput.copy(model = value, tableTextEdits = updatedEdits)
                FormFieldKey.LENS_MODEL -> clearedOutput.copy(lensModel = value, tableTextEdits = updatedEdits)
                FormFieldKey.SOFTWARE -> clearedOutput.copy(software = value, tableTextEdits = updatedEdits)
                FormFieldKey.ARTIST -> clearedOutput.copy(artist = value, tableTextEdits = updatedEdits)
                FormFieldKey.COPYRIGHT -> clearedOutput.copy(copyright = value, tableTextEdits = updatedEdits)
                FormFieldKey.DESCRIPTION -> clearedOutput.copy(description = value, tableTextEdits = updatedEdits)
                FormFieldKey.USER_COMMENT -> clearedOutput.copy(userComment = value, tableTextEdits = updatedEdits)
                FormFieldKey.ALTITUDE -> clearedOutput.copy(altitude = value)
                FormFieldKey.DIRECTION -> clearedOutput.copy(direction = value)
                else -> clearedOutput
            }
        }
    }

    fun updatePlusCode(value: String) {
        _uiState.update { s ->
            val trimmed = value.trim()
            var newLat = s.lat
            var newLng = s.lng
            if (trimmed.isEmpty()) {
                newLat = ""
                newLng = ""
            } else if (OlcCodec.isValidFull(trimmed)) {
                val (decLat, decLng) = OlcCodec.decode(trimmed)
                newLat = decLat.toString()
                newLng = decLng.toString()
            }
            s.invalidateBuild().copy(plusCode = value, lat = newLat, lng = newLng)
        }
    }

    fun updateLatLng(latValue: String? = null, lngValue: String? = null) {
        _uiState.update { s ->
            val nextLat = latValue ?: s.lat
            val nextLng = lngValue ?: s.lng
            val latTrim = nextLat.trim()
            val lngTrim = nextLng.trim()
            var nextPlus = s.plusCode
            if (latTrim.isEmpty() && lngTrim.isEmpty()) {
                nextPlus = ""
            } else {
                val latD = latTrim.toDoubleOrNull()
                val lngD = lngTrim.toDoubleOrNull()
                if (latD != null && lngD != null && latD in -90.0..90.0 && lngD in -180.0..180.0) {
                    nextPlus = OlcCodec.encode(latD, lngD, 11)
                }
            }
            s.invalidateBuild().copy(lat = nextLat, lng = nextLng, plusCode = nextPlus)
        }
    }

    fun updateDirectionRef(ref: String) {
        _uiState.update { it.invalidateBuild().copy(directionRef = ref) }
    }

    fun updateDateParts(
        date: String? = null,
        hour: String? = null,
        minute: String? = null,
        second: String? = null,
        ampm: String? = null
    ) {
        _uiState.update { s ->
            val nextDate = date ?: s.dateParts.date
            var nextHour = hour ?: s.dateParts.hour
            var nextMin = minute ?: s.dateParts.minute
            var nextSec = second ?: s.dateParts.second
            val nextAmPm = ampm ?: s.dateParts.ampm
            if (nextDate.isNotEmpty()) {
                if (nextHour.isEmpty()) nextHour = "12"
                if (nextMin.isEmpty()) nextMin = "00"
                if (nextSec.isEmpty()) nextSec = "00"
            }
            s.invalidateBuild().copy(
                dateParts = DateParts(
                    date = nextDate,
                    hour = nextHour,
                    minute = nextMin,
                    second = nextSec,
                    ampm = nextAmPm
                )
            )
        }
    }

    fun clearField(key: FormFieldKey) {
        _uiState.update { s ->
            val cleared = s.invalidateBuild()
            when (key) {
                FormFieldKey.MAKE,
                FormFieldKey.MODEL,
                FormFieldKey.LENS_MODEL,
                FormFieldKey.SOFTWARE,
                FormFieldKey.ARTIST,
                FormFieldKey.COPYRIGHT,
                FormFieldKey.DESCRIPTION,
                FormFieldKey.USER_COMMENT -> {
                    val updatedEdits = syncFormToTableMap(s.tableTextEdits, key, "")
                    when (key) {
                        FormFieldKey.MAKE -> cleared.copy(make = "", tableTextEdits = updatedEdits)
                        FormFieldKey.MODEL -> cleared.copy(model = "", tableTextEdits = updatedEdits)
                        FormFieldKey.LENS_MODEL -> cleared.copy(lensModel = "", tableTextEdits = updatedEdits)
                        FormFieldKey.SOFTWARE -> cleared.copy(software = "", tableTextEdits = updatedEdits)
                        FormFieldKey.ARTIST -> cleared.copy(artist = "", tableTextEdits = updatedEdits)
                        FormFieldKey.COPYRIGHT -> cleared.copy(copyright = "", tableTextEdits = updatedEdits)
                        FormFieldKey.DESCRIPTION -> cleared.copy(description = "", tableTextEdits = updatedEdits)
                        FormFieldKey.USER_COMMENT -> cleared.copy(userComment = "", tableTextEdits = updatedEdits)
                        else -> cleared
                    }
                }
                FormFieldKey.DATE_TIME -> cleared.copy(dateParts = DateParts())
                FormFieldKey.PLUS_CODE, FormFieldKey.LAT, FormFieldKey.LNG ->
                    cleared.copy(plusCode = "", lat = "", lng = "")
                FormFieldKey.ALTITUDE -> cleared.copy(altitude = "")
                FormFieldKey.DIRECTION -> cleared.copy(direction = "", directionRef = "T")
            }
        }
    }

    fun resetField(key: FormFieldKey) {
        _uiState.update { s ->
            val init = s.initialFields ?: return@update s
            val cleared = s.invalidateBuild()
            when (key) {
                FormFieldKey.MAKE -> cleared.copy(make = init.make, tableTextEdits = syncFormToTableMap(s.tableTextEdits, key, init.make))
                FormFieldKey.MODEL -> cleared.copy(model = init.model, tableTextEdits = syncFormToTableMap(s.tableTextEdits, key, init.model))
                FormFieldKey.LENS_MODEL -> cleared.copy(lensModel = init.lensModel, tableTextEdits = syncFormToTableMap(s.tableTextEdits, key, init.lensModel))
                FormFieldKey.SOFTWARE -> cleared.copy(software = init.software, tableTextEdits = syncFormToTableMap(s.tableTextEdits, key, init.software))
                FormFieldKey.ARTIST -> cleared.copy(artist = init.artist, tableTextEdits = syncFormToTableMap(s.tableTextEdits, key, init.artist))
                FormFieldKey.COPYRIGHT -> cleared.copy(copyright = init.copyright, tableTextEdits = syncFormToTableMap(s.tableTextEdits, key, init.copyright))
                FormFieldKey.DESCRIPTION -> cleared.copy(description = init.description, tableTextEdits = syncFormToTableMap(s.tableTextEdits, key, init.description))
                FormFieldKey.USER_COMMENT -> cleared.copy(userComment = init.userComment, tableTextEdits = syncFormToTableMap(s.tableTextEdits, key, init.userComment))
                FormFieldKey.DATE_TIME -> cleared.copy(dateParts = s.initialDateParts)
                FormFieldKey.PLUS_CODE -> cleared.copy(plusCode = s.initialPlusCode)
                FormFieldKey.LAT -> cleared.copy(lat = init.lat?.toString().orEmpty())
                FormFieldKey.LNG -> cleared.copy(lng = init.lng?.toString().orEmpty())
                FormFieldKey.ALTITUDE -> cleared.copy(altitude = init.altitude?.toString().orEmpty())
                FormFieldKey.DIRECTION -> cleared.copy(direction = init.direction?.toString().orEmpty(), directionRef = init.directionRef)
            }
        }
    }

    fun clearCameraSection() {
        listOf(
            FormFieldKey.MAKE, FormFieldKey.MODEL, FormFieldKey.LENS_MODEL, FormFieldKey.SOFTWARE,
            FormFieldKey.ARTIST, FormFieldKey.COPYRIGHT, FormFieldKey.DESCRIPTION, FormFieldKey.USER_COMMENT
        ).forEach(::clearField)
    }

    fun clearLocationSection() {
        listOf(FormFieldKey.PLUS_CODE, FormFieldKey.ALTITUDE, FormFieldKey.DIRECTION).forEach(::clearField)
    }

    fun requestClearAllFieldsDialog() {
        val s = _uiState.value
        val summary = buildList {
            fun addIf(label: String, v: String) { if (v.trim().isNotEmpty()) add("$label: \"${v.trim()}\"") }
            addIf("Camera Make", s.make)
            addIf("Camera Model", s.model)
            addIf("Lens Model", s.lensModel)
            addIf("Software", s.software)
            addIf("Artist", s.artist)
            addIf("Copyright", s.copyright)
            addIf("Description", s.description)
            addIf("User Comment", s.userComment)
            if (s.dateParts.date.isNotEmpty()) add("Date & Time: ${s.dateParts.date}")
            addIf("Plus Code", s.plusCode)
            addIf("Latitude", s.lat)
            addIf("Longitude", s.lng)
            addIf("Altitude", s.altitude)
            addIf("Direction", s.direction)
        }
        _uiState.update {
            it.copy(
                confirmDialog = ConfirmDialogState(
                    type = ConfirmActionType.CLEAR_ALL_FIELDS,
                    title = "Clear all form fields?",
                    description = "This empties every form field and marks its matching EXIF tag for deletion. Other camera/exposure tags remain untouched unless removed separately.",
                    summaryItems = summary
                )
            )
        }
    }

    fun requestRemoveAllMetadataDialog() {
        val s = _uiState.value
        val p = s.parsed ?: return
        val summary = buildList {
            if (p.exif != null) {
                fun countG(g: IfdGroup) = p.exif.ifds[g].orEmpty().count { !(ExifTagDictionary.POINTER_TAGS[g]?.contains(it.tag) ?: false) }
                val c0 = countG(IfdGroup.IFD0)
                val ce = countG(IfdGroup.Exif)
                val cg = countG(IfdGroup.GPS)
                val ci = countG(IfdGroup.Interop)
                if (c0 > 0) add("Image (IFD0): $c0 tags (including Orientation)")
                if (ce > 0) add("Exif: $ce tags${if (p.presence.makerNote) " (including MakerNote)" else ""}")
                if (cg > 0) add("GPS: $cg tags")
                if (ci > 0) add("Interoperability: $ci tags")
            }
            if (p.presence.xmp) add("XMP metadata packet")
            if (p.presence.iptc) add("IPTC metadata block")
            if (p.presence.comments) add("JPEG COM comments (${p.comments.size})")
            if (p.presence.thumbnail) add("Embedded thumbnail (IFD1 / JFXX)")
            if (p.presence.mpf) add("MPF APP2 block")
            if (p.presence.trailingBytes > 0) add("Trailing data after EOI (${JpegExifEngine.formatBytes(p.presence.trailingBytes)})")
            add("Preserved for image appearance: JFIF, ICC Color Profile, Adobe APP14")
        }
        _uiState.update {
            it.copy(
                confirmDialog = ConfirmDialogState(
                    type = ConfirmActionType.REMOVE_ALL_METADATA,
                    title = "Remove ALL metadata from output?",
                    description = "This marks every stored tag for removal and clears all form fields. You can undo with 'Revert to original' before applying.",
                    summaryItems = summary
                )
            )
        }
    }

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(confirmDialog = null) }
    }

    fun confirmDialogAction() {
        val dialog = _uiState.value.confirmDialog ?: return
        _uiState.update { it.copy(confirmDialog = null) }
        when (dialog.type) {
            ConfirmActionType.CLEAR_ALL_FIELDS -> {
                clearCameraSection()
                clearField(FormFieldKey.DATE_TIME)
                clearLocationSection()
            }
            ConfirmActionType.REMOVE_ALL_METADATA -> {
                val parsed = _uiState.value.parsed ?: return
                val allTagKeys = LinkedHashSet<String>()
                if (parsed.exif != null) {
                    for (g in IfdGroup.entries) {
                        for (entry in parsed.exif.ifds[g].orEmpty()) {
                            if (ExifTagDictionary.POINTER_TAGS[g]?.contains(entry.tag) != true) {
                                allTagKeys.add(ExifTagDictionary.tagKey(g, entry.tag))
                            }
                        }
                    }
                }
                clearCameraSection()
                clearField(FormFieldKey.DATE_TIME)
                clearLocationSection()
                _uiState.update {
                    it.invalidateBuild().copy(
                        removeAllMetadata = true,
                        removedTags = allTagKeys
                    )
                }
            }
        }
    }

    fun revertToOriginal() {
        _uiState.update { s ->
            val parsed = s.parsed ?: return@update s
            val init = s.initialFields ?: return@update s
            s.invalidateBuild().copy(
                make = init.make,
                model = init.model,
                lensModel = init.lensModel,
                software = init.software,
                artist = init.artist,
                copyright = init.copyright,
                description = init.description,
                userComment = init.userComment,
                dateParts = s.initialDateParts,
                plusCode = s.initialPlusCode,
                lat = init.lat?.toString().orEmpty(),
                lng = init.lng?.toString().orEmpty(),
                altitude = init.altitude?.toString().orEmpty(),
                direction = init.direction?.toString().orEmpty(),
                directionRef = init.directionRef,
                deepScrub = false,
                removeAllMetadata = false,
                removedTags = emptySet(),
                tableTextEdits = buildInitialTableEdits(parsed)
            )
        }
    }

    fun toggleDeepScrub(enabled: Boolean) {
        _uiState.update { it.invalidateBuild().copy(deepScrub = enabled) }
    }

    fun toggleKeepBackupJson(enabled: Boolean) {
        _uiState.update { it.invalidateBuild().copy(keepBackupJson = enabled) }
    }

    fun updateTableTextEdit(tagKey: String, newValue: String) {
        _uiState.update { s ->
            val edit = s.tableTextEdits[tagKey] ?: return@update s
            val nextMap = s.tableTextEdits.toMutableMap()
            nextMap[tagKey] = edit.copy(current = newValue)
            var nextState = s.invalidateBuild().copy(tableTextEdits = nextMap)
            val formKey = ExifTagDictionary.TAG_TO_FORM[tagKey]
            if (formKey != null) {
                nextState = when (formKey) {
                    FormFieldKey.MAKE -> nextState.copy(make = newValue)
                    FormFieldKey.MODEL -> nextState.copy(model = newValue)
                    FormFieldKey.LENS_MODEL -> nextState.copy(lensModel = newValue)
                    FormFieldKey.SOFTWARE -> nextState.copy(software = newValue)
                    FormFieldKey.ARTIST -> nextState.copy(artist = newValue)
                    FormFieldKey.COPYRIGHT -> nextState.copy(copyright = newValue)
                    FormFieldKey.DESCRIPTION -> nextState.copy(description = newValue)
                    FormFieldKey.USER_COMMENT -> nextState.copy(userComment = newValue)
                    else -> nextState
                }
            }
            nextState
        }
    }

    fun resetTableTextEdit(tagKey: String) {
        val original = _uiState.value.tableTextEdits[tagKey]?.original ?: return
        updateTableTextEdit(tagKey, original)
    }

    fun toggleRemoveTag(tagKey: String) {
        _uiState.update { s ->
            val next = s.removedTags.toMutableSet()
            var nextRemoveAll = s.removeAllMetadata
            if (next.contains(tagKey)) {
                next.remove(tagKey)
                nextRemoveAll = false
            } else {
                next.add(tagKey)
            }
            s.invalidateBuild().copy(removedTags = next, removeAllMetadata = nextRemoveAll)
        }
    }

    fun toggleRemoveGroup(group: IfdGroup) {
        _uiState.update { s ->
            val entries = s.parsed?.exif?.ifds?.get(group).orEmpty()
            val keys = entries
                .filter { ExifTagDictionary.POINTER_TAGS[group]?.contains(it.tag) != true }
                .map { ExifTagDictionary.tagKey(group, it.tag) }
            if (keys.isEmpty()) return@update s
            val next = s.removedTags.toMutableSet()
            val allRemoved = keys.all { next.contains(it) }
            var nextRemoveAll = s.removeAllMetadata
            if (allRemoved) {
                next.removeAll(keys.toSet())
                nextRemoveAll = false
            } else {
                next.addAll(keys)
            }
            s.invalidateBuild().copy(removedTags = next, removeAllMetadata = nextRemoveAll)
        }
    }

    fun applyChangesAndVerify() {
        val s = _uiState.value
        val parsed = s.parsed ?: return
        val initial = s.initialFields ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, buildResult = null, ioError = null, savedPhotoConfirmed = false, savedBackupConfirmed = false) }
            val result = withContext(Dispatchers.Default) {
                val dateStr = try {
                    if (s.isDateChanged()) formatExifDateString(s.dateParts) else null
                } catch (e: IllegalArgumentException) {
                    return@withContext BuildAndVerifyResult.ValidationError(e.message ?: "Invalid Date & Time.")
                }

                val plan = EditPlan(
                    make = s.make,
                    model = s.model,
                    lensModel = s.lensModel,
                    software = s.software,
                    artist = s.artist,
                    copyright = s.copyright,
                    description = s.description,
                    userComment = s.userComment,
                    dateStr = dateStr,
                    dateChanged = s.isDateChanged(),
                    plusCode = s.plusCode,
                    latStr = s.lat,
                    lngStr = s.lng,
                    coordsChanged = s.isFieldChanged(FormFieldKey.LAT) ||
                        s.isFieldChanged(FormFieldKey.LNG) ||
                        s.isFieldChanged(FormFieldKey.PLUS_CODE),
                    altStr = s.altitude,
                    altChanged = s.isFieldChanged(FormFieldKey.ALTITUDE),
                    dirStr = s.direction,
                    dirRef = s.directionRef,
                    dirChanged = s.isFieldChanged(FormFieldKey.DIRECTION),
                    deepScrub = s.deepScrub,
                    stripAll = s.removeAllMetadata,
                    removedTagKeys = s.removedTags,
                    tableTextEdits = s.tableTextEdits.mapValues { (_, v) -> v.original to v.current },
                    includeBackupJson = s.keepBackupJson
                )

                JpegExifEngine.applyEditsAndVerify(
                    parsed = parsed,
                    initial = initial,
                    initialPlusCode = s.initialPlusCode,
                    filename = s.filename,
                    plan = plan
                )
            }
            _uiState.update { it.copy(isApplying = false, buildResult = result) }
        }
    }

    fun saveVerifiedPhotoToUri(contentResolver: ContentResolver, uri: Uri) {
        val ready = _uiState.value.buildResult as? BuildAndVerifyResult.VerifiedReady ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(ready.outputBytes)
                        out.flush()
                    } ?: throw IllegalStateException("Could not open destination file for writing.")
                }
                _uiState.update { it.copy(savedPhotoConfirmed = true, ioError = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(ioError = e.message ?: "Failed to save edited JPEG.") }
            }
        }
    }

    fun saveBackupJsonToUri(contentResolver: ContentResolver, uri: Uri) {
        val ready = _uiState.value.buildResult as? BuildAndVerifyResult.VerifiedReady ?: return
        val json = ready.backupJson ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IllegalStateException("Could not open backup file for writing.")
                }
                _uiState.update { it.copy(savedBackupConfirmed = true, ioError = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(ioError = e.message ?: "Failed to save metadata backup JSON.") }
            }
        }
    }

    fun clearSession() {
        _uiState.value = EditorUiState()
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value = EditorUiState()
    }

    private fun formatExifDateString(parts: DateParts): String? {
        if (parts.date.isEmpty()) return null
        if (parts.hour.isEmpty() || parts.minute.isEmpty() || parts.second.isEmpty()) {
            throw IllegalArgumentException("Please select hour, minute, and second for Date & Time, or clear it.")
        }
        var h = parts.hour.toInt()
        if (parts.ampm == "AM" && h == 12) h = 0
        else if (parts.ampm == "PM" && h != 12) h += 12
        val ymd = parts.date.replace("-", ":")
        return "$ymd ${h.toString().padStart(2, '0')}:${parts.minute}:${parts.second}"
    }

    private fun syncFormToTableMap(
        existing: Map<String, TableTextEdit>,
        key: FormFieldKey,
        value: String
    ): Map<String, TableTextEdit> {
        val tagKey = ExifTagDictionary.FORM_TO_TAG[key] ?: return existing
        val edit = existing[tagKey] ?: return existing
        val next = existing.toMutableMap()
        next[tagKey] = edit.copy(current = value)
        return next
    }

    private fun EditorUiState.invalidateBuild(): EditorUiState =
        if (buildResult == null && !savedPhotoConfirmed && !savedBackupConfirmed && ioError == null) this
        else copy(buildResult = null, savedPhotoConfirmed = false, savedBackupConfirmed = false, ioError = null)

    private fun queryFilename(contentResolver: ContentResolver, uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun decodePreviewBitmap(bytes: ByteArray, orientation: Int): ImageBitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            var sampleSize = 1
            val maxDim = 1280
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            val matrix = Matrix()
            when (orientation) {
                2 -> matrix.preScale(-1f, 1f)
                3 -> matrix.postRotate(180f)
                4 -> matrix.preScale(1f, -1f)
                5 -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
                6 -> matrix.postRotate(90f)
                7 -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
                8 -> matrix.postRotate(270f)
                else -> return rawBitmap.asImageBitmap()
            }
            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            if (rotated !== rawBitmap) rawBitmap.recycle()
            rotated.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
}
