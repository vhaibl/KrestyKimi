package com.kresty.isolation.utils

import com.kresty.isolation.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkProfileCatalogTest {

    private val ownerApps = listOf(
        AppInfo(packageName = "org.telegram.messenger", appName = "Telegram"),
        AppInfo(packageName = "com.whatsapp", appName = "WhatsApp"),
        AppInfo(packageName = "com.spotify.music", appName = "Spotify")
    )

    @Test
    fun buildInstalledApps_marksOnlyPackagesPresentInWorkProfile() {
        val isolatedApps = WorkProfileCatalog.buildInstalledApps(
            ownerApps = ownerApps,
            installedPackages = setOf("org.telegram.messenger"),
            frozenPackages = emptySet()
        )

        assertEquals(1, isolatedApps.size)
        assertEquals("org.telegram.messenger", isolatedApps.single().packageName)
        assertTrue(isolatedApps.single().isInstalledInWorkProfile)
    }

    @Test
    fun buildAvailableApps_excludesAlreadyInstalledPackages() {
        val availableApps = WorkProfileCatalog.buildAvailableApps(
            ownerApps = ownerApps,
            installedPackages = setOf("org.telegram.messenger")
        )

        assertEquals(listOf("com.spotify.music", "com.whatsapp"), availableApps.map { it.packageName })
        assertTrue(availableApps.none { it.packageName == "org.telegram.messenger" })
    }

    @Test
    fun buildInstalledApps_marksFrozenPackages() {
        val isolatedApps = WorkProfileCatalog.buildInstalledApps(
            ownerApps = ownerApps,
            installedPackages = setOf("com.whatsapp"),
            frozenPackages = setOf("com.whatsapp")
        )

        assertTrue(isolatedApps.single().isFrozen)
        assertFalse(isolatedApps.single().isSystemApp)
    }
}
