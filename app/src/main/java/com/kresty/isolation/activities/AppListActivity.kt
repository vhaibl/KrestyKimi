package com.kresty.isolation.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kresty.isolation.R
import com.kresty.isolation.adapter.AppSelectionAdapter
import com.kresty.isolation.databinding.ActivityAppListBinding
import com.kresty.isolation.model.AppInfo
// import com.kresty.isolation.utils.PreferencesManager
import com.kresty.isolation.utils.AppSearchFilter
import com.kresty.isolation.utils.WorkProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var workProfileManager: WorkProfileManager
    // private lateinit var prefs: PreferencesManager
    private lateinit var appAdapter: AppSelectionAdapter
    
    private var allApps: List<AppInfo> = emptyList()
    private var currentApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        workProfileManager = WorkProfileManager(this)
        // prefs = PreferencesManager(this)
        
        setupRecyclerView()
        setupSearchView()
        
        // Load apps
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
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.GONE
            
            allApps = withContext(Dispatchers.IO) {
                workProfileManager.getAvailableAppsToClone()
            }
            
            // Mark already added apps
            val workProfileApps = withContext(Dispatchers.IO) {
                workProfileManager.getWorkProfileApps().map { it.packageName }.toSet()
            }
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
        lifecycleScope.launch {
            // Монетизация отключена - лимитов нет
            // Check subscription limit
            // val currentApps = workProfileManager.getIsolatedAppsCount()
            // val maxApps = prefs.getMaxAllowedApps()
            
            // if (currentApps >= maxApps) {
            //     Toast.makeText(this@AppListActivity, R.string.limit_reached, Toast.LENGTH_SHORT).show()
            //     return@launch
            // }
            
            val success = withContext(Dispatchers.IO) {
                workProfileManager.cloneAppToWorkProfile(app.packageName)
            }
            
            if (success) {
                Toast.makeText(this@AppListActivity, R.string.success_app_cloned, Toast.LENGTH_SHORT).show()
                
                // Update added packages
                val workProfileApps = workProfileManager.getWorkProfileApps().map { it.packageName }.toSet()
                appAdapter.setAddedPackages(workProfileApps)
                appAdapter.notifyDataSetChanged()
                
                // Finish if limit reached - ОТКЛЮЧЕНО
                // if (currentApps + 1 >= maxApps) {
                //     finish()
                // }
            } else {
                Toast.makeText(this@AppListActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
