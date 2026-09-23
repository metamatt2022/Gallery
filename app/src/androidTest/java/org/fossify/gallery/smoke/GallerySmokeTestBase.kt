package org.fossify.gallery.smoke

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.rule.GrantPermissionRule
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.Config
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule

/**
 * Shared base for Gallery instrumented smoke tests. Each test starts from a
 * fully reset app (wiped prefs/database, granted media + all-files access,
 * freshly seeded media) and cleans up after itself; no test may depend on
 * another test's state.
 */
abstract class GallerySmokeTestBase {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    protected var scenario: ActivityScenario<out Activity>? = null
    protected lateinit var seededPaths: List<String>

    @Before
    fun setUpBase() {
        SmokeTestData.resetAppState()
        assertTrue(
            "MANAGE_EXTERNAL_STORAGE grant did not take effect",
            SmokeTestData.grantAllFilesAccess()
        )
        seededPaths = SmokeTestData.seedImages()
    }

    @After
    fun tearDownBase() {
        closeScenario()
        SmokeTestData.clearSeededMedia()
        SmokeTestData.clearRecycleBin()
        SmokeTestData.resetAppState()
    }

    protected fun closeScenario() {
        scenario?.close()
        scenario = null
    }

    protected fun launchMain(): ActivityScenario<MainActivity> {
        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched
        return launched
    }

    protected fun targetConfig(): Config = SmokeTestData.targetContext().config

    protected fun targetPrefs(): SharedPreferences =
        SmokeTestData.targetContext()
            .getSharedPreferences(SmokeTestData.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    /**
     * Polls until [matcher] is displayed or [timeoutMs] elapses, then performs
     * one final check so failures surface the real Espresso error.
     */
    protected fun waitForView(matcher: Matcher<View>, timeoutMs: Long = 20_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (_: Throwable) {
                SystemClock.sleep(250)
            }
        }
        onView(matcher).check(matches(isDisplayed()))
    }
}

/** Matches a [RecyclerView] whose adapter currently holds [count] items. */
fun withRecyclerItemCount(count: Int): Matcher<View> =
    object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("RecyclerView with item count $count")
        }

        override fun matchesSafely(view: View): Boolean {
            if (view !is RecyclerView) return false
            return view.adapter?.itemCount == count
        }
    }
