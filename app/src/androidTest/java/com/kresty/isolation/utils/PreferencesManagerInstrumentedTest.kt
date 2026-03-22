package com.kresty.isolation.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesManagerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(context)
        preferencesManager.clear()
    }

    @After
    fun tearDown() {
        preferencesManager.clear()
    }

    @Test
    fun frozenApps_roundTripThroughSharedPreferences() {
        preferencesManager.addFrozenApp("org.telegram.messenger")

        assertTrue(preferencesManager.isAppFrozen("org.telegram.messenger"))

        preferencesManager.removeFrozenApp("org.telegram.messenger")

        assertFalse(preferencesManager.isAppFrozen("org.telegram.messenger"))
    }

    @Test
    fun subscriptionTier_updatesAllowedAppCount() {
        preferencesManager.setSubscriptionTier(PreferencesManager.TIER_BASIC)

        assertEquals(PreferencesManager.TIER_BASIC, preferencesManager.getSubscriptionTier())
        assertEquals(3, preferencesManager.getMaxAllowedApps())
        assertTrue(preferencesManager.hasActiveSubscription())
    }

    @Test
    fun firstLaunchFlag_canBeCleared() {
        assertTrue(preferencesManager.isFirstLaunch())

        preferencesManager.setFirstLaunchCompleted()

        assertFalse(preferencesManager.isFirstLaunch())
    }

    @Test
    fun workProfileMarker_roundTripsThroughSharedPreferences() {
        assertFalse(preferencesManager.isWorkProfileCreated())

        preferencesManager.setWorkProfileCreated(true)

        assertTrue(preferencesManager.isWorkProfileCreated())
    }
}
