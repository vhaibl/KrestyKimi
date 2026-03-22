package com.kresty.isolation.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.kresty.isolation.R
import com.kresty.isolation.adapter.AppsAdapter
import com.kresty.isolation.databinding.ActivityMainBinding
import com.kresty.isolation.model.AppInfo
import com.kresty.isolation.utils.PreferencesManager
import com.kresty.isolation.utils.WorkProfileBridge
import com.kresty.isolation.utils.WorkProfileManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var workProfileManager: WorkProfileManager
    private lateinit var prefs: PreferencesManager
    private lateinit var appsAdapter: AppsAdapter

    private var pendingOperation: String? = null
    private var pendingPackageName: String? = null

    private val workProfileActionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleProfileOperationStatus(result.resultCode, result.data)
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        workProfileManager = WorkProfileManager(this)
        prefs = PreferencesManager(this)

        setupRecyclerView()
        setupClickListeners()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_delete_profile -> {
                showDeleteProfileDialog()
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        appsAdapter = AppsAdapter(
            onFreezeClick = { app -> toggleFreezeApp(app) },
            onDeleteClick = { app -> showDeleteAppDialog(app) }
        )

        binding.appsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appsAdapter
        }
    }

    private fun setupClickListeners() {
        binding.setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        binding.statusCard.setOnClickListener {
            if (!workProfileManager.hasWorkProfile()) {
                startActivity(Intent(this, SetupActivity::class.java))
            }
        }

        binding.fabAddApp.setOnClickListener {
            if (!workProfileManager.hasWorkProfile()) {
                Toast.makeText(this, R.string.error_no_work_profile, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startActivity(Intent(this, AppListActivity::class.java))
        }

        binding.subscriptionCard.setOnClickListener {
            Toast.makeText(this, "Подписки временно недоступны", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        val hasWorkProfile = workProfileManager.hasWorkProfile()

        if (hasWorkProfile) {
            binding.statusTitle.text = getString(R.string.work_profile_active)
            binding.statusDescription.text = "Рабочий профиль настроен и готов к использованию"
            binding.statusIcon.setImageResource(R.drawable.ic_work_profile)
            binding.statusIcon.setColorFilter(getColor(R.color.success))
            binding.setupButton.visibility = View.GONE
            binding.fabAddApp.visibility = View.VISIBLE
            loadIsolatedApps()
        } else {
            binding.statusTitle.text = getString(R.string.work_profile_inactive)
            binding.statusDescription.text = "Нажмите для настройки рабочего профиля"
            binding.statusIcon.setImageResource(R.drawable.ic_work_profile)
            binding.statusIcon.setColorFilter(getColor(R.color.primary))
            binding.setupButton.visibility = View.VISIBLE
            binding.fabAddApp.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            binding.appsRecyclerView.visibility = View.GONE
            binding.frozenCountText.text = getString(R.string.freezed_apps_count, 0)
        }

        binding.subscriptionCard.visibility = View.GONE
    }

    private fun loadIsolatedApps() {
        val apps = workProfileManager.getWorkProfileApps()

        if (apps.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.appsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.appsRecyclerView.visibility = View.VISIBLE
            appsAdapter.submitList(apps)
        }

        val frozenCount = apps.count { it.isFrozen }
        binding.frozenCountText.text = getString(R.string.freezed_apps_count, frozenCount)
    }

    private fun toggleFreezeApp(app: AppInfo) {
        val operation = if (app.isFrozen) WorkProfileBridge.OP_UNFREEZE else WorkProfileBridge.OP_FREEZE

        if (workProfileManager.isProfileOwner()) {
            val success = if (app.isFrozen) {
                workProfileManager.unfreezeApp(app.packageName)
            } else {
                workProfileManager.freezeApp(app.packageName)
            }
            if (success) {
                if (app.isFrozen) {
                    workProfileManager.markAppUnfrozen(app.packageName)
                    Toast.makeText(this, R.string.success_app_unfrozen, Toast.LENGTH_SHORT).show()
                } else {
                    workProfileManager.markAppFrozen(app.packageName)
                    Toast.makeText(this, R.string.success_app_frozen, Toast.LENGTH_SHORT).show()
                }
                loadIsolatedApps()
            } else {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
            return
        }

        launchWorkProfileBridge(operation, app)
    }

    private fun showDeleteAppDialog(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_remove_title)
            .setMessage(R.string.dialog_remove_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                deleteApp(app)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun deleteApp(app: AppInfo) {
        if (workProfileManager.isProfileOwner()) {
            val success = workProfileManager.removeAppFromWorkProfile(app.packageName)
            if (success) {
                workProfileManager.unmarkAppManaged(app.packageName)
                Toast.makeText(this, R.string.success_app_removed, Toast.LENGTH_SHORT).show()
                loadIsolatedApps()
            } else {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
            return
        }

        launchWorkProfileBridge(WorkProfileBridge.OP_REMOVE, app)
    }

    private fun showDeleteProfileDialog() {
        if (!workProfileManager.hasWorkProfile()) {
            Toast.makeText(this, "Рабочий профиль не создан", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_work_profile)
            .setMessage(R.string.dialog_delete_work_profile_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                deleteWorkProfile()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun deleteWorkProfile() {
        if (workProfileManager.isProfileOwner()) {
            val success = workProfileManager.deleteWorkProfile()
            if (success) {
                prefs.clear()
                Toast.makeText(this, "Рабочий профиль удален", Toast.LENGTH_SHORT).show()
                updateUI()
            } else {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
            return
        }

        launchWorkProfileBridge(WorkProfileBridge.OP_DELETE_PROFILE, null)
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_description)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun launchWorkProfileBridge(operation: String, app: AppInfo?) {
        val intent = when {
            app != null -> workProfileManager.buildProfileActionIntent(operation, app)
            else -> WorkProfileBridge.buildManageIntent(operation)
        }

        if (intent == null) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            return
        }

        pendingOperation = operation
        pendingPackageName = app?.packageName
        workProfileActionLauncher.launch(intent)
    }

    private fun handleProfileOperationStatus(resultCode: Int, intent: Intent?) {
        val operation = intent?.getStringExtra(WorkProfileBridge.EXTRA_OPERATION) ?: pendingOperation.orEmpty()
        val packageName = intent?.getStringExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME) ?: pendingPackageName
        val success = resultCode == Activity.RESULT_OK && intent?.getBooleanExtra(WorkProfileBridge.EXTRA_SUCCESS, true) != false

        if (!success) {
            val errorMessage = intent?.getStringExtra(WorkProfileBridge.EXTRA_ERROR_MESSAGE)
            Toast.makeText(this, errorMessage ?: getString(R.string.error_generic), Toast.LENGTH_LONG).show()
            pendingOperation = null
            pendingPackageName = null
            return
        }

        when (operation) {
            WorkProfileBridge.OP_FREEZE -> {
                packageName?.let(workProfileManager::markAppFrozen)
                Toast.makeText(this, R.string.success_app_frozen, Toast.LENGTH_SHORT).show()
            }
            WorkProfileBridge.OP_UNFREEZE -> {
                packageName?.let(workProfileManager::markAppUnfrozen)
                Toast.makeText(this, R.string.success_app_unfrozen, Toast.LENGTH_SHORT).show()
            }
            WorkProfileBridge.OP_REMOVE -> {
                packageName?.let(workProfileManager::unmarkAppManaged)
                Toast.makeText(this, R.string.success_app_removed, Toast.LENGTH_SHORT).show()
            }
            WorkProfileBridge.OP_DELETE_PROFILE -> {
                prefs.clear()
                Toast.makeText(this, "Рабочий профиль удален", Toast.LENGTH_SHORT).show()
            }
        }

        pendingOperation = null
        pendingPackageName = null
    }
}
