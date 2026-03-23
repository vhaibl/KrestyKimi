package com.kresty.isolation.activities

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kresty.isolation.utils.WorkProfileBridge
import com.kresty.isolation.utils.WorkProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkProfileBridgeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkProfileBridge"
        private const val ACTION_INSTALL_CALLBACK = "com.kresty.isolation.action.PACKAGE_INSTALL_CALLBACK"
    }

    private lateinit var workProfileManager: WorkProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workProfileManager = WorkProfileManager(this)
        if (intent.action == ACTION_INSTALL_CALLBACK) {
            handleInstallCallback(intent)
        } else {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            if (intent.action == ACTION_INSTALL_CALLBACK) {
                handleInstallCallback(intent)
            } else {
                handleIntent(intent)
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val operation = intent.getStringExtra(WorkProfileBridge.EXTRA_OPERATION).orEmpty()
        val packageName = intent.getStringExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME)
        Log.d(TAG, "Handling bridge operation=$operation package=$packageName")

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
                if (isSystemApp) {
                    val success = workProfileManager.cloneAppToWorkProfile(packageName, true)
                    finishWithStatus(
                        operation,
                        packageName,
                        success,
                        if (success) null else "Не удалось добавить приложение в рабочий профиль"
                    )
                    return
                }

                if (workProfileManager.isPackageInstalledInCurrentProfile(packageName)) {
                    val success = workProfileManager.unfreezeApp(packageName)
                    finishWithStatus(
                        operation,
                        packageName,
                        success,
                        if (success) null else "Не удалось добавить приложение в рабочий профиль"
                    )
                    return
                }

                val sourceApkPath = intent.getStringExtra(WorkProfileBridge.EXTRA_SOURCE_APK_PATH)
                if (sourceApkPath.isNullOrBlank()) {
                    finishWithStatus(operation, packageName, false, "Не найден APK для установки")
                    return
                }

                val splitApkPaths = intent.getStringArrayExtra(WorkProfileBridge.EXTRA_SPLIT_APK_PATHS)
                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        workProfileManager.installPackageFromApkPaths(
                            packageName = packageName,
                            sourceApkPath = sourceApkPath,
                            splitApkPaths = splitApkPaths,
                            statusReceiver = buildInstallStatusReceiver(operation, packageName)
                        )
                    }
                    if (!success) {
                        finishWithStatus(operation, packageName, false, "Не удалось подготовить установку приложения")
                    }
                }
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

    private fun buildInstallStatusReceiver(operation: String, packageName: String): android.content.IntentSender {
        val callbackIntent = Intent(this, WorkProfileBridgeActivity::class.java).apply {
            action = ACTION_INSTALL_CALLBACK
            putExtra(WorkProfileBridge.EXTRA_OPERATION, operation)
            putExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME, packageName)
        }
        return PendingIntent.getActivity(
            this,
            packageName.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        ).intentSender
    }

    private fun handleInstallCallback(intent: Intent) {
        val operation = intent.getStringExtra(WorkProfileBridge.EXTRA_OPERATION).orEmpty()
        val packageName = intent.getStringExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.d(TAG, "Install callback status=$status package=$packageName message=$statusMessage")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                if (confirmationIntent == null) {
                    finishWithStatus(operation, packageName, false, statusMessage ?: "Не удалось открыть подтверждение установки")
                } else {
                    startActivity(confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                finishWithStatus(operation, packageName, true)
            }
            else -> {
                finishWithStatus(
                    operation,
                    packageName,
                    false,
                    statusMessage ?: "Не удалось установить приложение в рабочий профиль"
                )
            }
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
