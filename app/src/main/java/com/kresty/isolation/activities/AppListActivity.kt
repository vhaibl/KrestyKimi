package com.kresty.isolation.activities

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.kresty.isolation.R
import com.kresty.isolation.adapter.AppSelectionAdapter
import com.kresty.isolation.databinding.ActivityAppListBinding
import com.kresty.isolation.model.AppInfo
import com.kresty.isolation.utils.AppSearchFilter
import com.kresty.isolation.utils.WorkProfileBridge
import com.kresty.isolation.utils.WorkProfileManager

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var workProfileManager: WorkProfileManager
    private lateinit var appAdapter: AppSelectionAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var currentApps: List<AppInfo> = emptyList()
    private var pendingPackageName: String? = null

    private val cloneAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
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

        setupRecyclerView()
        setupSearchView()
        loadApps()
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
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        allApps = workProfileManager.getAvailableAppsToClone()
        val workProfileApps = workProfileManager.getWorkProfileApps().map { it.packageName }.toSet()
        appAdapter.setAddedPackages(workProfileApps)

        binding.progressBar.visibility = View.GONE

        if (allApps.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            currentApps = allApps
            appAdapter.submitList(currentApps)
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
        cloneAppLauncher.launch(intent)
    }

    private fun handleCloneResult(resultCode: Int, data: android.content.Intent?) {
        val success = resultCode == Activity.RESULT_OK && data?.getBooleanExtra(WorkProfileBridge.EXTRA_SUCCESS, true) != false
        val packageName = data?.getStringExtra(WorkProfileBridge.EXTRA_PACKAGE_NAME) ?: pendingPackageName

        if (success && !packageName.isNullOrBlank()) {
            workProfileManager.markAppManaged(packageName)
            Toast.makeText(this, R.string.success_app_cloned, Toast.LENGTH_SHORT).show()
        } else {
            val errorMessage = data?.getStringExtra(WorkProfileBridge.EXTRA_ERROR_MESSAGE)
            Toast.makeText(this, errorMessage ?: getString(R.string.error_generic), Toast.LENGTH_LONG).show()
        }

        pendingPackageName = null
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
