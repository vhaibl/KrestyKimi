package com.kresty.isolation.receivers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.kresty.isolation.R
import com.kresty.isolation.utils.PreferencesManager
import com.kresty.isolation.utils.WorkProfileBridge

class KrestyDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "KrestyDeviceAdmin"
        const val ACTION_PROFILE_READY = "com.kresty.isolation.PROFILE_READY"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, KrestyDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdmin(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(getComponentName(context))
        }

        fun isProfileOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isProfileOwnerApp(context.packageName)
        }

        fun hasWorkProfile(context: Context): Boolean {
            return isProfileOwner(context)
        }

        fun configureManagedProfile(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getComponentName(context)

            Log.d(TAG, "Configuring managed-profile bridge")
            PreferencesManager(context).setWorkProfileCreated(true)
            WorkProfileBridge.syncBridgeComponentState(context)
            dpm.setProfileName(adminComponent, context.getString(R.string.app_name))

            dpm.clearCrossProfileIntentFilters(adminComponent)
            dpm.addCrossProfileIntentFilter(
                adminComponent,
                WorkProfileBridge.buildManageIntentFilter(),
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
            )

            dpm.setProfileEnabled(adminComponent)
            Log.d(TAG, "Managed-profile bridge filters ensured")
            enableSystemApps(context, dpm, adminComponent)
        }

        private fun enableSystemApps(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
            val systemAppsToEnable = listOf(
                "com.android.documentsui",
                "com.google.android.documentsui",
                "com.android.settings"
            )

            for (pkg in systemAppsToEnable) {
                try {
                    dpm.enableSystemApp(admin, pkg)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not enable $pkg: ${e.message}")
                }
            }
        }
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.d(TAG, "Profile provisioning complete")
        
        try {
            configureManagedProfile(context)
            
            // Send broadcast to notify UI
            val broadcastIntent = Intent(ACTION_PROFILE_READY)
            broadcastIntent.setPackage(context.packageName)
            context.sendBroadcast(broadcastIntent)
            
            Toast.makeText(context, R.string.work_profile_active, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling profile: ${e.message}")
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device admin disabled")
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PROFILE_READY -> {
                Log.d(TAG, "Profile ready broadcast received")
            }
            else -> super.onReceive(context, intent)
        }
    }

}
