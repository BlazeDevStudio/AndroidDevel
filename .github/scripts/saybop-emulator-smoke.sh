#!/usr/bin/env bash
set -euo pipefail
adb devices
adb wait-for-device
adb install -r SayBop/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.saybop.app/.MainActivity
sleep 5
adb shell pidof com.saybop.app
adb shell uiautomator dump /sdcard/saybop-window.xml
adb shell cat /sdcard/saybop-window.xml | grep -i SAYBOP
cd SayBop
./gradlew :app:connectedDebugAndroidTest --stacktrace
