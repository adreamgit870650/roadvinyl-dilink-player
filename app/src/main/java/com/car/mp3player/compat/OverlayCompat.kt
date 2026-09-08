package com.car.mp3player.compat

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat

/** Overlay APIs and window types changed independently in Android 6 and 8. */
object OverlayCompat {
    fun canDrawOverlays(context: Context): Boolean = access(context) == OverlayAccess.ALLOWED

    fun access(context: Context): OverlayAccess =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                if (Settings.canDrawOverlays(context)) OverlayAccess.ALLOWED else OverlayAccess.DENIED
            }.getOrDefault(OverlayAccess.UNKNOWN)
        } else {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.SYSTEM_ALERT_WINDOW
            ) ==
                PackageManager.PERMISSION_GRANTED
            legacyOverlayAccess(permissionGranted, legacyAppOpMode(context))
        }

    private fun legacyAppOpMode(context: Context): Int? = runCatching {
        // Before API 23 there is no public string identifier for this operation.
        // Read the platform's own field and int overload instead of hardcoding an
        // OEM-sensitive op number. This only checks permission; it never grants it.
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) ?: return null
        val op = AppOpsManager::class.java.getField("OP_SYSTEM_ALERT_WINDOW").getInt(null)
        val check = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
        )
        check.invoke(manager, op, Process.myUid(), context.packageName) as? Int
    }.onFailure {
        Log.w("OverlayCompat", "Cannot determine legacy overlay AppOp", it)
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun windowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    fun permissionIntent(context: Context): Intent {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        return Intent(action, Uri.parse("package:${context.packageName}"))
    }
}

enum class OverlayAccess { ALLOWED, DENIED, UNKNOWN }

internal fun legacyOverlayAccess(permissionGranted: Boolean, appOpMode: Int?): OverlayAccess = when {
    !permissionGranted -> OverlayAccess.DENIED
    appOpMode == null -> OverlayAccess.UNKNOWN
    appOpMode == AppOpsManager.MODE_ALLOWED -> OverlayAccess.ALLOWED
    else -> OverlayAccess.DENIED
}
