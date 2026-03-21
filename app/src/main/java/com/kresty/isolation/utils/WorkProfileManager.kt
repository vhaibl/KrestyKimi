package com.kresty.isolation.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.kresty.isolation.model.AppInfo
import com.kresty.isolation.receivers.KrestyDeviceAdminReceiver

class WorkProfileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkProfileManager"
        const val REQUEST_PROVISION_MANAGED_PROFILE = 1
    }

    private val dpm: DevicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent: ComponentName = KrestyDeviceAdminReceiver.getComponentName(context)
    private val prefs = PreferencesManager(context)

    /**
     * Check if device supports work profiles
     */
    fun isWorkProfileSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
    }

    /**
     * Check if work profile is active
     */
    fun hasWorkProfile(): Boolean {
        return KrestyDeviceAdminReceiver.hasWorkProfile(context)
    }

    /**
     * Start provisioning (creating) work profile
     */
    fun startWorkProfileProvisioning(): Intent? {
        if (!isWorkProfileSupported()) {
            Log.w(TAG, "Work profile not supported on this device")
            return null
        }

        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, context.packageName)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, false)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, false)
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Log.e(TAG, "No handler for provisioning intent")
            null
        }
    }

    /**
     * Get list of apps installed in work profile
     */
    fun getWorkProfileApps(): List<AppInfo> {
        if (!hasWorkProfile()) return emptyList()

        val apps = mutableListOf<AppInfo>()
        try {
            // Get all packages for this user (work profile)
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            
            for (pkg in packages) {
                // Skip system apps and this app itself
                if (pkg.packageName == context.packageName) continue
                
                // Check if app is in work profile (enabled by profile owner)
                try {
                    val appInfo = pm.getApplicationInfo(pkg.packageName, 0)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    // Only include non-system apps that are enabled
                    if (!isSystemApp && appInfo.enabled) {
                        val isFrozen = prefs.isAppFrozen(pkg.packageName)
                        apps.add(AppInfo(
                            packageName = pkg.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(appInfo),
                            isSystemApp = false,
                            isInstalledInWorkProfile = true,
                            isFrozen = isFrozen
                        ))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting app info for ${pkg.packageName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting work profile apps: ${e.message}")
        }
        
        return apps.sortedBy { it.appName }
    }

    /**
     * Get list of apps available to clone (from main profile)
     */
    fun getAvailableAppsToClone(): List<AppInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<AppInfo>()
        val workProfileApps = getWorkProfileApps().map { it.packageName }.toSet()
        
        try {
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            
            for (pkg in packages) {
                // Skip already cloned apps
                if (workProfileApps.contains(pkg.packageName)) continue
                
                // Skip this app
                if (pkg.packageName == context.packageName) continue
                
                try {
                    val appInfo = pm.getApplicationInfo(pkg.packageName, 0)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    // Include user apps and some system apps
                    if (!isSystemApp || isEssentialSystemApp(pkg.packageName)) {
                        apps.add(AppInfo(
                            packageName = pkg.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(appInfo),
                            isSystemApp = isSystemApp,
                            isInstalledInWorkProfile = false
                        ))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting app info for ${pkg.packageName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available apps: ${e.message}")
        }
        
        return apps.sortedBy { it.appName }
    }

    /**
     * Clone app to work profile
     */
    fun cloneAppToWorkProfile(packageName: String): Boolean {
        if (!hasWorkProfile()) {
            Log.e(TAG, "Cannot clone app: no work profile")
            return false
        }

        return try {
            // Enable the app in work profile
            dpm.enableSystemApp(adminComponent, packageName)
            Log.d(TAG, "App $packageName enabled in work profile")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cloning app: ${e.message}")
            false
        }
    }

    /**
     * Remove app from work profile (hide/disable it)
     */
    fun removeAppFromWorkProfile(packageName: String): Boolean {
        if (!hasWorkProfile()) return false

        return try {
            // For work profile, we can't truly uninstall, but we can hide the app
            // by setting it as not enabled
            dpm.setApplicationHidden(adminComponent, packageName, true)
            prefs.removeFrozenApp(packageName)
            Log.d(TAG, "App $packageName hidden from work profile")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing app: ${e.message}")
            false
        }
    }

    /**
     * Freeze app (hide from launcher and disable)
     */
    fun freezeApp(packageName: String): Boolean {
        if (!hasWorkProfile()) return false

        return try {
            dpm.setApplicationHidden(adminComponent, packageName, true)
            prefs.addFrozenApp(packageName)
            Log.d(TAG, "App $packageName frozen")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error freezing app: ${e.message}")
            false
        }
    }

    /**
     * Unfreeze app (show in launcher and enable)
     */
    fun unfreezeApp(packageName: String): Boolean {
        if (!hasWorkProfile()) return false

        return try {
            dpm.setApplicationHidden(adminComponent, packageName, false)
            prefs.removeFrozenApp(packageName)
            Log.d(TAG, "App $packageName unfrozen")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unfreezing app: ${e.message}")
            false
        }
    }

    /**
     * Check if app is frozen
     */
    fun isAppFrozen(packageName: String): Boolean {
        return prefs.isAppFrozen(packageName)
    }

    /**
     * Delete work profile completely
     */
    fun deleteWorkProfile(): Boolean {
        return try {
            dpm.wipeData(0)
            prefs.setWorkProfileCreated(false)
            prefs.clear() // Clear all preferences
            Log.d(TAG, "Work profile deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting work profile: ${e.message}")
            false
        }
    }

    /**
     * Check if app is essential system app that should be shown
     */
    private fun isEssentialSystemApp(packageName: String): Boolean {
        val essentialApps = setOf(
            "com.android.chrome",
            "com.google.android.apps.maps",
            "com.google.android.gm",
            "com.google.android.youtube",
            "com.whatsapp",
            "com.telegram.messenger",
            "org.telegram.messenger",
            "com.vkontakte.android",
            "com.instagram.android",
            "com.facebook.katana",
            "com.twitter.android",
            "com.discord",
            "com.spotify.music",
            "com.google.android.apps.photos",
            "com.google.android.apps.docs",
            "com.microsoft.office.word",
            "com.microsoft.office.excel",
            "com.microsoft.office.powerpoint",
            "com.zoom.us",
            "com.microsoft.teams",
            "com.slack",
            "com.tinder",
            "com.badoo.mobile"
        )
        return essentialApps.contains(packageName)
    }

    /**
     * Get count of isolated apps
     */
    fun getIsolatedAppsCount(): Int {
        return getWorkProfileApps().size
    }

    /**
     * Get count of frozen apps
     */
    fun getFrozenAppsCount(): Int {
        return getWorkProfileApps().count { it.isFrozen }
    }
}
