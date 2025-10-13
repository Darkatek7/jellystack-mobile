package dev.jellystack.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsBootstrapMessage() {
        composeRule.onNodeWithText("Jellystack bootstrap running on Android").assertExists()
        composeRule.onNodeWithText("Playback state: Stopped").assertExists()
    }
}
