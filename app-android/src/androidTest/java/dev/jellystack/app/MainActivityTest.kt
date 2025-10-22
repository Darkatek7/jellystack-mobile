package dev.jellystack.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
    fun homeScreenShowsNavigationTabs() {
        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithText("Library").assertExists()
        composeRule.onNodeWithText("Media").assertExists()
    }

    @Test
    fun themeTogglePropagatesAcrossScreens() {
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithTag("theme_switch").performClick()
        composeRule.onNodeWithText("Dark mode enabled").assertExists()
        composeRule.onNodeWithContentDescription("Close settings").performClick()
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Dark mode enabled").assertExists()
        composeRule.onNodeWithContentDescription("Close settings").performClick()
    }

    @Test
    fun requestsSectionShowsLinkingControls() {
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Requests").assertExists()
        composeRule.onNodeWithText("Connect a Jellyfin server").assertExists().assertIsEnabled()
        composeRule.onNodeWithText("Connect a requests server").assertExists().assertIsNotEnabled()
        composeRule.onNodeWithText("Add a Jellyfin server first to link requests.").assertExists()
        composeRule.onNodeWithContentDescription("Close settings").performClick()
    }
}
