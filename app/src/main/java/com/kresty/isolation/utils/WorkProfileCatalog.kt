package com.kresty.isolation.utils

import com.kresty.isolation.model.AppInfo

object WorkProfileCatalog {

    fun buildInstalledApps(
        ownerApps: List<AppInfo>,
        installedPackages: Set<String>,
        frozenPackages: Set<String>
    ): List<AppInfo> {
        return ownerApps
            .asSequence()
            .filter { installedPackages.contains(it.packageName) }
            .map {
                it.copy(
                    isInstalledInWorkProfile = true,
                    isFrozen = frozenPackages.contains(it.packageName)
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    fun buildAvailableApps(
        ownerApps: List<AppInfo>,
        installedPackages: Set<String>
    ): List<AppInfo> {
        return ownerApps
            .asSequence()
            .filterNot { installedPackages.contains(it.packageName) }
            .map { it.copy(isInstalledInWorkProfile = false, isFrozen = false) }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }
}
