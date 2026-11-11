package com.kidslock.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kidslock.app.question.Question
import com.kidslock.app.question.QuestionEngine

class LockScreenActivity : AppCompatActivity() {

    companion object {
        private const val STARTING_TIMEOUT_MS = 5000L
        private var foregroundInstance: LockScreenActivity? = null

        @JvmStatic
        fun isOnTop(): Boolean {
            val a = foregroundInstance
            return a != null && !a.isFinishing && a.resumed
        }

        @JvmStatic
        fun isStarting(): Boolean {
            val a = foregroundInstance
            return a != null && !a.isFinishing && !a.resumed
                    && SystemClock.elapsedRealtime() - a.startedAtElapsed < STARTING_TIMEOUT_MS
        }
    }

    private lateinit var pref: PrefManager
    private lateinit var engine: QuestionEngine

    private lateinit var currentQuestion: Question
    private lateinit var currentOptions: List<String>
    private var correctCount = 0
    private var requiredCount = 0
    private var comboCount = 0
    private var groupCorrect = 0

    private lateinit var layoutMain: View
    private lateinit var tvCharacter: TextView
    private lateinit var tvQuestionHint: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var optionButtons: Array<Button>

    private lateinit var layoutPin: View
    private lateinit var tvPinDisplay: TextView
    private val pinInput = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())

    private var resumed = false
    private var answering = false
    private var unlocking = false
    private var pendingNext: Runnable? = null
    private val startedAtElapsed = SystemClock.elapsedRealtime()

    /** 时段恢复等场景下服务会自动解锁，锁屏页轮询到后自行退出。 */
    private val unlockWatch = object : Runnable {
        override fun run() {
            if (!pref.isLocked()) {
                KioskManager.exitLockTask(this@LockScreenActivity)
                finishAffinity()
                return
            }
            handler.postDelayed(this, 5000)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        foregroundInstance = this
        setContentView(R.layout.activity_lock_screen)

        val serviceIntent = Intent(this, LockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        pref = PrefManager(this)
        engine = QuestionEngine.create(this, pref)
        requiredCount = pref.unlockCount
        KioskManager.applyOwnerPolicies(this)
        LockOverlayManager.hideOverlay(this)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        hideSystemUI()

        initViews()
        loadNextQuestion()
        showScheduleReasonIfNeeded()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutPin.visibility == View.VISIBLE) {
                    layoutPin.visibility = View.GONE
                    layoutMain.visibility = View.VISIBLE
                    pinInput.setLength(0)
                    updatePinDisplay()
                }
            }
        })
    }

    private fun initViews() {
        layoutMain = findViewById(R.id.layoutMain)
        tvCharacter = findViewById(R.id.tvCharacter)
        tvQuestionHint = findViewById(R.id.tvQuestionHint)
        tvProgress = findViewById(R.id.tvProgress)
        tvFeedback = findViewById(R.id.tvFeedback)

        optionButtons = arrayOf(
            findViewById(R.id.btnOption1),
            findViewById(R.id.btnOption2),
            findViewById(R.id.btnOption3),
            findViewById(R.id.btnOption4)
        )
        for (i in optionButtons.indices) {
            val idx = i
            optionButtons[i].setOnClickListener { onOptionSelected(idx) }
        }

        findViewById<View>(R.id.btnParentUnlock).setOnClickListener { showPinPad() }

        layoutPin = findViewById(R.id.layoutPin)
        tvPinDisplay = findViewById(R.id.tvPinDisplay)

        val pinButtonIds = intArrayOf(
            R.id.btnPin0, R.id.btnPin1, R.id.btnPin2, R.id.btnPin3,
            R.id.btnPin4, R.id.btnPin5, R.id.btnPin6, R.id.btnPin7,
            R.id.btnPin8, R.id.btnPin9
        )
        for (i in 0..9) {
            val digit = i
            findViewById<View>(pinButtonIds[i]).setOnClickListener { onPinDigit(digit) }
        }
        findViewById<View>(R.id.btnPinClear).setOnClickListener {
            pinInput.setLength(0)
            updatePinDisplay()
        }
        findViewById<View>(R.id.btnPinBack).setOnClickListener {
            layoutPin.visibility = View.GONE
            layoutMain.visibility = View.VISIBLE
            pinInput.setLength(0)
            updatePinDisplay()
            findViewById<View>(R.id.btnOption1).post { findViewById<View>(R.id.btnOption1).requestFocus() }
        }
    }

    /** 休息时段内锁屏：直接说明原因，而不是让孩子白答一堆题。 */
    private fun showScheduleReasonIfNeeded() {
        if (!pref.isScheduleEnabled() || pref.isWithinSchedule()) return
        tvFeedback.text = "现在是休息时段（${pref.scheduleWindowLabel(pref.scheduleDayIndex())}），" +
                "先休息一下吧。家长可输入 PIN 临时使用。"
        tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
        tvFeedback.visibility = View.VISIBLE
    }

    private fun loadNextQuestion() {
        answering = false
        unlocking = false
        pendingNext = null
        currentQuestion = engine.nextQuestion()
        currentOptions = currentQuestion.options

        tvCharacter.text = currentQuestion.stem
        tvQuestionHint.text = currentQuestion.hint

        val textSize = resources.getDimension(
            if (currentQuestion.stemStyle == Question.StemStyle.HANZI) R.dimen.text_hanzi else R.dimen.text_stem
        )
        tvCharacter.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)

        for (i in optionButtons.indices) {
            if (i < currentOptions.size) {
                optionButtons[i].text = currentOptions[i]
                optionButtons[i].setBackgroundResource(R.drawable.btn_option_bg)
                optionButtons[i].isEnabled = true
            }
        }

        updateProgress()
        tvFeedback.visibility = View.GONE
    }

    private fun onOptionSelected(index: Int) {
        if (unlocking) return
        if (answering) {
            pendingNext?.let { handler.removeCallbacks(it) }
            loadNextQuestion()
            return
        }
        answering = true

        pref.recordAnswer(index == currentQuestion.correctIndex)
        engine.recordResult(currentQuestion, index == currentQuestion.correctIndex)

        if (index == currentQuestion.correctIndex) {
            correctCount++
            comboCount++
            groupCorrect++
            pref.recordCombo(comboCount)
            pref.addStars(1)
            val groupBonus = groupCorrect >= 5
            if (groupBonus) {
                pref.addStars(1)
                groupCorrect = 0
            }
            updateProgress()
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_correct)
            tvFeedback.text = if (groupBonus)
                "答对了！本组 5 题全对，奖励 2 颗星 ⭐⭐"
            else
                "答对了！+1 颗星 ⭐ · 已连对 $comboCount 题"
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
            tvFeedback.visibility = View.VISIBLE

            if (correctCount >= requiredCount) {
                unlocking = true
                handler.postDelayed({ unlock() }, 600)
            } else {
                scheduleNext(1000)
            }
        } else {
            correctCount = 0
            comboCount = 0
            groupCorrect = 0
            updateProgress()
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_wrong)
            for (i in currentOptions.indices) {
                if (i == currentQuestion.correctIndex) {
                    optionButtons[i].setBackgroundResource(R.drawable.btn_option_correct)
                    break
                }
            }
            val correctText = currentOptions.getOrNull(currentQuestion.correctIndex) ?: ""
            tvFeedback.text = "答错了，正确答案：$correctText · 已加入错题池，下次优先复习"
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
            tvFeedback.visibility = View.VISIBLE
            scheduleNext(2200)
        }
    }

    private fun scheduleNext(delayMillis: Long) {
        val runnable = Runnable { loadNextQuestion() }
        pendingNext = runnable
        handler.postDelayed(runnable, delayMillis)
    }

    private fun updateProgress() {
        val remaining = (requiredCount - correctCount).coerceAtLeast(0)
        val starText = " · ⭐ ${pref.getStars()}"
        tvProgress.text = if (remaining == 0) {
            String.format("已答对 %d/%d 道题，即将解锁…", correctCount, requiredCount) + starText
        } else {
            String.format("已答对 %d 题，还剩 %d 题即可解锁", correctCount, remaining) + starText
        }
    }

    private fun unlock(byParent: Boolean = false) {
        if (byParent) {
            // 家长 PIN 放行：不受今日额度限制，给一次完整的观看时长
            pref.grantDailyBypass(pref.getWatchLimitMinutes())
            // 家长也要能在休息时段临时使用（覆盖本次观看时长）
            pref.grantScheduleBypass(pref.getWatchLimitMinutes() * 60_000L)
        } else if (!pref.isWithinSchedule()) {
            // 休息时段不允许用答题解锁，只能家长 PIN 放行
            tvFeedback.text = "现在是休息时段（${pref.scheduleWindowLabel(pref.scheduleDayIndex())}），" +
                    "答题不能解锁，请家长输入 PIN"
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
            tvFeedback.visibility = View.VISIBLE
            focusUnlockButton()
            return
        }
        val minutes = pref.effectiveUnlockMinutes()
        if (minutes <= 0) {
            // 今日额度已用完：答对题也不再放行，避免「无限刷题换时长」
            tvFeedback.text = "今天的时间已经用完，明天再来吧"
            tvFeedback.visibility = View.VISIBLE
            focusUnlockButton()
            return
        }
        pref.setLocked(false)
        pref.setHomeAliasEnabled(this, false)
        KioskManager.exitLockTask(this)
        pref.consumeBonusMinutes()
        pref.startTimer(minutes)
        pref.resetPinFailures()
        finishAffinity()
        LockService.requestUnlock(this)
    }

    private fun showPinPad() {
        layoutMain.visibility = View.GONE
        layoutPin.visibility = View.VISIBLE
        pinInput.setLength(0)
        updatePinDisplay()
        findViewById<View>(R.id.btnPin1).post { findViewById<View>(R.id.btnPin1).requestFocus() }
    }

    /**
     * TV 遥控器支持：打开锁屏时自动聚焦第一个选项按钮
     */
    private fun focusUnlockButton() {
        findViewById<View>(R.id.btnOption1).post {
            findViewById<View>(R.id.btnOption1).requestFocus()
        }
    }

    private fun onPinDigit(digit: Int) {
        if (pinInput.length < 4) {
            pinInput.append(digit)
            updatePinDisplay()
            if (pinInput.length == 4) {
                handler.postDelayed({ checkPin() }, 200)
            }
        }
    }

    private fun updatePinDisplay() {
        val sb = StringBuilder()
        for (i in 0 until 4) {
            if (i < pinInput.length) {
                sb.append("●")
            } else {
                sb.append("○")
            }
            if (i < 3) sb.append("  ")
        }
        tvPinDisplay.text = sb.toString()
    }

    private fun checkPin() {
        if (pref.isPinLocked()) {
            val remainingSec = pref.getPinLockRemainingMillis() / 1000
            Toast.makeText(this, "尝试次数过多，请 $remainingSec 秒后再试", Toast.LENGTH_SHORT).show()
            pinInput.setLength(0)
            updatePinDisplay()
            return
        }
        if (pref.verifyPin(pinInput.toString())) {
            pref.resetPinFailures()
            unlock(byParent = true)
        } else {
            pref.recordPinFailure()
            Toast.makeText(this, "PIN码错误", Toast.LENGTH_SHORT).show()
            pinInput.setLength(0)
            updatePinDisplay()
        }
    }

    @Suppress("DEPRECATION")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        handler.removeCallbacks(unlockWatch)
        // 锁屏退到后台时立刻用 overlay 补位，避免出现 20:34 那种"锁屏已暂停、桌面/游戏裸奔"的空窗
        if (pref.isLocked()) {
            LockOverlayManager.holdOverlay(this)
        }
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        resumed = true
        foregroundInstance = this
        if (!pref.isLocked()) {
            // 已经被服务自动解锁（例如使用时段恢复），直接退出
            KioskManager.exitLockTask(this)
            finishAffinity()
            return
        }
        KioskManager.enterLockTask(this)
        MediaPause.pause(this)
        LockOverlayManager.hideOverlay(this)
        hideSystemUI()
        focusUnlockButton()
        handler.removeCallbacks(unlockWatch)
        handler.postDelayed(unlockWatch, 5000)

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                hideSystemUI()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
            // 兜底：onResume 时若 startLockTask 尚未生效，拿到焦点后重试一次
            if (pref.isLocked()) {
                KioskManager.enterLockTask(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (foregroundInstance == this) foregroundInstance = null
        // 正常解锁（pref 已置 false）时确保退出 kiosk；被系统回收时保持锁定状态
        if (isFinishing && !pref.isLocked()) {
            KioskManager.exitLockTask(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        val decorView = window.decorView
        val uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        decorView.systemUiVisibility = uiOptions

        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }
}
