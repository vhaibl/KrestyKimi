#!/usr/bin/env bash
set -euo pipefail

mkdir -p ci-artifacts
adb logcat -c || true

./gradlew assembleDebug assembleDebugAndroidTest

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

timeout 10m adb shell am instrument --user 0 -w \
  -e notClass com.kresty.isolation.activities.ManagedProfileFlowTest \
  com.kresty.isolation.test/androidx.test.runner.AndroidJUnitRunner | tee ci-artifacts/pretests.log
grep -q 'OK (' ci-artifacts/pretests.log
! grep -q 'FAILURES!!!' ci-artifacts/pretests.log

profile_output="$(timeout 2m adb shell pm create-user --profileOf 0 --managed 'Kresty Test' | tr -d '\r')"
printf '%s\n' "$profile_output" > ci-artifacts/profile-create-output.txt
profile_id="$(printf '%s\n' "$profile_output" | sed -n 's/.*id \([0-9][0-9]*\).*/\1/p')"

if [[ -z "$profile_id" ]]; then
  echo "Failed to create managed profile"
  printf '%s\n' "$profile_output"
  exit 1
fi

timeout 2m adb shell am start-user -w "$profile_id"
timeout 2m adb shell pm install-existing --user "$profile_id" com.kresty.isolation
timeout 2m adb shell dpm set-profile-owner --user "$profile_id" \
  'com.kresty.isolation/.receivers.KrestyDeviceAdminReceiver'
timeout 2m adb shell am broadcast --user "$profile_id" \
  -a android.app.action.PROFILE_PROVISIONING_COMPLETE \
  -n com.kresty.isolation/.receivers.KrestyDeviceAdminReceiver

adb shell pm list users | tee ci-artifacts/pm-list-users.txt
adb shell dumpsys device_policy | tee ci-artifacts/device-policy.txt
grep -F "$profile_id" ci-artifacts/pm-list-users.txt
grep -F "com.kresty.isolation/.receivers.KrestyDeviceAdminReceiver" ci-artifacts/device-policy.txt

timeout 10m adb shell am instrument --user 0 -w \
  -e class com.kresty.isolation.activities.ManagedProfileFlowTest \
  com.kresty.isolation.test/androidx.test.runner.AndroidJUnitRunner | tee ci-artifacts/managed-profile.log
adb logcat -d > ci-artifacts/logcat.txt || true
grep -q 'OK (' ci-artifacts/managed-profile.log
! grep -q 'FAILURES!!!' ci-artifacts/managed-profile.log
