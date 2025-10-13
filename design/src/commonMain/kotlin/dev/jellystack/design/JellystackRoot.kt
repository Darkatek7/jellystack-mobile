package dev.jellystack.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.jellystack.core.currentPlatform
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.players.PlaybackController

@Suppress("FunctionName", "ktlint:standard:function-naming")
@Composable
fun JellystackRoot(
    isDarkTheme: Boolean,
    controller: PlaybackController = PlaybackController(),
) {
    JellystackTheme(isDarkTheme = isDarkTheme) {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Jellystack bootstrap running on ${currentPlatform().name}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = "Playback state: ${controller.state.value}")
            }
        }
    }
}
