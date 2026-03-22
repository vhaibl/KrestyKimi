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
class SetupActivityTest {

    @Before
    fun clearPreferences() {
        PreferencesManager(ApplicationProvider.getApplicationContext()).clear()
    }

    @Test
    fun launch_displaysPrimaryActionAndHiddenProgress() {
        val scenario = launch(SetupActivity::class.java)

        try {
            scenario.onActivity { activity ->
                assertEquals(
                    View.VISIBLE,
                    activity.findViewById<View>(R.id.startButton).visibility
                )
                assertEquals(
                    View.GONE,
                    activity.findViewById<View>(R.id.progressBar).visibility
                )
            }
        } finally {
            scenario.close()
        }
    }
}
