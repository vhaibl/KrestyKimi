package com.kresty.isolation.activities

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.kresty.isolation.R
import com.kresty.isolation.utils.PreferencesManager
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ManagedProfileFlowTest {

    @Before
    fun clearOwnerPreferences() {
        PreferencesManager(ApplicationProvider.getApplicationContext()).clear()
    }

    @Test
    fun managedProfileFlow_addFreezeUnfreezeAndRemoveFirstAvailableApp() {
        val scenario = launch(MainActivity::class.java)
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        var selectedPackage: String? = null

        try {
            onView(withId(R.id.emptyState)).check(matches(isDisplayed()))
            onView(withId(R.id.fabAddApp)).check(matches(isDisplayed()))
            onView(withId(R.id.fabAddApp)).perform(click())

            onView(withId(R.id.recyclerView)).check(recyclerHasAtLeast(1))
            onView(withId(R.id.recyclerView)).perform(
                captureTextFromItemAtPosition(0, R.id.appPackage) { selectedPackage = it }
            )
            onView(withId(R.id.recyclerView)).perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                    0,
                    clickChildViewWithId(R.id.addButton)
                )
            )

            pressBack()

            onView(withId(R.id.appsRecyclerView)).check(recyclerHasAtLeast(1))
            onView(withId(R.id.appsRecyclerView)).perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                    0,
                    clickChildViewWithId(R.id.freezeButton)
                )
            )
            onView(withId(R.id.frozenCountText)).check(
                matches(withText(appContext.getString(R.string.freezed_apps_count, 1)))
            )

            onView(withId(R.id.appsRecyclerView)).perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                    0,
                    clickChildViewWithId(R.id.freezeButton)
                )
            )
            onView(withId(R.id.frozenCountText)).check(
                matches(withText(appContext.getString(R.string.freezed_apps_count, 0)))
            )

            onView(withId(R.id.appsRecyclerView)).perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                    0,
                    clickChildViewWithId(R.id.deleteButton)
                )
            )
            onView(withText(R.string.dialog_confirm)).perform(click())
            onView(withId(R.id.emptyState)).check(matches(isDisplayed()))

            onView(withId(R.id.fabAddApp)).perform(click())
            onView(withId(R.id.recyclerView)).check(recyclerHasAtLeast(1))
            val removedPackage = checkNotNull(selectedPackage) { "Expected selected package to be captured" }
            onView(withId(R.id.recyclerView)).perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText(removedPackage))
                )
            )
            onView(withText(removedPackage)).check(matches(isDisplayed()))
            onView(withId(R.id.recyclerView)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText(removedPackage)),
                    clickChildViewWithId(R.id.addButton)
                )
            )

            pressBack()

            onView(withId(R.id.appsRecyclerView)).check(recyclerHasAtLeast(1))
            onView(withText(removedPackage)).check(matches(isDisplayed()))
            onView(withId(R.id.appsRecyclerView)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withText(removedPackage)),
                    clickChildViewWithId(R.id.freezeButton)
                )
            )
            onView(withId(R.id.frozenCountText)).check(
                matches(withText(appContext.getString(R.string.freezed_apps_count, 1)))
            )
        } finally {
            scenario.close()
        }
    }

    private fun clickChildViewWithId(viewId: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()

            override fun getDescription(): String = "click child view with id $viewId"

            override fun perform(uiController: UiController, view: View) {
                val child = view.findViewById<View>(viewId)
                child?.performClick()
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private fun recyclerHasAtLeast(expectedMinimum: Int): ViewAssertion {
        return ViewAssertion { view, noViewFoundException ->
            if (noViewFoundException != null) {
                throw noViewFoundException
            }

            val recyclerView = view as? RecyclerView
                ?: throw AssertionError("Expected RecyclerView but was ${view?.javaClass?.name}")
            check(recyclerView.adapter != null) { "RecyclerView has no adapter" }
            val prefs = PreferencesManager(ApplicationProvider.getApplicationContext())
            val debugState = buildString {
                append("managed=${prefs.getManagedApps()}")
                append(", removedHidden=${prefs.getRemovedHiddenApps()}")
                append(", baseline=${prefs.getManagedProfileBaselineApps()}")
                append(", frozen=${prefs.getFrozenApps()}")
                append(", workProfileCreated=${prefs.isWorkProfileCreated()}")
            }
            check(recyclerView.adapter!!.itemCount >= expectedMinimum) {
                "Expected at least $expectedMinimum items but was ${recyclerView.adapter!!.itemCount}; $debugState"
            }
        }
    }

    private fun captureTextFromItemAtPosition(
        position: Int,
        viewId: Int,
        onTextCaptured: (String) -> Unit
    ): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()

            override fun getDescription(): String = "capture text from child view $viewId at position $position"

            override fun perform(uiController: UiController, view: View) {
                val recyclerView = view as? RecyclerView
                    ?: throw AssertionError("Expected RecyclerView but was ${view.javaClass.name}")
                recyclerView.scrollToPosition(position)
                uiController.loopMainThreadUntilIdle()
                val holder = recyclerView.findViewHolderForAdapterPosition(position)
                    ?: throw AssertionError("No ViewHolder at position $position")
                val child = holder.itemView.findViewById<View>(viewId)
                    ?: throw AssertionError("No child view with id $viewId at position $position")
                val text = (child as? android.widget.TextView)?.text?.toString()
                    ?: throw AssertionError("Expected TextView for id $viewId")
                onTextCaptured(text)
            }
        }
    }
}
