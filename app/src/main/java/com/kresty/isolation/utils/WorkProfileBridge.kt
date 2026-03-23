package com.kresty.isolation.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.kresty.isolation.activities.WorkProfileBridgeActivity
import com.kresty.isolation.receivers.KrestyDeviceAdminReceiver

object WorkProfileBridge {
    const val ACTION_MANAGE = "com.kresty.isolation.action.MANAGE_WORK_PROFILE"
    private const val BRIDGE_ALIAS_CLASS_NAME = "com.kresty.isolation.activities.WorkProfileBridgeAlias"

    const val EXTRA_OPERATION = "extra_operation"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_SUCCESS = "extra_success"
    const val EXTRA_IS_SYSTEM_APP = "extra_is_system_app"
    const val EXTRA_ERROR_MESSAGE = "extra_error_message"
    const val EXTRA_SOURCE_APK_PATH = "extra_source_apk_path"
    const val EXTRA_SPLIT_APK_PATHS = "extra_split_apk_paths"

    const val OP_CLONE = "clone"
    const val OP_REMOVE = "remove"
    const val OP_FREEZE = "freeze"
    const val OP_UNFREEZE = "unfreeze"
    const val OP_DELETE_PROFILE = "delete_profile"

    fun buildManageIntent(operation: String, packageName: String? = null): Intent {
        return Intent(ACTION_MANAGE).apply {
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
        val activityComponent = ComponentName(context, WorkProfileBridgeActivity::class.java)
        val aliasComponent = ComponentName(context.packageName, BRIDGE_ALIAS_CLASS_NAME)
        val enabledState = if (KrestyDeviceAdminReceiver.isProfileOwner(context)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        if (context.packageManager.getComponentEnabledSetting(aliasComponent) != enabledState) {
            context.packageManager.setComponentEnabledSetting(
                aliasComponent,
                enabledState,
                PackageManager.DONT_KILL_APP
            )
        }

        if (context.packageManager.getComponentEnabledSetting(activityComponent) != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            context.packageManager.setComponentEnabledSetting(
                activityComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
