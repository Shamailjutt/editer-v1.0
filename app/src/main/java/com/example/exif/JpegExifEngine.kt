package com.example.exif

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object JpegExifEngine {
    private val TYPE_SIZES = mapOf(
        1 to 1, 2 to 1, 3 to 2, 4 to 4, 5 to 8,
        6 to 1, 7 to 1, 8 to 2, 9 to 4, 10 to 8,
        11 to 4, 12 to 8
    )

    private val UTF8: Charset = Charsets.UTF_8
    private val UTF16LE: Charset = Charsets.UTF_16LE
    private val UTF16BE: Charset = Charsets.UTF_16BE

    private fun safeDecode(charsetName: String, bytes: ByteArray): String {
        return try {
            String(bytes, Charset.forName(charsetName))
        } catch (_: Exception) {
            String(bytes, UTF8)
        }
    }

    private fun stripTrailingNulls(s: String): String = s.trimEnd('\u0000')

    fun decodeAsciiBytes(bytes: ByteArray): String {
        val idx = bytes.indexOf(0.toByte())
        val end = if (idx == -1) bytes.size else idx
        return String(bytes, 0, end, UTF8)
    }

    fun decodeUtf16LE(bytes: ByteArray): String {
        var end = bytes.size - (bytes.size % 2)
        var i = 0
        while (i + 1 < end) {
            if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) {
                end = i
                break
            }
            i += 2
        }
        return String(bytes, 0, end, UTF16LE)
    }

    fun decodeUtf16BE(bytes: ByteArray): String {
        var end = bytes.size - (bytes.size % 2)
        var i = 0
        while (i + 1 < end) {
            if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) {
                end = i
                break
            }
            i += 2
        }
        return String(bytes, 0, end, UTF16BE)
    }

    fun decodePrefixedExifText(bytes: ByteArray, littleEndian: Boolean): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 8) {
            val p8 = String(bytes, 0, 8, Charsets.ISO_8859_1)
            val payload = bytes.copyOfRange(8, bytes.size)
            if (p8.startsWith("ASCII\u0000")) return decodeAsciiBytes(payload)
            if (p8 == "UNICODE\u0000") {
                if (payload.size >= 2 && (payload[0].toInt() and 0xFF) == 0xFE && (payload[1].toInt() and 0xFF) == 0xFF) {
                    return decodeUtf16BE(payload.copyOfRange(2, payload.size))
                }
                if (payload.size >= 2 && (payload[0].toInt() and 0xFF) == 0xFF && (payload[1].toInt() and 0xFF) == 0xFE) {
                    return decodeUtf16LE(payload.copyOfRange(2, payload.size))
                }
                return if (littleEndian) decodeUtf16LE(payload) else decodeUtf16BE(payload)
            }
            if (p8.startsWith("JIS\u0000")) {
                val z = payload.indexOf(0.toByte())
                val slice = if (z == -1) payload else payload.copyOfRange(0, z)
                return stripTrailingNulls(safeDecode("Shift_JIS", slice))
            }
        }
        return decodeAsciiBytes(bytes)
    }

    private fun startsWithAscii(bytes: ByteArray, offset: Int, str: String): Boolean {
        if (offset + str.length > bytes.size) return false
        for (i in str.indices) {
            if ((bytes[offset + i].toInt() and 0xFF) != str[i].code) return false
        }
        return true
    }

    fun readNumericValues(entry: ExifEntry, littleEndian: Boolean): List<Any> {
        val buf = ByteBuffer.wrap(entry.rawBytes).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val vals = ArrayList<Any>(entry.count)
        for (i in 0 until entry.count) {
            when (entry.type) {
                1 -> vals.add(entry.rawBytes[i].toInt() and 0xFF)
                3 -> vals.add(buf.getShort(i * 2).toInt() and 0xFFFF)
                4 -> vals.add(buf.getInt(i * 4).toLong() and 0xFFFFFFFFL)
                5 -> {
                    val n = buf.getInt(i * 8).toLong() and 0xFFFFFFFFL
                    val d = buf.getInt(i * 8 + 4).toLong() and 0xFFFFFFFFL
                    vals.add(RationalValue(n, d, if (d == 0L) 0.0 else n.toDouble() / d.toDouble()))
                }
                6 -> vals.add(entry.rawBytes[i].toInt())
                8 -> vals.add(buf.getShort(i * 2).toInt())
                9 -> vals.add(buf.getInt(i * 4))
                10 -> {
                    val n = buf.getInt(i * 8).toLong()
                    val d = buf.getInt(i * 8 + 4).toLong()
                    vals.add(RationalValue(n, d, if (d == 0L) 0.0 else n.toDouble() / d.toDouble()))
                }
                11 -> vals.add(buf.getFloat(i * 4).toDouble())
                12 -> vals.add(buf.getDouble(i * 8))
            }
        }
        return vals
    }

    fun formatExposureTime(value: Double): String {
        if (value <= 0.0) return "0 s"
        if (value < 1.0) {
            val recip = (1.0 / value).roundToInt()
            if (recip >= 2 && abs(1.0 / recip - value) / value < 0.03) return "1/$recip s"
            return "${trimTrailingZeros(value, 4)} s"
        }
        return "${trimTrailingZeros(value, 2)} s"
    }

    private fun trimTrailingZeros(v: Double, decimals: Int): String {
        val formatted = "%.${decimals}f".format(Locale.US, v)
        return formatted.trimEnd('0').trimEnd('.')
    }

    private fun formatDms(rationals: List<RationalValue>): String {
        if (rationals.size < 3) return ""
        return "${trimTrailingZeros(rationals[0].value, 4)}° ${trimTrailingZeros(rationals[1].value, 4)}' ${"%.2f".format(Locale.US, rationals[2].value)}\""
    }

    fun isEditableTextTag(group: IfdGroup, entry: ExifEntry): Boolean {
        if (ExifTagDictionary.POINTER_TAGS[group]?.contains(entry.tag) == true) return false
        if (group == IfdGroup.IFD0 && ExifTagDictionary.XP_TAGS.contains(entry.tag)) return true
        if (group == IfdGroup.Exif && entry.tag == 0x9286) return true
        if (group == IfdGroup.GPS && (entry.tag == 0x001B || entry.tag == 0x001C)) return true
        return entry.type == 2
    }

    fun extractEditableText(group: IfdGroup, entry: ExifEntry, littleEndian: Boolean): String {
        if (group == IfdGroup.IFD0 && ExifTagDictionary.XP_TAGS.contains(entry.tag)) {
            return decodeUtf16LE(entry.rawBytes)
        }
        if ((group == IfdGroup.Exif && entry.tag == 0x9286) || (group == IfdGroup.GPS && (entry.tag == 0x001B || entry.tag == 0x001C))) {
            return decodePrefixedExifText(entry.rawBytes, littleEndian)
        }
        return decodeAsciiBytes(entry.rawBytes)
    }

    fun formatReadableTag(group: IfdGroup, entry: ExifEntry, littleEndian: Boolean): String {
        val tag = entry.tag
        val type = entry.type
        val raw = entry.rawBytes

        if (group == IfdGroup.IFD0 && ExifTagDictionary.XP_TAGS.contains(tag)) return decodeUtf16LE(raw)
        if ((group == IfdGroup.Exif && tag == 0x9286) || (group == IfdGroup.GPS && (tag == 0x001B || tag == 0x001C))) {
            return decodePrefixedExifText(raw, littleEndian)
        }
        if ((group == IfdGroup.Exif && (tag == 0x9000 || tag == 0xA000)) || (group == IfdGroup.Interop && tag == 0x0002)) {
            return String(raw, UTF8)
        }
        if (group == IfdGroup.Exif && tag == 0x829A && (type == 5 || type == 10)) {
            val r = readNumericValues(entry, littleEndian).filterIsInstance<RationalValue>()
            if (r.isNotEmpty()) return formatExposureTime(r[0].value)
        }
        if (group == IfdGroup.GPS && (tag == 0x0002 || tag == 0x0004 || tag == 0x0014 || tag == 0x0016)) {
            val r = readNumericValues(entry, littleEndian).filterIsInstance<RationalValue>()
            if (r.size >= 3) return formatDms(r)
        }
        if (group == IfdGroup.GPS && tag == 0x0007) {
            val r = readNumericValues(entry, littleEndian).filterIsInstance<RationalValue>()
            if (r.size >= 3) {
                return r.take(3).joinToString(":") { it.value.roundToInt().toString().padStart(2, '0') }
            }
        }
        if (group == IfdGroup.Exif && tag == 0x927C) {
            return "[MakerNote binary data, ${raw.size} bytes]"
        }
        if (type == 2) return decodeAsciiBytes(raw)
        if (type == 7) {
            val printable = raw.isNotEmpty() && raw.size <= 64 && raw.all { b ->
                val v = b.toInt() and 0xFF
                (v in 32..126) || v == 0
            }
            if (printable) return decodeAsciiBytes(raw)
            val preview = raw.take(16).joinToString(" ") { b ->
                (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
            }
            return if (raw.size > 16) "[${raw.size} bytes: $preview …]" else "[${raw.size} bytes: $preview]"
        }
        if (type in 1..12) {
            val vals = readNumericValues(entry, littleEndian)
            if (type == 5 || type == 10) {
                return vals.filterIsInstance<RationalValue>().joinToString(", ") { r ->
                    if (r.den == 1L) r.num.toString() else "${r.num}/${r.den} (${trimTrailingZeros(r.value, 6)})"
                }
            }
            if (type == 11 || type == 12) {
                return vals.filterIsInstance<Double>().joinToString(", ") { trimTrailingZeros(it, 6) }
            }
            return vals.joinToString(", ")
        }
        return "[Type $type, ${raw.size} bytes]"
    }

    private data class SingleIfdResult(val entries: List<ExifEntry>, val nextIfd: Int)

    private fun parseSingleIfd(tiffBytes: ByteArray, buf: ByteBuffer, ifdOffset: Int): SingleIfdResult {
        if (ifdOffset < 0 || ifdOffset + 2 > tiffBytes.size) {
            throw IllegalArgumentException("Corrupt EXIF: IFD offset out of bounds.")
        }
        val count = buf.getShort(ifdOffset).toInt() and 0xFFFF
        if (ifdOffset + 2 + count * 12 + 4 > tiffBytes.size) {
            throw IllegalArgumentException("Truncated EXIF IFD entry table.")
        }
        val entries = ArrayList<ExifEntry>(count)
        var p = ifdOffset + 2
        for (i in 0 until count) {
            val tag = buf.getShort(p).toInt() and 0xFFFF
            val type = buf.getShort(p + 2).toInt() and 0xFFFF
            val valCountLong = buf.getInt(p + 4).toLong() and 0xFFFFFFFFL
            val unitSize = TYPE_SIZES[type]
            if (unitSize == null) {
                p += 12
                continue
            }
            val totalBytesLong = valCountLong * unitSize.toLong()
            if (totalBytesLong > tiffBytes.size.toLong()) {
                throw IllegalArgumentException("Corrupt EXIF tag 0x${tag.toString(16)}: invalid byte length.")
            }
            val totalBytes = totalBytesLong.toInt()
            val valCount = valCountLong.toInt()
            val valOffset = if (totalBytes <= 4) p + 8 else (buf.getInt(p + 8).toLong() and 0xFFFFFFFFL).toInt()
            if (valOffset < 0 || valOffset + totalBytes > tiffBytes.size) {
                throw IllegalArgumentException("Truncated EXIF value for tag 0x${tag.toString(16)}.")
            }
            entries.add(ExifEntry(tag, type, valCount, tiffBytes.copyOfRange(valOffset, valOffset + totalBytes)))
            p += 12
        }
        val nextIfd = (buf.getInt(p).toLong() and 0xFFFFFFFFL).toInt()
        return SingleIfdResult(entries, nextIfd)
    }

    fun parseExifApp1(tiffBytes: ByteArray): ParsedExif {
        if (tiffBytes.size < 8) {
            throw IllegalArgumentException("Corrupt EXIF header: shorter than 8 bytes.")
        }
        val order = String(tiffBytes, 0, 2, Charsets.ISO_8859_1)
        if (order != "II" && order != "MM") {
            throw IllegalArgumentException("Corrupt EXIF header: unknown byte order.")
        }
        val littleEndian = order == "II"
        val buf = ByteBuffer.wrap(tiffBytes).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        if ((buf.getShort(2).toInt() and 0xFFFF) != 42) {
            throw IllegalArgumentException("Corrupt EXIF TIFF magic number.")
        }
        val ifd0Offset = (buf.getInt(4).toLong() and 0xFFFFFFFFL).toInt()
        val ifd0 = parseSingleIfd(tiffBytes, buf, ifd0Offset)

        var exifEntries: List<ExifEntry> = emptyList()
        var gpsEntries: List<ExifEntry> = emptyList()
        var interopEntries: List<ExifEntry> = emptyList()

        val exifPtr = ifd0.entries.find { it.tag == 0x8769 }
        if (exifPtr != null) {
            val ptrVal = (readNumericValues(exifPtr, littleEndian).firstOrNull() as? Number)?.toInt() ?: 0
            if (ptrVal in 1 until tiffBytes.size) {
                exifEntries = parseSingleIfd(tiffBytes, buf, ptrVal).entries
                val interopPtr = exifEntries.find { it.tag == 0xA005 }
                if (interopPtr != null) {
                    val iPtrVal = (readNumericValues(interopPtr, littleEndian).firstOrNull() as? Number)?.toInt() ?: 0
                    if (iPtrVal in 1 until tiffBytes.size) {
                        interopEntries = parseSingleIfd(tiffBytes, buf, iPtrVal).entries
                    }
                }
            }
        }
        val gpsPtr = ifd0.entries.find { it.tag == 0x8825 }
        if (gpsPtr != null) {
            val gPtrVal = (readNumericValues(gpsPtr, littleEndian).firstOrNull() as? Number)?.toInt() ?: 0
            if (gPtrVal in 1 until tiffBytes.size) {
                gpsEntries = parseSingleIfd(tiffBytes, buf, gPtrVal).entries
            }
        }

        return ParsedExif(
            littleEndian = littleEndian,
            hasIfd1Thumbnail = ifd0.nextIfd > 0,
            ifds = mapOf(
                IfdGroup.IFD0 to ifd0.entries,
                IfdGroup.Exif to exifEntries,
                IfdGroup.GPS to gpsEntries,
                IfdGroup.Interop to interopEntries
            )
        )
    }

    fun parseJpeg(bytes: ByteArray): ParsedJpeg {
        if (bytes.size < 4 || (bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) {
            throw IllegalArgumentException("Not a valid JPEG/JPG file (missing SOI 0xFFD8 header).")
        }
        val headerSegments = ArrayList<JpegSegment>()
        var pos = 2
        var sawSof = false
        var firstSosPos = -1
        var width = 0
        var height = 0

        fun isSof(m: Int): Boolean =
            m in 0xC0..0xC3 || m in 0xC5..0xC7 || m in 0xC9..0xCB || m in 0xCD..0xCF

        while (pos < bytes.size) {
            if ((bytes[pos].toInt() and 0xFF) != 0xFF) {
                throw IllegalArgumentException("Corrupt JPEG structure at byte $pos: expected 0xFF marker.")
            }
            while (pos < bytes.size && (bytes[pos].toInt() and 0xFF) == 0xFF) pos++
            if (pos >= bytes.size) {
                throw IllegalArgumentException("Truncated JPEG: unexpected end of file inside marker.")
            }
            val markerStart = pos - 1
            val marker = bytes[pos++].toInt() and 0xFF
            if (marker == 0xD9) {
                throw IllegalArgumentException("Corrupt JPEG: reached End-Of-Image before any image scan data.")
            }
            if (marker == 0xDA) {
                firstSosPos = markerStart
                break
            }
            if (marker == 0x01 || marker in 0xD0..0xD7) {
                headerSegments.add(JpegSegment(marker, SegmentKind.STRUCT, ByteArray(0), bytes.copyOfRange(markerStart, pos)))
                continue
            }
            if (marker == 0x00) {
                throw IllegalArgumentException("Corrupt JPEG: unexpected 0xFF00 byte at header offset $markerStart.")
            }
            if (pos + 2 > bytes.size) {
                throw IllegalArgumentException("Truncated JPEG: incomplete segment length.")
            }
            val segLen = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
            if (segLen < 2 || pos + segLen > bytes.size) {
                throw IllegalArgumentException("Truncated or corrupt JPEG segment 0xFF${marker.toString(16).uppercase()}.")
            }
            val dataBytes = bytes.copyOfRange(pos + 2, pos + segLen)
            val raw = bytes.copyOfRange(markerStart, pos + segLen)

            if (isSof(marker)) {
                sawSof = true
                if (dataBytes.size >= 5) {
                    height = ((dataBytes[1].toInt() and 0xFF) shl 8) or (dataBytes[2].toInt() and 0xFF)
                    width = ((dataBytes[3].toInt() and 0xFF) shl 8) or (dataBytes[4].toInt() and 0xFF)
                }
            }

            val kind = when (marker) {
                0xE0 -> when {
                    startsWithAscii(dataBytes, 0, "JFIF\u0000") -> SegmentKind.JFIF
                    startsWithAscii(dataBytes, 0, "JFXX\u0000") -> SegmentKind.JFXX
                    else -> SegmentKind.APPN
                }
                0xE1 -> when {
                    startsWithAscii(dataBytes, 0, "Exif\u0000\u0000") -> SegmentKind.EXIF
                    startsWithAscii(dataBytes, 0, "http://ns.adobe.com/xap/1.0/\u0000") ||
                        startsWithAscii(dataBytes, 0, "http://ns.adobe.com/xmp/extension/\u0000") -> SegmentKind.XMP
                    else -> SegmentKind.APPN
                }
                0xE2 -> when {
                    startsWithAscii(dataBytes, 0, "ICC_PROFILE\u0000") -> SegmentKind.ICC
                    startsWithAscii(dataBytes, 0, "MPF\u0000") -> SegmentKind.MPF
                    else -> SegmentKind.APPN
                }
                0xED -> SegmentKind.IPTC
                0xEE -> if (startsWithAscii(dataBytes, 0, "Adobe\u0000")) SegmentKind.ADOBE else SegmentKind.APPN
                0xFE -> SegmentKind.COM
                in 0xE3..0xEF -> SegmentKind.APPN
                else -> SegmentKind.STRUCT
            }

            headerSegments.add(JpegSegment(marker, kind, dataBytes, raw))
            pos += segLen
        }

        if (!sawSof || firstSosPos == -1) {
            throw IllegalArgumentException("Corrupt JPEG: missing Start-Of-Frame (SOF) or Start-Of-Scan (SOS) image data.")
        }

        var scanPos = firstSosPos
        var realEoiEnd = -1
        while (scanPos < bytes.size) {
            if ((bytes[scanPos].toInt() and 0xFF) != 0xFF) {
                throw IllegalArgumentException("Corrupt JPEG scan stream at offset $scanPos.")
            }
            while (scanPos < bytes.size && (bytes[scanPos].toInt() and 0xFF) == 0xFF) scanPos++
            if (scanPos >= bytes.size) {
                throw IllegalArgumentException("Truncated JPEG: unexpected EOF inside scan marker.")
            }
            val marker = bytes[scanPos++].toInt() and 0xFF
            if (marker == 0xD9) {
                realEoiEnd = scanPos
                break
            }
            if (marker == 0x01 || marker in 0xD0..0xD7) continue
            if (scanPos + 2 > bytes.size) {
                throw IllegalArgumentException("Truncated JPEG: incomplete scan header.")
            }
            val segLen = ((bytes[scanPos].toInt() and 0xFF) shl 8) or (bytes[scanPos + 1].toInt() and 0xFF)
            if (segLen < 2 || scanPos + segLen > bytes.size) {
                throw IllegalArgumentException("Truncated JPEG: incomplete scan segment.")
            }
            scanPos += segLen
            if (marker == 0xDA) {
                while (scanPos < bytes.size) {
                    if ((bytes[scanPos].toInt() and 0xFF) != 0xFF) {
                        scanPos++
                        continue
                    }
                    val ffStart = scanPos
                    while (scanPos < bytes.size && (bytes[scanPos].toInt() and 0xFF) == 0xFF) scanPos++
                    if (scanPos >= bytes.size) {
                        throw IllegalArgumentException("Truncated JPEG: missing End-Of-Image (0xFFD9) marker.")
                    }
                    val nextByte = bytes[scanPos].toInt() and 0xFF
                    if (nextByte == 0x00 || nextByte in 0xD0..0xD7) {
                        scanPos++
                        continue
                    }
                    scanPos = ffStart
                    break
                }
            }
        }

        if (realEoiEnd == -1) {
            throw IllegalArgumentException("Truncated JPEG: missing End-Of-Image (0xFFD9) marker.")
        }

        val imageStream = bytes.copyOfRange(firstSosPos, realEoiEnd)
        val trailingBytes = bytes.size - realEoiEnd

        var exif: ParsedExif? = null
        val comments = ArrayList<String>()
        val xmpBlocks = ArrayList<String>()
        var hasExif = false
        var hasMakerNote = false
        var hasThumbnail = false
        var hasXmp = false
        var hasIptc = false
        var hasComments = false
        var hasIcc = false
        var hasMpf = false

        for (seg in headerSegments) {
            when (seg.kind) {
                SegmentKind.EXIF -> if (exif == null) {
                    hasExif = true
                    val parsedExif = parseExifApp1(seg.data.copyOfRange(6, seg.data.size))
                    exif = parsedExif
                    if (parsedExif.hasIfd1Thumbnail ||
                        (parsedExif.ifds[IfdGroup.IFD0]?.any { it.tag == 0x0201 } == true)
                    ) {
                        hasThumbnail = true
                    }
                    if (parsedExif.ifds[IfdGroup.Exif]?.any { it.tag == 0x927C } == true) {
                        hasMakerNote = true
                    }
                }
                SegmentKind.JFXX -> hasThumbnail = true
                SegmentKind.XMP -> {
                    hasXmp = true
                    if (startsWithAscii(seg.data, 0, "http://ns.adobe.com/xap/1.0/\u0000")) {
                        xmpBlocks.add(String(seg.data, 29, seg.data.size - 29, UTF8))
                    } else {
                        xmpBlocks.add("[Extended XMP chunk: ${seg.data.size} bytes]")
                    }
                }
                SegmentKind.IPTC -> hasIptc = true
                SegmentKind.COM -> {
                    hasComments = true
                    comments.add(String(seg.data, UTF8))
                }
                SegmentKind.ICC -> hasIcc = true
                SegmentKind.MPF -> hasMpf = true
                else -> Unit
            }
        }

        return ParsedJpeg(
            width = width,
            height = height,
            originalSize = bytes.size,
            headerSegments = headerSegments,
            imageStream = imageStream,
            exif = exif,
            comments = comments,
            xmpBlocks = xmpBlocks,
            presence = PresenceInfo(
                exif = hasExif,
                makerNote = hasMakerNote,
                thumbnail = hasThumbnail,
                xmp = hasXmp,
                iptc = hasIptc,
                comments = hasComments,
                icc = hasIcc,
                mpf = hasMpf,
                trailingBytes = trailingBytes
            )
        )
    }

    fun extractPhotoFields(parsed: ParsedJpeg): PhotoFields {
        val ex = parsed.exif ?: return PhotoFields()
        val le = ex.littleEndian

        fun getAscii(group: IfdGroup, tag: Int): String {
            val e = ex.ifds[group]?.find { it.tag == tag } ?: return ""
            return decodeAsciiBytes(e.rawBytes).trim()
        }

        fun getRat(group: IfdGroup, tag: Int): Double? {
            val e = ex.ifds[group]?.find { it.tag == tag } ?: return null
            val vals = readNumericValues(e, le)
            return (vals.firstOrNull() as? RationalValue)?.value
        }

        val ucEntry = ex.ifds[IfdGroup.Exif]?.find { it.tag == 0x9286 }
        val userComment = if (ucEntry != null) decodePrefixedExifText(ucEntry.rawBytes, le) else ""
        val dateStr = getAscii(IfdGroup.Exif, 0x9003)
            .ifEmpty { getAscii(IfdGroup.Exif, 0x9004) }
            .ifEmpty { getAscii(IfdGroup.IFD0, 0x0132) }

        var lat: Double? = null
        var lng: Double? = null
        val latE = ex.ifds[IfdGroup.GPS]?.find { it.tag == 0x0002 }
        val lngE = ex.ifds[IfdGroup.GPS]?.find { it.tag == 0x0004 }
        if (latE != null) {
            val r = readNumericValues(latE, le).filterIsInstance<RationalValue>()
            if (r.size >= 3) {
                var v = r[0].value + r[1].value / 60.0 + r[2].value / 3600.0
                if (getAscii(IfdGroup.GPS, 0x0001).uppercase().startsWith("S")) v = -v
                lat = "%.7f".format(Locale.US, v).toDouble()
            }
        }
        if (lngE != null) {
            val r = readNumericValues(lngE, le).filterIsInstance<RationalValue>()
            if (r.size >= 3) {
                var v = r[0].value + r[1].value / 60.0 + r[2].value / 3600.0
                if (getAscii(IfdGroup.GPS, 0x0003).uppercase().startsWith("W")) v = -v
                lng = "%.7f".format(Locale.US, v).toDouble()
            }
        }

        var altitude: Double? = null
        val altVal = getRat(IfdGroup.GPS, 0x0006)
        if (altVal != null) {
            val altRefE = ex.ifds[IfdGroup.GPS]?.find { it.tag == 0x0005 }
            val altRef = (altRefE?.let { readNumericValues(it, le).firstOrNull() } as? Number)?.toInt() ?: 0
            val signedAlt = if (altRef == 1) -altVal else altVal
            altitude = "%.2f".format(Locale.US, signedAlt).toDouble()
        }

        val dirVal = getRat(IfdGroup.GPS, 0x0011)
        val direction = dirVal?.let { "%.2f".format(Locale.US, it).toDouble() }
        val directionRef = if (getAscii(IfdGroup.GPS, 0x0010).uppercase() == "M") "M" else "T"

        var shutter = ""
        val expTime = getRat(IfdGroup.Exif, 0x829A)
        if (expTime != null) {
            shutter = formatExposureTime(expTime)
        } else {
            val apexS = getRat(IfdGroup.Exif, 0x9201)
            if (apexS != null) shutter = formatExposureTime(2.0.pow(-apexS))
        }

        var fNumber = ""
        val fVal = getRat(IfdGroup.Exif, 0x829D)
        if (fVal != null) {
            fNumber = "f/${trimTrailingZeros(fVal, 1)}"
        } else {
            val apexA = getRat(IfdGroup.Exif, 0x9202)
            if (apexA != null) fNumber = "f/${trimTrailingZeros(2.0.pow(apexA / 2.0), 1)}"
        }

        var iso = ""
        val isoE = ex.ifds[IfdGroup.Exif]?.find { it.tag == 0x8827 }
            ?: ex.ifds[IfdGroup.Exif]?.find { it.tag == 0x8833 }
        if (isoE != null) {
            val v = readNumericValues(isoE, le).firstOrNull()
            if (v != null) iso = "ISO $v"
        }

        val flVal = getRat(IfdGroup.Exif, 0x920A)
        val focalLength = if (flVal != null) "${trimTrailingZeros(flVal, 1)} mm" else ""

        return PhotoFields(
            make = getAscii(IfdGroup.IFD0, 0x010F),
            model = getAscii(IfdGroup.IFD0, 0x0110),
            lensMake = getAscii(IfdGroup.Exif, 0xA433),
            lensModel = getAscii(IfdGroup.Exif, 0xA434),
            software = getAscii(IfdGroup.IFD0, 0x0131),
            artist = getAscii(IfdGroup.IFD0, 0x013B),
            copyright = getAscii(IfdGroup.IFD0, 0x8298),
            description = getAscii(IfdGroup.IFD0, 0x010E),
            userComment = userComment,
            dateStr = dateStr,
            lat = lat,
            lng = lng,
            altitude = altitude,
            direction = direction,
            directionRef = directionRef,
            shutter = shutter,
            fNumber = fNumber,
            iso = iso,
            focalLength = focalLength
        )
    }

    // =========================================================================
    // ENCODERS & EXIF APP1 BUILDER
    // =========================================================================

    fun encodeAsciiTag(tag: Int, str: String): ExifEntry {
        val enc = str.toByteArray(UTF8)
        val raw = ByteArray(enc.size + 1)
        enc.copyInto(raw, 0)
        return ExifEntry(tag, 2, raw.size, raw)
    }

    fun encodeXpTag(tag: Int, str: String): ExifEntry {
        val raw = ByteArray((str.length + 1) * 2)
        for (i in str.indices) {
            val c = str[i].code
            raw[i * 2] = (c and 0xFF).toByte()
            raw[i * 2 + 1] = ((c shr 8) and 0xFF).toByte()
        }
        return ExifEntry(tag, 1, raw.size, raw)
    }

    fun encodeByteTag(tag: Int, arr: IntArray): ExifEntry {
        val raw = ByteArray(arr.size) { arr[it].toByte() }
        return ExifEntry(tag, 1, raw.size, raw)
    }

    fun encodeLongTag(tag: Int, value: Long, littleEndian: Boolean): ExifEntry {
        val raw = ByteArray(4)
        ByteBuffer.wrap(raw).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            .putInt((value and 0xFFFFFFFFL).toInt())
        return ExifEntry(tag, 4, 1, raw)
    }

    fun encodeRationalTag(tag: Int, pairs: List<Pair<Long, Long>>, littleEndian: Boolean): ExifEntry {
        val raw = ByteArray(pairs.size * 8)
        val buf = ByteBuffer.wrap(raw).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        pairs.forEachIndexed { idx, (num, den) ->
            buf.putInt(idx * 8, (num and 0xFFFFFFFFL).toInt())
            buf.putInt(idx * 8 + 4, (den and 0xFFFFFFFFL).toInt())
        }
        return ExifEntry(tag, 5, pairs.size, raw)
    }

    fun encodePrefixedTextTag(tag: Int, str: String, littleEndian: Boolean): ExifEntry {
        val isAscii = str.all { it.code <= 127 }
        if (isAscii) {
            val ascii = str.toByteArray(UTF8)
            val raw = ByteArray(8 + ascii.size)
            val prefix = byteArrayOf(0x41, 0x53, 0x43, 0x49, 0x49, 0x00, 0x00, 0x00)
            prefix.copyInto(raw, 0)
            ascii.copyInto(raw, 8)
            return ExifEntry(tag, 7, raw.size, raw)
        }
        val raw = ByteArray(8 + str.length * 2)
        val prefix = byteArrayOf(0x55, 0x4E, 0x49, 0x43, 0x4F, 0x44, 0x45, 0x00)
        prefix.copyInto(raw, 0)
        for (i in str.indices) {
            val code = str[i].code
            if (littleEndian) {
                raw[8 + i * 2] = (code and 0xFF).toByte()
                raw[8 + i * 2 + 1] = ((code shr 8) and 0xFF).toByte()
            } else {
                raw[8 + i * 2] = ((code shr 8) and 0xFF).toByte()
                raw[8 + i * 2 + 1] = (code and 0xFF).toByte()
            }
        }
        return ExifEntry(tag, 7, raw.size, raw)
    }

    fun decimalToDmsRationals(decimal: Double): List<Pair<Long, Long>> {
        val absVal = abs(decimal)
        var deg = kotlin.math.floor(absVal).toLong()
        val minF = (absVal - deg) * 60.0
        var min = kotlin.math.floor(minF).toLong()
        var secNum = ((minF - min) * 60.0 * 10000.0).roundToLong()
        if (secNum >= 600000L) {
            secNum -= 600000L
            min += 1L
        }
        if (min >= 60L) {
            min -= 60L
            deg += 1L
        }
        return listOf(deg to 1L, min to 1L, secNum to 10000L)
    }

    private fun measureIfdBlockSize(entries: List<ExifEntry>): Int {
        var size = 2 + entries.size * 12 + 4
        for (e in entries) {
            if (e.rawBytes.size > 4) {
                size += e.rawBytes.size
                if (size % 2 != 0) size += 1
            }
        }
        return size
    }

    private fun writeIfdBlock(entries: List<ExifEntry>, startOffset: Int, littleEndian: Boolean): ByteArray {
        val sorted = entries.sortedBy { it.tag }
        val out = ByteArray(measureIfdBlockSize(sorted))
        val buf = ByteBuffer.wrap(out).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        var dataRelOffset = 2 + sorted.size * 12 + 4
        buf.putShort(0, (sorted.size and 0xFFFF).toShort())
        sorted.forEachIndexed { idx, e ->
            val rowOff = 2 + idx * 12
            buf.putShort(rowOff, (e.tag and 0xFFFF).toShort())
            buf.putShort(rowOff + 2, (e.type and 0xFFFF).toShort())
            buf.putInt(rowOff + 4, e.count)
            if (e.rawBytes.size <= 4) {
                e.rawBytes.copyInto(out, rowOff + 8)
            } else {
                buf.putInt(rowOff + 8, startOffset + dataRelOffset)
                e.rawBytes.copyInto(out, dataRelOffset)
                dataRelOffset += e.rawBytes.size
                if (dataRelOffset % 2 != 0) dataRelOffset += 1
            }
        }
        buf.putInt(2 + sorted.size * 12, 0)
        return out
    }

    fun buildExifApp1(ifds: Map<IfdGroup, List<ExifEntry>>, littleEndian: Boolean): ByteArray? {
        val cleanIfd0 = (ifds[IfdGroup.IFD0] ?: emptyList())
            .filter { !(ExifTagDictionary.POINTER_TAGS[IfdGroup.IFD0]?.contains(it.tag) ?: false) && it.tag != 0x0201 && it.tag != 0x0202 }
            .toMutableList()
        val cleanExif = (ifds[IfdGroup.Exif] ?: emptyList())
            .filter { !(ExifTagDictionary.POINTER_TAGS[IfdGroup.Exif]?.contains(it.tag) ?: false) }
            .toMutableList()
        val cleanGps = (ifds[IfdGroup.GPS] ?: emptyList()).toMutableList()
        val cleanInterop = (ifds[IfdGroup.Interop] ?: emptyList()).toMutableList()

        val hasInterop = cleanInterop.isNotEmpty()
        val hasExif = cleanExif.isNotEmpty() || hasInterop
        val hasGps = cleanGps.isNotEmpty()
        if (cleanIfd0.isEmpty() && !hasExif && !hasGps) return null

        val ifd0SizeEstimate = measureIfdBlockSize(
            cleanIfd0 + listOfNotNull(
                if (hasExif) encodeLongTag(0x8769, 0L, littleEndian) else null,
                if (hasGps) encodeLongTag(0x8825, 0L, littleEndian) else null
            )
        )
        val exifOffset = 8 + ifd0SizeEstimate
        val exifSizeEstimate = if (hasExif) {
            measureIfdBlockSize(cleanExif + if (hasInterop) listOf(encodeLongTag(0xA005, 0L, littleEndian)) else emptyList())
        } else 0
        val interopOffset = exifOffset + exifSizeEstimate
        val interopSize = if (hasInterop) measureIfdBlockSize(cleanInterop) else 0
        val gpsOffset = interopOffset + interopSize
        val gpsSize = if (hasGps) measureIfdBlockSize(cleanGps) else 0

        if (hasExif) cleanIfd0.add(encodeLongTag(0x8769, exifOffset.toLong(), littleEndian))
        if (hasGps) cleanIfd0.add(encodeLongTag(0x8825, gpsOffset.toLong(), littleEndian))
        if (hasInterop) cleanExif.add(encodeLongTag(0xA005, interopOffset.toLong(), littleEndian))

        val tiffTotalLen = 8 + ifd0SizeEstimate + exifSizeEstimate + interopSize + gpsSize
        val segLen = 2 + 6 + tiffTotalLen
        if (segLen > 65535) {
            throw IllegalStateException(
                "EXIF APP1 block size (${segLen} bytes) exceeds the JPEG 65,535-byte limit. " +
                    "Remove large tags (such as MakerNote) or enable Deep Scrub before saving."
            )
        }

        val app1 = ByteArray(2 + segLen)
        app1[0] = 0xFF.toByte()
        app1[1] = 0xE1.toByte()
        app1[2] = ((segLen shr 8) and 0xFF).toByte()
        app1[3] = (segLen and 0xFF).toByte()
        byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00).copyInto(app1, 4)

        val tiff = ByteArray(tiffTotalLen)
        val tBuf = ByteBuffer.wrap(tiff).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val boByte = (if (littleEndian) 0x49 else 0x4D).toByte()
        tiff[0] = boByte
        tiff[1] = boByte
        tBuf.putShort(2, 42.toShort())
        tBuf.putInt(4, 8)

        writeIfdBlock(cleanIfd0, 8, littleEndian).copyInto(tiff, 8)
        if (hasExif) writeIfdBlock(cleanExif, exifOffset, littleEndian).copyInto(tiff, exifOffset)
        if (hasInterop) writeIfdBlock(cleanInterop, interopOffset, littleEndian).copyInto(tiff, interopOffset)
        if (hasGps) writeIfdBlock(cleanGps, gpsOffset, littleEndian).copyInto(tiff, gpsOffset)
        tiff.copyInto(app1, 10)
        return app1
    }

    // =========================================================================
    // RAW BYTE SEARCH & POST-BUILD OUTPUT VERIFICATION
    // =========================================================================

    private fun findSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    fun bytesContainText(haystack: ByteArray, str: String): Boolean {
        if (haystack.isEmpty() || str.isEmpty()) return false
        if (findSubarray(haystack, str.toByteArray(UTF8))) return true
        if (findSubarray(haystack, str.lowercase(Locale.ROOT).toByteArray(UTF8))) return true
        if (findSubarray(haystack, str.uppercase(Locale.ROOT).toByteArray(UTF8))) return true

        val le = ByteArray(str.length * 2)
        val be = ByteArray(str.length * 2)
        for (i in str.indices) {
            val c = str[i].code
            le[i * 2] = (c and 0xFF).toByte()
            le[i * 2 + 1] = ((c shr 8) and 0xFF).toByte()
            be[i * 2] = ((c shr 8) and 0xFF).toByte()
            be[i * 2 + 1] = (c and 0xFF).toByte()
        }
        return findSubarray(haystack, le) || findSubarray(haystack, be)
    }

    private data class OldNeedle(val label: String, val oldText: String, val newText: String)
    private data class ExactCheck(
        val group: IfdGroup,
        val tagId: Int,
        val expectDeleted: Boolean,
        val oldExact: String = "",
        val newExact: String = ""
    )

    private fun verifyOutputBytes(
        outBytes: ByteArray,
        anyEdit: Boolean,
        deepScrub: Boolean,
        stripAll: Boolean,
        removedTagKeys: Set<String>,
        oldNeedles: List<OldNeedle>,
        exactChecks: List<ExactCheck>
    ): VerificationResult {
        val reParsed = parseJpeg(outBytes)
        val violations = LinkedHashSet<String>()

        if (reParsed.presence.trailingBytes > 0) {
            violations.add("Trailing data after EOI (${reParsed.presence.trailingBytes} bytes)")
        }
        if (reParsed.presence.thumbnail) violations.add("Embedded thumbnail (IFD1 or JFXX)")
        if (reParsed.presence.mpf) violations.add("MPF APP2 block")
        if (anyEdit && reParsed.presence.xmp) violations.add("XMP packet")
        if (anyEdit && reParsed.presence.iptc) violations.add("IPTC block")
        if ((deepScrub || stripAll) && reParsed.presence.comments) violations.add("JPEG COM comment")
        if ((deepScrub || stripAll) && reParsed.presence.makerNote) violations.add("EXIF MakerNote (0x927C)")

        val allowedKinds = setOf(SegmentKind.JFIF, SegmentKind.EXIF, SegmentKind.ICC, SegmentKind.ADOBE, SegmentKind.STRUCT)
        for (seg in reParsed.headerSegments) {
            if (!allowedKinds.contains(seg.kind) && (deepScrub || stripAll)) {
                violations.add("Metadata segment 0xFF${seg.marker.toString(16).uppercase()} (${seg.kind.name})")
            }
        }

        if (reParsed.exif != null) {
            for (key in removedTagKeys) {
                val parts = key.split(":")
                if (parts.size == 2) {
                    val group = IfdGroup.valueOf(parts[0])
                    val tagId = parts[1].removePrefix("0x").toInt(16)
                    if (reParsed.exif.ifds[group]?.any { it.tag == tagId } == true) {
                        val name = ExifTagDictionary.tagName(group, tagId)
                        violations.add("EXIF ${group.name} tag $name (${ExifTagDictionary.hexTag(tagId)})")
                    }
                }
            }

            val le = reParsed.exif.littleEndian
            for (chk in exactChecks) {
                val entry = reParsed.exif.ifds[chk.group]?.find { it.tag == chk.tagId }
                val tName = ExifTagDictionary.tagName(chk.group, chk.tagId)
                val hex = ExifTagDictionary.hexTag(chk.tagId)
                if (chk.expectDeleted) {
                    if (entry != null) violations.add("EXIF ${chk.group.name} tag $tName ($hex)")
                } else if (entry != null && chk.oldExact.isNotEmpty() && chk.oldExact != chk.newExact) {
                    val cur = if (isEditableTextTag(chk.group, entry)) {
                        extractEditableText(chk.group, entry, le)
                    } else {
                        formatReadableTag(chk.group, entry, le)
                    }
                    if (cur == chk.oldExact) {
                        violations.add("EXIF ${chk.group.name} tag $tName ($hex)")
                    }
                }
            }
        }

        for (needle in oldNeedles) {
            val oldStr = needle.oldText
            if (oldStr.length < 3) continue
            if (needle.newText.isNotEmpty() && needle.newText.contains(oldStr, ignoreCase = true)) continue

            if (reParsed.exif != null) {
                val le = reParsed.exif.littleEndian
                for (group in IfdGroup.entries) {
                    for (entry in reParsed.exif.ifds[group].orEmpty()) {
                        val tName = ExifTagDictionary.tagName(group, entry.tag)
                        val hex = ExifTagDictionary.hexTag(entry.tag)
                        val readable = if (isEditableTextTag(group, entry)) {
                            extractEditableText(group, entry, le)
                        } else {
                            formatReadableTag(group, entry, le)
                        }
                        if (readable.contains(oldStr, ignoreCase = true)) {
                            violations.add("EXIF ${group.name} tag $tName ($hex) still contains old ${needle.label}")
                            continue
                        }
                        if (entry.tag == 0x927C || entry.type == 7 || entry.type == 1) {
                            if (bytesContainText(entry.rawBytes, oldStr)) {
                                violations.add("EXIF ${group.name} tag $tName ($hex) still contains old ${needle.label}")
                            }
                        }
                    }
                }
            }

            reParsed.comments.forEachIndexed { idx, comment ->
                if (comment.contains(oldStr, ignoreCase = true)) {
                    violations.add("JPEG COM comment #${idx + 1} still contains old ${needle.label}")
                }
            }

            reParsed.headerSegments.forEach { seg ->
                if (seg.kind in setOf(SegmentKind.XMP, SegmentKind.IPTC, SegmentKind.APPN) && seg.data.isNotEmpty()) {
                    if (bytesContainText(seg.data, oldStr)) {
                        violations.add("${seg.kind.name} segment (0xFF${seg.marker.toString(16).uppercase()}) still contains old ${needle.label}")
                    }
                }
            }
        }

        return VerificationResult(ok = violations.isEmpty(), violations = violations.toList())
    }

    // =========================================================================
    // APPLY EDITS, REBUILD JPEG, VERIFY OUTPUT, BUILD BACKUP JSON
    // =========================================================================

    fun applyEditsAndVerify(
        parsed: ParsedJpeg,
        initial: PhotoFields,
        initialPlusCode: String,
        filename: String,
        plan: EditPlan
    ): BuildAndVerifyResult {
        try {
            val littleEndian = parsed.exif?.littleEndian ?: false
            val changes = ArrayList<String>()
            val oldNeedles = ArrayList<OldNeedle>()
            val exactChecks = ArrayList<ExactCheck>()

            val latTrim = plan.latStr.trim()
            val lngTrim = plan.lngStr.trim()
            val plusTrim = plan.plusCode.trim()
            if ((latTrim.isNotEmpty() && lngTrim.isEmpty()) || (latTrim.isEmpty() && lngTrim.isNotEmpty())) {
                return BuildAndVerifyResult.ValidationError("Both Latitude and Longitude must be filled together (or both cleared).")
            }
            var latVal: Double? = null
            var lngVal: Double? = null
            if (latTrim.isNotEmpty() && lngTrim.isNotEmpty()) {
                latVal = latTrim.toDoubleOrNull()
                    ?: return BuildAndVerifyResult.ValidationError("Latitude must be a valid number.")
                lngVal = lngTrim.toDoubleOrNull()
                    ?: return BuildAndVerifyResult.ValidationError("Longitude must be a valid number.")
                if (latVal < -90.0 || latVal > 90.0) {
                    return BuildAndVerifyResult.ValidationError("Latitude must be between -90 and 90.")
                }
                if (lngVal < -180.0 || lngVal > 180.0) {
                    return BuildAndVerifyResult.ValidationError("Longitude must be between -180 and 180.")
                }
            } else if (plusTrim.isNotEmpty() && plusTrim != initialPlusCode) {
                val dec = OlcCodec.decode(plusTrim)
                latVal = dec.first
                lngVal = dec.second
            }
            if (plusTrim != initialPlusCode && plusTrim.isNotEmpty() && !OlcCodec.isValidFull(plusTrim)) {
                return BuildAndVerifyResult.ValidationError("Invalid Google Plus Code. Enter a full 10+ character code or clear it.")
            }

            val altTrim = plan.altStr.trim()
            val altVal: Double? = if (altTrim.isNotEmpty()) {
                altTrim.toDoubleOrNull()
                    ?: return BuildAndVerifyResult.ValidationError("Altitude must be a valid number.")
            } else null

            val dirTrim = plan.dirStr.trim()
            val dirVal: Double? = if (dirTrim.isNotEmpty()) {
                val v = dirTrim.toDoubleOrNull()
                    ?: return BuildAndVerifyResult.ValidationError("Direction must be a valid number.")
                if (v < 0.0 || v > 360.0) {
                    return BuildAndVerifyResult.ValidationError("Direction must be between 0 and 360 degrees.")
                }
                v
            } else null

            val ifds: MutableMap<IfdGroup, MutableList<ExifEntry>> = when {
                plan.stripAll -> mutableMapOf(
                    IfdGroup.IFD0 to mutableListOf(),
                    IfdGroup.Exif to mutableListOf(),
                    IfdGroup.GPS to mutableListOf(),
                    IfdGroup.Interop to mutableListOf()
                )
                plan.deepScrub -> {
                    val m = mutableMapOf(
                        IfdGroup.IFD0 to mutableListOf<ExifEntry>(),
                        IfdGroup.Exif to mutableListOf<ExifEntry>(),
                        IfdGroup.GPS to mutableListOf<ExifEntry>(),
                        IfdGroup.Interop to mutableListOf<ExifEntry>()
                    )
                    val origOrient = parsed.exif?.ifds?.get(IfdGroup.IFD0)?.find { it.tag == 0x0112 }
                    if (origOrient != null && !plan.removedTagKeys.contains("IFD0:0x0112")) {
                        m[IfdGroup.IFD0]!!.add(origOrient.copy(rawBytes = origOrient.rawBytes.copyOf()))
                    }
                    changes.add("Deep Scrub erased all existing EXIF, MakerNotes, XMP, IPTC, comments, extra APPn, and JFXX thumbnails (preserving Orientation, JFIF, ICC profile & Adobe APP14)")
                    m
                }
                parsed.exif != null -> mutableMapOf(
                    IfdGroup.IFD0 to parsed.exif.ifds[IfdGroup.IFD0].orEmpty().map { it.copy(rawBytes = it.rawBytes.copyOf()) }.toMutableList(),
                    IfdGroup.Exif to parsed.exif.ifds[IfdGroup.Exif].orEmpty().map { it.copy(rawBytes = it.rawBytes.copyOf()) }.toMutableList(),
                    IfdGroup.GPS to parsed.exif.ifds[IfdGroup.GPS].orEmpty().map { it.copy(rawBytes = it.rawBytes.copyOf()) }.toMutableList(),
                    IfdGroup.Interop to parsed.exif.ifds[IfdGroup.Interop].orEmpty().map { it.copy(rawBytes = it.rawBytes.copyOf()) }.toMutableList()
                )
                else -> mutableMapOf(
                    IfdGroup.IFD0 to mutableListOf(),
                    IfdGroup.Exif to mutableListOf(),
                    IfdGroup.GPS to mutableListOf(),
                    IfdGroup.Interop to mutableListOf()
                )
            }

            fun upsert(group: IfdGroup, entry: ExifEntry) {
                val list = ifds[group]!!
                val idx = list.indexOfFirst { it.tag == entry.tag }
                if (idx != -1) list[idx] = entry else list.add(entry)
            }

            fun delete(group: IfdGroup, tagId: Int) {
                ifds[group]!!.removeAll { it.tag == tagId }
            }

            var anyUserEdit = plan.stripAll || plan.deepScrub

            fun handleText(
                curVal: String,
                origVal: String,
                group: IfdGroup,
                tagId: Int,
                label: String,
                encoder: (String) -> ExifEntry
            ) {
                val trimmed = curVal.trim()
                val changed = curVal != origVal
                if (changed && origVal.isNotEmpty()) {
                    oldNeedles.add(OldNeedle(label, origVal, trimmed))
                    exactChecks.add(ExactCheck(group, tagId, trimmed.isEmpty(), origVal, trimmed))
                }
                if (plan.deepScrub && !plan.stripAll) {
                    if (trimmed.isNotEmpty()) {
                        upsert(group, encoder(trimmed))
                        if (changed) changes.add("Updated $label")
                    } else if (changed) {
                        changes.add("Deleted $label")
                    }
                } else if (changed && !plan.stripAll) {
                    anyUserEdit = true
                    if (trimmed.isEmpty()) {
                        delete(group, tagId)
                        changes.add("Deleted $label")
                    } else {
                        upsert(group, encoder(trimmed))
                        changes.add("Updated $label")
                    }
                }
            }

            handleText(plan.make, initial.make, IfdGroup.IFD0, 0x010F, "Camera Make") { encodeAsciiTag(0x010F, it) }
            handleText(plan.model, initial.model, IfdGroup.IFD0, 0x0110, "Camera Model") { encodeAsciiTag(0x0110, it) }
            handleText(plan.lensModel, initial.lensModel, IfdGroup.Exif, 0xA434, "Lens Model") { encodeAsciiTag(0xA434, it) }
            handleText(plan.software, initial.software, IfdGroup.IFD0, 0x0131, "Software") { encodeAsciiTag(0x0131, it) }
            handleText(plan.artist, initial.artist, IfdGroup.IFD0, 0x013B, "Artist") { encodeAsciiTag(0x013B, it) }
            handleText(plan.copyright, initial.copyright, IfdGroup.IFD0, 0x8298, "Copyright") { encodeAsciiTag(0x8298, it) }
            handleText(plan.description, initial.description, IfdGroup.IFD0, 0x010E, "Image Description") { encodeAsciiTag(0x010E, it) }
            handleText(plan.userComment, initial.userComment, IfdGroup.Exif, 0x9286, "User Comment") { encodePrefixedTextTag(0x9286, it, littleEndian) }

            // Date & Time
            val newDateStr = if (plan.dateChanged) plan.dateStr else null
            if (plan.dateChanged && initial.dateStr.isNotEmpty()) {
                oldNeedles.add(OldNeedle("Date/Time", initial.dateStr, newDateStr ?: ""))
                val origDatePart = initial.dateStr.split(" ").firstOrNull().orEmpty()
                val newDatePart = newDateStr?.split(" ")?.firstOrNull().orEmpty()
                if (origDatePart.isNotEmpty() && origDatePart != newDatePart) {
                    oldNeedles.add(OldNeedle("Date", origDatePart, newDatePart))
                }
                exactChecks.add(ExactCheck(IfdGroup.IFD0, 0x0132, newDateStr == null, initial.dateStr, newDateStr ?: ""))
                listOf(0x9003, 0x9004).forEach { t ->
                    exactChecks.add(ExactCheck(IfdGroup.Exif, t, newDateStr == null, initial.dateStr, newDateStr ?: ""))
                }
                listOf(0x9290, 0x9291, 0x9292).forEach { t ->
                    exactChecks.add(ExactCheck(IfdGroup.Exif, t, expectDeleted = true))
                }
            }
            if (plan.deepScrub && !plan.stripAll) {
                if (plan.dateChanged) {
                    if (newDateStr != null) {
                        upsert(IfdGroup.IFD0, encodeAsciiTag(0x0132, newDateStr))
                        upsert(IfdGroup.Exif, encodeAsciiTag(0x9003, newDateStr))
                        upsert(IfdGroup.Exif, encodeAsciiTag(0x9004, newDateStr))
                        changes.add("Set Date/Time ($newDateStr)")
                    } else {
                        changes.add("Deleted Date/Time")
                    }
                } else if (initial.dateStr.isNotEmpty()) {
                    upsert(IfdGroup.IFD0, encodeAsciiTag(0x0132, initial.dateStr))
                    upsert(IfdGroup.Exif, encodeAsciiTag(0x9003, initial.dateStr))
                    upsert(IfdGroup.Exif, encodeAsciiTag(0x9004, initial.dateStr))
                }
            } else if (plan.dateChanged && !plan.stripAll) {
                anyUserEdit = true
                listOf(0x9290, 0x9291, 0x9292).forEach { delete(IfdGroup.Exif, it) }
                if (newDateStr != null) {
                    upsert(IfdGroup.IFD0, encodeAsciiTag(0x0132, newDateStr))
                    upsert(IfdGroup.Exif, encodeAsciiTag(0x9003, newDateStr))
                    upsert(IfdGroup.Exif, encodeAsciiTag(0x9004, newDateStr))
                    changes.add("Updated DateTimeOriginal, DateTimeDigitized & ModifyDate ($newDateStr) and removed SubSecTime tags")
                } else {
                    delete(IfdGroup.IFD0, 0x0132)
                    delete(IfdGroup.Exif, 0x9003)
                    delete(IfdGroup.Exif, 0x9004)
                    changes.add("Deleted DateTimeOriginal, DateTimeDigitized, ModifyDate & SubSecTime tags")
                }
            }

            fun ensureGpsVer() {
                if (ifds[IfdGroup.GPS]!!.none { it.tag == 0x0000 }) {
                    ifds[IfdGroup.GPS]!!.add(encodeByteTag(0x0000, intArrayOf(2, 3, 0, 0)))
                }
            }

            val origLatStr = initial.lat?.toString().orEmpty()
            val origLngStr = initial.lng?.toString().orEmpty()
            if (plan.coordsChanged && (origLatStr.isNotEmpty() || origLngStr.isNotEmpty())) {
                if (origLatStr.isNotEmpty()) oldNeedles.add(OldNeedle("Latitude", origLatStr, latVal?.toString().orEmpty()))
                if (origLngStr.isNotEmpty()) oldNeedles.add(OldNeedle("Longitude", origLngStr, lngVal?.toString().orEmpty()))
                if (initialPlusCode.isNotEmpty()) oldNeedles.add(OldNeedle("Plus Code", initialPlusCode, plusTrim))
                exactChecks.add(ExactCheck(IfdGroup.GPS, 0x001C, expectDeleted = true))
                if (latVal == null) {
                    listOf(0x0001, 0x0002, 0x0003, 0x0004).forEach { t ->
                        exactChecks.add(ExactCheck(IfdGroup.GPS, t, expectDeleted = true))
                    }
                }
            }
            if (plan.deepScrub && !plan.stripAll) {
                if (latVal != null && lngVal != null) {
                    ensureGpsVer()
                    upsert(IfdGroup.GPS, encodeAsciiTag(0x0001, if (latVal >= 0) "N" else "S"))
                    upsert(IfdGroup.GPS, encodeRationalTag(0x0002, decimalToDmsRationals(latVal), littleEndian))
                    upsert(IfdGroup.GPS, encodeAsciiTag(0x0003, if (lngVal >= 0) "E" else "W"))
                    upsert(IfdGroup.GPS, encodeRationalTag(0x0004, decimalToDmsRationals(lngVal), littleEndian))
                    if (plan.coordsChanged) changes.add("Updated GPS coordinates ($latVal, $lngVal)")
                } else if (plan.coordsChanged) {
                    changes.add("Deleted GPS coordinates")
                }
            } else if (plan.coordsChanged && !plan.stripAll) {
                anyUserEdit = true
                delete(IfdGroup.GPS, 0x001C)
                if (latVal != null && lngVal != null) {
                    ensureGpsVer()
                    upsert(IfdGroup.GPS, encodeAsciiTag(0x0001, if (latVal >= 0) "N" else "S"))
                    upsert(IfdGroup.GPS, encodeRationalTag(0x0002, decimalToDmsRationals(latVal), littleEndian))
                    upsert(IfdGroup.GPS, encodeAsciiTag(0x0003, if (lngVal >= 0) "E" else "W"))
                    upsert(IfdGroup.GPS, encodeRationalTag(0x0004, decimalToDmsRationals(lngVal), littleEndian))
                    changes.add("Updated GPS coordinates ($latVal, $lngVal) and deleted GPSAreaInformation")
                } else {
                    listOf(0x0001, 0x0002, 0x0003, 0x0004).forEach { delete(IfdGroup.GPS, it) }
                    changes.add("Deleted GPS coordinates and GPSAreaInformation")
                }
            }

            // GPS Altitude
            if (plan.altChanged && initial.altitude != null && altVal == null) {
                listOf(0x0005, 0x0006).forEach { exactChecks.add(ExactCheck(IfdGroup.GPS, it, expectDeleted = true)) }
            }
            if (plan.deepScrub && !plan.stripAll) {
                if (altVal != null) {
                    ensureGpsVer()
                    upsert(IfdGroup.GPS, encodeByteTag(0x0005, intArrayOf(if (altVal < 0) 1 else 0)))
                    upsert(IfdGroup.GPS, encodeRationalTag(0x0006, listOf((abs(altVal) * 1000.0).roundToLong() to 1000L), littleEndian))
                    if (plan.altChanged) changes.add("Updated GPS Altitude ($altVal m)")
                } else if (plan.altChanged) {
                    changes.add("Deleted GPS Altitude")
                }
            } else if (plan.altChanged && !plan.stripAll) {
                anyUserEdit = true
                if (altVal != null) {
                    ensureGpsVer()
                    upsert(IfdGroup.GPS, encodeByteTag(0x0005, intArrayOf(if (altVal < 0) 1 else 0)))
                    upsert(IfdGroup.GPS, encodeRationalTag(0x0006, listOf((abs(altVal) * 1000.0).roundToLong() to 1000L), littleEndian))
                    changes.add("Updated GPS Altitude ($altVal m)")
                } else {
                    delete(IfdGroup.GPS, 0x0005)
                    delete(IfdGroup.GPS, 0x0006)
                    changes.add("Deleted GPS Altitude")
                }
            }

            // GPS Direction
            if (plan.dirChanged && initial.direction != null && dirVal == null) {
                listOf(0x0010, 0x0011).forEach { exactChecks.add(ExactCheck(IfdGroup.GPS, it, expectDeleted = true)) }
            }
            if (plan.deepScrub && !plan.stripAll) {
                if (dirVal != null) {
                    ensureGpsVer()
                    upsert(IfdGroup.GPS, encodeAsciiTag(0x0010, plan.dirRef))
                    upsert(IfdGroup.GPS, encodeRationalTag(0x0011, listOf((dirVal * 1000.0).roundToLong() to 1000L), littleEndian))
                    if (plan.dirChanged) changes.add("Updated GPS Direction ($dirVal° ${plan.dirRef})")
                } else if (plan.dirChanged) {
                    changes.add("Deleted GPS Direction")
                }
            } else if (plan.dirChanged && !plan.stripAll) {
                anyUserEdit = true
                if (dirVal != null) {
                    ensureGpsVer()
                    upsert(IfdGroup.GPS, encodeAsciiTag(0x0010, plan.dirRef))
                    upsert(IfdGroup.GPS, encodeRationalTag(0x0011, listOf((dirVal * 1000.0).roundToLong() to 1000L), littleEndian))
                    changes.add("Updated GPS Direction ($dirVal° ${plan.dirRef})")
                } else {
                    delete(IfdGroup.GPS, 0x0010)
                    delete(IfdGroup.GPS, 0x0011)
                    changes.add("Deleted GPS Direction")
                }
            }

            // Inline table text edits
            if (!plan.deepScrub && !plan.stripAll) {
                for ((tagKey, pair) in plan.tableTextEdits) {
                    if (ExifTagDictionary.TAG_TO_FORM.containsKey(tagKey)) continue
                    if (plan.removedTagKeys.contains(tagKey)) continue
                    val (original, current) = pair
                    if (current == original) continue
                    if (tagKey == "GPS:0x001C" && plan.coordsChanged) continue

                    val parts = tagKey.split(":")
                    val group = IfdGroup.valueOf(parts[0])
                    val tagId = parts[1].removePrefix("0x").toInt(16)
                    val trimmed = current.trim()
                    val label = ExifTagDictionary.tagName(group, tagId)
                    val hex = ExifTagDictionary.hexTag(tagId)

                    anyUserEdit = true
                    if (original.isNotEmpty()) {
                        oldNeedles.add(OldNeedle(label, original, trimmed))
                        exactChecks.add(ExactCheck(group, tagId, trimmed.isEmpty(), original, trimmed))
                    }

                    when {
                        trimmed.isEmpty() -> {
                            delete(group, tagId)
                            changes.add("Deleted ${group.name} tag $label ($hex)")
                        }
                        group == IfdGroup.IFD0 && ExifTagDictionary.XP_TAGS.contains(tagId) -> {
                            upsert(IfdGroup.IFD0, encodeXpTag(tagId, trimmed))
                            changes.add("Updated $label ($hex)")
                        }
                        group == IfdGroup.GPS && (tagId == 0x001B || tagId == 0x001C) -> {
                            upsert(IfdGroup.GPS, encodePrefixedTextTag(tagId, trimmed, littleEndian))
                            changes.add("Updated $label ($hex)")
                        }
                        else -> {
                            upsert(group, encodeAsciiTag(tagId, trimmed))
                            changes.add("Updated ${group.name} tag $label ($hex)")
                        }
                    }
                }
            }

            // Per-tag removals
            if (plan.removedTagKeys.isNotEmpty() && !plan.stripAll) {
                anyUserEdit = true
                for (key in plan.removedTagKeys) {
                    val parts = key.split(":")
                    val group = IfdGroup.valueOf(parts[0])
                    val tagId = parts[1].removePrefix("0x").toInt(16)
                    val orig = plan.tableTextEdits[key]?.first
                    if (!orig.isNullOrEmpty()) {
                        oldNeedles.add(OldNeedle(ExifTagDictionary.tagName(group, tagId), orig, ""))
                    }
                    delete(group, tagId)
                    changes.add("Removed ${group.name} tag ${ExifTagDictionary.tagName(group, tagId)} (${ExifTagDictionary.hexTag(tagId)})")
                }
            }

            if (plan.stripAll) changes.add("Removed ALL metadata from output")
            if (ifds[IfdGroup.GPS]!!.size == 1 && ifds[IfdGroup.GPS]!![0].tag == 0x0000) {
                ifds[IfdGroup.GPS]!!.clear()
            }

            val newExifApp1 = buildExifApp1(ifds, littleEndian)

            val outParts = ArrayList<ByteArray>()
            outParts.add(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            val jfifSeg = parsed.headerSegments.find { it.kind == SegmentKind.JFIF }
            if (jfifSeg != null) outParts.add(jfifSeg.raw)
            if (newExifApp1 != null) outParts.add(newExifApp1)

            var droppedXmp = false
            var droppedIptc = false
            var removedMpf = false
            var removedJfxx = false

            for (seg in parsed.headerSegments) {
                if (seg.kind == SegmentKind.JFIF || seg.kind == SegmentKind.EXIF) continue
                if (seg.kind == SegmentKind.MPF) {
                    removedMpf = true
                    continue
                }
                if (seg.kind == SegmentKind.JFXX) {
                    removedJfxx = true
                    continue
                }
                if (plan.deepScrub || plan.stripAll) {
                    if (seg.kind in setOf(SegmentKind.XMP, SegmentKind.IPTC, SegmentKind.COM, SegmentKind.APPN)) continue
                } else if (anyUserEdit) {
                    if (seg.kind == SegmentKind.XMP) {
                        droppedXmp = true
                        continue
                    }
                    if (seg.kind == SegmentKind.IPTC) {
                        droppedIptc = true
                        continue
                    }
                }
                outParts.add(seg.raw)
            }
            outParts.add(parsed.imageStream)

            val totalLen = outParts.sumOf { it.size }
            val outBytes = ByteArray(totalLen)
            var offset = 0
            for (part in outParts) {
                part.copyInto(outBytes, offset)
                offset += part.size
            }

            val verification = verifyOutputBytes(
                outBytes = outBytes,
                anyEdit = anyUserEdit,
                deepScrub = plan.deepScrub,
                stripAll = plan.stripAll,
                removedTagKeys = plan.removedTagKeys,
                oldNeedles = oldNeedles,
                exactChecks = exactChecks
            )

            if (!verification.ok) {
                return BuildAndVerifyResult.VerificationBlocked(verification.violations)
            }

            if ((parsed.exif?.hasIfd1Thumbnail == true) || removedJfxx) {
                changes.add("Removed embedded thumbnail (IFD1 / JFXX)")
            }
            if (removedMpf) changes.add("Removed MPF APP2 block")
            if (parsed.presence.trailingBytes > 0) {
                changes.add("Removed trailing data after EOI (${formatBytes(parsed.presence.trailingBytes)})")
            }
            if (!plan.deepScrub && !plan.stripAll && droppedXmp) changes.add("Dropped XMP packet")
            if (!plan.deepScrub && !plan.stripAll && droppedIptc) changes.add("Dropped IPTC block")
            if (changes.isEmpty()) changes.add("No fields changed — output verified and cut cleanly at EOI")

            val backupJson = if (plan.includeBackupJson) buildOriginalBackupJson(parsed, filename) else null

            return BuildAndVerifyResult.VerifiedReady(
                outputBytes = outBytes,
                backupJson = backupJson,
                originalSize = parsed.originalSize,
                newSize = outBytes.size,
                changes = changes
            )
        } catch (e: Exception) {
            return BuildAndVerifyResult.ValidationError(e.message ?: "Failed to apply changes.")
        }
    }

    fun buildOriginalBackupJson(parsed: ParsedJpeg, filename: String): String {
        val sb = StringBuilder()
        fun esc(s: String): String {
            val out = StringBuilder(s.length + 8)
            for (ch in s) {
                when (ch) {
                    '\\' -> out.append("\\\\")
                    '"' -> out.append("\\\"")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> if (ch.code < 0x20) out.append("\\u%04x".format(ch.code)) else out.append(ch)
                }
            }
            return out.toString()
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val exportedAt = sdf.format(Date())

        sb.append("{\n")
        sb.append("  \"filename\": \"${esc(filename)}\",\n")
        sb.append("  \"exportedAt\": \"${esc(exportedAt)}\",\n")
        sb.append("  \"warning\": \"This backup contains your original location, dates, and text. Store it privately.\",\n")
        val bo = parsed.exif?.let { if (it.littleEndian) "\"II\"" else "\"MM\"" } ?: "null"
        sb.append("  \"byteOrder\": $bo,\n")
        sb.append("  \"exif\": {\n")
        if (parsed.exif != null) {
            val le = parsed.exif.littleEndian
            IfdGroup.entries.forEachIndexed { gIdx, group ->
                val list = parsed.exif.ifds[group].orEmpty()
                sb.append("    \"${group.name}\": [")
                if (list.isNotEmpty()) sb.append("\n")
                list.forEachIndexed { idx, entry ->
                    val valStr = if (isEditableTextTag(group, entry)) {
                        extractEditableText(group, entry, le)
                    } else {
                        formatReadableTag(group, entry, le)
                    }
                    sb.append("      {\n")
                    sb.append("        \"tag\": \"${ExifTagDictionary.hexTag(entry.tag)}\",\n")
                    sb.append("        \"name\": \"${esc(ExifTagDictionary.tagName(group, entry.tag))}\",\n")
                    sb.append("        \"type\": ${entry.type},\n")
                    sb.append("        \"value\": \"${esc(valStr)}\"\n")
                    sb.append("      }")
                    if (idx < list.lastIndex) sb.append(",")
                    sb.append("\n")
                }
                sb.append(if (list.isNotEmpty()) "    ]" else "]")
                if (gIdx < IfdGroup.entries.lastIndex) sb.append(",")
                sb.append("\n")
            }
        }
        sb.append("  },\n")
        sb.append("  \"comments\": [")
        parsed.comments.forEachIndexed { idx, c ->
            if (idx == 0) sb.append("\n")
            sb.append("    \"${esc(c)}\"")
            if (idx < parsed.comments.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append(if (parsed.comments.isNotEmpty()) "  ],\n" else "],\n")
        sb.append("  \"xmp\": [")
        parsed.xmpBlocks.forEachIndexed { idx, x ->
            if (idx == 0) sb.append("\n")
            sb.append("    \"${esc(x)}\"")
            if (idx < parsed.xmpBlocks.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append(if (parsed.xmpBlocks.isNotEmpty()) "  ]\n" else "]\n")
        sb.append("}")
        return sb.toString()
    }

    fun formatBytes(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1048576 -> "%.1f KB".format(Locale.US, bytes / 1024.0)
        else -> "%.2f MB".format(Locale.US, bytes / 1048576.0)
    }
}
