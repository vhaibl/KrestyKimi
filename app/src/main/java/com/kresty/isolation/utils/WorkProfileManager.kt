package com.kresty.isolation.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.kresty.isolation.model.AppInfo
import com.kresty.isolation.receivers.KrestyDeviceAdminReceiver
import kotlin.math.max

class WorkProfileManager(private val context: Context) {

    companion object {
        private const val TAG = "WorkProfileManager"
        const val REQUEST_PROVISION_MANAGED_PROFILE = 1
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val packageManager = context.packageManager
    private val adminComponent: ComponentName = KrestyDeviceAdminReceiver.getComponentName(context)
    private val prefs = PreferencesManager(context)

    fun isWorkProfileSupported(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
    }

    fun hasWorkProfile(): Boolean {
        if (isProfileOwner()) return true

        val managedProfile = getManagedProfileHandle()
        if (managedProfile != null) {
            if (!prefs.isWorkProfileCreated()) {
                prefs.setWorkProfileCreated(true)
            }
            return true
        }

        // LauncherApps can temporarily stop surfacing the managed profile while it
        // is still present. Keep the persisted marker as the source of truth until
        // an explicit delete path clears it.
        return prefs.isWorkProfileCreated()
    }

    fun isProfileOwner(): Boolean {
        return KrestyDeviceAdminReceiver.isProfileOwner(context)
    }

    fun setWorkProfileCreated(created: Boolean) {
        prefs.setWorkProfileCreated(created)
        if (!created) {
            prefs.setManagedApps(emptySet())
            prefs.setFrozenApps(emptySet())
            prefs.setManagedProfileBaselineApps(emptySet())
            prefs.setRemovedHiddenApps(emptySet())
        }
    }

    fun ensureCrossProfileIntentFilters() {
        if (!isProfileOwner()) return

        try {
            dpm.clearCrossProfileIntentFilters(adminComponent)
            dpm.addCrossProfileIntentFilter(
                adminComponent,
                IntentFilter(WorkProfileBridge.ACTION_MANAGE).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                // Parent-profile launches must resolve into the managed profile.
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to ensure cross-profile filters: ${e.message}")
        }
    }

    fun startWorkProfileProvisioning(): Intent? {
        if (!isWorkProfileSupported()) return null

        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, context.packageName)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, false)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, false)
        }

        return intent.takeIf { it.resolveActivity(packageManager) != null }
    }

    fun getWorkProfileApps(): List<AppInfo> {
        if (!hasWorkProfile() || isProfileOwner()) return emptyList()

        if (prefs.getManagedApps().isEmpty()) {
            return emptyList()
        }

        repairLegacyManagedStateIfNeeded()

        val managedPackages = prefs.getManagedApps()
        if (managedPackages.isEmpty()) {
            return emptyList()
        }

        val visibleManagedPackages = getVisiblePackagesInManagedProfile()

        return managedPackages
            .mapNotNull { packageName ->
                buildDisplayAppInfo(
                    packageName = packageName,
                    isInstalledInWorkProfile = prefs.isAppFrozen(packageName) || visibleManagedPackages.contains(packageName)
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    fun getAvailableAppsToClone(): List<AppInfo> {
        if (!hasWorkProfile() || isProfileOwner()) return emptyList()

        repairLegacyManagedStateIfNeeded()

        val unavailablePackages = (prefs.getManagedProfileBaselineApps() - prefs.getRemovedHiddenApps()) +
            prefs.getManagedApps() +
            context.packageName
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName in unavailablePackages) {
                    return@mapNotNull null
                }

                val appInfo = try {
                    packageManager.getApplicationInfo(packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }

                AppInfo(
                    packageName = packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    fun buildProfileActionIntent(action: String, app: AppInfo): Intent? {
        if (!hasWorkProfile() || isProfileOwner()) return null

        return WorkProfileBridge.buildManageIntent(action, app.packageName).apply {
            putExtra(WorkProfileBridge.EXTRA_IS_SYSTEM_APP, app.isSystemApp)
        }
    }

    fun markAppManaged(packageName: String) {
        prefs.removeRemovedHiddenApp(packageName)
        prefs.addManagedApp(packageName)
    }

    fun unmarkAppManaged(packageName: String) {
        prefs.removeManagedApp(packageName)
        prefs.removeFrozenApp(packageName)
    }

    fun markAppRemoved(packageName: String) {
        prefs.removeManagedApp(packageName)
        prefs.removeFrozenApp(packageName)
        prefs.addRemovedHiddenApp(packageName)
    }

    fun markAppFrozen(packageName: String) {
        prefs.addFrozenApp(packageName)
    }

    fun markAppUnfrozen(packageName: String) {
        prefs.removeFrozenApp(packageName)
    }

    fun cloneAppToWorkProfile(packageName: String, isSystemApp: Boolean = isSystemApp(packageName)): Boolean {
        if (!isProfileOwner()) return false

        if (isPackageInstalledInCurrentProfile(packageName)) {
            return if (isApplicationHidden(packageName)) {
                setAppHiddenLocally(packageName, false)
            } else {
                true
            }
        }

        return if (isSystemApp) {
            cloneSystemAppToCurrentProfile(packageName)
        } else {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && installExistingPackage(packageName)
        }
    }

    fun freezeApp(packageName: String): Boolean {
        return isProfileOwner() && setAppHiddenLocally(packageName, true)
    }

    fun unfreezeApp(packageName: String): Boolean {
        return isProfileOwner() && setAppHiddenLocally(packageName, false)
    }

    fun removeAppFromWorkProfile(packageName: String): Boolean {
        return isProfileOwner() && setAppHiddenLocally(packageName, true)
    }

    fun deleteWorkProfile(): Boolean {
        if (!isProfileOwner()) return false

        return try {
            dpm.wipeData(0)
            prefs.setWorkProfileCreated(false)
            prefs.clear()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting work profile: ${e.message}")
            false
        }
    }

    fun isPackageInstalledInCurrentProfile(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
        } catch (_: Exception) {
            false
        }
    }

    fun isApplicationHidden(packageName: String): Boolean {
        if (!isProfileOwner()) return false

        return try {
            dpm.isApplicationHidden(adminComponent, packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect hidden state for $packageName: ${e.message}")
            false
        }
    }

    fun cloneSystemAppToCurrentProfile(packageName: String): Boolean {
        return try {
            dpm.enableSystemApp(adminComponent, packageName)
            dpm.setApplicationHidden(adminComponent, packageName, false)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to enable system app $packageName: ${e.message}")
            false
        }
    }

    fun installExistingPackage(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false

        return try {
            dpm.installExistingPackage(adminComponent, packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to install existing package $packageName: ${e.message}")
            false
        }
    }

    fun setAppHiddenLocally(packageName: String, hidden: Boolean): Boolean {
        return try {
            dpm.setApplicationHidden(adminComponent, packageName, hidden)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update hidden state for $packageName: ${e.message}")
            false
        }
    }

    fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getIsolatedAppsCount(): Int = getWorkProfileApps().size

    fun getFrozenAppsCount(): Int = getWorkProfileApps().count { it.isFrozen }

    fun hasLiveManagedProfile(): Boolean {
        return isProfileOwner() || getManagedProfileHandle() != null
    }

    fun isPackageVisibleInManagedProfile(packageName: String): Boolean {
        return packageName in getVisiblePackagesInManagedProfile()
    }

    fun waitForManagedProfilePackageVisibility(
        packageName: String,
        expectedVisible: Boolean,
        timeoutMs: Long = 5_000L,
        pollIntervalMs: Long = 250L
    ): Boolean {
        val deadline = System.currentTimeMillis() + max(timeoutMs, pollIntervalMs)
        while (System.currentTimeMillis() <= deadline) {
            if (isPackageVisibleInManagedProfile(packageName) == expectedVisible) {
                return true
            }
            Thread.sleep(pollIntervalMs)
        }
        return isPackageVisibleInManagedProfile(packageName) == expectedVisible
    }

    fun waitForManagedProfileDeletion(
        timeoutMs: Long = 8_000L,
        pollIntervalMs: Long = 250L
    ): Boolean {
        val deadline = System.currentTimeMillis() + max(timeoutMs, pollIntervalMs)
        while (System.currentTimeMillis() <= deadline) {
            if (!hasLiveManagedProfile()) {
                return true
            }
            Thread.sleep(pollIntervalMs)
        }
        return !hasLiveManagedProfile()
    }

    private fun buildDisplayAppInfo(packageName: String, isInstalledInWorkProfile: Boolean = true): AppInfo? {
        return try {
            val ownerAppInfo = packageManager.getApplicationInfo(packageName, 0)
            AppInfo(
                packageName = packageName,
                appName = packageManager.getApplicationLabel(ownerAppInfo).toString(),
                icon = packageManager.getApplicationIcon(ownerAppInfo),
                isSystemApp = (ownerAppInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isInstalledInWorkProfile = isInstalledInWorkProfile,
                isFrozen = prefs.isAppFrozen(packageName)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getVisiblePackagesInManagedProfile(): Set<String> {
        return if (isProfileOwner()) {
            getVisibleLauncherPackagesInCurrentProfile()
        } else {
            val profileHandle = getManagedProfileHandle() ?: return emptySet()
            try {
                launcherApps.getActivityList(null, profileHandle)
                    .map { it.applicationInfo.packageName }
                    .toSet()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to query managed-profile launcher apps: ${e.message}")
                emptySet()
            }
        }
    }

    private fun getVisibleLauncherPackagesInCurrentProfile(): Set<String> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private fun getManagedProfileHandle(): UserHandle? {
        return try {
            launcherApps.profiles.firstOrNull { it != Process.myUserHandle() }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect managed profiles: ${e.message}")
            null
        }
    }

    private fun repairLegacyManagedStateIfNeeded() {
        if (isProfileOwner()) return

        val visiblePackages = getVisiblePackagesInManagedProfile()
        if (visiblePackages.isEmpty()) return

        val baselinePackages = prefs.getManagedProfileBaselineApps()
        val managedPackages = prefs.getManagedApps()
        val removedHiddenPackages = prefs.getRemovedHiddenApps()
        val frozenPackages = prefs.getFrozenApps()
        if (baselinePackages.isEmpty()) {
            if (managedPackages.isNotEmpty() || removedHiddenPackages.isNotEmpty() || frozenPackages.isNotEmpty()) {
                return
            }

            prefs.setManagedProfileBaselineApps(visiblePackages)
            return
        }

        if (managedPackages.isNotEmpty() && removedHiddenPackages.isEmpty() && managedPackages.all { it in baselinePackages } && frozenPackages.isEmpty()) {
            prefs.setManagedApps(emptySet())
        }
    }
}
