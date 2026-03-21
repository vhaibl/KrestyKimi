package com.kresty.isolation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking work profile status")
            
            // Check if work profile exists and restore frozen apps state if needed
            if (KrestyDeviceAdminReceiver.hasWorkProfile(context)) {
                // Work profile is active, nothing special to do
                Log.d(TAG, "Work profile is active")
            }
        }
    }
}
