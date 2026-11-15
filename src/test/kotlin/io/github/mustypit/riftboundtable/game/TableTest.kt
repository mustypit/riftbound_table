package io.github.mustypit.riftboundtable.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

class TableTest {
    @Before fun load() = Cards.install(File("src/main/resources/cards/cards.json").readText())

    private fun deck(legend: String, champion: String, domains: List<Domain>, fields: List<String>): Deck {
        val pool = Cards.all.filter { DeckRules.isMainCard(it) && it.domainList.isNotEmpty() && it.domainList.all { d -> d in domains } && !it.isChampion }
        val main = linkedMapOf(champion to 1)
        for (c in pool) { if (main.values.sum() >= 40) break; main[c.slug] = minOf(3, 40 - main.values.sum()) }
        return Deck(name = legend, legend = legend, champion = champion, main = main,
            runes = mapOf(domains[0].name to 6, domains[1].name to 6), battlefields = fields)
    }

    private val me get() = deck("loose-cannon", "jinx-rebel", listOf(Domain.FURY, Domain.CHAOS), listOf("Back-Alley Bar", "Bandle Tree", "Altar to Unity"))
    private val opp get() = deck("daughter-of-the-void", "kai-sa-survivor", listOf(Domain.FURY, Domain.MIND), listOf("Grove of the God-Willow", "Fortified Position", "Aspirant's Climb"))

    private fun game(first: Side = Side.ME, seed: Int = 1) =
        Table.newGame(me, opp, first, "Back-Alley Bar" to "Grove of the God-Willow", Random(seed))

    @Test fun newGameLaysOutEveryZone() {
        val s = game()
        for (side in Side.entries) {
            assertEquals(1, s.count(ZoneKind.LEGEND, side))
            assertEquals(1, s.count(ZoneKind.CHAMPION, side))
            assertEquals(Table.OPENING_HAND, s.count(ZoneKind.HAND, side))
            assertEquals(39 - Table.OPENING_HAND, s.count(ZoneKind.DECK, side))
            assertEquals(12, s.count(ZoneKind.RUNE_DECK, side))
            assertEquals(0, s.count(ZoneKind.RUNES, side))
        }
        assertEquals(listOf("back-alley-bar", "grove-of-the-god-willow"), s.battlefields)
        assertEquals(s.cards.size, s.zones.values.sumOf { it.size })
        assertEquals(s.cards.size, s.zones.values.flatten().toSet().size)
        val runeSlugs = s.cardsIn(Zone.of(ZoneKind.RUNE_DECK, Side.OPP)).map { it.slug }.toSet()
        assertEquals(setOf("fury-rune", "mind-rune"), runeSlugs)
        runeSlugs.forEach { assertTrue(it, Cards.bySlug(it) != null) }
    }

    @Test fun goingFirstPutsYourBattlefieldOnTheLeft() {
        assertEquals(listOf("grove-of-the-god-willow", "back-alley-bar"), game(first = Side.OPP).battlefields)
        assertEquals(listOf(Side.OPP, Side.ME), game(first = Side.OPP).battlefieldOwners)
    }

    @Test fun shufflesDependOnTheRandomSource() {
        fun library(s: TableState) = (s.cardsIn(Zone.of(ZoneKind.HAND, Side.ME)) + s.cardsIn(Zone.of(ZoneKind.DECK, Side.ME))).map { it.slug }
        val a = library(game(seed = 1))
        val b = library(game(seed = 2))
        assertEquals(a.sorted(), b.sorted())
        assertTrue(a != b)
        assertEquals(a, library(game(seed = 1)))
    }

    @Test fun startOfTurnChannelsDrawsAndTheSecondPlayerGetsAnExtraRune() {
        var s = Table.apply(game(), Action.StartTurn)
        assertEquals(2, s.count(ZoneKind.RUNES, Side.ME))
        assertEquals(5, s.count(ZoneKind.HAND, Side.ME))
        s = Table.apply(s, Action.EndTurn)
        assertEquals(Side.OPP, s.active); assertEquals(Phase.AWAKEN, s.phase)
        s = Table.apply(s, Action.StartTurn)
        assertEquals(3, s.count(ZoneKind.RUNES, Side.OPP))
        s = Table.apply(Table.apply(s, Action.EndTurn), Action.StartTurn)
        assertEquals(4, s.count(ZoneKind.RUNES, Side.ME))
        s = Table.apply(Table.apply(s, Action.EndTurn), Action.StartTurn)
        assertEquals(5, s.count(ZoneKind.RUNES, Side.OPP))
    }

    @Test fun startOfTurnReadiesAndScoresHolds() {
        var s = Table.apply(game(), Action.StartTurn)
        val rune = s.ids(Zone.of(ZoneKind.RUNES, Side.ME)).first()
        s = Table.apply(s, Action.Exhaust(rune, true))
        s = Table.apply(s, Action.Control(0, Side.ME))
        s = Table.apply(Table.apply(s, Action.EndTurn), Action.StartTurn)
        s = Table.apply(Table.apply(s, Action.EndTurn), Action.StartTurn)
        assertFalse(s.card(rune).exhausted)
        assertEquals(1, s.score[Side.ME])
        assertTrue(s.log.any { it.text == "Holds Back-Alley Bar: +1" })
    }

    @Test fun movingIntoABattlefieldYouDontControlOpensAShowdown() {
        var s = Table.apply(game(), Action.StartTurn)
        val unit = s.cardsIn(Zone.of(ZoneKind.CHAMPION, Side.ME)).first().id
        s = Table.apply(s, Action.Move(unit, Zone.of(ZoneKind.BASE, Side.ME)))
        assertNull(s.showdown)
        s = Table.apply(s, Action.Move(unit, Zone.bf(Side.ME, 1)))
        assertEquals(1, s.showdown)
        assertTrue(s.log.last().text.startsWith("Showdown at Grove"))
        s = Table.apply(s, Action.Damage(unit, 3))
        s = Table.apply(s, Action.Might(unit, 2))
        s = Table.apply(s, Action.EndTurn)
        assertNull(s.showdown)
        assertEquals(0, s.card(unit).damage); assertEquals(0, s.card(unit).mightMod)
    }

    @Test fun leavingTheBoardClearsCountersAndHiddenCardsStayAnonymousInTheLog() {
        var s = game()
        val card = s.ids(Zone.of(ZoneKind.HAND, Side.ME)).first()
        val name = Cards.bySlug(s.card(card).slug)!!.name
        s = Table.apply(s, Action.FaceDown(card, true))
        s = Table.apply(s, Action.Move(card, Zone.bf(Side.ME, 0)))
        assertEquals("Hides a card at Back-Alley Bar", s.log.last().text)
        assertTrue(s.log.none { name in it.text })
        s = Table.apply(s, Action.Damage(card, 2))
        s = Table.apply(s, Action.Move(card, Zone.of(ZoneKind.TRASH, Side.ME)))
        assertFalse(s.card(card).faceDown); assertEquals(0, s.card(card).damage)
    }

    @Test fun drawingFromAPileTakesTheTopAndRecyclingGoesToTheBottom() {
        var s = Table.apply(game(), Action.StartTurn)
        val top = s.ids(Zone.of(ZoneKind.DECK, Side.ME)).first()
        s = Table.apply(s, Action.Draw(Side.ME))
        assertEquals(top, s.ids(Zone.of(ZoneKind.HAND, Side.ME)).last())
        val rune = s.ids(Zone.of(ZoneKind.RUNES, Side.ME)).first()
        s = Table.apply(s, Action.Recycle(rune))
        assertEquals(rune, s.ids(Zone.of(ZoneKind.RUNE_DECK, Side.ME)).last())
    }

    @Test fun scoreStaysBetweenZeroAndEightAndEightWins() {
        var s = game()
        repeat(10) { s = Table.apply(s, Action.Score(Side.OPP, 1, PointKind.CONQUER)) }
        assertEquals(8, s.score[Side.OPP]); assertEquals(Side.OPP, s.winner())
        s = Table.apply(s, Action.Score(Side.OPP, -9))
        assertEquals(0, s.score[Side.OPP]); assertNull(s.winner())
    }

    @Test fun passHandsFocusOver() {
        var s = Table.apply(game(), Action.StartTurn)
        s = Table.apply(s, Action.Pass)
        assertEquals(Side.OPP, s.focus); assertEquals(Side.ME, s.active)
        s = Table.apply(s, Action.Pass)
        assertEquals(Side.ME, s.focus)
    }

    @Test fun everyActionUndoesAndRedoes() {
        val h = TableHistory(game())
        val start = h.state
        h.apply(Action.StartTurn)
        val unit = h.state.cardsIn(Zone.of(ZoneKind.CHAMPION, Side.ME)).first().id
        val steps = listOf(
            Action.Move(unit, Zone.bf(Side.ME, 1)), Action.Exhaust(unit, true), Action.Damage(unit, 2), Action.Buff(unit, true),
            Action.Xp(unit, 1), Action.Score(Side.ME, 1, PointKind.CONQUER), Action.Control(1, Side.ME), Action.Pass,
            Action.SetPool(Side.ME, Pool(1, 1)), Action.Draw(Side.OPP), Action.EndTurn,
        )
        val states = mutableListOf(h.state)
        steps.forEach { states += h.apply(it) }
        for (i in steps.indices.reversed()) assertEquals(states[i], h.undo())
        for (i in steps.indices) assertEquals(states[i + 1], h.redo())
        repeat(steps.size + 1) { h.undo() }
        assertEquals(start, h.state)
        assertFalse(h.canUndo)
    }

    @Test fun gamesStartWaitingForTheFirstTurnAndMulliganSwapsWithTheTopCard() {
        var s = game()
        assertEquals(Phase.AWAKEN, s.phase)
        val card = s.ids(Zone.of(ZoneKind.HAND, Side.ME))[1]
        val top = s.ids(Zone.of(ZoneKind.DECK, Side.ME)).first()
        s = Table.apply(s, Action.Mulligan(card))
        assertEquals(top, s.ids(Zone.of(ZoneKind.HAND, Side.ME))[1])
        assertEquals(card, s.ids(Zone.of(ZoneKind.DECK, Side.ME)).last())
        assertEquals(Table.OPENING_HAND, s.count(ZoneKind.HAND, Side.ME))
    }

    @Test fun aNewActionClearsRedo() {
        val h = TableHistory(game())
        h.apply(Action.StartTurn); h.undo()
        assertTrue(h.canRedo)
        h.apply(Action.Pass)
        assertFalse(h.canRedo)
    }
}
