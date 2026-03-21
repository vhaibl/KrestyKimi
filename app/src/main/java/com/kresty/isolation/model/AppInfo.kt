package com.kresty.isolation.model

import android.graphics.drawable.Drawable

/**
 * Data class representing an application
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false,
    val isInstalledInWorkProfile: Boolean = false,
    val isFrozen: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false
        return packageName == other.packageName
    }

    override fun hashCode(): Int {
        return packageName.hashCode()
    }
}

/**
 * Subscription tier for monetization
 */
sealed class SubscriptionTier {
    abstract val maxApps: Int
    abstract val displayName: String
    
    object Free : SubscriptionTier() {
        override val maxApps: Int = 1
        override val displayName: String = "Бесплатно"
    }
    
    object Basic : SubscriptionTier() {
        override val maxApps: Int = 3
        override val displayName: String = "Базовая"
    }
    
    object Unlimited : SubscriptionTier() {
        override val maxApps: Int = Int.MAX_VALUE
        override val displayName: String = "Безлимит"
    }
    
    fun canAddApp(currentAppCount: Int): Boolean {
        return currentAppCount < maxApps
    }
}

/**
 * Subscription product IDs for Google Play Billing
 */
object SubscriptionProducts {
    const val BASIC_MONTHLY = "kresty_basic_monthly"
    const val UNLIMITED_MONTHLY = "kresty_unlimited_monthly"
}
