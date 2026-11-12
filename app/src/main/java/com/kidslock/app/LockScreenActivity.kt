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
import com.kidslock.app.question.AnswerMatcher
import com.kidslock.app.question.Question
import com.kidslock.app.question.QuestionEngine

class LockScreenActivity : AppCompatActivity() {

    companion object {
        /**
         * 仅供首次配置向导的「试锁」使用：大于 0 时进入测试模式，
         * 不显示题目、不消耗星星，倒计时结束自动退出（锁定状态由向导负责清除）。
         */
        const val EXTRA_TEST_SECONDS = "extra_test_seconds"

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
    /** 答对累计、答错保留进度（详情见 [AnswerProgress]）。 */
    private var progress = AnswerProgress(required = 0)
    private var comboCount = 0
    private var groupCorrect = 0

    private lateinit var layoutMain: View
    private lateinit var layoutOptions1: View
    private lateinit var layoutOptions2: View
    private lateinit var layoutFill: View
    private lateinit var tvFillInput: TextView
    private lateinit var tvCharacter: TextView
    private lateinit var tvQuestionHint: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvQuota: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var optionButtons: Array<Button>
    private val fillBuffer = StringBuilder()

    private lateinit var layoutPin: View
    private lateinit var tvPinDisplay: TextView
    private val pinInput = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())

    private var resumed = false
    private var answering = false
    private var unlocking = false
    private var pendingNext: Runnable? = null
    private val startedAtElapsed = SystemClock.elapsedRealtime()

    /** 试锁模式剩余秒数，0 表示正常答题锁屏。 */
    private var testRemaining = 0

    private val testTick = object : Runnable {
        override fun run() {
            testRemaining--
            if (testRemaining <= 0) {
                exitTestLock()
                return
            }
            tvProgress.text = "试锁测试中，剩余 ${testRemaining} 秒"
            handler.postDelayed(this, 1000)
        }
    }

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
        progress = AnswerProgress(required = pref.unlockCount)
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
        if (intent.getIntExtra(EXTRA_TEST_SECONDS, 0) > 0) {
            startTestLock(intent.getIntExtra(EXTRA_TEST_SECONDS, 0))
        } else {
            loadNextQuestion()
            showScheduleReasonIfNeeded()
        }

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
        layoutOptions1 = findViewById(R.id.layoutOptions1)
        layoutOptions2 = findViewById(R.id.layoutOptions2)
        layoutFill = findViewById(R.id.layoutFill)
        tvFillInput = findViewById(R.id.tvFillInput)
        tvCharacter = findViewById(R.id.tvCharacter)
        tvQuestionHint = findViewById(R.id.tvQuestionHint)
        tvProgress = findViewById(R.id.tvProgress)
        tvQuota = findViewById(R.id.tvQuota)
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

        initFillKeypad()

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

    /** 填空题的数字键盘：0-9、小数点、分数线、退格、确认。 */
    private fun initFillKeypad() {
        val digitIds = intArrayOf(
            R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4,
            R.id.btnKey5, R.id.btnKey6, R.id.btnKey7, R.id.btnKey8, R.id.btnKey9
        )
        for (i in digitIds.indices) {
            findViewById<View>(digitIds[i]).setOnClickListener { onFillChar(i.toString()) }
        }
        findViewById<View>(R.id.btnKeyDot).setOnClickListener { onFillChar(".") }
        findViewById<View>(R.id.btnKeySlash).setOnClickListener { onFillChar("/") }
        findViewById<View>(R.id.btnKeyBack).setOnClickListener { onFillBack() }
        findViewById<View>(R.id.btnKeyOk).setOnClickListener { submitFill() }
    }

    /** 休息时段内锁屏：直接说明原因，而不是让孩子白答一堆题。 */
    private fun showScheduleReasonIfNeeded() {
        if (!pref.isScheduleEnabled() || pref.isWithinSchedule()) return
        tvFeedback.text = "现在是休息时段（${pref.scheduleWindowLabel(pref.scheduleDayIndex())}），" +
                "先休息一下吧。家长可输入 PIN 临时使用。"
        tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
        tvFeedback.visibility = View.VISIBLE
    }

    /**
     * 试锁模式：只展示锁屏外观与倒计时，不出题、不记成绩，
     * 让家长确认「锁屏是否真的盖住了桌面」。
     */
    private fun startTestLock(seconds: Int) {
        testRemaining = seconds
        layoutOptions1.visibility = View.GONE
        layoutOptions2.visibility = View.GONE
        layoutFill.visibility = View.GONE
        findViewById<View>(R.id.btnParentUnlock).visibility = View.GONE

        tvCharacter.text = "试锁中"
        tvQuestionHint.text = "这是测试锁屏，不会记录成绩"
        tvProgress.text = "试锁测试中，剩余 ${testRemaining} 秒"
        tvQuota.visibility = View.GONE
        tvFeedback.text = "如果此刻仍能回到桌面，说明悬浮窗/锁屏没有生效，请检查权限与后台限制"
        tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvFeedback.visibility = View.VISIBLE
        Diag.i("Lock", "test lock UI shown for ${seconds}s")
        handler.postDelayed(testTick, 1000)
    }

    private fun exitTestLock() {
        handler.removeCallbacks(testTick)
        KioskManager.exitLockTask(this)
        // 不在这里解锁：锁定状态由向导的定时兜底统一清除，
        // 避免测试刚结束、向导还没反应时桌面短暂可用。
        finish()
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

        if (currentQuestion.fillBlank) {
            showFillMode()
        } else {
            showChoiceMode()
        }

        updateProgress()
        tvFeedback.visibility = View.GONE
    }

    private fun showChoiceMode() {
        layoutFill.visibility = View.GONE
        layoutOptions1.visibility = View.VISIBLE
        layoutOptions2.visibility = View.VISIBLE
        findViewById<View>(R.id.btnParentUnlock).nextFocusUpId = R.id.btnOption3
        for (i in optionButtons.indices) {
            if (i < currentOptions.size) {
                optionButtons[i].text = currentOptions[i]
                optionButtons[i].setBackgroundResource(R.drawable.btn_option_bg)
                optionButtons[i].isEnabled = true
            }
        }
    }

    private fun showFillMode() {
        layoutOptions1.visibility = View.GONE
        layoutOptions2.visibility = View.GONE
        layoutFill.visibility = View.VISIBLE
        findViewById<View>(R.id.btnParentUnlock).nextFocusUpId = R.id.btnKeyOk
        fillBuffer.setLength(0)
        updateFillDisplay()
        findViewById<View>(R.id.btnKey7).post { findViewById<View>(R.id.btnKey7).requestFocus() }
    }

    private fun updateFillDisplay() {
        tvFillInput.text = if (fillBuffer.isEmpty()) "—" else fillBuffer.toString()
    }

    private fun onFillChar(text: String) {
        if (answering || unlocking) return
        if (fillBuffer.length >= 8) return
        // 小数点和分数线各只允许一个，避免出现 "1.2.3"
        if (text == "." && fillBuffer.contains('.')) return
        if (text == "/" && fillBuffer.contains('/')) return
        fillBuffer.append(text)
        updateFillDisplay()
    }

    private fun onFillBack() {
        if (answering || unlocking) return
        if (fillBuffer.isNotEmpty()) {
            fillBuffer.setLength(fillBuffer.length - 1)
            updateFillDisplay()
        }
    }

    private fun submitFill() {
        if (answering || unlocking || fillBuffer.isEmpty()) return
        answering = true
        val expected = currentOptions.getOrNull(currentQuestion.correctIndex) ?: ""
        val correct = AnswerMatcher.matches(fillBuffer.toString(), expected)
        applyAnswer(correct)
    }

    private fun onOptionSelected(index: Int) {
        if (unlocking) return
        if (answering) {
            pendingNext?.let { handler.removeCallbacks(it) }
            loadNextQuestion()
            return
        }
        answering = true

        val correct = index == currentQuestion.correctIndex
        if (correct) {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_correct)
        } else {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_wrong)
            for (i in currentOptions.indices) {
                if (i == currentQuestion.correctIndex) {
                    optionButtons[i].setBackgroundResource(R.drawable.btn_option_correct)
                    break
                }
            }
        }
        applyAnswer(correct)
    }

    /** 选择题 / 填空题共用的判分与奖励逻辑。 */
    private fun applyAnswer(correct: Boolean) {
        pref.recordAnswer(correct)
        pref.recordStudyAnswer(currentQuestion.subject, correct)
        if (currentQuestion.isReview) {
            pref.recordReviewAnswer(currentQuestion.subject, correct)
        }
        engine.recordResult(currentQuestion, correct)

        if (correct) {
            comboCount++
            groupCorrect++
            pref.recordCombo(comboCount)
            pref.addStars(1)
            val groupBonus = groupCorrect >= 5
            if (groupBonus) {
                pref.addStars(1)
                groupCorrect = 0
            }
            progress = progress.onCorrect()
            updateProgress()
            showFeedback(
                if (groupBonus) "答对了！本组 5 题全对，奖励 2 颗星 ${Glyphs.star}${Glyphs.star}"
                else "答对了！+1 颗星 ${Glyphs.star} · 已连对 $comboCount 题",
                R.color.correct_green
            )
            if (progress.unlocked) {
                unlocking = true
                handler.postDelayed({ unlock() }, 600)
            } else {
                scheduleNext(1000)
            }
        } else {
            // 答错不清零：保留已答对进度，换一道新题继续，星星扣到 0 为止
            val lostStar = pref.removeStars(1)
            comboCount = 0
            groupCorrect = 0
            progress = progress.onWrong()
            updateProgress()
            val correctText = currentOptions.getOrNull(currentQuestion.correctIndex) ?: ""
            val starText = if (lostStar > 0) "扣 1 颗星 ${Glyphs.star}" else "星星已扣完"
            showFeedback(
                "答错了，正确答案：$correctText · $starText，换一道新题继续",
                R.color.wrong_red
            )
            scheduleNext(2200)
        }
    }

    private fun showFeedback(text: String, colorRes: Int) {
        tvFeedback.text = text
        tvFeedback.setTextColor(ContextCompat.getColor(this, colorRes))
        tvFeedback.visibility = View.VISIBLE
    }

    private fun scheduleNext(delayMillis: Long) {
        val runnable = Runnable { loadNextQuestion() }
        pendingNext = runnable
        handler.postDelayed(runnable, delayMillis)
    }

    private fun updateProgress() {
        val starText = " · ${Glyphs.star} ${pref.getStars()}"
        tvProgress.text = if (progress.unlocked) {
            String.format("已答对 %d/%d 道题，即将解锁…", progress.correct, progress.required) + starText
        } else {
            String.format("已答对 %d 题，还剩 %d 题即可解锁", progress.correct, progress.remaining) + starText
        }
        tvQuota.text = dailyQuotaText()
        tvQuota.visibility = View.VISIBLE
    }

    /** 锁屏底部展示今日额度，让孩子知道「还有多久」而不是盲目答题。 */
    private fun dailyQuotaText(): String {
        if (pref.isDailyBypassActive()) return "今日已由家长临时放行，不占额度"
        val limit = pref.getDailyLimitMinutes()
        if (limit <= 0) return "今日不限时"
        val remaining = pref.getDailyRemainingMs()
        if (remaining <= 0) return "今日额度已用完，明天再来吧"
        val minutes = ((remaining + 59_999) / 60_000).coerceAtLeast(1)
        return if (minutes < 60) {
            "今日剩余 $minutes 分钟"
        } else {
            "今日剩余 ${minutes / 60} 小时 ${minutes % 60} 分钟"
        }
    }

    private fun unlock(byParent: Boolean = false) {
        if (byParent) {
            // 家长 PIN 放行：不受今日额度限制，给一次完整的观看时长
            pref.grantDailyBypass(pref.getWatchLimitMinutes())
            // 家长也要能在休息时段临时使用（覆盖本次观看时长）
            pref.grantScheduleBypass(pref.getWatchLimitMinutes() * 60_000L)
            AuditLog.record(
                this,
                "锁屏",
                "家长 PIN 放行",
                "本次 ${pref.getWatchLimitMinutes()} 分钟"
            )
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
        val target = if (::currentQuestion.isInitialized && currentQuestion.fillBlank) {
            R.id.btnKey7
        } else {
            R.id.btnOption1
        }
        findViewById<View>(target).post { findViewById<View>(target).requestFocus() }
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
        if (testRemaining == 0) focusUnlockButton()
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
