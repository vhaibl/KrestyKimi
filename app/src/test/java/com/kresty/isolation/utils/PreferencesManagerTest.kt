package com.kresty.isolation.utils

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PreferencesManagerTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        sharedPreferences = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)

        `when`(context.getSharedPreferences(eq("kresty_preferences"), eq(Context.MODE_PRIVATE)))
            .thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)

        preferencesManager = PreferencesManager(context)
    }

    @Test
    fun getSubscriptionTierFallsBackToFree() {
        `when`(sharedPreferences.getString(eq("subscription_tier"), eq(PreferencesManager.TIER_FREE)))
            .thenReturn(null)

        assertEquals(PreferencesManager.TIER_FREE, preferencesManager.getSubscriptionTier())
        assertEquals(1, preferencesManager.getMaxAllowedApps())
        assertFalse(preferencesManager.hasActiveSubscription())
    }

    @Test
    fun basicTierEnablesThreeAppsAndActiveSubscription() {
        `when`(sharedPreferences.getString(eq("subscription_tier"), eq(PreferencesManager.TIER_FREE)))
            .thenReturn(PreferencesManager.TIER_BASIC)

        assertEquals(3, preferencesManager.getMaxAllowedApps())
        assertTrue(preferencesManager.hasActiveSubscription())
    }

    @Test
    fun addFrozenAppPersistsUpdatedSet() {
        val existing = linkedSetOf("com.example.old")
        val expected = linkedSetOf("com.example.old", "com.example.new")

        `when`(sharedPreferences.getStringSet(eq("frozen_apps"), eq(emptySet<String>())))
            .thenReturn(existing)
        `when`(editor.putStringSet(eq("frozen_apps"), eq(expected))).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)

        preferencesManager.addFrozenApp("com.example.new")

        verify(editor).putStringSet("frozen_apps", expected)
        verify(editor).commit()
    }

    @Test
    fun removeFrozenAppPersistsUpdatedSet() {
        val existing = linkedSetOf("com.example.old", "com.example.remove")
        val expected = linkedSetOf("com.example.old")

        `when`(sharedPreferences.getStringSet(eq("frozen_apps"), eq(emptySet<String>())))
            .thenReturn(existing)
        `when`(editor.putStringSet(eq("frozen_apps"), eq(expected))).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)

        preferencesManager.removeFrozenApp("com.example.remove")

        verify(editor).putStringSet("frozen_apps", expected)
        verify(editor).commit()
    }

    @Test
    fun setFirstLaunchCompletedStoresFalse() {
        `when`(editor.putBoolean(eq("first_launch"), eq(false))).thenReturn(editor)

        preferencesManager.setFirstLaunchCompleted()

        verify(editor).putBoolean("first_launch", false)
        verify(editor).apply()
    }

    @Test
    fun setWorkProfileCreatedStoresMarker() {
        `when`(editor.putBoolean(eq("work_profile_created"), eq(true))).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)

        preferencesManager.setWorkProfileCreated(true)

        verify(editor).putBoolean("work_profile_created", true)
        verify(editor).commit()
    }

    @Test
    fun addManagedAppPersistsUpdatedSet() {
        val existing = linkedSetOf("com.example.old")
        val expected = linkedSetOf("com.example.old", "com.example.new")

        `when`(sharedPreferences.getStringSet(eq("managed_apps"), eq(emptySet<String>())))
            .thenReturn(existing)
        `when`(editor.putStringSet(eq("managed_apps"), eq(expected))).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)

        preferencesManager.addManagedApp("com.example.new")

        verify(editor).putStringSet("managed_apps", expected)
        verify(editor).commit()
    }

    @Test
    fun removeManagedAppPersistsUpdatedSet() {
        val existing = linkedSetOf("com.example.old", "com.example.remove")
        val expected = linkedSetOf("com.example.old")

        `when`(sharedPreferences.getStringSet(eq("managed_apps"), eq(emptySet<String>())))
            .thenReturn(existing)
        `when`(editor.putStringSet(eq("managed_apps"), eq(expected))).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)

        preferencesManager.removeManagedApp("com.example.remove")

        verify(editor).putStringSet("managed_apps", expected)
        verify(editor).commit()
    }
}
