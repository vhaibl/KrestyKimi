package com.kresty.isolation.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kresty.isolation.R
import com.kresty.isolation.adapter.AppsAdapter
// import com.kresty.isolation.billing.RuStoreBillingManager
import com.kresty.isolation.databinding.ActivityMainBinding
import com.kresty.isolation.model.AppInfo
import com.kresty.isolation.receivers.KrestyDeviceAdminReceiver
import com.kresty.isolation.utils.PreferencesManager
import com.kresty.isolation.utils.WorkProfileManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var workProfileManager: WorkProfileManager
    // private lateinit var billingManager: RuStoreBillingManager
    private lateinit var prefs: PreferencesManager
    private lateinit var appsAdapter: AppsAdapter
    
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KrestyDeviceAdminReceiver.Actions.ACTION_PROFILE_READY) {
                updateUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        
        // Initialize managers
        workProfileManager = WorkProfileManager(this)
        // billingManager = RuStoreBillingManager(this)
        prefs = PreferencesManager(this)
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup click listeners
        setupClickListeners()
        
        // Register broadcast receiver
        registerReceiver(profileReceiver, IntentFilter(KrestyDeviceAdminReceiver.Actions.ACTION_PROFILE_READY))
        
        // Initialize RuStore Billing - ОТКЛЮЧЕНО
        // billingManager.initialize()
        
        // Initial UI update
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        // billingManager.queryPurchases()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(profileReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_subscription -> {
                // Монетизация отключена - показываем сообщение
                Toast.makeText(this, "Подписки временно недоступны", Toast.LENGTH_SHORT).show()
                // startActivity(Intent(this, SubscriptionActivity::class.java))
                true
            }
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
            
            // Монетизация отключена - лимитов нет
            // Check subscription limit
            // val currentApps = workProfileManager.getIsolatedAppsCount()
            // val maxApps = prefs.getMaxAllowedApps()
            
            // if (currentApps >= maxApps) {
            //     showLimitReachedDialog()
            //     return@setOnClickListener
            // }
            
            startActivity(Intent(this, AppListActivity::class.java))
        }
        
        binding.subscriptionCard.setOnClickListener {
            // Монетизация отключена
            Toast.makeText(this, "Подписки временно недоступны", Toast.LENGTH_SHORT).show()
            // startActivity(Intent(this, SubscriptionActivity::class.java))
        }
    }

    private fun updateUI() {
        val hasWorkProfile = workProfileManager.hasWorkProfile()
        
        // Update status card
        if (hasWorkProfile) {
            binding.statusTitle.text = getString(R.string.work_profile_active)
            binding.statusDescription.text = "Рабочий профиль настроен и готов к использованию"
            binding.statusIcon.setImageResource(R.drawable.ic_work_profile)
            binding.statusIcon.setColorFilter(getColor(R.color.success))
            binding.setupButton.visibility = View.GONE
            binding.fabAddApp.visibility = View.VISIBLE
            
            // Load isolated apps
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
        }
        
        // Update subscription info - ОТКЛЮЧЕНО
        // updateSubscriptionInfo()
        binding.subscriptionCard.visibility = View.GONE // Скрываем карточку подписки
    }

    /*
    private fun updateSubscriptionInfo() {
        val tier = prefs.getSubscriptionTier()
        val maxApps = prefs.getMaxAllowedApps()
        val currentApps = workProfileManager.getIsolatedAppsCount()
        
        when (tier) {
            PreferencesManager.TIER_FREE -> {
                binding.subscriptionTitle.text = getString(R.string.free_tier)
                binding.subscriptionDescription.text = getString(R.string.free_description)
            }
            PreferencesManager.TIER_BASIC -> {
                binding.subscriptionTitle.text = getString(R.string.basic_tier)
                binding.subscriptionDescription.text = getString(R.string.basic_description)
            }
            PreferencesManager.TIER_UNLIMITED -> {
                binding.subscriptionTitle.text = getString(R.string.unlimited_tier)
                binding.subscriptionDescription.text = getString(R.string.unlimited_description)
            }
        }
        
        // Update apps count
        val appsCountText = if (maxApps == Int.MAX_VALUE) {
            getString(R.string.unlimited_apps)
        } else {
            getString(R.string.apps_count, currentApps, maxApps)
        }
        binding.appsCountText.text = appsCountText
    }
    */

    private fun loadIsolatedApps() {
        lifecycleScope.launch {
            val apps = workProfileManager.getWorkProfileApps()
            
            if (apps.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.appsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.appsRecyclerView.visibility = View.VISIBLE
                appsAdapter.submitList(apps)
            }
            
            // Update frozen count
            val frozenCount = apps.count { it.isFrozen }
            binding.frozenCountText.text = getString(R.string.freezed_apps_count, frozenCount)
            
            // Update subscription info (apps count may have changed)
            // updateSubscriptionInfo()
        }
    }

    private fun toggleFreezeApp(app: AppInfo) {
        val success = if (app.isFrozen) {
            workProfileManager.unfreezeApp(app.packageName)
        } else {
            workProfileManager.freezeApp(app.packageName)
        }
        
        if (success) {
            val message = if (app.isFrozen) {
                R.string.success_app_unfrozen
            } else {
                R.string.success_app_frozen
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            loadIsolatedApps()
        } else {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
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
        val success = workProfileManager.removeAppFromWorkProfile(app.packageName)
        if (success) {
            Toast.makeText(this, R.string.success_app_removed, Toast.LENGTH_SHORT).show()
            loadIsolatedApps()
        } else {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
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
        val success = workProfileManager.deleteWorkProfile()
        if (success) {
            Toast.makeText(this, "Рабочий профиль удален", Toast.LENGTH_SHORT).show()
            updateUI()
        } else {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    /*
    private fun showLimitReachedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.limit_reached)
            .setMessage(R.string.limit_reached_message)
            .setPositiveButton(R.string.upgrade) { _, _ ->
                startActivity(Intent(this, SubscriptionActivity::class.java))
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
    */

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_description)
            .setPositiveButton("OK", null)
            .show()
    }
}
