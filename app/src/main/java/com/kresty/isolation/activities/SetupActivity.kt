package com.kresty.isolation.activities

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kresty.isolation.R
import com.kresty.isolation.databinding.ActivitySetupBinding
import com.kresty.isolation.receivers.KrestyDeviceAdminReceiver
import com.kresty.isolation.utils.WorkProfileManager

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var workProfileManager: WorkProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        workProfileManager = WorkProfileManager(this)
        
        binding.startButton.setOnClickListener {
            startWorkProfileSetup()
        }
    }

    private fun startWorkProfileSetup() {
        // Check if work profile is supported
        if (!workProfileManager.isWorkProfileSupported()) {
            Toast.makeText(this, "Рабочий профиль не поддерживается на этом устройстве", Toast.LENGTH_LONG).show()
            return
        }
        
        // Show progress
        showProgress(true)
        
        // Start provisioning
        val provisioningIntent = workProfileManager.startWorkProfileProvisioning()
        
        if (provisioningIntent != null) {
            startActivityForResult(
                provisioningIntent,
                WorkProfileManager.REQUEST_PROVISION_MANAGED_PROFILE
            )
        } else {
            showProgress(false)
            Toast.makeText(this, "Не удалось запустить настройку профиля", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        showProgress(false)
        
        if (requestCode == WorkProfileManager.REQUEST_PROVISION_MANAGED_PROFILE) {
            if (resultCode == Activity.RESULT_OK) {
                // Work profile created successfully
                Toast.makeText(this, R.string.work_profile_active, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // User cancelled or error
                Toast.makeText(this, R.string.error_provisioning_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showProgress(show: Boolean) {
        if (show) {
            binding.progressBar.visibility = View.VISIBLE
            binding.progressText.visibility = View.VISIBLE
            binding.startButton.isEnabled = false
        } else {
            binding.progressBar.visibility = View.GONE
            binding.progressText.visibility = View.GONE
            binding.startButton.isEnabled = true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
