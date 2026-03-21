# KrestyKimi Analysis Report

## 1. Architecture Overview

### Repository shape

- Single Android application module: `:app`
- Root Gradle files: `build.gradle`, `settings.gradle`, `gradle.properties`
- App module build: `app/build.gradle`

### Package structure

- `com.kresty.isolation`
  - `KrestyApplication.kt`
- `com.kresty.isolation.activities`
  - `MainActivity.kt`
  - `SetupActivity.kt`
  - `AppListActivity.kt`
- `com.kresty.isolation.adapter`
  - `AppsAdapter.kt`
  - `AppSelectionAdapter.kt`
- `com.kresty.isolation.model`
  - `AppInfo.kt`
- `com.kresty.isolation.receivers`
  - `BootReceiver.kt`
  - `KrestyDeviceAdminReceiver.kt`
- `com.kresty.isolation.utils`
  - `WorkProfileManager.kt`
  - `PreferencesManager.kt`
  - `AppSearchFilter.kt`

### Module boundaries and responsibilities

- UI layer
  - `app/src/main/java/com/kresty/isolation/activities/MainActivity.kt`
  - `app/src/main/java/com/kresty/isolation/activities/SetupActivity.kt`
  - `app/src/main/java/com/kresty/isolation/activities/AppListActivity.kt`
- UI adapters
  - `app/src/main/java/com/kresty/isolation/adapter/AppsAdapter.kt`
  - `app/src/main/java/com/kresty/isolation/adapter/AppSelectionAdapter.kt`
- Business logic
  - `app/src/main/java/com/kresty/isolation/utils/WorkProfileManager.kt`
  - `app/src/main/java/com/kresty/isolation/utils/PreferencesManager.kt`
  - `app/src/main/java/com/kresty/isolation/utils/AppSearchFilter.kt`
- Android system integration
  - `app/src/main/java/com/kresty/isolation/receivers/KrestyDeviceAdminReceiver.kt`
  - `app/src/main/java/com/kresty/isolation/receivers/BootReceiver.kt`

### Entry points

- Application class
  - `app/src/main/java/com/kresty/isolation/KrestyApplication.kt:6`
- Launcher activity
  - `app/src/main/java/com/kresty/isolation/activities/MainActivity.kt:26`
  - `app/src/main/AndroidManifest.xml:36`
- Internal setup flow
  - `app/src/main/java/com/kresty/isolation/activities/SetupActivity.kt:16`
- App selection flow
  - `app/src/main/java/com/kresty/isolation/activities/AppListActivity.kt:20`
- Broadcast receivers
  - `app/src/main/java/com/kresty/isolation/receivers/KrestyDeviceAdminReceiver.kt:13`
  - `app/src/main/java/com/kresty/isolation/receivers/BootReceiver.kt:8`
- Services
  - None declared in the manifest

### AndroidManifest summary

File: `app/src/main/AndroidManifest.xml`

- Permissions
  - `android.permission.QUERY_ALL_PACKAGES`
  - `android.permission.REQUEST_DELETE_PACKAGES`
  - `android.permission.RECEIVE_BOOT_COMPLETED`
- Declared components
  - `MainActivity` exported launcher activity
  - `SetupActivity` non-exported
  - `AppListActivity` non-exported
  - `KrestyDeviceAdminReceiver` exported with `android.permission.BIND_DEVICE_ADMIN`
  - `BootReceiver` exported for `BOOT_COMPLETED`
- Intent filters
  - `MAIN` / `LAUNCHER` on `MainActivity`
  - `DEVICE_ADMIN_ENABLED` and `PROFILE_PROVISIONING_COMPLETE` on `KrestyDeviceAdminReceiver`
  - `BOOT_COMPLETED` on `BootReceiver`

### Work profile / managed profile APIs

- `DevicePolicyManager` is the core platform API used throughout:
  - `app/src/main/java/com/kresty/isolation/utils/WorkProfileManager.kt:21`
  - `app/src/main/java/com/kresty/isolation/receivers/KrestyDeviceAdminReceiver.kt:41`
- Managed profile provisioning:
  - `ACTION_PROVISION_MANAGED_PROFILE` in `WorkProfileManager.kt:48`
- Profile owner checks:
  - `isProfileOwnerApp()` in `KrestyDeviceAdminReceiver.kt:27-30`
- Profile enablement and naming:
  - `setProfileName()` and `setProfileEnabled()` in `KrestyDeviceAdminReceiver.kt:46-49`
- App hiding / freezing:
  - `setApplicationHidden()` in `WorkProfileManager.kt:179`, `:196`, `:213`
- App enablement in profile:
  - `enableSystemApp()` in `WorkProfileManager.kt:161`
- Profile wipe / deletion:
  - `wipeData(0)` in `WorkProfileManager.kt:235`

### ProfileOwner / DeviceOwner usage

- Confirmed ProfileOwner usage
  - `DevicePolicyManager.isProfileOwnerApp()` in `KrestyDeviceAdminReceiver.kt:27-30`
- No confirmed DeviceOwner API usage
  - No `isDeviceOwnerApp()`, `setDeviceOwner`, or device-owner-only flows found in the app code

### Isolation / sandboxing mechanisms

- Primary isolation mechanism
  - Android Work Profile / managed profile via `DevicePolicyManager`
- Cross-profile intent usage
  - No explicit cross-profile intent APIs found
- UsageStatsManager
  - Not used
- App discovery and isolation model
  - Enumerates installed packages via `PackageManager.getInstalledPackages()`
  - Uses app hiding and enabling APIs to manage work-profile visibility
- Internal app eventing
  - Package-scoped broadcast `com.kresty.isolation.PROFILE_READY` in `KrestyDeviceAdminReceiver.kt:55-57`

### Build configuration

From `app/build.gradle`

- `compileSdk 33`
- `minSdk 29`
- `targetSdk 33`
- View binding enabled
- Release minification enabled with `proguard-android-optimize.txt` and `app/proguard-rules.pro`

Dependencies in use

- AndroidX Core KTX 1.9.0
- AppCompat 1.6.1
- Material 1.8.0
- ConstraintLayout 2.1.4
- RecyclerView 1.3.0
- Lifecycle runtime/viewmodel KTX 2.6.1
- Kotlin Coroutines Android 1.6.4

### Test directories

Existing before this pass

- No `app/src/test`
- No `app/src/androidTest`

Added in this pass

- Unit tests
  - `app/src/test/java/com/kresty/isolation/model/AppInfoTest.kt`
  - `app/src/test/java/com/kresty/isolation/model/SubscriptionTierTest.kt`
  - `app/src/test/java/com/kresty/isolation/utils/AppSearchFilterTest.kt`
  - `app/src/test/java/com/kresty/isolation/utils/PreferencesManagerTest.kt`
- Instrumented tests
  - `app/src/androidTest/java/com/kresty/isolation/activities/MainActivityTest.kt`
  - `app/src/androidTest/java/com/kresty/isolation/activities/SetupActivityTest.kt`
  - `app/src/androidTest/java/com/kresty/isolation/utils/PreferencesManagerInstrumentedTest.kt`

### Architectural summary

KrestyKimi is a small, single-module Android app that wraps Android Enterprise managed-profile APIs behind a simple three-screen UI. The app’s core is `WorkProfileManager`, which provisions a work profile, enables apps inside it, hides apps to simulate freezing, and wipes the profile on deletion. There is no service layer, no database, and no network stack currently enabled in code. The codebase is easy to map, but the managed-profile privileges requested are materially broader than the current feature set requires.

## 2. Security Findings

Top 5 issues from static review. Severity reflects current code plus the privilege level granted after device-admin/profile-owner enrollment.

### 1. High: over-broad device-admin policy declaration

- Evidence
  - `app/src/main/res/xml/device_admin_receiver.xml:3-14`
  - Declares `limit-password`, `reset-password`, `force-lock`, `wipe-data`, `expire-password`, `disable-camera`, `disable-keyguard-features`
- Attack path
  - Once the user grants device-admin/profile-owner trust, any compromise, malicious update, or hidden code path can exercise far more sensitive controls than the app’s public feature set suggests.
- Impact
  - Potential password policy control, camera disablement, keyguard restrictions, and destructive wipe capability.
- Smallest remediation
  - Remove unused policies and keep only the minimum policy surface required for managed-profile provisioning and profile lifecycle tasks.
  - Re-audit every `DevicePolicyManager` call against the declared `<uses-policies>`.

### 2. High: destructive profile wipe is one method call away

- Evidence
  - `app/src/main/java/com/kresty/isolation/utils/WorkProfileManager.kt:233-239`
- Attack path
  - Any reachable path inside the trusted app process can call `dpm.wipeData(0)` after profile-owner activation.
- Impact
  - Immediate deletion of the managed profile and all app data inside it.
- Smallest remediation
  - Gate the wipe flow behind an explicit capability check and an additional confirmation step that is harder to trigger accidentally.
  - Centralize wipe operations in a dedicated deletion path with logging and user-visible warnings.

### 3. Medium: broad package inventory and visibility permissions

- Evidence
  - Manifest permission: `app/src/main/AndroidManifest.xml:7-10`
  - Installed package enumeration: `app/src/main/java/com/kresty/isolation/utils/WorkProfileManager.kt:73`, `:116`
- Attack path
  - The app can inventory all installed apps on the device, and any future code added to this permission surface inherits that visibility.
- Impact
  - Privacy-sensitive app inventory collection and increased policy/review friction for distribution channels.
- Smallest remediation
  - Replace `QUERY_ALL_PACKAGES` where possible with narrower package visibility declarations or launcher-query based discovery.
  - Remove `REQUEST_DELETE_PACKAGES` until a real delete flow exists.

### 4. Low: exported boot receiver can be spoofed by third-party apps

- Evidence
  - Manifest: `app/src/main/AndroidManifest.xml:101-108`
  - Receiver logic: `app/src/main/java/com/kresty/isolation/receivers/BootReceiver.kt:14-23`
- Attack path
  - Any third-party app can send a forged `BOOT_COMPLETED` broadcast to an exported receiver.
- Impact
  - Today the receiver only logs and checks profile state, so the immediate impact is low. This becomes riskier if boot-time restoration logic grows later.
- Smallest remediation
  - Restrict the receiver if system delivery still works on target devices, or add defensive checks and keep the boot path side-effect free.

### 5. Low: release hardening exists, but obfuscation is only partial

- Evidence
  - `app/build.gradle:18-22`
  - `app/proguard-rules.pro:3-32`
- Attack path
  - Reverse-engineering remains easier than necessary because wide class groups are kept, including all activities and receivers.
- Impact
  - Lower resistance to static analysis of privileged app flows.
- Smallest remediation
  - Tighten keep rules to only reflection-sensitive types and platform-required entry points.
  - Add a release build validation step in CI to ensure shrink/minify still passes after rule changes.

### Notes from the audit

- No hardcoded production secrets or API keys were found in the app code.
- `PreferencesManager` uses `MODE_PRIVATE` SharedPreferences and stores low-sensitivity local state only.
- No database or file-based storage layer is present.
- No externally exposed activities beyond the launcher were found.
- No direct unsafe IPC surface was found beyond the exported boot receiver and the expected device-admin receiver.
- Static review only: runtime verification of profile-owner behavior on real devices is still required.

## 3. Competitive Analysis

### Comparison table

| App | Isolation mechanism | Android baseline | Permission / privilege profile | Setup UX | Feature set | Open-source / license | Security posture | Limits / known issues |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| KrestyKimi | Managed profile / Work Profile via `DevicePolicyManager` | API 29+ from `app/build.gradle` | `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `RECEIVE_BOOT_COMPLETED`, device-admin/profile-owner | Very simple one-button setup flow in `SetupActivity`; no advanced/manual path | Create profile, list apps, clone, freeze, unfreeze, remove, delete profile | Yes, MIT (`README.md`) | Narrow code surface, but over-privileged admin policy and no automated security checks | No advanced clone/install path, no VPN segmentation, no cross-profile tooling, no proven device compatibility handling |
| Shelter | Work Profile | Android devices with Work Profile support; F-Droid build requires Android 8.0+ per listing | Work-profile / device-admin trust; `PACKAGE_USAGE_STATS` visible on F-Droid listing | Guided setup wizard, warns about vendor ROM/device compatibility | Clone apps, run two accounts, freeze work-profile apps | Yes, GPLv3 in current upstream metadata | Mature FOSS posture, but explicitly depends on OEM Work Profile implementation quality | Vendor ROM compatibility issues; upstream warns that broken ROMs can cause severe device issues |
| Island | Work Profile plus “Managed Mainland” mode | Android 5.0+ per official site | DPC/device-admin trust; open APIs for third-party apps; manual setup sometimes needed; Play build has install restrictions | More advanced than Kresty; supports manual setup and extension-based flows | Clone apps, freeze, hide apps, parallel accounts, open DPC APIs, Managed Mainland mode | Yes, Apache-2.0 | Powerful and extensible, but broader surface and advanced modes raise complexity/risk | Dual Apps conflicts, Samsung/custom ROM limits, some clone flows need extension/root/Shizuku |
| Insular | FLOSS fork of Island on Work Profile | Android 8.1+ per F-Droid | Device-admin, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, cross-profile/user permissions, no Internet permission | Similar to Island; F-Droid friendly packaging | Clone, freeze, archive, re-freeze, hide apps, selective VPN enable/disable, USB restrictions | Yes, Apache-2.0 | Strong privacy story for the app itself: no Internet permission, blobs removed | Still inherits Island complexity and some device/ROM compatibility problems; issue history includes bootloop reports |
| Test DPC | Reference DPC for managed profile and device owner | Android 5.0+ per official README | Full DPC / enterprise test privilege surface | Developer-oriented, not consumer-friendly | Work profile and device-owner provisioning, app restrictions, enterprise policy testing | Yes, Apache-2.0 | Best reference for API behavior, not hardened as a consumer isolation app | Built for testing and enterprise validation, not polished end-user isolation |

### Positioning summary

KrestyKimi currently sits below Shelter, Island, and Insular in maturity and feature depth, but above a raw sample like Test DPC in end-user focus. Its current advantage is simplicity: one app module, a short provisioning flow, and a limited UI. Its main weakness is that the privileged surface is not yet as disciplined as the app’s small feature set would suggest.

If KrestyKimi wants to compete credibly with Shelter or Island, it needs three things:

- Better privilege minimization and device compatibility handling
- Real automated verification on emulator and physical-profile setups
- Feature differentiation beyond basic clone/freeze/remove flows

### External comparison sources

- Island homepage: https://island.oasisfeng.com/
- Island FAQ: https://island.oasisfeng.com/faq
- Island source repository: https://github.com/oasisfeng/island
- Shelter upstream metadata and README snippets:
  - https://gitea.angry.im/PeterCxy/Shelter/?id=a3759669d03af61b196ed9d25a92b66578ab81ba
  - https://f-droid.org/sq/packages/net.typeblog.shelter/
- Insular F-Droid listing: https://f-droid.org/en/packages/com.oasisfeng.island.fdroid/
- Test DPC repository: https://github.com/googlesamples/android-testdpc

## 4. Test Coverage Report

### Existing test coverage before this pass

- None. The repository had no `src/test` or `src/androidTest` directories.

### New or modified test-related files

- Added unit tests
  - `app/src/test/java/com/kresty/isolation/model/AppInfoTest.kt`
  - `app/src/test/java/com/kresty/isolation/model/SubscriptionTierTest.kt`
  - `app/src/test/java/com/kresty/isolation/utils/AppSearchFilterTest.kt`
  - `app/src/test/java/com/kresty/isolation/utils/PreferencesManagerTest.kt`
- Added instrumented tests
  - `app/src/androidTest/java/com/kresty/isolation/activities/MainActivityTest.kt`
  - `app/src/androidTest/java/com/kresty/isolation/activities/SetupActivityTest.kt`
  - `app/src/androidTest/java/com/kresty/isolation/utils/PreferencesManagerInstrumentedTest.kt`
- Added test support / extraction
  - `app/src/main/java/com/kresty/isolation/utils/AppSearchFilter.kt`
  - `app/src/main/java/com/kresty/isolation/activities/AppListActivity.kt`
- Build/test configuration
  - `app/build.gradle`
  - `gradle.properties`
  - `.gitignore`
  - `gradlew` executable bit restored
  - `gradle/wrapper/gradle-wrapper.jar` restored from an empty tracked file so `./gradlew` can run

### What is covered now

- Model behavior
  - `AppInfo` equality / hash behavior
  - `SubscriptionTier` app-count rules
- Business logic
  - `AppSearchFilter` query matching behavior
  - `PreferencesManager` read/write behavior
- UI smoke flows
  - Main screen renders setup CTA
  - Setup screen renders primary action and idle progress state

### Coverage percentage

- Exact code coverage percentage is not available.
- No JaCoCo or Kover reporting was configured in the repository.
- Current result is test presence and scope expansion, not measured line coverage.

### Local execution status

Commands attempted

- `./gradlew test --no-daemon --console=plain`
- `./gradlew connectedAndroidTest --no-daemon --console=plain`
- `ANDROID_HOME=/home/admin/Android/Sdk ANDROID_SDK_ROOT=/home/admin/Android/Sdk ./gradlew test --no-daemon --console=plain`
- `ANDROID_HOME=/home/admin/Android/Sdk ANDROID_SDK_ROOT=/home/admin/Android/Sdk ./gradlew connectedAndroidTest --no-daemon --console=plain`

Observed blockers

- Fixed before rerun
  - committed `local.properties` with invalid `sdk.dir`
  - empty tracked `gradle/wrapper/gradle-wrapper.jar`
  - repository resolution conflict between `build.gradle` and `settings.gradle`
  - missing `android.useAndroidX=true`
- Immediate local blocker without extra environment wiring
  - Gradle fails because `local.properties` was removed from the tree and no SDK path is exported:
    - `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file`
- Remaining environment blocker after wiring `ANDROID_HOME` and `ANDROID_SDK_ROOT`
  - Local host is `aarch64`, while AGP 7.3.1 invokes an x86_64 `aapt2` binary in this environment:
    - `qemu-x86_64: Could not open '/lib64/ld-linux-x86-64.so.2': No such file or directory`
  - The local SDK also does not include the emulator binary or system images needed to run `connectedAndroidTest` outside GitHub Actions.

### Pass / fail summary

- Unit tests: not completed locally because Android resource compilation failed before test execution on this ARM64 host
- Instrumented tests: not completed locally; after SDK wiring, the same ARM64 `aapt2` failure occurs before test execution, and the local SDK is missing emulator packages
- Expected CI path: GitHub Actions on `ubuntu-latest` x86_64 should avoid the ARM64 `aapt2` blocker

## 5. CI/CD Pipeline Summary

### Files added

- Workflow:
  - `.github/workflows/build-and-deploy.yml`
- Documentation:
  - `.github/workflows/README.md`

### Workflow behavior

Path: `.github/workflows/build-and-deploy.yml`

- Triggers
  - Push to `main`
  - Push to `master`
  - Manual `workflow_dispatch`
- Manual input
  - `skip_tests` boolean, default `false`
- Environment
  - `ubuntu-latest`
  - JDK 17 via `actions/setup-java@v4`
  - Android SDK setup via `android-actions/setup-android@v3`
  - API 33 packages installed via `sdkmanager`
- Test steps
  - `./gradlew test`
  - `reactivecircus/android-emulator-runner@v2` with API 29, `x86_64`, `pixel_4` running `./gradlew connectedAndroidTest`
- Build step
  - Attempts `./gradlew assembleRelease`
  - Falls back to `./gradlew assembleDebug` if release build fails
- Artifacts
  - Uploads `app/build/outputs/apk/**/*.apk` as `krestykimi-apk`
- Telegram delivery
  - Uses `curl` with Telegram `sendDocument`
  - Reads `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` from GitHub Secrets
  - Includes build status and commit SHA in the caption

### Required GitHub Secrets

- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHAT_ID`

### Manual trigger instructions

- Open GitHub `Actions`
- Select `Build and Deploy`
- Click `Run workflow`
- Optionally set `skip_tests=true` for a hotfix build

## 6. Recommendations

### Top 3 improvements

1. Reduce the device-admin policy surface to the minimum actually required.
   - This is the biggest security win relative to the app’s current size.
2. Add x86_64 CI verification as the source of truth and upgrade AGP/tooling when ready for ARM64-friendly local builds.
   - Today the local ARM64 host cannot execute the bundled `aapt2`.
3. Harden the product model before adding more features.
   - Define exactly which operations are allowed in personal profile vs managed profile, and verify them with emulator/device tests.

### Roadmap comparison against Shelter / Island / Insular

Features KrestyKimi should consider next

- Better compatibility diagnostics and a manual setup fallback like Island
- Safer first-run warnings about OEM / ROM incompatibilities like Shelter
- Selective VPN or network segmentation controls similar to Insular
- More precise app visibility and clone/install flows
- Structured logging / telemetry-free diagnostics for support
- Coverage reporting and release validation in CI

Features KrestyKimi should avoid adding before privilege cleanup

- Advanced mainland control like Island’s Managed Mainland
- Broader admin/device-owner modes
- Additional hidden-system-app manipulation paths

### Bottom line

KrestyKimi has a viable minimal product skeleton, but it is not yet competitive with the leading Work Profile isolation apps on robustness, compatibility handling, or security discipline. The codebase is small enough that these gaps are fixable quickly, provided the next step is privilege reduction plus reliable automated verification rather than feature expansion alone.
