package com.kresty.isolation.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.kresty.isolation.utils.WorkProfileBridge
import com.kresty.isolation.utils.WorkProfileManager

class WorkProfileBridgeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkProfileBridge"
    }

    private lateinit var workProfileManager: WorkProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workProfileManager = WorkProfileManager(this)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        val operation = intent.getStringExtra(WorkProfileBridge.EXTRA_OPERATION).orEmpty()
        val packageName = intent.getStringExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME)

        if (intent.action != WorkProfileBridge.ACTION_MANAGE) {
            finishWithStatus(operation, packageName, false, "Некорректный bridge intent")
            return
        }

        if (!workProfileManager.isProfileOwner()) {
            Log.w(TAG, "Ignoring bridge request outside profile-owner context")
            finishWithStatus(operation, packageName, false, "Рабочий профиль не активирован")
            return
        }

        when (operation) {
            WorkProfileBridge.OP_CLONE -> {
                if (packageName.isNullOrBlank()) {
                    finishWithStatus(operation, packageName, false, "Не указан пакет приложения")
                    return
                }
                val isSystemApp = intent.getBooleanExtra(
                    WorkProfileBridge.EXTRA_IS_SYSTEM_APP,
                    workProfileManager.isSystemApp(packageName)
                )
                val success = workProfileManager.cloneAppToWorkProfile(packageName, isSystemApp)
                finishWithStatus(
                    operation,
                    packageName,
                    success,
                    if (success) null else "Не удалось добавить приложение в рабочий профиль"
                )
            }

            WorkProfileBridge.OP_REMOVE -> {
                if (packageName.isNullOrBlank()) {
                    finishWithStatus(operation, packageName, false, "Не указан пакет приложения")
                    return
                }
                val success = workProfileManager.removeAppFromWorkProfile(packageName)
                finishWithStatus(
                    operation,
                    packageName,
                    success,
                    if (success) null else "Не удалось удалить приложение из рабочего профиля"
                )
            }

            WorkProfileBridge.OP_FREEZE -> {
                if (packageName.isNullOrBlank()) {
                    finishWithStatus(operation, packageName, false, "Не указан пакет приложения")
                    return
                }
                val success = workProfileManager.freezeApp(packageName)
                finishWithStatus(
                    operation,
                    packageName,
                    success,
                    if (success) null else "Не удалось заморозить приложение"
                )
            }

            WorkProfileBridge.OP_UNFREEZE -> {
                if (packageName.isNullOrBlank()) {
                    finishWithStatus(operation, packageName, false, "Не указан пакет приложения")
                    return
                }
                val success = workProfileManager.unfreezeApp(packageName)
                finishWithStatus(
                    operation,
                    packageName,
                    success,
                    if (success) null else "Не удалось разморозить приложение"
                )
            }

            WorkProfileBridge.OP_DELETE_PROFILE -> {
                val success = workProfileManager.deleteWorkProfile()
                finishWithStatus(
                    operation,
                    packageName,
                    success,
                    if (success) null else "Не удалось удалить рабочий профиль"
                )
            }

            else -> finishWithStatus(operation, packageName, false, "Неизвестная операция")
        }
    }

    private fun finishWithStatus(
        operation: String,
        packageName: String?,
        success: Boolean,
        errorMessage: String? = null
    ) {
        setResult(
            if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Intent().apply {
                putExtra(WorkProfileBridge.EXTRA_OPERATION, operation)
                putExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME, packageName)
                putExtra(WorkProfileBridge.EXTRA_SUCCESS, success)
                errorMessage?.let { putExtra(WorkProfileBridge.EXTRA_ERROR_MESSAGE, it) }
            }
        )
        finish()
    }
}
