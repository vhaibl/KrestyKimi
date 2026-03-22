package com.kresty.isolation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kresty.isolation.utils.WorkProfileBridge

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WorkProfileBridge.syncBridgeComponentState(context)

            if (KrestyDeviceAdminReceiver.isProfileOwner(context)) {
                KrestyDeviceAdminReceiver.configureManagedProfile(context)
            }

            if (KrestyDeviceAdminReceiver.hasWorkProfile(context)) {
                Log.d(TAG, "Work profile bridge is active")
            }
        }
    }
}
