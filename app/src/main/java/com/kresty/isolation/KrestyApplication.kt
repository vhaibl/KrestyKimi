package com.kresty.isolation

import android.app.Application
import android.content.Context
import com.kresty.isolation.utils.WorkProfileBridge

class KrestyApplication : Application() {

    companion object {
        lateinit var instance: KrestyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        WorkProfileBridge.syncBridgeComponentState(this)
    }
}

fun Context.isWorkProfileSupported(): Boolean {
    return packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MANAGED_USERS)
}
