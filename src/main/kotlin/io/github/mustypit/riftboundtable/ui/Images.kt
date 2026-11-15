package io.github.mustypit.riftboundtable.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.mustypit.riftboundtable.game.Battlefields
import io.github.mustypit.riftboundtable.game.Cards
import org.jetbrains.skia.Image

/**
 * Card pictures from the bundled resources (cards/<slug>.webp, battlefields/<id>.jpg, legends/<id>.jpg),
 * decoded once and kept. Decoding a 400 px webp takes a few milliseconds, so it happens on first use.
 */
object Images {
    private val cache = HashMap<String, ImageBitmap?>()

    private fun load(path: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(path) {
            Images::class.java.getResourceAsStream("/$path")?.use { Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
        }
    }

    fun card(slug: String): ImageBitmap? = Cards.bySlug(slug)?.image?.let { load("cards/$it") }
    /** The battlefield's artwork only (no text), or the whole card when there's no crop. */
    fun battlefieldArt(slug: String): ImageBitmap? {
        val name = Cards.bySlug(slug)?.name
        return Battlefields.byName(name)?.id?.let { id -> listOf("jpg", "webp", "png").firstNotNullOfOrNull { load("battlefields/$id.$it") } } ?: card(slug)
    }
    fun legendArt(id: String): ImageBitmap? = listOf("webp", "jpg", "png").firstNotNullOfOrNull { load("legends/$id.$it") }
    val back: ImageBitmap? get() = load("back.webp")
}
