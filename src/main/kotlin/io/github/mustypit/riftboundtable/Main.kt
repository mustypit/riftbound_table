package io.github.mustypit.riftboundtable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.mustypit.riftboundtable.game.Action
import io.github.mustypit.riftboundtable.game.Phase
import io.github.mustypit.riftboundtable.game.Side
import io.github.mustypit.riftboundtable.ui.AppTheme
import io.github.mustypit.riftboundtable.ui.BattlefieldScreen
import io.github.mustypit.riftboundtable.ui.DiceOverlay
import io.github.mustypit.riftboundtable.ui.ImportScreen
import io.github.mustypit.riftboundtable.ui.NewGameScreen
import io.github.mustypit.riftboundtable.ui.PickDeckScreen
import io.github.mustypit.riftboundtable.ui.SettingsScreen
import io.github.mustypit.riftboundtable.ui.TableScreen
import io.github.mustypit.riftboundtable.ui.deckColors

fun main() {
    AppState.loadLibrary()
    application {
        val app = remember { AppState() }
        Window(
            onCloseRequest = ::exitApplication, title = "Riftbound Table", icon = painterResource("icon.png"),
            state = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
            onPreviewKeyEvent = { tableKey(app, it) },
        ) {
            window.minimumSize = java.awt.Dimension(1100, 720)
            App(app)
        }
    }
}

@Composable
fun App(app: AppState, diceClockMs: Float? = null) {
    AppTheme(app.settings.table) {
        AnimatedContent(app.screen, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, contentKey = { it::class }) { s ->
            when (s) {
                Screen.NewGame -> NewGameScreen(app)
                is Screen.PickDeck -> PickDeckScreen(app, s.seat)
                is Screen.Import -> ImportScreen(app, s.seat)
                Screen.Settings -> SettingsScreen(app)
                is Screen.Roll -> DiceOverlay(s.roll, app::seatName, { deckColors(app.deckOf(it)) }, diceClockMs) { app.rolled(s.roll) }
                is Screen.Battlefields -> BattlefieldScreen(app, s)
                Screen.Table -> TableScreen(app)
            }
        }
    }
}

/** Table shortcuts: Space pass · N start turn · E end turn · D draw · Ctrl/Cmd Z undo · Ctrl/Cmd Shift Z redo. */
fun tableKey(app: AppState, e: KeyEvent): Boolean {
    if (app.screen != Screen.Table || e.type != KeyEventType.KeyDown || app.cover != null) return false
    val t = app.table ?: return false
    val mod = e.isCtrlPressed || e.isMetaPressed
    when {
        mod && e.key == Key.Z && e.isShiftPressed -> app.redo()
        mod && e.key == Key.Z -> app.undo()
        mod -> return false
        e.key == Key.Spacebar -> app.act(if (t.phase == Phase.AWAKEN) Action.StartTurn else Action.Pass)
        e.key == Key.N && t.phase == Phase.AWAKEN -> app.act(Action.StartTurn)
        e.key == Key.E -> app.act(Action.EndTurn)
        e.key == Key.D -> app.act(Action.Draw(app.viewer))
        else -> return false
    }
    return true
}

