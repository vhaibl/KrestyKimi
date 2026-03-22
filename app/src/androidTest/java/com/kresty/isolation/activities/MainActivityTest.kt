package com.kresty.isolation.activities

import android.view.View
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kresty.isolation.R
import com.kresty.isolation.utils.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Before
    fun clearPreferences() {
        PreferencesManager(ApplicationProvider.getApplicationContext()).clear()
    }

    @Test
    fun launch_displaysSetupButtonWhenWorkProfileIsMissing() {
        val scenario = launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity ->
                assertEquals(
                    View.VISIBLE,
                    activity.findViewById<View>(R.id.setupButton).visibility
                )
            }
        } finally {
            scenario.close()
        }
    }
}
