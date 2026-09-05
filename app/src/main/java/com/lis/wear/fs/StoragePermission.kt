package com.lis.wear.fs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Storage access on Wear OS is awkward: no file manager, no reliable document
 * picker UI, and scoped storage blocks arbitrary reads on API 33+.
 *
 * Strategy, in order of ease:
 *  1. Legacy read (API 30–32): READ_EXTERNAL_STORAGE runtime permission.
 *  2. "All files access" (MANAGE_EXTERNAL_STORAGE) — grantable via the system
 *     settings screen or, when Shizuku is running, programmatically.
 *  3. SAF import (ACTION_OPEN_DOCUMENT) as an always-available fallback.
 */
object StoragePermission {

    fun hasAllFilesAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    fun hasLegacyRead(context: Context): Boolean =
        Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

    fun canScanStorage(context: Context): Boolean =
        hasAllFilesAccess(context) || hasLegacyRead(context)

    fun openAllFilesSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    // region Shizuku

    fun isShizukuAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun requestShizukuPermission(code: Int = 1000) {
        runCatching {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(code)
            }
        }
    }

    fun hasShizukuPermission(): Boolean =
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)

    /**
     * Grant MANAGE_EXTERNAL_STORAGE ("all files access") through the appops
     * service binder exposed by Shizuku. This lets us read /sdcard on API 33+
     * without a file manager. Best-effort: returns false if anything goes wrong.
     */
    fun grantAllFilesViaShizuku(context: Context): Boolean {
        return runCatching {
            if (!isShizukuAvailable() || !hasShizukuPermission()) return@runCatching false
            val opCode = appOpsOpCode("android:manage_external_storage") ?: return@runCatching false
            val appops = ShizukuBinderWrapper(
                rikka.shizuku.SystemServiceHelper.getSystemService("appops")
            )
            val data = android.os.Parcel.obtain()
            val reply = android.os.Parcel.obtain()
            try {
                data.writeInterfaceToken("com.android.internal.app.IAppOpsService")
                data.writeInt(1) // mode MODE_ALLOWED
                data.writeInt(android.os.Process.myUid())
                data.writeString(context.packageName)
                data.writeString(null) // featureId
                data.writeInt(opCode)
                data.writeString("android:manage_external_storage")
                // setMode(IBinder token, ...) — pass null token via writeStrongBinder
                data.writeStrongBinder(null)
                val ok = appops.transact(SET_MODE_CODE, data, reply, 0)
                reply.readException()
                ok
            } finally {
                data.recycle()
                reply.recycle()
            }
        }.getOrDefault(false) && hasAllFilesAccess(context)
    }

    /**
     * Resolve the numeric appops op code for a string op name (e.g.
     * "android:manage_external_storage"). Uses reflection because
     * AppOpsManager.strOpToOp is a hidden API.
     */
    private fun appOpsOpCode(name: String): Int? = runCatching {
        val m = Class.forName("android.app.AppOpsManager")
        val method = m.getMethod("strOpToOp", String::class.java)
        method.invoke(null, name) as? Int
    }.getOrNull()

    // IAppOpsService.setMode transaction code (stable across API 30+ in practice).
    private const val SET_MODE_CODE = 15
}