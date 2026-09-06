package cc.kennydev.silic2.app.data.repository

import android.content.Context
import cc.kennydev.silic2.app.data.model.SoundClass
import java.io.BufferedReader
import java.io.InputStreamReader

object SoundClassRepository {
    private var cache: Map<Int, SoundClass>? = null
    private var listCache: List<SoundClass>? = null

    fun getSoundClasses(context: Context): Map<Int, SoundClass> {
        cache?.let { return it }

        val map = mutableMapOf<Int, SoundClass>()
        val list = mutableListOf<SoundClass>()

        try {
            context.assets.open("soundclass.csv").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var isFirstLine = true
                    reader.forEachLine { rawLine ->
                        // 移除 UTF-8 BOM
                        val line = if (isFirstLine && rawLine.startsWith("\uFEFF")) {
                            rawLine.substring(1)
                        } else {
                            rawLine
                        }

                        if (isFirstLine) {
                            isFirstLine = false
                            return@forEachLine
                        }

                        if (line.isBlank()) return@forEachLine

                        val parts = line.split(",")
                        if (parts.size >= 6) {
                            try {
                                val id = parts[0].trim().toInt()
                                val speciesName = parts[1].trim()
                                val soundClass = parts[2].trim()
                                val scientificName = parts[3].trim()
                                val freqLow = parts[4].trim().toIntOrNull() ?: 0
                                val freqHigh = parts[5].trim().toIntOrNull() ?: 0

                                val item = SoundClass(
                                    soundclassId = id,
                                    speciesName = speciesName,
                                    soundClass = soundClass,
                                    scientificName = scientificName,
                                    freqLow = freqLow,
                                    freqHigh = freqHigh
                                )
                                map[id] = item
                                list.add(item)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cache = map
        listCache = list
        return map
    }

    fun getAllList(context: Context): List<SoundClass> {
        if (listCache == null) {
            getSoundClasses(context)
        }
        return listCache ?: emptyList()
    }
}
