package cc.kennydev.silic2.app.data.model

data class SilicDetection(
    val soundclassId: Int,
    val speciesName: String,
    val soundClass: String,
    val scientificName: String,
    val confidence: Float,
    val timeBeginMs: Long,
    val timeEndMs: Long,
    val freqLowHz: Int,
    val freqHighHz: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val category: AnimalCategory
        get() = AnimalCategory.fromSpecies(speciesName)

    val formattedTime: String
        get() {
            val startSec = timeBeginMs / 1000.0
            val endSec = timeEndMs / 1000.0
            return String.format("%.1fs - %.1fs", startSec, endSec)
        }

    val formattedFreq: String
        get() = "$freqLowHz - $freqHighHz Hz"

    val formattedConfidence: String
        get() = "${(confidence * 100).toInt()}%"
}
