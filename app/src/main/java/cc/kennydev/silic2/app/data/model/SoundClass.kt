package cc.kennydev.silic2.app.data.model

data class SoundClass(
    val soundclassId: Int,
    val speciesName: String,
    val soundClass: String,
    val scientificName: String,
    val freqLow: Int,
    val freqHigh: Int
) {
    val category: AnimalCategory
        get() = AnimalCategory.fromSpecies(speciesName)

    val displayName: String
        get() = "$speciesName $soundClass"

    val fullDescription: String
        get() = "$soundclassId: $speciesName ($scientificName) $soundClass"
}
