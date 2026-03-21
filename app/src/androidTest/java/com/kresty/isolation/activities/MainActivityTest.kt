package com.kresty.isolation.activities

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kresty.isolation.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun setupButton_opensSetupScreen() {
        launch(MainActivity::class.java)

        onView(withId(R.id.setupButton))
            .check(matches(isDisplayed()))
            .perform(click())

        onView(withId(R.id.startButton)).check(matches(isDisplayed()))
    }
}
