package com.kresty.isolation.utils

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.core.content.FileProvider
import com.kresty.isolation.model.AppInfo
import com.kresty.isolation.receivers.KrestyDeviceAdminReceiver
import java.io.File

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
        if (managedProfile == null && prefs.isWorkProfileCreated()) {
            prefs.setWorkProfileCreated(false)
        }
        return managedProfile != null
    }

    fun isProfileOwner(): Boolean {
        return KrestyDeviceAdminReceiver.isProfileOwner(context)
    }

    fun setWorkProfileCreated(created: Boolean) {
        prefs.setWorkProfileCreated(created)
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
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
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
        if (!hasWorkProfile()) return emptyList()

        val managedPackages = prefs.getManagedApps()
        val activePackages = managedPackages.filter(::isInstalledInManagedProfile)
        val stalePackages = managedPackages - activePackages.toSet()
        stalePackages.forEach { packageName ->
            prefs.removeManagedApp(packageName)
            prefs.removeFrozenApp(packageName)
        }

        return activePackages
            .mapNotNull(::buildDisplayAppInfo)
            .sortedBy { it.appName.lowercase() }
    }

    fun getAvailableAppsToClone(): List<AppInfo> {
        if (!hasWorkProfile()) return emptyList()

        val unavailablePackages = prefs.getManagedApps()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == context.packageName || unavailablePackages.contains(packageName)) {
                    return@mapNotNull null
                }

                val appInfo = try {
                    packageManager.getApplicationInfo(packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }

                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystemApp && !isEssentialSystemApp(packageName)) {
                    return@mapNotNull null
                }
                if (!isSystemApp && !appInfo.splitPublicSourceDirs.isNullOrEmpty()) {
                    return@mapNotNull null
                }

                AppInfo(
                    packageName = packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    isSystemApp = isSystemApp
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    fun buildProfileActionIntent(action: String, app: AppInfo): Intent? {
        if (!hasWorkProfile()) return null

        return WorkProfileBridge.buildManageIntent(action, app.packageName).apply {
            if (action == WorkProfileBridge.OP_CLONE && !app.isSystemApp) {
                val stagedApkUri = stageBaseApk(app.packageName) ?: return null
                putExtra(WorkProfileBridge.EXTRA_STAGED_APK_URI, stagedApkUri.toString())
                clipData = ClipData.newUri(context.contentResolver, "Staged APK", stagedApkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun markAppManaged(packageName: String) {
        prefs.addManagedApp(packageName)
    }

    fun unmarkAppManaged(packageName: String) {
        prefs.removeManagedApp(packageName)
        prefs.removeFrozenApp(packageName)
    }

    fun markAppFrozen(packageName: String) {
        prefs.addFrozenApp(packageName)
    }

    fun markAppUnfrozen(packageName: String) {
        prefs.removeFrozenApp(packageName)
    }

    fun cloneAppToWorkProfile(packageName: String): Boolean {
        if (!isProfileOwner()) return false
        if (isPackageInstalledInCurrentProfile(packageName)) {
            return if (isApplicationHidden(packageName)) {
                setAppHiddenLocally(packageName, false)
            } else {
                true
            }
        }

        return cloneSystemAppToCurrentProfile(packageName) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && installExistingPackage(packageName))
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

    fun buildPackageInstallIntent(packageName: String, stagedApkUri: Uri?): Intent? {
        val apkUri = stagedApkUri ?: stageBaseApk(packageName) ?: return null
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            clipData = ClipData.newUri(context.contentResolver, "Staged APK", apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
        }.takeIf { it.resolveActivity(packageManager) != null }
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

    private fun buildDisplayAppInfo(packageName: String): AppInfo? {
        return try {
            val ownerAppInfo = packageManager.getApplicationInfo(packageName, 0)
            AppInfo(
                packageName = packageName,
                appName = packageManager.getApplicationLabel(ownerAppInfo).toString(),
                icon = packageManager.getApplicationIcon(ownerAppInfo),
                isSystemApp = (ownerAppInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isInstalledInWorkProfile = true,
                isFrozen = prefs.isAppFrozen(packageName)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun isInstalledInManagedProfile(packageName: String): Boolean {
        if (isProfileOwner()) {
            return isPackageInstalledInCurrentProfile(packageName)
        }

        val profileHandle = getManagedProfileHandle() ?: return false
        return try {
            val appInfo = launcherApps.getApplicationInfo(
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES,
                profileHandle
            )
            (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect $packageName in managed profile: ${e.message}")
            false
        }
    }

    private fun getManagedProfileHandle(): UserHandle? {
        return try {
            launcherApps.profiles.firstOrNull { it != Process.myUserHandle() }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect managed profiles: ${e.message}")
            null
        }
    }

    private fun stageBaseApk(packageName: String): Uri? {
        val appInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }

        val sourceApk = appInfo.publicSourceDir ?: appInfo.sourceDir ?: return null
        val stagingDir = File(context.cacheDir, "cloned-apks/$packageName").apply {
            mkdirs()
        }
        val targetApk = File(stagingDir, "base.apk")

        return try {
            File(sourceApk).inputStream().use { input ->
                targetApk.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApk
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to stage APK for $packageName: ${e.message}")
            null
        }
    }

    private fun isEssentialSystemApp(packageName: String): Boolean {
        return packageName in setOf(
            "com.android.settings",
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.android.vending",
            "org.fdroid.fdroid"
        )
    }
}
