package com.kresty.isolation.activities

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kresty.isolation.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupActivityTest {

    @Test
    fun launch_displaysPrimaryActionAndHiddenProgress() {
        val scenario = launch(SetupActivity::class.java)

        try {
            onView(withId(R.id.startButton)).check(matches(isDisplayed()))
            onView(withId(R.id.progressBar)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        } finally {
            scenario.close()
        }
    }
}
