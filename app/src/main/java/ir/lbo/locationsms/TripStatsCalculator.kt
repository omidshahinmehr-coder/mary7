package ir.lbo.locationsms

import android.location.Location
import java.text.SimpleDateFormat
import java.util.Locale

data class TripStats(
    val pointCount: Int,
    val totalDistanceMeters: Double,
    val durationMillis: Long,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double
)

/**
 * Parses the same CSV format LocationLogger writes
 * ("timestamp,latitude,longitude" header, then rows formatted with
 * "yyyy-MM-dd HH:mm:ss") and computes basic trip statistics from it —
 * reusing data that's already being logged, no new tracking needed.
 */
object TripStatsCalculator {

    // A single point-to-point speed above this is almost certainly a GPS
    // glitch (e.g. a brief bad fix), not a real speed — excluded from the
    // max-speed figure so one bad row doesn't produce a nonsense result.
    private const val MAX_PLAUSIBLE_SPEED_KMH = 300.0

    fun calculate(csvContent: String): TripStats? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val rows = csvContent.lines()
            .mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size < 3) return@mapNotNull null
                val timestamp = try {
                    dateFormat.parse(parts[0].trim())?.time
                } catch (e: Exception) {
                    null
                } ?: return@mapNotNull null
                val lat = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                val lng = parts[2].trim().toDoubleOrNull() ?: return@mapNotNull null
                Triple(timestamp, lat, lng)
            }
            .sortedBy { it.first }

        if (rows.size < 2) return null

        var totalDistance = 0.0
        var maxSpeedKmh = 0.0
        val results = FloatArray(1)

        for (i in 1 until rows.size) {
            val (t0, lat0, lng0) = rows[i - 1]
            val (t1, lat1, lng1) = rows[i]

            Location.distanceBetween(lat0, lng0, lat1, lng1, results)
            val segmentDistance = results[0]
            totalDistance += segmentDistance

            val segmentSeconds = (t1 - t0) / 1000.0
            if (segmentSeconds > 0) {
                val speedKmh = (segmentDistance / segmentSeconds) * 3.6
                if (speedKmh > maxSpeedKmh && speedKmh < MAX_PLAUSIBLE_SPEED_KMH) {
                    maxSpeedKmh = speedKmh
                }
            }
        }

        val durationMillis = rows.last().first - rows.first().first
        val durationHours = durationMillis / 3_600_000.0
        val averageSpeedKmh = if (durationHours > 0) (totalDistance / 1000.0) / durationHours else 0.0

        return TripStats(
            pointCount = rows.size,
            totalDistanceMeters = totalDistance,
            durationMillis = durationMillis,
            averageSpeedKmh = averageSpeedKmh,
            maxSpeedKmh = maxSpeedKmh
        )
    }
}
