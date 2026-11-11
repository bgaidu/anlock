package com.kidslock.app

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 局域网家长面板：同一 Wi-Fi 下用手机/电脑浏览器查看状态、锁定、放行、加时。
 *
 * - 无第三方依赖：基于 ServerSocket 实现一个只支持 GET 的最小 HTTP 服务；
 * - 访问必须带口令（t 参数），口令在应用内生成，可随时重新生成让旧链接失效；
 * - 服务跑在 LockService 里，服务停止或家长关闭面板时立即释放端口。
 */
object LanServer {
    const val PORT = 8099
    private const val TAG = "LanServer"
    private const val MAX_REQUEST_BYTES = 8 * 1024

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    @Volatile
    private var appContext: Context? = null

    /** 启动失败原因（端口被占用等），用于应用内提示。 */
    @Volatile
    var lastError: String? = null
        private set

    fun isRunning(): Boolean = acceptThread?.isAlive == true

    @Synchronized
    fun start(context: Context) {
        if (isRunning()) return
        lastError = null
        appContext = context.applicationContext
        val thread = Thread({ acceptLoop() }, "kidslock-lan")
        thread.isDaemon = true
        acceptThread = thread
        thread.start()
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close server failed", e)
        }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        if (appContext != null) Diag.i("Lan", "panel stopped")
        appContext = null
    }

    /** 本机当前可被局域网访问的 IPv4 地址（优先 wlan）。 */
    fun localIpv4Addresses(): List<String> {
        val result = ArrayList<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        result.add(addr.hostAddress ?: continue)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "enumerate addresses failed", e)
        }
        // wlan 地址一般以 192.168 / 10. 开头，简单排前面即可
        return result.sortedBy { if (it.startsWith("192.168.") || it.startsWith("10.")) 0 else 1 }
    }

    fun url(): String? = localIpv4Addresses().firstOrNull()?.let { "http://$it:$PORT" }

    private fun acceptLoop() {
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(PORT), 8)
            serverSocket = ss
            Diag.i("Lan", "panel listening on $PORT")
            while (!ss.isClosed) {
                val socket = try {
                    ss.accept()
                } catch (e: Exception) {
                    break
                }
                try {
                    handle(socket)
                } catch (e: Exception) {
                    Log.w(TAG, "handle request failed", e)
                } finally {
                    try {
                        socket.close()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "lan server stopped", e)
            lastError = e.message ?: e.javaClass.simpleName
            Diag.w("Lan", "server stopped: ${e.message}")
        } finally {
            serverSocket = null
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 5000
        val ctx = appContext ?: return
        val requestLine = readRequestLine(socket.getInputStream()) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2 || parts[0] != "GET") {
            respond(socket, 405, "text/plain; charset=utf-8", "Method Not Allowed")
            return
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = parseQuery(target.substringAfter('?', ""))

        val pref = PrefManager(ctx)
        if (!pref.isLanEnabled()) {
            respond(socket, 403, "text/html; charset=utf-8", page("访问已关闭", "<p>家长已在应用内关闭局域网面板。</p>"))
            return
        }
        if (!tokenMatches(query["t"], pref.getLanToken())) {
            respond(socket, 403, "text/html; charset=utf-8", page("口令错误", "<p>链接不完整或口令已失效，请在应用内重新复制地址。</p>"))
            return
        }

        when (path) {
            "/", "/index.html" -> respond(socket, 200, "text/html; charset=utf-8", buildPage(ctx, pref, query["msg"]))
            "/action" -> {
                val msg = handleAction(ctx, pref, query)
                val location = "/?t=" + urlEncode(pref.getLanToken()) + "&msg=" + urlEncode(msg)
                respond(socket, 303, "text/plain; charset=utf-8", "", mapOf("Location" to location))
            }
            "/api/status" -> respond(socket, 200, "application/json; charset=utf-8", statusJson(pref))
            "/favicon.ico" -> respond(socket, 204, "text/plain", "")
            else -> respond(socket, 404, "text/plain; charset=utf-8", "Not Found")
        }
    }

    private fun readRequestLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var last4 = 0
        while (buffer.size() < MAX_REQUEST_BYTES) {
            val b = input.read()
            if (b < 0) break
            buffer.write(b)
            last4 = (last4 shl 8) or b
            // 头部结束：\r\n\r\n
            if (last4 == 0x0D0A0D0A) break
        }
        val text = buffer.toString(Charsets.UTF_8.name())
        return text.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun handleAction(ctx: Context, pref: PrefManager, query: Map<String, String>): String {
        return when (query["cmd"]) {
            "lock" -> {
                lockNow(ctx, pref)
                "已立即锁定"
            }
            "unlock" -> {
                val minutes = (query["min"]?.toIntOrNull() ?: 30).coerceIn(1, 180)
                unlock(ctx, pref, minutes)
                "已放行 $minutes 分钟"
            }
            "bonus" -> {
                val minutes = (query["min"]?.toIntOrNull() ?: 10).coerceIn(1, 120)
                pref.addBonusMinutes(minutes)
                "已加 $minutes 分钟" + if (pref.isTimerActive()) "（本轮剩余已延长）" else "（下次解锁生效）"
            }
            "stop" -> {
                pref.stopTimer()
                lockNow(ctx, pref)
                "已结束本轮计时并锁定"
            }
            else -> "未知操作"
        }
    }

    private fun lockNow(ctx: Context, pref: PrefManager) {
        pref.setLocked(true)
        pref.setLockReason(PrefManager.REASON_MANUAL)
        pref.setHomeAliasEnabled(ctx, true)
        pref.stopTimer()
        KioskManager.syncLockState(ctx)
        LockService.requestLock(ctx)
    }

    /** 家长放行：不受今日额度限制，给一次完整观看时长。 */
    private fun unlock(ctx: Context, pref: PrefManager, minutes: Int) {
        pref.grantDailyBypass(minutes)
        pref.grantScheduleBypass(minutes * 60_000L)
        pref.setLocked(false)
        pref.setHomeAliasEnabled(ctx, false)
        KioskManager.syncLockState(ctx)
        pref.resetPinFailures()
        pref.startTimer(minutes)
        LockService.requestUnlock(ctx)
    }

    private fun statusJson(pref: PrefManager): String {
        val timerRemaining = if (pref.isTimerActive()) pref.getRemainingMillis() else 0L
        return buildString {
            append('{')
            append("\"locked\":").append(pref.isLocked()).append(',')
            append("\"lockReason\":\"").append(pref.getLockReason()).append("\",")
            append("\"timerActive\":").append(pref.isTimerActive()).append(',')
            append("\"remainingMs\":").append(timerRemaining).append(',')
            append("\"withinSchedule\":").append(pref.isWithinSchedule()).append(',')
            append("\"scheduleEnabled\":").append(pref.isScheduleEnabled()).append(',')
            append("\"dailyLimitMin\":").append(pref.getDailyLimitMinutes()).append(',')
            append("\"dailyUsedMs\":").append(pref.getDailyUsedMs()).append(',')
            append("\"stars\":").append(pref.getStars())
            append('}')
        }
    }

    private fun buildPage(ctx: Context, pref: PrefManager, msg: String?): String {
        val token = pref.getLanToken()
        val timerActive = pref.isTimerActive() && pref.getRemainingMillis() > 0
        val stateText = when {
            pref.isLocked() -> when (pref.getLockReason()) {
                PrefManager.REASON_SCHEDULE -> "已锁定（不在使用时段）"
                PrefManager.REASON_MANUAL -> "已锁定（家长手动）"
                else -> "已锁定（时间用完）"
            }
            timerActive -> "使用中 · 剩余 " + formatDuration(pref.getRemainingMillis())
            pref.isScheduleEnabled() && pref.isScheduleFreeMode() && pref.isWithinSchedule() ->
                "时段内自由使用（仅时段限制）"
            else -> "未锁定"
        }
        val dailyLimit = pref.getDailyLimitMinutes()
        val usedMinutes = (pref.getDailyUsedMs() + 59_999) / 60_000
        val dailyText = if (dailyLimit <= 0) "已用 ${usedMinutes} 分钟（未设上限）"
        else "已用 ${usedMinutes} / ${dailyLimit} 分钟"
        val scheduleText = if (pref.isScheduleEnabled())
            "今天 " + pref.scheduleWindowLabel(pref.scheduleDayIndex()) +
                    if (pref.isScheduleFreeMode()) "（时段内自由）" else "（时段内答题）"
        else "未开启"
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        val msgHtml = if (msg.isNullOrEmpty()) "" else "<div class=\"msg\">${esc(msg)}</div>"
        val action = "/action?t=" + urlEncode(token)
        val body = """
            $msgHtml
            <div class="card">
              <div class="row"><span>当前状态</span><b>${esc(stateText)}</b></div>
              <div class="row"><span>使用时段</span><b>${esc(scheduleText)}</b></div>
              <div class="row"><span>今日时长</span><b>${esc(dailyText)}</b></div>
              <div class="row"><span>星星</span><b>${pref.getStars()} 颗</b></div>
            </div>
            <div class="grid">
              <a class="btn danger" href="$action&cmd=lock">立即锁定</a>
              <a class="btn" href="$action&cmd=unlock&min=15">放行 15 分钟</a>
              <a class="btn" href="$action&cmd=unlock&min=30">放行 30 分钟</a>
              <a class="btn" href="$action&cmd=unlock&min=60">放行 60 分钟</a>
              <a class="btn" href="$action&cmd=bonus&min=10">加时 10 分钟</a>
              <a class="btn" href="$action&cmd=stop">结束本轮并锁定</a>
              <a class="btn ghost" href="/?t=${urlEncode(token)}">刷新</a>
            </div>
            <p class="hint">同一 Wi-Fi 下可访问；本页链接含访问口令，请勿转发给小孩。${
            if (version.isNotEmpty()) "版本 $version" else ""
        }</p>
        """.trimIndent()
        return page("家长面板", body)
    }

    private fun page(title: String, body: String): String = """
        <!doctype html>
        <html lang="zh-CN"><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="refresh" content="20">
        <title>$title · 儿童锁屏</title>
        <style>
          :root { color-scheme: light dark; }
          * { box-sizing: border-box; }
          body { margin:0; padding:20px; font-family:-apple-system,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif; background:#f5f6f8; color:#202124; }
          h1 { font-size:20px; margin:0 0 16px; }
          .card { background:#fff; border-radius:14px; padding:6px 16px; box-shadow:0 1px 3px rgba(0,0,0,.08); margin-bottom:16px; }
          .row { display:flex; justify-content:space-between; gap:12px; padding:10px 0; border-bottom:1px solid #eee; font-size:15px; }
          .row:last-child { border-bottom:0; }
          .row span { color:#5f6368; }
          .grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
          .btn { display:flex; align-items:center; justify-content:center; min-height:52px; border-radius:12px; background:#1a73e8; color:#fff; text-decoration:none; font-size:15px; font-weight:600; }
          .btn.danger { background:#d93025; }
          .btn.ghost { background:#e8eaed; color:#202124; }
          .msg { background:#e6f4ea; color:#137333; border-radius:10px; padding:12px 14px; margin-bottom:14px; font-size:14px; }
          .hint { color:#80868b; font-size:12px; line-height:1.6; margin-top:16px; }
          @media (prefers-color-scheme: dark) {
            body { background:#17181a; color:#e8eaed; }
            .card { background:#232427; box-shadow:none; }
            .row { border-color:#333; }
            .row span { color:#9aa0a6; }
            .btn.ghost { background:#2d2e31; color:#e8eaed; }
            .msg { background:#1e3a2b; color:#81c995; }
          }
        </style></head>
        <body>
        <h1>儿童锁屏 · $title</h1>
        $body
        </body></html>
    """.trimIndent()

    private fun formatDuration(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        val out = HashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) pair.substring(0, idx) else pair
            val value = if (idx >= 0) pair.substring(idx + 1) else ""
            try {
                out[URLDecoder.decode(key, "UTF-8")] = URLDecoder.decode(value, "UTF-8")
            } catch (e: Exception) {
                out[key] = value
            }
        }
        return out
    }

    private fun urlEncode(value: String): String =
        try {
            URLEncoder.encode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }

    private fun tokenMatches(provided: String?, expected: String): Boolean {
        if (provided.isNullOrEmpty() || expected.isEmpty()) return false
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun respond(
        socket: Socket,
        status: Int,
        contentType: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            204 -> "No Content"
            303 -> "See Other"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "OK"
        }
        val headers = StringBuilder()
        headers.append("HTTP/1.1 $status $reason\r\n")
        headers.append("Content-Type: $contentType\r\n")
        headers.append("Content-Length: ${bytes.size}\r\n")
        headers.append("Cache-Control: no-store\r\n")
        headers.append("Connection: close\r\n")
        for ((key, value) in extraHeaders) headers.append("$key: $value\r\n")
        headers.append("\r\n")
        val output = socket.getOutputStream()
        output.write(headers.toString().toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }
}
