@file:OptIn(ExperimentalTestApi::class)

package io.github.mustypit.riftboundtable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import io.github.mustypit.riftboundtable.game.Pool
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

        // Start the first turn, drag a unit from hand to base, then move the champion in.
        onNodeWithTag("primary").performClick()
        assertEquals(Phase.ACTION, app.table!!.phase)
        val handCard = unitInHand(app, first)
        drag("card-$handCard", "zone-BASE-$first")
        assertEquals(Zone.of(ZoneKind.BASE, first), app.table!!.zoneOf(handCard))
        assertTrue(app.table!!.card(handCard).exhausted) // units enter exhausted
        val champ = app.table!!.ids(Zone.of(ZoneKind.CHAMPION, first)).first()
        drag("card-$champ", "zone-BATTLEFIELD-$first-1")
        assertEquals(1, app.table!!.showdown)
        onNodeWithTag("card-$handCard").performClick() // ready
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
        val card = unitInHand(app, Side.ME)
        waitForIdle()
        app.act(Action.Move(card, Zone.of(ZoneKind.BASE, Side.ME)))
        for (i in 0..13) { mainClock.advanceTimeBy(20); frame("move_%02d".format(i)) }
        app.act(Action.Exhaust(card, false))
        for (i in 0..9) { mainClock.advanceTimeBy(20); frame("exhaust_%02d".format(i)) }
        app.act(Action.Score(Side.ME, 1, io.github.mustypit.riftboundtable.game.PointKind.CONQUER))
        for (i in 0..13) { mainClock.advanceTimeBy(20); frame("score_%02d".format(i)) }
        mainClock.autoAdvance = true
        app.leaveTable(); waitForIdle()
        app.roll()
        for (i in 0..74) { clock = i * 60f; waitForIdle(); frame("roll_%02d".format(i)) }
    }

    /** A unit in [side]'s hand (fetched from the deck if the hand has none). */
    private fun unitInHand(app: AppState, side: Side): Int {
        val t = app.table!!
        val hand = Zone.of(ZoneKind.HAND, side)
        t.ids(hand).firstOrNull { Cards.bySlug(t.card(it).slug)?.type == "unit" }?.let { return it }
        val id = t.ids(Zone.of(ZoneKind.DECK, side)).first { Cards.bySlug(t.card(it).slug)?.type == "unit" }
        app.act(Action.Move(id, hand)); return id
    }

    private fun cardInHand(app: AppState, side: Side, type: String): Int {
        val t = app.table!!
        val hand = Zone.of(ZoneKind.HAND, side)
        t.ids(hand).firstOrNull { Cards.bySlug(t.card(it).slug)?.type == type }?.let { return it }
        val id = t.ids(Zone.of(ZoneKind.DECK, side)).first { Cards.bySlug(t.card(it).slug)?.type == type }
        app.act(Action.Move(id, hand)); return id
    }

    private fun ComposeUiTest.key(k: Key, shift: Boolean = false) {
        onNodeWithTag("app-root").performKeyInput { if (shift) withKeyDown(Key.ShiftLeft) { pressKey(k) } else pressKey(k) }
        waitForIdle()
    }

    /** The RiftAtlas-style interactions at a 1920×1025 window (the owner's screen). */
    @Test fun tableInteractions() = runDesktopComposeUiTest(1920, 1025) {
        val app = newApp()
        app.saveDeck(jinx.copy(name = "Jinx"), Side.ME); app.saveDeck(kaisa.copy(name = "Kai'Sa"), Side.OPP)
        setContent { App(app) }
        app.startTable(io.github.mustypit.riftboundtable.game.Table.newGame(jinx, kaisa, Side.ME, "Back-Alley Bar" to "Grove of the God-Willow", Random(3)))
        waitForIdle()
        key(Key.Spacebar) // start turn
        assertEquals(Phase.ACTION, app.table!!.phase)
        val (r1, r2) = app.table!!.ids(Zone.of(ZoneKind.RUNES, Side.ME))

        // Runes: click taps for 1 energy, click again readies; right-click recycles for 1 power.
        onNodeWithTag("card-$r1").performClick()
        assertTrue(app.table!!.card(r1).exhausted); assertEquals(Pool(1, 0), app.table!!.pools[Side.ME])
        onNodeWithTag("card-$r1").performClick()
        assertFalse(app.table!!.card(r1).exhausted)
        onNodeWithTag("card-$r2").performMouseInput { rightClick(center) }
        assertEquals(Pool(1, 1), app.table!!.pools[Side.ME])
        assertEquals(Zone.of(ZoneKind.RUNE_DECK, Side.ME), app.table!!.zoneOf(r2))

        // Clicking a hand card plays it: units to base (exhausted), spells onto the chain.
        val unit = unitInHand(app, Side.ME)
        onNodeWithTag("card-$unit").performClick()
        assertEquals(Zone.of(ZoneKind.BASE, Side.ME), app.table!!.zoneOf(unit))
        assertTrue(app.table!!.card(unit).exhausted)
        val spell = cardInHand(app, Side.ME, "spell")
        onNodeWithTag("card-$spell").performClick()
        assertEquals(Zone.CHAIN, app.table!!.zoneOf(spell))

        // T: target from the top of the chain, then click the unit.
        key(Key.T)
        assertEquals(spell, app.targeting)
        onNodeWithTag("target-banner").assertExists()
        shot("14_targeting")
        onNodeWithTag("card-$unit").performClick()
        assertEquals(listOf(unit), app.table!!.card(spell).targets)
        assertNull(app.targeting)
        shot("15_chain_target")

        // Hovering shows the card large; hovering a battlefield shows the whole battlefield card.
        val other = app.table!!.ids(Zone.of(ZoneKind.HAND, Side.ME)).first()
        onNodeWithTag("card-$other").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithTag("preview").assertExists()
        shot("16_hover_hand")
        onNodeWithTag("bfcard-1").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithTag("bf-preview").assertExists()
        shot("17_hover_battlefield")

        // S resolves: the spell goes to the trash and its arrow disappears.
        key(Key.S)
        assertEquals(Zone.of(ZoneKind.TRASH, Side.ME), app.table!!.zoneOf(spell))

        // C on the hovered unit: might and damage.
        onNodeWithTag("card-$unit").performMouseInput { moveTo(center) }
        waitForIdle()
        key(Key.C)
        onNodeWithTag("edit").assertExists()
        onNodeWithContentDescription("More Might").performClick()
        onNodeWithContentDescription("More Damage").performClick()
        onNodeWithContentDescription("More Damage").performClick()
        assertEquals(1, app.table!!.card(unit).mightMod); assertEquals(2, app.table!!.card(unit).damage)
        shot("18_edit_might")
        key(Key.Escape)
        assertNull(app.editing)

        // R undoes, Shift+R redoes.
        key(Key.R)
        assertEquals(1, app.table!!.card(unit).damage)
        key(Key.R, shift = true)
        assertEquals(2, app.table!!.card(unit).damage)

        // Pool steppers.
        onAllNodesWithContentDescription("More Energy")[1].performClick()
        assertEquals(2, app.table!!.pools[Side.ME]!!.energy)
        shot("19_table_1920")

        // Dragging a permanent (here the legend) or a battlefield card onto the chain panel puts its ability on the chain.
        val legend = app.table!!.ids(Zone.of(ZoneKind.LEGEND, Side.ME)).first()
        drag("card-$legend", "chain")
        assertEquals(Zone.of(ZoneKind.LEGEND, Side.ME), app.table!!.zoneOf(legend))
        val ability = app.table!!.ids(Zone.CHAIN).last()
        assertTrue(app.table!!.card(ability).ability); assertEquals(legend, app.table!!.card(ability).source)
        drag("bfcard-0", "chain")
        assertEquals(2, app.table!!.ids(Zone.CHAIN).size)
        assertEquals(app.table!!.battlefields[0], app.table!!.card(app.table!!.ids(Zone.CHAIN).last()).slug)
        shot("20_abilities_on_chain")
        key(Key.S); key(Key.S)
        assertTrue(app.table!!.ids(Zone.CHAIN).isEmpty())
        assertEquals(Zone.of(ZoneKind.LEGEND, Side.ME), app.table!!.zoneOf(legend))
    }

    private var clock by androidx.compose.runtime.mutableStateOf(0f)
}

private operator fun Offset.times(k: Float) = Offset(x * k, y * k)
