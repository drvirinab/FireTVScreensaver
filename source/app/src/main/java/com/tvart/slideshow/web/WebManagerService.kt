package com.tvart.slideshow.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.data.Defaults
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface

/** Foreground service hosting [WebServer]. Stops itself after [Defaults.WEB_IDLE_MS] without requests. */
class WebManagerService : Service() {
    private var server: WebServer? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val idleCheck = object : Runnable {
        override fun run() {
            val s = server ?: return
            if (System.currentTimeMillis() - s.lastRequest >= Defaults.WEB_IDLE_MS) stopSelf()
            else handler.postDelayed(this, 30_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = ArtRepo.get(this)
        goForeground(repo.settings.webPin)
        if (server == null) {
            try {
                val s = WebServer(applicationContext, Defaults.WEB_PORT)
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = s
                isRunning = true
                repo.settings.webEnabled = true
                try {
                    wake = (getSystemService(POWER_SERVICE) as PowerManager)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvart:web").apply { acquire(Defaults.WEB_IDLE_MS + 60_000) }
                } catch (_: Exception) {}
                handler.postDelayed(idleCheck, 30_000)
            } catch (e: Exception) {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground(pin: String) {
        val text = "${url(this) ?: "No network"}   PIN $pin"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val b = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Web manager", NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val n = b.setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("TV Art web manager").setContentText(text).setOngoing(true).build()
        startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        try { server?.stop() } catch (_: Exception) {}
        server = null
        try { wake?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        isRunning = false
        try { ArtRepo.get(this).settings.webEnabled = false } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "web_manager"
        private const val NOTIF_ID = 8080

        @Volatile var isRunning = false
            private set

        fun start(context: Context) {
            val i = Intent(context, WebManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebManagerService::class.java))
        }

        fun url(context: Context): String? {
            val addrs = ArrayList<Pair<String, String>>()
            try {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (a in ni.inetAddresses) if (a is Inet4Address && !a.isLoopbackAddress) addrs.add(ni.name to a.hostAddress!!)
                }
            } catch (_: Exception) {}
            val pick = addrs.firstOrNull { it.first.startsWith("wlan") || it.first.startsWith("eth") } ?: addrs.firstOrNull()
            return pick?.let { "http://${it.second}:${Defaults.WEB_PORT}" }
        }
    }
}
