package com.kresty.isolation.activities

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.kresty.isolation.R
import com.kresty.isolation.utils.PreferencesManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ManagedProfileFlowTest {

    companion object {
        private const val TARGET_PACKAGE = "com.kresty.ownerfixture"
        private const val TIMEOUT_MS = 30_000L
    }

    private lateinit var appContext: Context
    private lateinit var device: UiDevice

    @Before
    fun clearOwnerPreferences() {
        appContext = ApplicationProvider.getApplicationContext()
        PreferencesManager(appContext).clear()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun managedProfileFlow_addFreezeUnfreezeAndRemoveFirstAvailableApp() {
        val scenario = launch(MainActivity::class.java)
        val appPackage = appContext.packageName

        try {
            waitForObject(By.res(appPackage, "emptyState"))
            clickObject(By.res(appPackage, "fabAddApp"))

            waitForObject(By.res(appPackage, "recyclerView"))
            scrollRecyclerToText(appPackage, "recyclerView", TARGET_PACKAGE)
            clickChildInRowByText(TARGET_PACKAGE, "addButton")

            waitForCloneCompletion(appPackage)
            waitForObject(By.text(TARGET_PACKAGE))
            clickChildInRowByText(TARGET_PACKAGE, "freezeButton")
            waitForObject(By.text(appContext.getString(R.string.freezed_apps_count, 1)))

            clickChildInRowByText(TARGET_PACKAGE, "freezeButton")
            waitForObject(By.text(appContext.getString(R.string.freezed_apps_count, 0)))

            clickChildInRowByText(TARGET_PACKAGE, "deleteButton")
            clickObject(By.text(appContext.getString(R.string.dialog_confirm)))
            waitForObject(By.res(appPackage, "emptyState"))

            clickObject(By.res(appPackage, "fabAddApp"))
            waitForObject(By.res(appPackage, "recyclerView"))
            scrollRecyclerToText(appPackage, "recyclerView", TARGET_PACKAGE)
            clickChildInRowByText(TARGET_PACKAGE, "addButton")

            waitForCloneCompletion(appPackage)
            waitForObject(By.text(TARGET_PACKAGE))
            clickChildInRowByText(TARGET_PACKAGE, "freezeButton")
            waitForObject(By.text(appContext.getString(R.string.freezed_apps_count, 1)))
        } finally {
            scenario.close()
        }
    }

    private fun waitForObject(selector: androidx.test.uiautomator.BySelector): UiObject2 {
        val found = device.wait(Until.findObject(selector), TIMEOUT_MS)
        return found ?: throw AssertionError("Timed out waiting for selector: $selector")
    }

    private fun clickObject(selector: androidx.test.uiautomator.BySelector) {
        waitForObject(selector).click()
        device.waitForIdle()
    }

    private fun waitForCloneCompletion(appPackage: String) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            maybeClickInstallerPrompt()
            val appsRecycler = device.findObject(By.res(appPackage, "appsRecyclerView"))
            if (appsRecycler != null) {
                return
            }
            SystemClock.sleep(500)
        }
        throw AssertionError("Timed out waiting for cloned app list to appear")
    }

    private fun scrollRecyclerToText(appPackage: String, recyclerId: String, text: String) {
        val scrollable = UiScrollable(UiSelector().resourceId("$appPackage:id/$recyclerId")).setAsVerticalList()
        check(scrollable.scrollTextIntoView(text)) {
            "Could not scroll $recyclerId to text $text"
        }
        device.waitForIdle()
    }

    private fun clickChildInRowByText(text: String, childRes: String) {
        val rowText = waitForObject(By.text(text))
        var current: UiObject2? = rowText

        while (current != null) {
            val button = current.findObject(By.res(appContext.packageName, childRes))
            if (button != null) {
                button.click()
                device.waitForIdle()
                return
            }
            current = current.parent
        }

        throw AssertionError("Could not find child $childRes for row containing $text")
    }

    private fun maybeClickInstallerPrompt() {
        val candidateButtons = listOf(
            By.res("com.android.permissioncontroller", "ok_button"),
            By.res("com.android.permissioncontroller", "continue_button"),
            By.res("com.android.packageinstaller", "ok_button"),
            By.res("com.google.android.packageinstaller", "ok_button"),
            By.res("android", "button1"),
            By.text("Install"),
            By.text("Установить"),
            By.text("Done"),
            By.text("Готово")
        )

        for (selector in candidateButtons) {
            val button = device.findObject(selector)
            if (button != null && button.isEnabled) {
                button.click()
                device.waitForIdle()
                SystemClock.sleep(500)
                return
            }
        }
    }
}
