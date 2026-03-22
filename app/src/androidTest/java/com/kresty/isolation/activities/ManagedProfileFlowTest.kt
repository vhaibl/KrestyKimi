package com.kresty.isolation.activities

import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.kresty.isolation.R
import com.kresty.isolation.utils.PreferencesManager
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.hasDescendant
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
    fun managedProfileFlow_addFreezeUnfreezeAndRemoveSystemApp() {
        val scenario = launch(MainActivity::class.java)

        try {
            onView(withId(R.id.fabAddApp)).check(matches(isDisplayed()))
            onView(withId(R.id.fabAddApp)).perform(click())

            onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("settings"))
            onView(withId(R.id.recyclerView)).perform(
                RecyclerViewActions.actionOnItem<androidx.recyclerview.widget.RecyclerView.ViewHolder>(
                    hasDescendant(withText("com.android.settings")),
                    clickChildViewWithId(R.id.addButton)
                )
            )

            pressBack()

            onView(withText("com.android.settings")).check(matches(isDisplayed()))
            onView(withId(R.id.appsRecyclerView)).perform(
                RecyclerViewActions.actionOnItem<androidx.recyclerview.widget.RecyclerView.ViewHolder>(
                    hasDescendant(withText("com.android.settings")),
                    clickChildViewWithId(R.id.freezeButton)
                )
            )
            onView(withText(R.string.status_frozen)).check(matches(isDisplayed()))

            onView(withId(R.id.appsRecyclerView)).perform(
                RecyclerViewActions.actionOnItem<androidx.recyclerview.widget.RecyclerView.ViewHolder>(
                    hasDescendant(withText("com.android.settings")),
                    clickChildViewWithId(R.id.freezeButton)
                )
            )
            onView(withText(R.string.status_active)).check(matches(isDisplayed()))

            onView(withId(R.id.appsRecyclerView)).perform(
                RecyclerViewActions.actionOnItem<androidx.recyclerview.widget.RecyclerView.ViewHolder>(
                    hasDescendant(withText("com.android.settings")),
                    clickChildViewWithId(R.id.deleteButton)
                )
            )
            onView(withText(R.string.dialog_confirm)).perform(click())

            onView(withId(R.id.emptyState)).check(matches(isDisplayed()))
            onView(withText("com.android.settings")).check(doesNotExist())
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
}
