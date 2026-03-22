package com.kresty.isolation.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.kresty.isolation.utils.WorkProfileBridge
import com.kresty.isolation.utils.WorkProfileManager

class WorkProfileBridgeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkProfileBridge"
        private const val REQUEST_PACKAGE_INSTALL = 2001
        private const val REQUEST_PACKAGE_UNINSTALL = 2002
    }

    private lateinit var workProfileManager: WorkProfileManager
    private var pendingOperation: String? = null
    private var pendingPackageName: String? = null

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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_PACKAGE_INSTALL, REQUEST_PACKAGE_UNINSTALL -> {
                finishWithStatus(
                    pendingOperation.orEmpty(),
                    pendingPackageName,
                    resultCode == Activity.RESULT_OK
                )
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val operation = intent.getStringExtra(WorkProfileBridge.EXTRA_OPERATION)
        val packageName = intent.getStringExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME)

        if (!workProfileManager.isProfileOwner()) {
            Log.w(TAG, "Ignoring bridge request outside profile-owner context")
            finishWithStatus(operation.orEmpty(), packageName, false)
            return
        }

        when (operation) {
            WorkProfileBridge.OP_CLONE -> handleClone(intent, packageName)
            WorkProfileBridge.OP_REMOVE -> handleRemove(packageName)
            WorkProfileBridge.OP_FREEZE -> finishWithStatus(operation, packageName, workProfileManager.freezeApp(packageName.orEmpty()))
            WorkProfileBridge.OP_UNFREEZE -> finishWithStatus(operation, packageName, workProfileManager.unfreezeApp(packageName.orEmpty()))
            WorkProfileBridge.OP_DELETE_PROFILE -> finishWithStatus(operation, packageName, workProfileManager.deleteWorkProfile())
            else -> finishWithStatus(operation.orEmpty(), packageName, false)
        }
    }

    private fun handleClone(intent: Intent, packageName: String?) {
        if (packageName.isNullOrBlank()) {
            finishWithStatus(WorkProfileBridge.OP_CLONE, packageName, false)
            return
        }

        pendingOperation = WorkProfileBridge.OP_CLONE
        pendingPackageName = packageName

        if (workProfileManager.isPackageInstalledInCurrentProfile(packageName)) {
            val success = if (workProfileManager.isApplicationHidden(packageName)) {
                workProfileManager.unfreezeApp(packageName)
            } else {
                true
            }
            finishWithStatus(WorkProfileBridge.OP_CLONE, packageName, success)
            return
        }

        if (workProfileManager.cloneSystemAppToCurrentProfile(packageName)) {
            finishWithStatus(WorkProfileBridge.OP_CLONE, packageName, true)
            return
        }

        if (workProfileManager.installExistingPackage(packageName)) {
            finishWithStatus(WorkProfileBridge.OP_CLONE, packageName, true)
            return
        }

        val stagedApkUri = intent.getStringExtra(WorkProfileBridge.EXTRA_STAGED_APK_URI)?.let(Uri::parse)
        val installIntent = workProfileManager.buildPackageInstallIntent(packageName, stagedApkUri)
        if (installIntent == null) {
            finishWithStatus(WorkProfileBridge.OP_CLONE, packageName, false)
            return
        }

        startActivityForResult(installIntent, REQUEST_PACKAGE_INSTALL)
    }

    private fun handleRemove(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            finishWithStatus(WorkProfileBridge.OP_REMOVE, packageName, false)
            return
        }

        pendingOperation = WorkProfileBridge.OP_REMOVE
        pendingPackageName = packageName

        if (workProfileManager.isSystemApp(packageName)) {
            finishWithStatus(WorkProfileBridge.OP_REMOVE, packageName, workProfileManager.removeAppFromWorkProfile(packageName))
            return
        }

        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", packageName, null)).apply {
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }

        startActivityForResult(uninstallIntent, REQUEST_PACKAGE_UNINSTALL)
    }

    private fun finishWithStatus(operation: String, packageName: String?, success: Boolean) {
        setResult(
            if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Intent().apply {
                putExtra(WorkProfileBridge.EXTRA_OPERATION, operation)
                putExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME, packageName)
                putExtra(WorkProfileBridge.EXTRA_SUCCESS, success)
            }
        )
        finish()
    }
}
