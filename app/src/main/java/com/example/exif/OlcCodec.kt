package com.example.exif

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Offline Open Location Code (Google Plus Code) encoder, decoder, and validator.
 */
object OlcCodec {
    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEPARATOR = '+'
    private const val SEP_POS = 8
    private const val BASE = 20
    private const val PAIR_LEN = 10
    private const val GRID_ROWS = 5
    private const val GRID_COLS = 4
    private const val LAT_MAX = 90.0
    private const val LNG_MAX = 180.0

    fun isValidFull(code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        val clean = code.trim().uppercase()
        val sep = clean.indexOf(SEPARATOR)
        if (sep != SEP_POS || sep != clean.lastIndexOf(SEPARATOR)) return false
        val stripped = clean.replace(SEPARATOR.toString(), "")
        if (stripped.length < 10 || stripped.contains('0')) return false
        for (ch in stripped) {
            if (ALPHABET.indexOf(ch) == -1) return false
        }
        return true
    }

    fun decode(code: String): Pair<Double, Double> {
        if (!isValidFull(code)) {
            throw IllegalArgumentException(
                "Invalid Google Plus Code. Enter a full 10+ character code (for example, 8J5MJ3H6+62)."
            )
        }
        val clean = code.trim().uppercase().replace(SEPARATOR.toString(), "")
        var latLo = -LAT_MAX
        var lngLo = -LNG_MAX
        var latPlace = 400.0
        var lngPlace = 400.0
        for (i in 0 until PAIR_LEN step 2) {
            latPlace /= BASE
            lngPlace /= BASE
            latLo += ALPHABET.indexOf(clean[i]) * latPlace
            lngLo += ALPHABET.indexOf(clean[i + 1]) * lngPlace
        }
        for (i in PAIR_LEN until clean.length) {
            val d = ALPHABET.indexOf(clean[i])
            latPlace /= GRID_ROWS
            lngPlace /= GRID_COLS
            latLo += (d / GRID_COLS) * latPlace
            lngLo += (d % GRID_COLS) * lngPlace
        }
        val lat = ((latLo + latLo + latPlace) / 2.0)
        val lng = ((lngLo + lngLo + lngPlace) / 2.0)
        return Pair(
            "%.7f".format(java.util.Locale.US, lat).toDouble(),
            "%.7f".format(java.util.Locale.US, lng).toDouble()
        )
    }

    fun encode(lat: Double, lng: Double, length: Int = 11): String {
        var latitude = min(LAT_MAX, max(-LAT_MAX, lat))
        var longitude = lng
        while (longitude < -LNG_MAX) longitude += 360.0
        while (longitude >= LNG_MAX) longitude -= 360.0
        if (latitude == LAT_MAX) latitude -= 1e-7

        var latVal = latitude + LAT_MAX
        var lngVal = longitude + LNG_MAX
        var latPlace = 400.0
        var lngPlace = 400.0
        val sb = StringBuilder()

        for (i in 0 until PAIR_LEN step 2) {
            latPlace /= BASE
            lngPlace /= BASE
            val ld = min(BASE - 1, max(0, floor(latVal / latPlace).toInt()))
            val gd = min(BASE - 1, max(0, floor(lngVal / lngPlace).toInt()))
            sb.append(ALPHABET[ld]).append(ALPHABET[gd])
            latVal -= ld * latPlace
            lngVal -= gd * lngPlace
            if (i == 6) sb.append(SEPARATOR)
        }

        val extra = min(15, max(PAIR_LEN, length))
        for (i in PAIR_LEN until extra) {
            latPlace /= GRID_ROWS
            lngPlace /= GRID_COLS
            val row = min(GRID_ROWS - 1, max(0, floor(latVal / latPlace).toInt()))
            val col = min(GRID_COLS - 1, max(0, floor(lngVal / lngPlace).toInt()))
            latVal -= row * latPlace
            lngVal -= col * lngPlace
            sb.append(ALPHABET[row * GRID_COLS + col])
        }
        return sb.toString()
    }
}
