package io.github.mustypit.riftboundtable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mustypit.riftboundtable.data.Settings
import io.github.mustypit.riftboundtable.data.Store
import io.github.mustypit.riftboundtable.game.Action
import io.github.mustypit.riftboundtable.game.Cards
import io.github.mustypit.riftboundtable.game.Deck
import io.github.mustypit.riftboundtable.game.DeckRules
import io.github.mustypit.riftboundtable.game.Dice
import io.github.mustypit.riftboundtable.game.Side
import io.github.mustypit.riftboundtable.game.Table
import io.github.mustypit.riftboundtable.game.TableHistory
import io.github.mustypit.riftboundtable.game.TableState
import io.github.mustypit.riftboundtable.game.ZoneKind
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

sealed interface Screen {
    data object NewGame : Screen
    /** Importing a deck for a seat (or for the library when null). */
    data class Import(val seat: Side?) : Screen
    data class PickDeck(val seat: Side) : Screen
    data object Settings : Screen
    data class Roll(val roll: Dice.Roll) : Screen
    /** Each seat picks one of its three battlefields, [seat] first. */
    data class Battlefields(val first: Side, val seat: Side, val picked: Map<Side, String> = emptyMap()) : Screen
    data object Table : Screen
}

/**
 * Everything the window shows, and every user action. [random] is SecureRandom in the app; tests
 * pass a seeded one.
 */
class AppState(
    private val store: Store = Store.default(),
    private val random: Random = SecureRandom().asKotlinRandom(),
) {
    var settings by mutableStateOf(store.settings()); private set
    var decks by mutableStateOf(store.decks()); private set
    var screen by mutableStateOf<Screen>(Screen.NewGame); private set
    var history: TableHistory? = null; private set
    /** The table as shown; changes with every action. */
    var table by mutableStateOf<TableState?>(null); private set
    /** Who sits at the bottom of the screen and sees their hand. */
    var viewer by mutableStateOf(Side.ME); private set
    /** Hot-seat: the screen is covered until [cover] clicks "Continue". */
    var cover by mutableStateOf<Side?>(null); private set

    fun seatName(side: Side) = if (side == Side.ME) "Player 1" else "Player 2"
    fun shortName(side: Side) = if (side == Side.ME) "P1" else "P2"
    fun deckOf(side: Side): Deck? = decks.firstOrNull { it.id == (if (side == Side.ME) settings.myDeck else settings.oppDeck) }

    fun go(s: Screen) { screen = s }
    fun updateSettings(f: (Settings) -> Settings) { settings = f(settings); store.saveSettings(settings) }

    fun saveDeck(deck: Deck, seat: Side?): Deck {
        val saved = if (deck.id == 0L) deck.copy(id = (decks.maxOfOrNull { it.id } ?: 0L) + 1) else deck
        decks = decks.filter { it.id != saved.id } + saved
        store.saveDecks(decks)
        if (seat != null) chooseDeck(seat, saved)
        return saved
    }

    fun deleteDeck(deck: Deck) { decks = decks.filter { it.id != deck.id }; store.saveDecks(decks) }

    fun chooseDeck(seat: Side, deck: Deck) = updateSettings { if (seat == Side.ME) it.copy(myDeck = deck.id) else it.copy(oppDeck = deck.id) }

    /** Throws the dice (results drawn here, then shown). */
    fun roll() { if (deckOf(Side.ME) != null && deckOf(Side.OPP) != null) screen = Screen.Roll(Dice.roll(random)) }

    fun rolled(roll: Dice.Roll) { screen = Screen.Battlefields(first = roll.winner, seat = roll.winner) }

    fun pickBattlefield(pick: Screen.Battlefields, name: String) {
        val picked = pick.picked + (pick.seat to name)
        val other = pick.seat.other
        screen = if (other in picked) { startGame(pick.first, picked); Screen.Table } else pick.copy(seat = other, picked = picked)
    }

    private fun startGame(first: Side, picked: Map<Side, String>) {
        val state = Table.newGame(deckOf(Side.ME)!!, deckOf(Side.OPP)!!, first, picked.getValue(Side.ME) to picked.getValue(Side.OPP), random)
        startTable(state)
    }

    /** Puts a laid-out table on screen (tests use this to start from a given position). */
    fun startTable(state: TableState) {
        history = TableHistory(state); table = state; viewer = state.focus; cover = null; screen = Screen.Table
    }

    fun act(a: Action) {
        val h = history ?: return
        val before = h.state
        table = h.apply(a)
        afterChange(before)
    }

    fun shuffle(side: Side, kind: ZoneKind) {
        val h = history ?: return
        table = h.set(Table.shuffle(h.state, side, kind, random))
    }

    fun undo() { val h = history ?: return; val before = h.state; table = h.undo(); afterChange(before) }
    fun redo() { val h = history ?: return; val before = h.state; table = h.redo(); afterChange(before) }

    /** Hot-seat: when focus moves to the other seat, cover the screen before showing their hand. */
    private fun afterChange(before: TableState) {
        val t = table ?: return
        if (t.focus != before.focus && t.focus != viewer) cover = t.focus
    }

    fun uncover() { cover?.let { viewer = it }; cover = null }
    /** Swap seats without passing (hot-seat). */
    fun switchSeat() { cover = viewer.other }

    fun leaveTable() { history = null; table = null; screen = Screen.NewGame }

    companion object {
        /** Loads the bundled card library and battlefield art list (once per process). */
        fun loadLibrary() {
            if (Cards.all.isNotEmpty()) return
            fun res(p: String) = AppState::class.java.getResourceAsStream(p)!!.bufferedReader().readText()
            Cards.install(res("/cards/cards.json"))
            io.github.mustypit.riftboundtable.game.Battlefields.install(res("/battlefields/battlefields.json"))
            io.github.mustypit.riftboundtable.game.Legends.install(res("/legends/legends.json"))
        }

        fun battlefieldsOf(deck: Deck): List<String> = deck.battlefields.filter { DeckRules.battlefieldCard(it) != null }
    }
}
