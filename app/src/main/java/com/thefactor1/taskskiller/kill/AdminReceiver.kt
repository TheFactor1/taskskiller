package com.thefactor1.taskskiller.kill

import android.app.admin.DeviceAdminReceiver

/**
 * Present only so the app can be made device owner via
 * `adb shell dpm set-device-owner`. It intentionally overrides nothing — the
 * privileges, not the callbacks, are the point.
 */
class AdminReceiver : DeviceAdminReceiver()
