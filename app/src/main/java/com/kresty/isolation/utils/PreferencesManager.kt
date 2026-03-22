package com.kresty.isolation.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "kresty_preferences"
        private const val KEY_SUBSCRIPTION_TIER = "subscription_tier"
        private const val KEY_FROZEN_APPS = "frozen_apps"
        private const val KEY_MANAGED_APPS = "managed_apps"
        private const val KEY_WORK_PROFILE_CREATED = "work_profile_created"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        
        // Subscription tiers
        const val TIER_FREE = "free"
        const val TIER_BASIC = "basic"
        const val TIER_UNLIMITED = "unlimited"
    }

    /**
     * Get current subscription tier
     */
    fun getSubscriptionTier(): String {
        return prefs.getString(KEY_SUBSCRIPTION_TIER, TIER_FREE) ?: TIER_FREE
    }

    /**
     * Set subscription tier
     */
    fun setSubscriptionTier(tier: String) {
        prefs.edit().putString(KEY_SUBSCRIPTION_TIER, tier).apply()
    }

    /**
     * Check if user has active subscription (not free tier)
     */
    fun hasActiveSubscription(): Boolean {
        return getSubscriptionTier() != TIER_FREE
    }

    /**
     * Get max allowed apps based on subscription
     */
    fun getMaxAllowedApps(): Int {
        return when (getSubscriptionTier()) {
            TIER_BASIC -> 3
            TIER_UNLIMITED -> Int.MAX_VALUE
            else -> 1 // Free tier
        }
    }

    /**
     * Check if work profile was created
     */
    fun isWorkProfileCreated(): Boolean {
        return prefs.getBoolean(KEY_WORK_PROFILE_CREATED, false)
    }

    /**
     * Set work profile created status
     */
    fun setWorkProfileCreated(created: Boolean) {
        prefs.edit().putBoolean(KEY_WORK_PROFILE_CREATED, created).apply()
    }

    /**
     * Get set of frozen app package names
     */
    fun getFrozenApps(): Set<String> {
        return prefs.getStringSet(KEY_FROZEN_APPS, emptySet()) ?: emptySet()
    }

    /**
     * Get set of app package names explicitly managed by Kresty
     */
    fun getManagedApps(): Set<String> {
        return prefs.getStringSet(KEY_MANAGED_APPS, emptySet()) ?: emptySet()
    }

    /**
     * Add app to managed list
     */
    fun addManagedApp(packageName: String) {
        val managedApps = getManagedApps().toMutableSet()
        managedApps.add(packageName)
        prefs.edit().putStringSet(KEY_MANAGED_APPS, managedApps).apply()
    }

    fun setManagedApps(packageNames: Set<String>) {
        prefs.edit().putStringSet(KEY_MANAGED_APPS, packageNames).apply()
    }

    /**
     * Remove app from managed list
     */
    fun removeManagedApp(packageName: String) {
        val managedApps = getManagedApps().toMutableSet()
        managedApps.remove(packageName)
        prefs.edit().putStringSet(KEY_MANAGED_APPS, managedApps).apply()
    }

    /**
     * Add app to frozen list
     */
    fun addFrozenApp(packageName: String) {
        val frozen = getFrozenApps().toMutableSet()
        frozen.add(packageName)
        prefs.edit().putStringSet(KEY_FROZEN_APPS, frozen).apply()
    }

    /**
     * Remove app from frozen list
     */
    fun removeFrozenApp(packageName: String) {
        val frozen = getFrozenApps().toMutableSet()
        frozen.remove(packageName)
        prefs.edit().putStringSet(KEY_FROZEN_APPS, frozen).apply()
    }

    /**
     * Check if app is frozen
     */
    fun isAppFrozen(packageName: String): Boolean {
        return getFrozenApps().contains(packageName)
    }

    /**
     * Check if this is first launch
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Set first launch completed
     */
    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    /**
     * Clear all preferences
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
