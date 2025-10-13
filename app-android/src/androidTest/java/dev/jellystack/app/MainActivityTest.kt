package dev.jellystack.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsThemeAndPlaybackState() {
        composeRule.onNodeWithText("Jellystack running on Android").assertExists()
        composeRule.onNodeWithText("Current theme: Light").assertExists()
        composeRule.onNodeWithText("Playback state: Stopped").assertExists()
    }

    @Test
    fun themeTogglePropagatesAcrossScreens() {
        composeRule.onNodeWithText("Open theme settings").performClick()
        composeRule.onNodeWithTag("theme_switch").performClick()
        composeRule.onNodeWithText("Dark mode enabled").assertExists()
        composeRule.onNodeWithContentDescription("Navigate back").performClick()
        composeRule.onNodeWithText("Current theme: Dark").assertExists()
    }
}
