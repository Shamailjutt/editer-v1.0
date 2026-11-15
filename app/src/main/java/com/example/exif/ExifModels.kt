package com.example.exif

enum class SegmentKind {
    JFIF,
    JFXX,
    EXIF,
    XMP,
    ICC,
    MPF,
    IPTC,
    ADOBE,
    COM,
    APPN,
    STRUCT
}

data class JpegSegment(
    val marker: Int,
    val kind: SegmentKind,
    val data: ByteArray,
    val raw: ByteArray
)

data class ExifEntry(
    val tag: Int,
    val type: Int,
    val count: Int,
    val rawBytes: ByteArray
)

data class ParsedExif(
    val littleEndian: Boolean,
    val hasIfd1Thumbnail: Boolean,
    val ifds: Map<IfdGroup, List<ExifEntry>>
)

data class PresenceInfo(
    val exif: Boolean,
    val makerNote: Boolean,
    val thumbnail: Boolean,
    val xmp: Boolean,
    val iptc: Boolean,
    val comments: Boolean,
    val icc: Boolean,
    val mpf: Boolean,
    val trailingBytes: Int
)

data class ParsedJpeg(
    val width: Int,
    val height: Int,
    val originalSize: Int,
    val headerSegments: List<JpegSegment>,
    val imageStream: ByteArray,
    val exif: ParsedExif?,
    val comments: List<String>,
    val xmpBlocks: List<String>,
    val presence: PresenceInfo
) {
    val orientation: Int
        get() {
            val entry = exif?.ifds?.get(IfdGroup.IFD0)?.find { it.tag == 0x0112 } ?: return 1
            val vals = JpegExifEngine.readNumericValues(entry, exif.littleEndian)
            return (vals.firstOrNull() as? Number)?.toInt() ?: 1
        }
}

data class RationalValue(
    val num: Long,
    val den: Long,
    val value: Double
)

data class PhotoFields(
    val make: String = "",
    val model: String = "",
    val lensMake: String = "",
    val lensModel: String = "",
    val software: String = "",
    val artist: String = "",
    val copyright: String = "",
    val description: String = "",
    val userComment: String = "",
    val dateStr: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val altitude: Double? = null,
    val direction: Double? = null,
    val directionRef: String = "T",
    val shutter: String = "",
    val fNumber: String = "",
    val iso: String = "",
    val focalLength: String = ""
)

data class VerificationResult(
    val ok: Boolean,
    val violations: List<String>
)

data class EditPlan(
    val make: String,
    val model: String,
    val lensModel: String,
    val software: String,
    val artist: String,
    val copyright: String,
    val description: String,
    val userComment: String,
    val dateStr: String?,
    val dateChanged: Boolean,
    val plusCode: String,
    val latStr: String,
    val lngStr: String,
    val coordsChanged: Boolean,
    val altStr: String,
    val altChanged: Boolean,
    val dirStr: String,
    val dirRef: String,
    val dirChanged: Boolean,
    val deepScrub: Boolean,
    val stripAll: Boolean,
    val removedTagKeys: Set<String>,
    val tableTextEdits: Map<String, Pair<String, String>>, // tagKey -> (original, current)
    val includeBackupJson: Boolean
)

sealed interface BuildAndVerifyResult {
    data class VerifiedReady(
        val outputBytes: ByteArray,
        val backupJson: String?,
        val originalSize: Int,
        val newSize: Int,
        val changes: List<String>
    ) : BuildAndVerifyResult

    data class VerificationBlocked(
        val violations: List<String>
    ) : BuildAndVerifyResult

    data class ValidationError(
        val message: String
    ) : BuildAndVerifyResult
}
