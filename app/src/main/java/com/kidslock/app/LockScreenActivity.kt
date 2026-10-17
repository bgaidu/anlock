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
    private val startedAtElapsed = SystemClock.elapsedRealtime()

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

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        hideSystemUI()

        initViews()
        loadNextQuestion()
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

    private fun loadNextQuestion() {
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
        for (b in optionButtons) {
            b.isEnabled = false
        }

        pref.recordAnswer(index == currentQuestion.correctIndex)

        if (index == currentQuestion.correctIndex) {
            correctCount++
            updateProgress()
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_correct)
            tvFeedback.text = "答对了！"
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
            tvFeedback.visibility = View.VISIBLE

            if (correctCount >= requiredCount) {
                handler.postDelayed({ unlock() }, 600)
            } else {
                handler.postDelayed({ loadNextQuestion() }, 1000)
            }
        } else {
            correctCount = 0
            updateProgress()
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_wrong)
            for (i in currentOptions.indices) {
                if (i == currentQuestion.correctIndex) {
                    optionButtons[i].setBackgroundResource(R.drawable.btn_option_correct)
                    break
                }
            }
            tvFeedback.text = "答错了，重新开始！"
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
            tvFeedback.visibility = View.VISIBLE
            handler.postDelayed({ loadNextQuestion() }, 1500)
        }
    }

    private fun updateProgress() {
        val remaining = (requiredCount - correctCount).coerceAtLeast(0)
        tvProgress.text = if (remaining == 0) {
            String.format("已答对 %d/%d 道题，即将解锁…", correctCount, requiredCount)
        } else {
            String.format("已答对 %d 题，还剩 %d 题即可解锁", correctCount, remaining)
        }
    }

    private fun unlock() {
        pref.setLocked(false)
        pref.setHomeAliasEnabled(this, false)
        val minutes = pref.getWatchLimitMinutes()
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
            unlock()
        } else {
            pref.recordPinFailure()
            Toast.makeText(this, "PIN码错误", Toast.LENGTH_SHORT).show()
            pinInput.setLength(0)
            updatePinDisplay()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (layoutPin.visibility == View.VISIBLE) {
            layoutPin.visibility = View.GONE
            layoutMain.visibility = View.VISIBLE
            pinInput.setLength(0)
            updatePinDisplay()
        }
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        foregroundInstance = this
        hideSystemUI()
        focusUnlockButton()

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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (foregroundInstance == this) foregroundInstance = null
    }

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
