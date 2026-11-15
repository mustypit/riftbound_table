# Riftbound Table (desktop virtual table for the Riftbound TCG)

Personal, unofficial desktop app: a virtual table to play Riftbound hot-seat, and (phase 3) against
Claude through the owner's own Claude Code login (`claude -p`). **No rules engine**: players move
cards and enforce the rules themselves, like RiftAtlas. Kotlin + Compose for Desktop. The owner
installs the Windows/macOS/Linux installers that GitHub Actions builds. Not affiliated with Riot
Games; personal use only (card images, data and official card backs are fine; never use Riot's logos
in the app's own marks).

The approved plan and mockups: claude.ai/artifact/X7gP4PtD9yWxigcgXKKejG (plan v2).

## Environment (Claude Code cloud sessions)

- Use the **riftbound_table** cloud environment: it allows `dl.google.com` (Google Maven: Compose
  Desktop needs androidx artifacts from it) and the official rules hosts.
- `repo.maven.apache.org` answers 429 to these machines; `settings.gradle.kts` lists
  `repo1.maven.org` first, which works. Run Gradle through `scripts/gradle-retry.sh <tasks>`.
- No display: everything is tested headlessly on the JVM (`runDesktopComposeUiTest`).
- OpenRift (card data/images) is blocked here; `.github/workflows/update-legends.yml` refreshes
  `src/main/resources/{cards,legends,battlefields}` weekly on GitHub (`scripts/build_legend_assets.py`).

## Build and test: before every push

```bash
scripts/gradle-retry.sh test
```

Runs every test and writes screenshots to `build/screenshots/*.png` and animation frames to
`build/screenshots/anim/`. **Look at the screenshots after every UI change**; turn frames into GIFs
for the owner. CI (`.github/workflows/build.yml`) runs the same, uploads screenshots + report, then
builds `.msi`/`.exe` (Windows), `.dmg` (macOS, unsigned) and `.deb` (Linux) as artifacts.

| Test | What it covers |
|---|---|
| `game/TableTest` | Chain (spells to chain, LIFO resolve to trash, targets), rune tap +1 energy / recycle +1 power, units enter/move exhausted, damage cleared when combat ends, set might/damage; new game layout (legends, champions, shuffled decks without the champion copy, rune decks, battlefields, opening hands), shuffles from the random source, start of turn (ready, holds, channel 2 / 3 for the second player's first turn, draw), showdown opening, end of turn cleanup, hidden cards anonymous in the log, piles, score 0–8, focus, mulligan, undo/redo of every action |
| `AppTest` | The real app: import two decks by deck code, roll, battlefields, table, drag a unit to base (enters exhausted) and to a battlefield, menu, hot-seat cover, settings, Navy table; at 1920×1025: rune tap/ready/recycle with pools, click-to-play, chain + T targeting + arrow, hover previews (card and battlefield), S resolve, C might/damage, R / Shift+R; animation frames (card slide, exhaust, score pop, d20 roll) |
| `game/CardsTest`, `DecksTest`, `DeckCodesTest`, `DiceTest`, `LegendsTest` | Copied from RiftTracker with their fixtures (card library, deck rules, Piltover Archive deck codes, fair d20) |

## Architecture

- `game/` (pure Kotlin): copied from RiftTracker (`Cards`, `Decks`, `DeckCodes`, `D20`, `Dice`,
  `Legends`, `Battlefields`), **not shared**: the apps drift apart on purpose (owner).
  `game/Table.kt` is the table: `TableState` (card instances by id, ordered zones per side, two
  battlefields, control, score, turn/active/focus/phase, showdown, floating pools, log), `Action`
  (every change; one undo step each), `Table.apply` (pure), `Table.newGame`, `TableHistory`
  (undo/redo by keeping states). Bookkeeping only: the start-of-turn helper readies, scores holds
  from the control markers, channels and draws, because players ask for it; nothing is enforced.
  `SECOND_PLAYER_EXTRA_RUNE` is from memory: verify against the official core rules (phase 3).
- `App.kt`: `AppState` (screens, decks, settings, the running table, viewer seat, hot-seat cover).
  Random source is SecureRandom; tests pass a seeded one. `data/Store.kt`: decks and settings as
  JSON in `~/.riftbound-table/`.
- `ui/TableScreen.kt`: the table, **RiftAtlas layout** (owner): each half mirrored around the
  battlefields; edge row = rune deck + floating Energy/Power, hand, deck, trash, banishment; board
  row = rune box (two rows of rune cards, exhausted ones sideways, "ready/total"), base, champion,
  legend; score tracks 0–8 beside each player counting towards the middle; battlefields show their
  art across the zone with a name band (click name = showdown, chip = control). Side panel: turn,
  log, chain (top card large, Resolve, Target), focus, Pass/Start turn, Undo/Draw/End turn.
  **Interactions follow RiftAtlas** (owner's demo video): card sizes scale with the window height
  (`Dims`); the hand peeks from the bottom edge and the hovered card rises; hovering any visible card
  shows it large beside it (above it for the hand), hovering a battlefield card shows the whole card.
  Click: runes tap for 1 energy (or ready if exhausted), board cards exhaust/ready, hand cards play
  (spells onto the chain, units/gear to base). Right-click: runes recycle for 1 power, other cards and
  piles open menus, battlefield card opens showdown/control. Units enter exhausted and exhaust when
  moving between base and battlefields (model). Spells never go to base: dropped on the board they go
  on the chain. Chain cards target permanents (T, then click; arrows drawn from chain to target).
  **Abilities on the chain** (owner: triggered abilities like Minefield, activated ones like Viktor's
  legend): dragging a permanent (unit, gear, legend, champion, rune) or a battlefield card onto the
  chain panel (chain + focus area) puts `Action.Activate` on the chain: an `ability = true` stand-in
  card (`source` = the permanent; null for battlefields) while the permanent stays put; it targets like
  a spell and disappears when it resolves. Also "Ability to chain" in card and battlefield menus.
  Closing a showdown ends the combat (damage there wears off). Drag & drop between zones; cards slide
  from their last position only when they change zone (no jitter on reflow/hover); hand fan/lift is
  drawn inside the card's own hit box.
- Shortcuts (`Main.tableKey`, on the app root so they're testable): Space pass/start turn · E end turn
  · D draw · R undo · Shift+R redo (Ctrl/Cmd Z too) · S resolve · T target · C might/damage of the
  hovered card · Esc cancel. The app re-takes keyboard focus after every change.
- `ui/DiceOverlay.kt`: RiftTracker's 3D d20, drawn with Skia.
- Card backs: the **official** ones (owner's request, personal use), `src/main/resources/back_main.webp`
  (blue: main deck, hand), `back_rune.webp` (white: runes), `back_legend.webp` (black: legends,
  battlefields); `Images.back(slug)` picks by card type. Source: RiftAtlas's static assets
  (`assets.riftatlas-workers.com/riftbound/static/cardback-{blue,white,black}.png`, 744×1039).

## Product decisions

- Minimal UI copy (owner): short labels, no explanatory sentences, one-line empty states.
- Look: Graphite dark (paper #121314, cards #1D1E20), Butter accent #FCEA92, seat colours from the
  legend's two domains. Table colour Graphite (default) or Navy in Settings; card size 70–130 %.
- Motion small and quick (<250 ms); send the owner GIFs of new motion.
- Hidden info towards Claude (phase 3): Claude only learns the owner's hand size, face-up cards with
  full text, existence of face-down cards, trash with full text, deck size. Never the decklist.
- Develop on a branch, push, merge to `main` only when the owner says. No PRs unless asked.
- Releases: automatic. Every push to `main` runs Build, and its `release` job publishes
  `v1.0.<run number>` with the `.exe`, `.msi`, `.dmg` and `.deb` (same number as `packageVersion`).
