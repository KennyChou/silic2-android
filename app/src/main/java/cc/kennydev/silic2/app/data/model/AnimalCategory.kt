package cc.kennydev.silic2.app.data.model

enum class AnimalCategory(val displayName: String, val emoji: String) {
    ALL("全部", "🌿"),
    BIRD("鳥類", "🐦"),
    FROG("蛙類", "🐸"),
    MAMMAL("哺乳類", "🦌"),
    OTHER("其他", "🦎");

    companion object {
        fun fromSpecies(speciesName: String): AnimalCategory {
            val frogKeywords = listOf("蛙", "蟾", "樹蟾")
            if (frogKeywords.any { speciesName.contains(it) }) {
                return FROG
            }

            val mammalKeywords = listOf(
                "鼯鼠", "松鼠", "獼猴", "山羌", "水鹿", "野山羊", "石虎", "犬", "食蟹獴", "智人", "野豬", "蝙蝠"
            )
            if (mammalKeywords.any { speciesName.contains(it) }) {
                return MAMMAL
            }

            val otherKeywords = listOf("蝎虎", "壁虎", "蜥", "蛇")
            if (otherKeywords.any { speciesName.contains(it) }) {
                return OTHER
            }

            return BIRD
        }
    }
}
