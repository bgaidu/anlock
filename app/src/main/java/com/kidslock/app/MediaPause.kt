package com.kidslock.app

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * 锁屏出现时暂停正在播放的媒体，避免「锁屏页已经盖住画面，背景视频还在出声」。
 *
 * 三级策略：
 * 1. 只在真的有声音播放时才发 MEDIA_PAUSE 键；
 * 2. 再请求音频焦点，让配合的播放器自动暂停；
 * 3. 800ms 后仍在响就静音 STREAM_MUSIC 兜底。
 *
 * 状态写入 SharedPreferences，进程被杀后重启也能恢复原来的音量/静音状态，
 * 不会出现「一次锁屏把整机声音永久静音」的事故。
 */
object MediaPause {

    private const val TAG = "MediaPause"
    private const val PREFS = "kids_media"
    private const val KEY_ACTIVE = "active"
    private const val KEY_ORIGINAL_VOLUME = "original_volume"
    private const val KEY_WAS_MUTED = "was_muted"
    private const val KEY_WE_MUTED = "we_muted"

    private val handler = Handler(Looper.getMainLooper())
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    /** 最近一次暂停时是否真的有音频在播（诊断用） */
    @Volatile
    var lastPauseHadAudio: Boolean = false
        private set

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun audio(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun pause(context: Context) {
        val app = context.applicationContext
        val audio = audio(app) ?: return
        val p = prefs(app)
        if (p.getBoolean(KEY_ACTIVE, false)) {
            enforce(app)
            return
        }
        try {
            val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val wasMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                audio.isStreamMute(AudioManager.STREAM_MUSIC)
            else volume == 0
            lastPauseHadAudio = audio.isMusicActive
            p.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putInt(KEY_ORIGINAL_VOLUME, volume)
                .putBoolean(KEY_WAS_MUTED, wasMuted)
                .putBoolean(KEY_WE_MUTED, false)
                .apply()
            if (audio.isMusicActive) sendPause(audio)
            audio.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            Log.i(TAG, "pause start volume=$volume wasMuted=$wasMuted music=${audio.isMusicActive}")
            Diag.i("Media", "pause start volume=$volume wasMuted=$wasMuted music=${audio.isMusicActive}")
        } catch (e: Exception) {
            Log.w(TAG, "pause failed", e)
            return
        }
        // 给播放器一点反应时间；仍在响就静音兜底
        handler.postDelayed({ enforce(app) }, 800)
    }

    /** 锁屏期间周期性调用：孩子按播放键/切应用重新放出声时再次压住。 */
    fun enforce(context: Context) {
        val app = context.applicationContext
        val audio = audio(app) ?: return
        val p = prefs(app)
        if (!p.getBoolean(KEY_ACTIVE, false)) return
        try {
            if (!audio.isMusicActive) return
            sendPause(audio)
            val volume = p.getInt(KEY_ORIGINAL_VOLUME, 0)
            val wasMuted = p.getBoolean(KEY_WAS_MUTED, false)
            // HDMI 固定音量 / 部分盒子读到的 volume=0 并不代表真静音，这种设备不做全局静音，
            // 否则可能把整机声音锁死且无法恢复。
            if (!wasMuted && volume > 0 && !p.getBoolean(KEY_WE_MUTED, false)) {
                setMuted(audio, true)
                p.edit().putBoolean(KEY_WE_MUTED, true).apply()
                Log.i(TAG, "music still active, muted STREAM_MUSIC")
                Diag.i("Media", "music still active after pause, muted STREAM_MUSIC")
            }
        } catch (e: Exception) {
            Log.w(TAG, "enforce failed", e)
        }
    }

    /** 解锁 / 服务销毁时恢复；幂等，可重复调用。 */
    fun release(context: Context) {
        val app = context.applicationContext
        val audio = audio(app)
        val p = prefs(app)
        if (!p.getBoolean(KEY_ACTIVE, false)) return
        handler.removeCallbacksAndMessages(null)
        try {
            if (p.getBoolean(KEY_WE_MUTED, false) && audio != null) {
                setMuted(audio, false)
            }
            audio?.abandonAudioFocus(focusListener)
        } catch (e: Exception) {
            Log.w(TAG, "release restore failed", e)
        } finally {
            p.edit().clear().apply()
            Log.i(TAG, "released at ${SystemClock.elapsedRealtime()}")
        }
    }

    private fun sendPause(audio: AudioManager) {
        try {
            val now = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
            )
            audio.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
            )
        } catch (e: Exception) {
            Log.w(TAG, "send pause key failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun setMuted(audio: AudioManager, value: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (value) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
        } else {
            audio.setStreamMute(AudioManager.STREAM_MUSIC, value)
        }
    }
}
