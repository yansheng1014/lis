package com.lis.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.lis.wear.ui.MainActivity

/**
 * 重建应用进程。
 *
 * 仅一个用途：存储 AppOp 写好之后，如果系统没有对本进程热重挂载，就需要一个全新
 * 进程才能看到 /sdcard（挂载视图在进程 fork 时就定型）。
 *
 * 手法是通用的 phoenix 方案：本 Activity 跑在独立的 `:restart` 进程里，由它杀掉
 * 主进程、再把 [MainActivity] 拉起来，最后自杀。发起方是前台 Activity，所以不受
 * 后台启动 Activity 的限制，也不依赖 Shizuku 或任何权限。
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 媒体服务是 START_STICKY 的，主进程被杀后系统会把它拉回来（挂载视图还是旧的），
        // 所以先显式停掉。用类名字符串，避开 media3 的 @UnstableApi opt-in。
        runCatching {
            stopService(
                Intent().setClassName(this, "com.lis.wear.playback.LisPlaybackService")
            )
        }

        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        if (mainPid > 0 && mainPid != Process.myPid()) {
            runCatching { Process.killProcess(mainPid) }
        }

        val next = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(
                MainActivity.EXTRA_SCAN_FILES,
                intent.getBooleanExtra(EXTRA_SCAN_AFTER, false),
            )
        }
        runCatching { startActivity(next) }
        finish()

        // 等新进程真正起来再收掉这个辅助进程，否则 startActivity 可能随进程一起没了。
        Handler(Looper.getMainLooper()).postDelayed(
            { Runtime.getRuntime().exit(0) },
            EXIT_DELAY_MS,
        )
    }

    companion object {
        private const val EXTRA_MAIN_PID = "main_pid"
        private const val EXTRA_SCAN_AFTER = "scan_after"
        private const val EXIT_DELAY_MS = 1_500L

        /** 由主进程调用；返回后主进程很快会被杀掉。 */
        fun trigger(context: Context, scanAfterRestart: Boolean) {
            val intent = Intent(context, RestartActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_MAIN_PID, Process.myPid())
                putExtra(EXTRA_SCAN_AFTER, scanAfterRestart)
            }
            context.startActivity(intent)
        }
    }
}