package io.github.mustypit.riftboundtable.game

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The two seats. [ME] sits at the bottom of the table; [OPP] is Claude (or the second player in hot-seat). */
@Serializable
enum class Side { ME, OPP;
    val other get() = if (this == ME) OPP else ME
}

/**
 * Where a card can be. Per side: hand, main deck, trash, banishment, base, channelled runes, rune deck,
 * legend and champion zone. [BATTLEFIELD] zones also carry the battlefield's index (0 or 1): a side's
 * units there, including its face-down card.
 */
@Serializable
enum class ZoneKind(val hidden: Boolean = false, val pile: Boolean = false) {
    HAND(hidden = true), DECK(hidden = true, pile = true), TRASH(pile = true), BANISH(pile = true),
    BASE, RUNES, RUNE_DECK(hidden = true, pile = true), LEGEND, CHAMPION, BATTLEFIELD
}

@Serializable
data class Zone(val kind: ZoneKind, val side: Side, val bf: Int = -1) {
    init { require((kind == ZoneKind.BATTLEFIELD) == (bf >= 0)) { "Only battlefield zones have an index" } }
    companion object {
        fun bf(side: Side, index: Int) = Zone(ZoneKind.BATTLEFIELD, side, index)
        fun of(kind: ZoneKind, side: Side) = Zone(kind, side)
    }
}

/** One physical card on the table. [id]s are unique within a game and never reused. */
@Serializable
data class CardInst(
    val id: Int,
    val slug: String,
    val owner: Side,
    val exhausted: Boolean = false,
    val damage: Int = 0,
    /** A buff (+1 might; a unit has at most one). */
    val buff: Boolean = false,
    /** Temporary might change until the end of the turn ("+2 this turn"). */
    val mightMod: Int = 0,
    val xp: Int = 0,
    val faceDown: Boolean = false,
)

@Serializable
enum class Phase(val label: String) {
    AWAKEN("Awaken"), BEGINNING("Begin"), CHANNEL("Channel"), DRAW("Draw"), ACTION("Action"), END("End")
}

@Serializable
enum class PointKind { CONQUER, HOLD, ABILITY }

/** Energy and power added mid-turn and not spent yet (the "floating" counters). */
@Serializable
data class Pool(val energy: Int = 0, val power: Int = 0)

@Serializable
data class LogEntry(val side: Side?, val text: String)

@Serializable
data class TableState(
    val cards: Map<Int, CardInst> = emptyMap(),
    /** Card ids per zone, in order. For piles the first id is the top card. */
    val zones: Map<Zone, List<Int>> = emptyMap(),
    /** Battlefield card slugs, index 0 and 1. */
    val battlefields: List<String> = emptyList(),
    /** Who brought each battlefield. */
    val battlefieldOwners: List<Side> = emptyList(),
    val control: List<Side?> = listOf(null, null),
    val score: Map<Side, Int> = mapOf(Side.ME to 0, Side.OPP to 0),
    val pointsToWin: Int = Table.POINTS_TO_WIN,
    val first: Side = Side.ME,
    /** Turn number, counting both players' turns (turn 1 is [first]'s). */
    val turn: Int = 1,
    val active: Side = Side.ME,
    val phase: Phase = Phase.ACTION,
    /** Who is to decide now: the active player, or the other one after a Pass. */
    val focus: Side = Side.ME,
    /** Battlefield index with an open showdown, or null. */
    val showdown: Int? = null,
    val pools: Map<Side, Pool> = mapOf(Side.ME to Pool(), Side.OPP to Pool()),
    val log: List<LogEntry> = emptyList(),
    val nextId: Int = 1,
    /** Turns each side has started (for "the second player channels one more rune on their first turn"). */
    val turnsTaken: Map<Side, Int> = mapOf(Side.ME to 0, Side.OPP to 0),
) {
    fun ids(z: Zone): List<Int> = zones[z].orEmpty()
    fun cardsIn(z: Zone): List<CardInst> = ids(z).map { cards.getValue(it) }
    fun zoneOf(id: Int): Zone? = zones.entries.firstOrNull { id in it.value }?.key
    fun card(id: Int): CardInst = cards.getValue(id)
    fun count(kind: ZoneKind, side: Side) = ids(Zone.of(kind, side)).size
    fun winner(): Side? = Side.entries.firstOrNull { (score[it] ?: 0) >= pointsToWin }
}

/** Every change to the table. Each one is a single undo step. */
@Serializable
sealed interface Action {
    /** Move a card. [index] null = end of the zone; for piles 0 = top. */
    @Serializable data class Move(val id: Int, val to: Zone, val index: Int? = null) : Action
    @Serializable data class Exhaust(val id: Int, val exhausted: Boolean) : Action
    @Serializable data class Damage(val id: Int, val delta: Int) : Action
    @Serializable data class Buff(val id: Int, val on: Boolean) : Action
    @Serializable data class Might(val id: Int, val delta: Int) : Action
    @Serializable data class Xp(val id: Int, val delta: Int) : Action
    @Serializable data class FaceDown(val id: Int, val down: Boolean) : Action
    @Serializable data class Draw(val side: Side, val n: Int = 1) : Action
    @Serializable data class Channel(val side: Side, val n: Int) : Action
    /** Put a rune from the board on the bottom of its rune deck (paying power). */
    @Serializable data class Recycle(val id: Int) : Action
    @Serializable data class Shuffle(val side: Side, val kind: ZoneKind) : Action
    @Serializable data class Score(val side: Side, val delta: Int, val kind: PointKind? = null) : Action
    @Serializable data class SetScore(val side: Side, val value: Int) : Action
    @Serializable data class Control(val bf: Int, val side: Side?) : Action
    @Serializable data class Showdown(val bf: Int?) : Action
    @Serializable data class SetPool(val side: Side, val pool: Pool) : Action
    @Serializable data class ReadyAll(val side: Side) : Action
    /** Awaken, beginning (hold points), channel, draw: the start-of-turn helper. */
    @Serializable data object StartTurn : Action
    /** Hand focus to the other player. */
    @Serializable data object Pass : Action
    /** End phase: damage and "this turn" effects wear off, pools empty, the other player's turn. */
    @Serializable data object EndTurn : Action
    @Serializable data class Note(val side: Side?, val text: String) : Action
    /** Mulligan one card: it goes to the bottom of the deck and the top card replaces it. */
    @Serializable data class Mulligan(val id: Int) : Action
}

/**
 * Pure table logic. No rules engine: the players enforce the rules; this only moves cards and keeps
 * the bookkeeping (score, control, focus, turn) the players ask for.
 */
object Table {
    const val POINTS_TO_WIN = 8
    const val OPENING_HAND = 4
    const val CHANNEL_PER_TURN = 2
    /** The player going second channels one extra rune on their first turn. Verify against the core rules (phase 3). */
    const val SECOND_PLAYER_EXTRA_RUNE = 1

    /** Rune card slugs per domain, as in cards.json. */
    fun runeSlug(d: Domain) = "${d.name.lowercase()}-rune"

    /**
     * Lays out a new game: legends, champions, shuffled main decks (without the chosen champion's
     * copy), shuffled rune decks, the chosen battlefields and opening hands. [random] must be
     * SecureRandom-backed in the app.
     */
    fun newGame(me: Deck, opp: Deck, first: Side, fields: Pair<String, String>, random: Random): TableState {
        var s = TableState(first = first, active = first, focus = first, phase = Phase.AWAKEN)
        val bfs = mutableListOf<String>()
        for ((side, deck) in listOf(Side.ME to me, Side.OPP to opp)) {
            s = s.add(side, Zone.of(ZoneKind.LEGEND, side), listOf(deck.legend))
            val main = deck.main.flatMap { (slug, n) -> List(n) { slug } }.toMutableList()
            deck.champion?.let { c -> if (main.remove(c)) s = s.add(side, Zone.of(ZoneKind.CHAMPION, side), listOf(c)) }
            s = s.add(side, Zone.of(ZoneKind.DECK, side), main.shuffled(random))
            val runes = deck.runes.flatMap { (d, n) -> List(n) { runeSlug(Domain.valueOf(d)) } }
            s = s.add(side, Zone.of(ZoneKind.RUNE_DECK, side), runes.shuffled(random))
            val fieldName = if (side == Side.ME) fields.first else fields.second
            bfs += DeckRules.battlefieldCard(fieldName)?.slug ?: fieldName
        }
        // The going-first player's battlefield sits on the left.
        val owners = if (first == Side.ME) listOf(Side.ME, Side.OPP) else listOf(Side.OPP, Side.ME)
        s = s.copy(battlefields = owners.map { bfs[it.ordinal] }, battlefieldOwners = owners)
        s = apply(s, Action.Draw(Side.ME, OPENING_HAND))
        s = apply(s, Action.Draw(Side.OPP, OPENING_HAND))
        return s.copy(log = listOf(LogEntry(first, "Goes first")))
    }

    private fun TableState.add(side: Side, z: Zone, slugs: List<String>): TableState {
        val newCards = slugs.mapIndexed { i, slug -> CardInst(nextId + i, slug, side) }
        return copy(
            cards = cards + newCards.associateBy { it.id },
            zones = zones + (z to ids(z) + newCards.map { it.id }),
            nextId = nextId + slugs.size,
        )
    }

    private fun TableState.update(id: Int, f: (CardInst) -> CardInst) = copy(cards = cards + (id to f(card(id))))
    private fun TableState.log(side: Side?, text: String) = copy(log = (log + LogEntry(side, text)).takeLast(200))
    private fun name(slug: String) = Cards.bySlug(slug)?.name ?: slug

    /** The visible name of a card for the log: face-down and hidden cards stay anonymous. */
    private fun TableState.shown(id: Int, z: Zone?): String {
        val c = card(id)
        return if (c.faceDown || z == null || z.kind.hidden) "a card" else name(c.slug)
    }

    private fun zoneLabel(s: TableState, z: Zone): String = when (z.kind) {
        ZoneKind.BATTLEFIELD -> name(s.battlefields.getOrElse(z.bf) { "battlefield ${z.bf + 1}" })
        ZoneKind.HAND -> "hand"; ZoneKind.DECK -> "deck"; ZoneKind.TRASH -> "trash"; ZoneKind.BANISH -> "banishment"
        ZoneKind.BASE -> "base"; ZoneKind.RUNES -> "runes"; ZoneKind.RUNE_DECK -> "rune deck"
        ZoneKind.LEGEND -> "legend"; ZoneKind.CHAMPION -> "champion zone"
    }

    fun apply(s: TableState, a: Action): TableState = when (a) {
        is Action.Move -> move(s, a)
        is Action.Exhaust -> s.update(a.id) { it.copy(exhausted = a.exhausted) }
        is Action.Damage -> s.update(a.id) { it.copy(damage = (it.damage + a.delta).coerceAtLeast(0)) }
        is Action.Buff -> s.update(a.id) { it.copy(buff = a.on) }
        is Action.Might -> s.update(a.id) { it.copy(mightMod = it.mightMod + a.delta) }
        is Action.Xp -> s.update(a.id) { it.copy(xp = (it.xp + a.delta).coerceAtLeast(0)) }
        is Action.FaceDown -> s.update(a.id) { it.copy(faceDown = a.down) }
            .let { if (!a.down) it.log(s.card(a.id).owner, "Reveals ${name(s.card(a.id).slug)}") else it }
        is Action.Draw -> draw(s, a.side, a.n)
        is Action.Channel -> channel(s, a.side, a.n)
        is Action.Recycle -> {
            val c = s.card(a.id)
            val from = s.zoneOf(a.id)
            val to = Zone.of(ZoneKind.RUNE_DECK, c.owner)
            s.copy(zones = s.zones + (from!! to s.ids(from) - a.id) + (to to s.ids(to) - a.id + a.id))
                .update(a.id) { it.copy(exhausted = false) }
                .log(c.owner, "Recycles ${name(c.slug)}")
        }
        is Action.Shuffle -> s // Shuffling needs randomness: use [shuffle].
        is Action.Score -> {
            val v = ((s.score[a.side] ?: 0) + a.delta).coerceIn(0, s.pointsToWin)
            val what = when (a.kind) { PointKind.CONQUER -> " (conquer)"; PointKind.HOLD -> " (hold)"; PointKind.ABILITY -> " (ability)"; null -> "" }
            s.copy(score = s.score + (a.side to v)).log(a.side, (if (a.delta >= 0) "+${a.delta}" else "${a.delta}") + " point$what")
        }
        is Action.SetScore -> s.copy(score = s.score + (a.side to a.value.coerceIn(0, s.pointsToWin))).log(a.side, "Score ${a.value}")
        is Action.Control -> s.copy(control = s.control.toMutableList().also { it[a.bf] = a.side })
        is Action.Showdown -> s.copy(showdown = a.bf).let {
            if (a.bf != null) it.log(null, "Showdown at ${name(s.battlefields[a.bf])}") else it
        }
        is Action.SetPool -> s.copy(pools = s.pools + (a.side to Pool(a.pool.energy.coerceAtLeast(0), a.pool.power.coerceAtLeast(0))))
        is Action.ReadyAll -> readyAll(s, a.side)
        Action.StartTurn -> startTurn(s)
        Action.Pass -> s.copy(focus = s.focus.other).log(s.focus, "Pass")
        Action.EndTurn -> endTurn(s)
        is Action.Note -> s.log(a.side, a.text)
        is Action.Mulligan -> {
            val side = s.card(a.id).owner
            val deck = Zone.of(ZoneKind.DECK, side); val hand = Zone.of(ZoneKind.HAND, side)
            if (s.zoneOf(a.id) != hand || s.ids(deck).isEmpty()) s else {
                val top = s.ids(deck).first()
                s.copy(zones = s.zones + (hand to s.ids(hand).map { if (it == a.id) top else it }) + (deck to s.ids(deck).drop(1) + a.id))
                    .log(side, "Mulligans a card")
            }
        }
    }

    private fun move(s: TableState, a: Action.Move): TableState {
        val from = s.zoneOf(a.id) ?: return s
        val c = s.card(a.id)
        val without = s.ids(from) - a.id
        val target = if (from == a.to) without else s.ids(a.to)
        val i = (a.index ?: target.size).coerceIn(0, target.size)
        val placed = target.toMutableList().apply { add(i, a.id) }
        var zones = s.zones + (from to without) + (a.to to placed)
        var t = s.copy(zones = zones)
        // Leaving the board clears damage, buffs and exhaustion; going to a hidden zone also turns it face up.
        val onBoard = setOf(ZoneKind.BASE, ZoneKind.BATTLEFIELD, ZoneKind.RUNES, ZoneKind.LEGEND, ZoneKind.CHAMPION)
        if (a.to.kind !in onBoard) t = t.update(a.id) { it.copy(exhausted = false, damage = 0, buff = false, mightMod = 0, faceDown = false) }
        if (from == a.to) return t
        // A unit moving to a battlefield its side doesn't control opens a showdown there.
        if (a.to.kind == ZoneKind.BATTLEFIELD && from.kind != ZoneKind.HAND && s.control[a.to.bf] != a.to.side && s.showdown == null &&
            !c.faceDown && Cards.bySlug(c.slug)?.type == "unit") {
            t = t.copy(showdown = a.to.bf)
        }
        val who = a.to.side.takeIf { a.to.kind != ZoneKind.TRASH } ?: c.owner
        val text = when {
            from.kind == ZoneKind.HAND && a.to.kind == ZoneKind.BATTLEFIELD && t.card(a.id).faceDown -> "Hides a card at ${zoneLabel(s, a.to)}"
            from.kind == ZoneKind.HAND -> "Plays ${t.shown(a.id, a.to)} to ${zoneLabel(s, a.to)}"
            else -> "${s.shown(a.id, from).replaceFirstChar { it.uppercase() }} ${zoneLabel(s, from)} → ${zoneLabel(s, a.to)}"
        }
        t = t.log(who, text)
        return if (t.showdown != s.showdown && t.showdown != null) t.log(null, "Showdown at ${zoneLabel(s, a.to)}") else t
    }

    private fun draw(s: TableState, side: Side, n: Int): TableState {
        val deck = Zone.of(ZoneKind.DECK, side); val hand = Zone.of(ZoneKind.HAND, side)
        val take = s.ids(deck).take(n)
        if (take.isEmpty()) return s.log(side, "Deck is empty")
        return s.copy(zones = s.zones + (deck to s.ids(deck).drop(take.size)) + (hand to s.ids(hand) + take))
            .log(side, "Draws ${take.size}")
    }

    private fun channel(s: TableState, side: Side, n: Int): TableState {
        val rd = Zone.of(ZoneKind.RUNE_DECK, side); val runes = Zone.of(ZoneKind.RUNES, side)
        val take = s.ids(rd).take(n)
        if (take.isEmpty()) return s
        return s.copy(zones = s.zones + (rd to s.ids(rd).drop(take.size)) + (runes to s.ids(runes) + take))
            .log(side, "Channels ${take.size}")
    }

    private fun boardZones(s: TableState, side: Side): List<Zone> =
        listOf(Zone.of(ZoneKind.BASE, side), Zone.of(ZoneKind.RUNES, side), Zone.of(ZoneKind.LEGEND, side), Zone.of(ZoneKind.CHAMPION, side)) +
            s.battlefields.indices.map { Zone.bf(side, it) }

    private fun readyAll(s: TableState, side: Side): TableState {
        var t = s
        for (z in boardZones(s, side)) for (id in s.ids(z)) t = t.update(id) { it.copy(exhausted = false) }
        return t
    }

    /** Awaken (ready all), beginning (one point per battlefield held), channel, draw; then the action phase. */
    fun startTurn(s: TableState): TableState {
        val side = s.active
        var t = readyAll(s, side).log(side, "Turn ${s.turn}")
        s.control.forEachIndexed { i, who ->
            if (who == side && t.winner() == null) t = apply(t, Action.Score(side, 1, PointKind.HOLD)).let {
                it.copy(log = it.log.dropLast(1) + LogEntry(side, "Holds ${name(s.battlefields[i])}: +1"))
            }
        }
        val firstTurn = (s.turnsTaken[side] ?: 0) == 0
        val extra = if (firstTurn && side != s.first) SECOND_PLAYER_EXTRA_RUNE else 0
        t = channel(t, side, CHANNEL_PER_TURN + extra)
        t = draw(t, side, 1)
        return t.copy(phase = Phase.ACTION, focus = side, turnsTaken = t.turnsTaken + (side to (t.turnsTaken[side] ?: 0) + 1))
    }

    fun endTurn(s: TableState): TableState {
        var cards = s.cards.mapValues { (_, c) -> c.copy(damage = 0, mightMod = 0) }
        val next = s.active.other
        return s.copy(
            cards = cards, pools = mapOf(Side.ME to Pool(), Side.OPP to Pool()), showdown = null,
            active = next, focus = next, phase = Phase.AWAKEN, turn = s.turn + 1,
        ).log(s.active, "Ends turn")
    }

    fun shuffle(s: TableState, side: Side, kind: ZoneKind, random: Random): TableState {
        val z = Zone.of(kind, side)
        return s.copy(zones = s.zones + (z to s.ids(z).shuffled(random))).log(side, "Shuffles ${zoneLabel(s, z)}")
    }

    /** Ready runes of a side, by domain, and in total. */
    fun readyRunes(s: TableState, side: Side): Map<Domain?, Int> =
        s.cardsIn(Zone.of(ZoneKind.RUNES, side)).filter { !it.exhausted }
            .groupingBy { Cards.bySlug(it.slug)?.domainList?.firstOrNull() }.eachCount()
}

/** The table with its history: every applied action can be undone and redone. */
class TableHistory(initial: TableState) {
    private val past = ArrayDeque<TableState>()
    private val future = ArrayDeque<TableState>()
    var state: TableState = initial
        private set

    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    fun apply(a: Action): TableState = set(Table.apply(state, a))

    /** Records a new state produced outside [Table.apply] (shuffles). */
    fun set(next: TableState): TableState {
        if (next == state) return state
        past.addLast(state); if (past.size > 500) past.removeFirst()
        future.clear(); state = next
        return state
    }

    fun undo(): TableState { if (past.isNotEmpty()) { future.addLast(state); state = past.removeLast() }; return state }
    fun redo(): TableState { if (future.isNotEmpty()) { past.addLast(state); state = future.removeLast() }; return state }
}
