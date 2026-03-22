package com.kresty.isolation.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.kresty.isolation.activities.WorkProfileBridgeActivity
import com.kresty.isolation.receivers.KrestyDeviceAdminReceiver

object WorkProfileBridge {
    const val PERMISSION_MANAGE_WORK_PROFILE = "com.kresty.isolation.permission.MANAGE_WORK_PROFILE"
    const val ACTION_MANAGE = "com.kresty.isolation.action.MANAGE_WORK_PROFILE"

    const val EXTRA_OPERATION = "extra_operation"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_SUCCESS = "extra_success"
    const val EXTRA_IS_SYSTEM_APP = "extra_is_system_app"
    const val EXTRA_ERROR_MESSAGE = "extra_error_message"

    const val OP_CLONE = "clone"
    const val OP_REMOVE = "remove"
    const val OP_FREEZE = "freeze"
    const val OP_UNFREEZE = "unfreeze"
    const val OP_DELETE_PROFILE = "delete_profile"

    fun buildManageIntent(operation: String, packageName: String? = null): Intent {
        return Intent(ACTION_MANAGE).apply {
            `package` = "com.kresty.isolation"
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(EXTRA_OPERATION, operation)
            packageName?.let { putExtra(EXTRA_PACKAGE_NAME, it) }
        }
    }

    fun buildManageIntentFilter(): IntentFilter {
        return IntentFilter(ACTION_MANAGE).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
    }

    fun syncBridgeComponentState(context: Context) {
        val component = ComponentName(context, WorkProfileBridgeActivity::class.java)
        val enabledState = if (KrestyDeviceAdminReceiver.isProfileOwner(context)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        if (context.packageManager.getComponentEnabledSetting(component) != enabledState) {
            context.packageManager.setComponentEnabledSetting(
                component,
                enabledState,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
