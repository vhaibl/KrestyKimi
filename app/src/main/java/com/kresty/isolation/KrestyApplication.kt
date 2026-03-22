package com.kresty.isolation

import android.app.Application
import android.content.Context
import android.util.Log
import com.kresty.isolation.receivers.KrestyDeviceAdminReceiver
import com.kresty.isolation.utils.WorkProfileBridge

class KrestyApplication : Application() {

    companion object {
        private const val TAG = "KrestyApplication"
        lateinit var instance: KrestyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        WorkProfileBridge.syncBridgeComponentState(this)

        // Shell-based profile-owner setup in tests can bypass the normal
        // provisioning callback. Re-apply the managed-profile bridge config
        // whenever the work-profile app process starts as profile owner.
        if (KrestyDeviceAdminReceiver.isProfileOwner(this)) {
            try {
                KrestyDeviceAdminReceiver.configureManagedProfile(this)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to ensure managed-profile configuration: ${e.message}")
            }
        }
    }
}

fun Context.isWorkProfileSupported(): Boolean {
    return packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MANAGED_USERS)
}
