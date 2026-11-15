package com.example.exif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JpegExifEngineTest {

    private fun buildSyntheticJpeg(
        littleEndian: Boolean = false,
        artist: String = "Shamail Ghafoor",
        makerNoteSecret: String? = null,
        comment: String? = null,
        includeXmp: Boolean = true,
        includeIcc: Boolean = true,
        trailingBytes: ByteArray = ByteArray(0)
    ): ByteArray {
        val parts = ArrayList<ByteArray>()
        parts.add(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI

        // JFIF APP0
        val jfifData = byteArrayOf(
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        )
        parts.add(wrapSegment(0xE0, jfifData))

        // EXIF APP1
        val ifd0 = mutableListOf(
            JpegExifEngine.encodeAsciiTag(0x010F, "Canon"),
            JpegExifEngine.encodeAsciiTag(0x0110, "EOS R5"),
            JpegExifEngine.encodeAsciiTag(0x013B, artist),
            JpegExifEngine.encodeByteTag(0x0112, intArrayOf(6))
        )
        // Orientation SHORT (type 3)
        ifd0.removeAll { it.tag == 0x0112 }
        val orientRaw = if (littleEndian) byteArrayOf(0x06, 0x00) else byteArrayOf(0x00, 0x06)
        ifd0.add(ExifEntry(0x0112, 3, 1, orientRaw))

        val exifIfd = mutableListOf(
            JpegExifEngine.encodeAsciiTag(0x9003, "2025:05:10 14:30:00"),
            JpegExifEngine.encodePrefixedTextTag(0x9286, "Original private note", littleEndian)
        )
        if (makerNoteSecret != null) {
            val mnBytes = makerNoteSecret.toByteArray(Charsets.UTF_8)
            exifIfd.add(ExifEntry(0x927C, 7, mnBytes.size, mnBytes))
        }

        val gpsIfd = mutableListOf(
            JpegExifEngine.encodeByteTag(0x0000, intArrayOf(2, 3, 0, 0)),
            JpegExifEngine.encodeAsciiTag(0x0001, "N"),
            JpegExifEngine.encodeRationalTag(0x0002, JpegExifEngine.decimalToDmsRationals(37.7749), littleEndian),
            JpegExifEngine.encodeAsciiTag(0x0003, "W"),
            JpegExifEngine.encodeRationalTag(0x0004, JpegExifEngine.decimalToDmsRationals(-122.4194), littleEndian)
        )

        val exifApp1 = JpegExifEngine.buildExifApp1(
            mapOf(
                IfdGroup.IFD0 to ifd0,
                IfdGroup.Exif to exifIfd,
                IfdGroup.GPS to gpsIfd,
                IfdGroup.Interop to emptyList()
            ),
            littleEndian
        )!!
        parts.add(exifApp1)

        if (includeXmp) {
            val xmpPayload = "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>Sample XMP</x:xmpmeta>".toByteArray(Charsets.UTF_8)
            parts.add(wrapSegment(0xE1, xmpPayload))
        }
        if (includeIcc) {
            val iccPayload = "ICC_PROFILE\u0000\u0001\u0001TESTICC".toByteArray(Charsets.UTF_8)
            parts.add(wrapSegment(0xE2, iccPayload))
        }
        if (comment != null) {
            parts.add(wrapSegment(0xFE, comment.toByteArray(Charsets.UTF_8)))
        }

        // SOF0 (width=640, height=480)
        val sof0 = byteArrayOf(0x08, 0x01, 0xE0.toByte(), 0x02, 0x80.toByte(), 0x03, 0x01, 0x11, 0x00)
        parts.add(wrapSegment(0xC0, sof0))

        // SOS + dummy scan data + EOI
        val sos = wrapSegment(0xDA, byteArrayOf(0x01, 0x01, 0x00, 0x00, 0x3F, 0x00))
        parts.add(sos)
        parts.add(byteArrayOf(0x12, 0x34, 0x56, 0xFF.toByte(), 0xD9.toByte()))
        if (trailingBytes.isNotEmpty()) parts.add(trailingBytes)

        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var off = 0
        for (p in parts) {
            p.copyInto(out, off)
            off += p.size
        }
        return out
    }

    private fun wrapSegment(marker: Int, data: ByteArray): ByteArray {
        val len = data.size + 2
        val seg = ByteArray(2 + len)
        seg[0] = 0xFF.toByte()
        seg[1] = marker.toByte()
        seg[2] = ((len shr 8) and 0xFF).toByte()
        seg[3] = (len and 0xFF).toByte()
        data.copyInto(seg, 4)
        return seg
    }

    @Test
    fun `olc encode and decode round trip`() {
        val code = OlcCodec.encode(37.7749, -122.4194, 11)
        assertTrue(OlcCodec.isValidFull(code))
        val (lat, lng) = OlcCodec.decode(code)
        assertTrue(abs(lat - 37.7749) < 0.001)
        assertTrue(abs(lng - (-122.4194)) < 0.001)
    }

    @Test
    fun `parse synthetic jpeg extracts dimensions, presence badges, and fields`() {
        val bytes = buildSyntheticJpeg(
            littleEndian = true,
            makerNoteSecret = "MakerNotePayload",
            comment = "Photo comment",
            trailingBytes = byteArrayOf(1, 2, 3, 4)
        )
        val parsed = JpegExifEngine.parseJpeg(bytes)
        assertEquals(640, parsed.width)
        assertEquals(480, parsed.height)
        assertEquals(6, parsed.orientation)
        assertTrue(parsed.presence.exif)
        assertTrue(parsed.presence.makerNote)
        assertTrue(parsed.presence.xmp)
        assertTrue(parsed.presence.icc)
        assertTrue(parsed.presence.comments)
        assertEquals(4, parsed.presence.trailingBytes)

        val fields = JpegExifEngine.extractPhotoFields(parsed)
        assertEquals("Canon", fields.make)
        assertEquals("EOS R5", fields.model)
        assertEquals("Shamail Ghafoor", fields.artist)
        assertEquals("2025:05:10 14:30:00", fields.dateStr)
        assertNotNull(fields.lat)
        assertNotNull(fields.lng)
    }

    @Test
    fun `verification blocks save when cleared artist leaks in MakerNote or COM`() {
        val bytes = buildSyntheticJpeg(
            artist = "Shamail Ghafoor",
            makerNoteSecret = "DeviceOwner=Shamail Ghafoor",
            comment = "Shot by Shamail Ghafoor"
        )
        val parsed = JpegExifEngine.parseJpeg(bytes)
        val initial = JpegExifEngine.extractPhotoFields(parsed)
        val plus = OlcCodec.encode(initial.lat!!, initial.lng!!, 11)

        val plan = EditPlan(
            make = initial.make,
            model = initial.model,
            lensModel = initial.lensModel,
            software = initial.software,
            artist = "", // cleared!
            copyright = initial.copyright,
            description = initial.description,
            userComment = initial.userComment,
            dateStr = initial.dateStr,
            dateChanged = false,
            plusCode = plus,
            latStr = initial.lat.toString(),
            lngStr = initial.lng.toString(),
            coordsChanged = false,
            altStr = "",
            altChanged = false,
            dirStr = "",
            dirRef = "T",
            dirChanged = false,
            deepScrub = false,
            stripAll = false,
            removedTagKeys = emptySet(),
            tableTextEdits = emptyMap(),
            includeBackupJson = false
        )

        val result = JpegExifEngine.applyEditsAndVerify(parsed, initial, plus, "sample.jpg", plan)
        assertTrue(result is BuildAndVerifyResult.VerificationBlocked)
        val blocked = result as BuildAndVerifyResult.VerificationBlocked
        assertTrue(blocked.violations.any { it.contains("MakerNote") })
        assertTrue(blocked.violations.any { it.contains("COM comment") })
    }

    @Test
    fun `deep scrub removes MakerNote, XMP, COM, trailing bytes while preserving ICC and Orientation`() {
        val bytes = buildSyntheticJpeg(
            artist = "Shamail Ghafoor",
            makerNoteSecret = "Owner=Shamail Ghafoor",
            comment = "Owner=Shamail Ghafoor",
            includeXmp = true,
            includeIcc = true,
            trailingBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        )
        val parsed = JpegExifEngine.parseJpeg(bytes)
        val initial = JpegExifEngine.extractPhotoFields(parsed)
        val plus = OlcCodec.encode(initial.lat!!, initial.lng!!, 11)

        val plan = EditPlan(
            make = "Leica",
            model = initial.model,
            lensModel = "",
            software = "",
            artist = "",
            copyright = "",
            description = "",
            userComment = "",
            dateStr = null,
            dateChanged = true,
            plusCode = "",
            latStr = "",
            lngStr = "",
            coordsChanged = true,
            altStr = "",
            altChanged = false,
            dirStr = "",
            dirRef = "T",
            dirChanged = false,
            deepScrub = true,
            stripAll = false,
            removedTagKeys = emptySet(),
            tableTextEdits = emptyMap(),
            includeBackupJson = true
        )

        val result = JpegExifEngine.applyEditsAndVerify(parsed, initial, plus, "sample.jpg", plan)
        assertTrue(result is BuildAndVerifyResult.VerifiedReady)
        val ready = result as BuildAndVerifyResult.VerifiedReady
        assertNotNull(ready.backupJson)
        assertTrue(ready.backupJson!!.contains("Shamail Ghafoor"))

        val reParsed = JpegExifEngine.parseJpeg(ready.outputBytes)
        assertFalse(reParsed.presence.makerNote)
        assertFalse(reParsed.presence.xmp)
        assertFalse(reParsed.presence.comments)
        assertTrue(reParsed.presence.icc)
        assertEquals(0, reParsed.presence.trailingBytes)
        assertEquals(6, reParsed.orientation)

        val reFields = JpegExifEngine.extractPhotoFields(reParsed)
        assertEquals("Leica", reFields.make)
        assertEquals("", reFields.artist)
        assertNull(reFields.lat)
        assertNull(reFields.lng)
    }
}
