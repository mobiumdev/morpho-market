package dev.mobium.shop

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/** True where a remote drives the app, as on a TV: the TV shell provides it. */
val LocalRemote = staticCompositionLocalOf { false }

/**
 * A text field a remote can pass. On a phone it is the field itself. Under a
 * remote, focus lands on a frame around it, and the remote's select opens the
 * field and its keyboard: a Compose text field opens its keyboard the moment
 * it has focus, and that keyboard then took every key, so a remote passing
 * through could not leave. Leaving the field ends the editing.
 *
 * [field] draws the text field with the modifier it is given; its test tag
 * stays on the field, so a test fills and reads it the same way on both.
 */
@Composable
fun RemoteField(modifier: Modifier = Modifier, field: @Composable (Modifier) -> Unit) {
    if (!LocalRemote.current) {
        Box(modifier) { field(Modifier) }
        return
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val frame = remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var framed by remember { mutableStateOf(false) }
    Box(
        modifier
            .border(3.dp, if (framed) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(6.dp))
            .focusRequester(frame)
            .onFocusChanged { framed = it.isFocused }
            .onPreviewKeyEvent { e ->
                // On the key's release, not its press: a keyboard opened on the
                // press got the release, and pressed one of its own keys.
                when {
                    !framed || e.key != Key.DirectionCenter -> false
                    e.type == KeyEventType.KeyUp -> {
                        editing = true
                        inner.requestFocus()
                        keyboard?.show()
                        true
                    }
                    else -> true
                }
            }
            .focusable(),
    ) {
        field(
            Modifier
                .focusRequester(inner)
                .focusProperties { canFocus = editing }
                .onFocusChanged { if (editing && !it.isFocused) editing = false },
        )
    }
}

/**
 * A text field lets the D-pad's up and down leave it. A Compose text field
 * keeps every arrow key, so once in one a remote could not get out. Left and
 * right still move the cursor; a phone, with no D-pad, is unaffected.
 */
@Composable
fun Modifier.dpadLeaves(): Modifier {
    val focus = LocalFocusManager.current
    return onPreviewKeyEvent { e ->
        e.type == KeyEventType.KeyDown && when (e.key) {
            Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
            else -> false
        }
    }
}
