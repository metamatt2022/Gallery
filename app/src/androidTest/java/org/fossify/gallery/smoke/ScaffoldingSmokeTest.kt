package org.fossify.gallery.smoke

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.fossify.gallery.R
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Proves the instrumented harness works end to end: seeded media appears in
 * the folder grid, and opening the folder shows exactly the seeded items.
 * Future task suites live alongside this file and reuse [GallerySmokeTestBase].
 */
class ScaffoldingSmokeTest : GallerySmokeTestBase() {

    @Test
    fun seededFolder_appearsInFolderGrid() {
        launchMain()

        onView(withId(R.id.directories_grid)).check(matches(isDisplayed()))
        waitForView(withText(SmokeTestData.SEED_FOLDER_NAME))
        onView(withText(SmokeTestData.SEED_FOLDER_NAME)).check(matches(isDisplayed()))
    }

    @Test
    fun seededFolder_opensMediaGridWithSeededImages() {
        launchMain()

        waitForView(withText(SmokeTestData.SEED_FOLDER_NAME))
        onView(withText(SmokeTestData.SEED_FOLDER_NAME)).perform(click())

        waitForView(withId(R.id.media_grid))
        onView(withId(R.id.media_grid)).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.media_grid), withRecyclerItemCount(SmokeTestData.SEED_IMAGE_COUNT)))
            .check(matches(isDisplayed()))
        onView(withId(R.id.media_empty_text_placeholder)).check(matches(not(isDisplayed())))
    }
}

@RunWith(Suite::class)
@Suite.SuiteClasses(ScaffoldingSmokeTest::class)
class ScaffoldingSuite
