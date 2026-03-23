package com.kresty.isolation.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kresty.isolation.R
import com.kresty.isolation.adapter.AppSelectionAdapter
import com.kresty.isolation.databinding.ActivityAppListBinding
import com.kresty.isolation.model.AppInfo
import com.kresty.isolation.utils.AppSearchFilter
import com.kresty.isolation.utils.WorkProfileBridge
import com.kresty.isolation.utils.WorkProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : AppCompatActivity() {

    private data class AppListUiState(
        val availableApps: List<AppInfo>,
        val managedPackages: Set<String>
    )

    private lateinit var binding: ActivityAppListBinding
    private lateinit var workProfileManager: WorkProfileManager
    private lateinit var appAdapter: AppSelectionAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var currentApps: List<AppInfo> = emptyList()
    private var pendingPackageName: String? = null
    private var loadAppsJob: Job? = null
    private var reconcileCloneJob: Job? = null

    private val cloneAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        reconcileCloneJob?.cancel()
        handleCloneResult(result.resultCode, result.data)
        loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        workProfileManager = WorkProfileManager(this)
        if (workProfileManager.isProfileOwner()) {
            Toast.makeText(this, "Добавление приложений доступно только из основного профиля", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pendingPackageName != null) {
                    return
                }
                finish()
            }
        })

        setupRecyclerView()
        setupSearchView()
        loadApps()
    }

    override fun onDestroy() {
        reconcileCloneJob?.cancel()
        loadAppsJob?.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (pendingPackageName != null) {
            reconcilePendingClone()
        }
    }

    private fun setupRecyclerView() {
        appAdapter = AppSelectionAdapter { app ->
            addAppToWorkProfile(app)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@AppListActivity)
            adapter = appAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterApps(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText)
                return true
            }
        })
    }

    private fun loadApps() {
        loadAppsJob?.cancel()
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        val currentQuery = binding.searchView.query?.toString()
        loadAppsJob = lifecycleScope.launch {
            val state = withContext(Dispatchers.Default) {
                AppListUiState(
                    availableApps = workProfileManager.getAvailableAppsToClone(),
                    managedPackages = workProfileManager.getWorkProfileApps().map { it.packageName }.toSet()
                )
            }

            if (!isActive || isFinishing || isDestroyed) {
                return@launch
            }

            allApps = state.availableApps
            appAdapter.setAddedPackages(state.managedPackages)
            binding.progressBar.visibility = View.GONE
            filterApps(currentQuery)
        }
    }

    private fun filterApps(query: String?) {
        currentApps = AppSearchFilter.filter(allApps, query)

        if (currentApps.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            appAdapter.submitList(currentApps)
        }
    }

    private fun addAppToWorkProfile(app: AppInfo) {
        if (pendingPackageName != null) {
            return
        }

        if (workProfileManager.isProfileOwner()) {
            val success = workProfileManager.cloneAppToWorkProfile(app.packageName)
            if (success) {
                workProfileManager.markAppManaged(app.packageName)
                Toast.makeText(this, R.string.success_app_cloned, Toast.LENGTH_SHORT).show()
                loadApps()
            } else {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val intent = workProfileManager.buildProfileActionIntent(WorkProfileBridge.OP_CLONE, app)
        if (intent == null) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            return
        }

        pendingPackageName = app.packageName
        try {
            cloneAppLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pendingPackageName = null
            Toast.makeText(this, "Рабочий профиль недоступен", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleCloneResult(resultCode: Int, data: android.content.Intent?) {
        val success = resultCode == Activity.RESULT_OK && data?.getBooleanExtra(WorkProfileBridge.EXTRA_SUCCESS, true) != false
        val packageName = data?.getStringExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME) ?: pendingPackageName

        if (success && !packageName.isNullOrBlank()) {
            workProfileManager.markAppManaged(packageName)
            Toast.makeText(this, R.string.success_app_cloned, Toast.LENGTH_SHORT).show()
            pendingPackageName = null
            setResult(Activity.RESULT_OK)
            finish()
            return
        } else {
            val errorMessage = data?.getStringExtra(WorkProfileBridge.EXTRA_ERROR_MESSAGE)
            Toast.makeText(this, errorMessage ?: getString(R.string.error_generic), Toast.LENGTH_LONG).show()
        }

        pendingPackageName = null
    }

    private fun reconcilePendingClone() {
        if (reconcileCloneJob?.isActive == true) {
            return
        }

        reconcileCloneJob = lifecycleScope.launch {
            val packageName = pendingPackageName ?: return@launch
            delay(500)

            if (pendingPackageName != packageName || isFinishing || isDestroyed) {
                return@launch
            }

            val success = withContext(Dispatchers.IO) {
                workProfileManager.waitForManagedProfilePackageVisibility(
                    packageName = packageName,
                    expectedVisible = true
                )
            }

            if (!isActive || isFinishing || isDestroyed || pendingPackageName != packageName) {
                return@launch
            }

            if (success) {
                workProfileManager.markAppManaged(packageName)
                Toast.makeText(this@AppListActivity, R.string.success_app_cloned, Toast.LENGTH_SHORT).show()
                pendingPackageName = null
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                pendingPackageName = null
                Toast.makeText(this@AppListActivity, R.string.error_generic, Toast.LENGTH_LONG).show()
                loadApps()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
