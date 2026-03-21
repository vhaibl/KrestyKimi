package com.kresty.isolation.utils

import com.kresty.isolation.model.AppInfo

object AppSearchFilter {
    fun filter(apps: List<AppInfo>, query: String?): List<AppInfo> {
        if (query.isNullOrBlank()) {
            return apps
        }

        val normalizedQuery = query.trim()
        return apps.filter { app ->
            app.appName.contains(normalizedQuery, ignoreCase = true) ||
                app.packageName.contains(normalizedQuery, ignoreCase = true)
        }
    }
}
