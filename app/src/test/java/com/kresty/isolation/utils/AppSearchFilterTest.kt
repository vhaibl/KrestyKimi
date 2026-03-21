package com.kresty.isolation.utils

import com.kresty.isolation.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchFilterTest {

    private val apps = listOf(
        AppInfo(packageName = "org.telegram.messenger", appName = "Telegram"),
        AppInfo(packageName = "com.spotify.music", appName = "Spotify"),
        AppInfo(packageName = "com.example.notes", appName = "Notes")
    )

    @Test
    fun filter_returnsAllAppsForBlankQuery() {
        assertEquals(apps, AppSearchFilter.filter(apps, " "))
    }

    @Test
    fun filter_matchesAppNameIgnoringCase() {
        val result = AppSearchFilter.filter(apps, "spot")

        assertEquals(listOf(apps[1]), result)
    }

    @Test
    fun filter_matchesPackageNameIgnoringCase() {
        val result = AppSearchFilter.filter(apps, "telegram")

        assertEquals(listOf(apps[0]), result)
    }
}
