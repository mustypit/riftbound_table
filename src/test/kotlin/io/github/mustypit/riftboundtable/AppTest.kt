@file:OptIn(ExperimentalTestApi::class)

package io.github.mustypit.riftboundtable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.mustypit.riftboundtable.data.Store
import io.github.mustypit.riftboundtable.game.Action
import io.github.mustypit.riftboundtable.game.Cards
import io.github.mustypit.riftboundtable.game.Deck
import io.github.mustypit.riftboundtable.game.DeckCodes
import io.github.mustypit.riftboundtable.game.DeckRules
import io.github.mustypit.riftboundtable.game.Domain
import io.github.mustypit.riftboundtable.game.Phase
import io.github.mustypit.riftboundtable.game.Side
import io.github.mustypit.riftboundtable.game.Zone
import io.github.mustypit.riftboundtable.game.ZoneKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.random.Random

/** Drives the real app headlessly and saves screenshots to build/screenshots (look at them after UI changes). */
class AppTest {
    private val shots = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    init { AppState.loadLibrary() }

    companion object {
        fun deck(legend: String, champion: String, domains: List<Domain>, fields: List<String>): Deck {
            val pool = Cards.all.filter { DeckRules.isMainCard(it) && it.domainList.isNotEmpty() && it.domainList.all { d -> d in domains } && !it.isChampion && !it.banned && "signature" !in it.superTypes }
            val main = linkedMapOf(champion to 1)
            for (c in pool) { if (main.values.sum() >= 40) break; main[c.slug] = minOf(3, 40 - main.values.sum()) }
            return Deck(name = "", legend = legend, champion = champion, main = main, runes = mapOf(domains[0].name to 6, domains[1].name to 6), battlefields = fields)
        }
        val jinx get() = deck("loose-cannon", "jinx-rebel", listOf(Domain.FURY, Domain.CHAOS), listOf("Back-Alley Bar", "Bandle Tree", "Altar to Unity"))
        val kaisa get() = deck("daughter-of-the-void", "kai-sa-survivor", listOf(Domain.FURY, Domain.MIND), listOf("Grove of the God-Willow", "Fortified Position", "Altar to Unity"))
    }

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(shots, "$name.png"))
    }

    private fun newApp() = AppState(Store(Files.createTempDirectory("rbt").toFile()), Random(7))

    /** Drags one node onto another with touch input (the table's drag works with mouse and touch). */
    private fun ComposeUiTest.drag(fromTag: String, toTag: String) {
        val from = onNodeWithTag(fromTag).fetchSemanticsNode().boundsInRoot
        val to = onNodeWithTag(toTag).fetchSemanticsNode().boundsInRoot
        val delta = to.center - from.center
        onNodeWithTag(fromTag).performTouchInput {
            down(center)
            for (i in 1..12) moveTo(center + delta * (i / 12f))
            up()
        }
        waitForIdle()
    }

    @Test fun fullHotSeatSetupAndTable() = runDesktopComposeUiTest(1440, 900) {
        val app = newApp()
        setContent { App(app, diceClockMs = clock) }
        shot("01_new_game_empty")

        // Import both decks from deck codes, the way players paste them.
        for ((seat, d) in listOf(Side.ME to jinx, Side.OPP to kaisa)) {
            onNodeWithTag("seat-$seat").performClick()
            onNodeWithTag("import").performClick()
            onNodeWithTag("code").performClick()
            val code = DeckCodes.fromDeck(d)
            onNodeWithTag("code").performTextInput(code)
            waitForIdle()
            if (seat == Side.OPP) shot("02_import")
            onNodeWithTag("save").performClick()
        }
        assertEquals(2, app.decks.size)
        shot("03_new_game")

        onNodeWithTag("roll").performClick()
        val roll = (app.screen as Screen.Roll).roll
        clock = 400f; shot("04_roll_throwing")
        clock = 9000f; shot("05_roll_settled")
        onNodeWithTag("dice").performClick()

        val first = roll.winner
        assertTrue(app.screen is Screen.Battlefields)
        shot("06_battlefield")
        onNodeWithTag("field-${if (first == Side.ME) "Back-Alley Bar" else "Grove of the God-Willow"}").performClick()
        onNodeWithTag("field-${if (first == Side.ME) "Grove of the God-Willow" else "Back-Alley Bar"}").performClick()
        assertEquals(Screen.Table, app.screen)
        assertEquals(first, app.viewer)
        shot("07_table_start")

        // Start the first turn, play a card from hand to base by dragging, then move the champion in.
        onNodeWithTag("primary").performClick()
        val t = app.table!!
        assertEquals(Phase.ACTION, t.phase)
        val handCard = t.ids(Zone.of(ZoneKind.HAND, first)).first()
        drag("card-$handCard", "zone-BASE-$first")
        assertEquals(Zone.of(ZoneKind.BASE, first), app.table!!.zoneOf(handCard))
        val champ = t.ids(Zone.of(ZoneKind.CHAMPION, first)).first()
        drag("card-$champ", "zone-BATTLEFIELD-$first-1")
        assertEquals(1, app.table!!.showdown)
        onNodeWithTag("card-$handCard").performClick() // exhaust
        app.act(Action.Damage(champ, 2)); app.act(Action.Buff(champ, true))
        onNodeWithTag("control-0").performClick()
        onNodeWithTag("score-$first-2").performClick()
        shot("08_table_play")

        onNodeWithTag("card-$champ").performMouseInput { rightClick(center) }
        shot("09_menu")
        onNodeWithTag("menu-Face down").performClick()
        assertTrue(app.table!!.card(champ).faceDown)

        // Pass: hot-seat covers the screen until the other player continues.
        onNodeWithTag("primary").performClick()
        assertNotNull(app.cover)
        shot("10_cover")
        onNodeWithTag("continue").performClick()
        assertEquals(first.other, app.viewer)
        shot("11_other_seat")
    }

    @Test fun settingsAndNavyTable() = runDesktopComposeUiTest(1440, 900) {
        val app = newApp()
        app.saveDeck(jinx.copy(name = "Jinx"), Side.ME); app.saveDeck(kaisa.copy(name = "Kai'Sa"), Side.OPP)
        setContent { App(app) }
        onNodeWithTag("settings").performClick()
        onNodeWithTag("theme-NAVY").performClick()
        shot("12_settings")
        app.startTable(io.github.mustypit.riftboundtable.game.Table.newGame(jinx, kaisa, Side.ME, "Back-Alley Bar" to "Grove of the God-Willow", Random(3)))
        app.act(Action.StartTurn)
        shot("13_table_navy")
    }

    /** Frames of the table's motion (paused clock, 20 ms steps) for GIFs: card slide, exhaust, score pop; and the d20 roll. */
    @Test fun animationFrames() = runDesktopComposeUiTest(1440, 900) {
        val anim = File(shots, "anim").apply { mkdirs() }
        fun frame(name: String) = ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(anim, "$name.png"))
        val app = newApp()
        app.saveDeck(jinx.copy(name = "Jinx"), Side.ME); app.saveDeck(kaisa.copy(name = "Kai'Sa"), Side.OPP)
        setContent { App(app, diceClockMs = clock) }
        app.startTable(io.github.mustypit.riftboundtable.game.Table.newGame(jinx, kaisa, Side.ME, "Back-Alley Bar" to "Grove of the God-Willow", Random(3)))
        app.act(Action.StartTurn)
        waitForIdle()
        mainClock.autoAdvance = false
        val t = app.table!!
        val card = t.ids(Zone.of(ZoneKind.HAND, Side.ME))[2]
        app.act(Action.Move(card, Zone.of(ZoneKind.BASE, Side.ME)))
        for (i in 0..13) { mainClock.advanceTimeBy(20); frame("move_%02d".format(i)) }
        app.act(Action.Exhaust(card, true))
        for (i in 0..9) { mainClock.advanceTimeBy(20); frame("exhaust_%02d".format(i)) }
        app.act(Action.Score(Side.ME, 1, io.github.mustypit.riftboundtable.game.PointKind.CONQUER))
        for (i in 0..13) { mainClock.advanceTimeBy(20); frame("score_%02d".format(i)) }
        mainClock.autoAdvance = true
        app.leaveTable(); waitForIdle()
        app.roll()
        for (i in 0..74) { clock = i * 60f; waitForIdle(); frame("roll_%02d".format(i)) }
    }

    private var clock by androidx.compose.runtime.mutableStateOf(0f)
}

private operator fun Offset.times(k: Float) = Offset(x * k, y * k)
