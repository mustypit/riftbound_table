package io.github.mustypit.riftboundtable.data

import io.github.mustypit.riftboundtable.game.Deck
import io.github.mustypit.riftboundtable.ui.TableTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Settings(
    val table: TableTheme = TableTheme.GRAPHITE,
    /** Card size on the table, 70–130 %. */
    val cardScale: Int = 100,
    val myDeck: Long? = null,
    val oppDeck: Long? = null,
)

/** Saved decks and settings as JSON files in the user's app folder (~/.riftbound-table by default). */
class Store(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val decksFile get() = File(dir, "decks.json")
    private val settingsFile get() = File(dir, "settings.json")

    fun decks(): List<Deck> = read(decksFile) { json.decodeFromString<List<Deck>>(it) } ?: emptyList()
    fun saveDecks(decks: List<Deck>) = write(decksFile, json.encodeToString(decks))
    fun settings(): Settings = read(settingsFile) { json.decodeFromString<Settings>(it) } ?: Settings()
    fun saveSettings(s: Settings) = write(settingsFile, json.encodeToString(s))

    private fun <T> read(f: File, parse: (String) -> T): T? = runCatching { if (f.isFile) parse(f.readText()) else null }.getOrNull()
    private fun write(f: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    companion object {
        fun default() = Store(File(System.getProperty("user.home"), ".riftbound-table"))
    }
}
