package com.kidslock.app

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kidslock.app.question.BankRepository
import com.kidslock.app.question.CustomBankStore
import com.kidslock.app.question.Grade
import com.kidslock.app.question.QuestionEngine
import com.kidslock.app.question.ReviewStore
import com.kidslock.app.question.Subject
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_POST_NOTIFICATIONS = 1001

        /** 周报通知点开时带上，进入设置页后直接弹出「近 7 天报告」。 */
        const val EXTRA_SHOW_WEEKLY_REPORT = "show_weekly_report"
        private val IMPORTABLE_SUBJECTS = arrayOf(Subject.PINYIN, Subject.ENGLISH, Subject.CITU)

        /** 设置页是否在前台：LockService 用它决定是否显示剩余时间角标 */
        @Volatile
        var isVisible: Boolean = false
            private set
    }

    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private var pendingImportSubject: String? = null
    private var pendingImportGrade = -1
    private var pendingImportSettings = false
    private var pendingExportSettings = false
    private var pendingExportSubject: String? = null
    private var pendingExportGrade = -1

    private lateinit var pref: PrefManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvStatus: TextView
    private lateinit var tvRemaining: TextView
    private lateinit var btnAutoStart: Button
    private lateinit var btnEmergencyReboot: Button
    private lateinit var btnShowCountdown: Button
    private lateinit var tvWatchTime: TextView
    private lateinit var tvDailyUsage: TextView
    private lateinit var btnDailyLimit: Button
    private lateinit var btnUsageHistory: Button
    private lateinit var btnWeeklyReport: Button
    private lateinit var btnLanPanel: Button
    private lateinit var btnQuestionTypes: Button
    private lateinit var btnExportBank: Button
    private lateinit var tvSchedule: TextView
    private lateinit var btnSchedule: Button
    private lateinit var btnStarExchange: Button
    private lateinit var tvUnlockCount: TextView
    private lateinit var btnGuardStatus: Button
    private lateinit var btnPermissions: Button
    private lateinit var btnHealth: Button
    private lateinit var btnDiag: Button
    private var permStatusDirty = true
    private var permMissingCount = 0
    private lateinit var tvQuestionGrade: TextView
    private lateinit var tvQuestionSubjects: TextView
    private lateinit var tvSubjectWarning: TextView
    private lateinit var tvAccuracy: TextView
    private lateinit var btnResetStats: Button
    private lateinit var tvPinStatus: TextView
    private val btnGrades = arrayOfNulls<Button>(7)
    private lateinit var btnSubjectPinyin: Button
    private lateinit var btnSubjectMath: Button
    private lateinit var btnSubjectEnglish: Button
    private lateinit var btnSubjectCitu: Button

    private lateinit var layoutSettings: View
    private lateinit var layoutGate: View
    private lateinit var tvGatePinDisplay: TextView
    private val gatePinInput = StringBuilder()
    private var gateVerified = false
    private var pendingWeeklyReport = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = PrefManager(this)
        // 进程被强停/被杀后重新打开时，把缺失的时间一次性补扣掉
        pref.deductOfflineIfAny()
        KioskManager.applyOwnerPolicies(this)

        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        importLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> onBankFilePicked(uri) }

        exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> onExportFilePicked(uri) }

        if (pref.isLocked()) {
            val lockIntent = Intent(this, LockScreenActivity::class.java)
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(lockIntent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        requestNotificationPermissionIfNeeded()
        initViews()
        setupListeners()
        updateStatus()
        applyGate()

        if (pref.isScheduleEnabled() && !pref.isTimerActive()) {
            // 兜底：使用时段守护可能因进程被杀而缺席，进入设置页时顺手拉起来
            startLockService()
        }

        // 每次进设置页都重排一次周报闹钟（幂等；开关关闭时会自动取消）
        WeeklyReportAlarm.schedule(this)
        handleWeeklyReportIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWeeklyReportIntent(intent)
    }

    /**
     * 从周报通知点进来：设置页已经通过家长 PIN 验证就直接弹报告，
     * 否则先记住，等 PIN 验证通过再弹（避免孩子从通知直接看到使用数据）。
     */
    private fun handleWeeklyReportIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_WEEKLY_REPORT, false) != true) return
        intent.removeExtra(EXTRA_SHOW_WEEKLY_REPORT)
        if (!gateVerified) {
            pendingWeeklyReport = true
            toast("输入家长 PIN 后查看本周报告")
            return
        }
        window.decorView.post { showWeeklyReportDialog() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_POST_NOTIFICATIONS
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        permStatusDirty = true
        pref = PrefManager(this)
        KioskManager.applyOwnerPolicies(this)
        if (pref.isLocked()) {
            val lockIntent = Intent(this, LockScreenActivity::class.java)
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(lockIntent)
            finish()
            return
        }
        if (pref.isTimerActive()) {
            startLockService()
            Revival.schedule(this)
        }
        if (!RecoveryAccessibilityService.isServiceEnabled(this)) {
            RecoveryAccessibilityService.trySelfEnable(this)
        }
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        applyGate()
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
        handler.removeCallbacks(updateRunnable)
        gateVerified = false
    }

    @Suppress("DEPRECATION")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        // 显示当前版本号，方便确认设备上装的是哪个版本
        findViewById<TextView>(R.id.tvVersion).text = try {
            "版本 v" + packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "" }
        tvRemaining = findViewById(R.id.tvRemaining)
        btnAutoStart = findViewById(R.id.btnAutoStart)
        btnEmergencyReboot = findViewById(R.id.btnEmergencyReboot)
        btnShowCountdown = findViewById(R.id.btnShowCountdown)
        tvWatchTime = findViewById(R.id.tvWatchTime)
        tvDailyUsage = findViewById(R.id.tvDailyUsage)
        tvDailyUsage.setOnClickListener {
            if (pref.wasClockRolledBack()) {
                pref.clearClockRollback()
                updateStatus()
                toast("已确认：今日额度保持不清零")
            }
        }
        btnDailyLimit = findViewById(R.id.btnDailyLimit)
        btnDailyLimit.setOnClickListener { showDailyLimitDialog() }
        btnUsageHistory = findViewById(R.id.btnUsageHistory)
        btnUsageHistory.setOnClickListener { showUsageHistoryDialog() }
        btnWeeklyReport = findViewById(R.id.btnWeeklyReport)
        btnWeeklyReport.setOnClickListener { showWeeklyReportDialog() }
        btnLanPanel = findViewById(R.id.btnLanPanel)
        btnLanPanel.setOnClickListener { showLanPanelDialog() }
        btnExportBank = findViewById(R.id.btnExportBank)
        btnExportBank.setOnClickListener { showExportBankDialog() }
        tvSchedule = findViewById(R.id.tvSchedule)
        btnSchedule = findViewById(R.id.btnSchedule)
        btnSchedule.setOnClickListener { showScheduleDialog() }
        btnStarExchange = findViewById(R.id.btnStarExchange)
        btnStarExchange.setOnClickListener { showStarExchangeDialog() }
        tvUnlockCount = findViewById(R.id.tvUnlockCount)
        btnGuardStatus = findViewById(R.id.btnGuardStatus)
        btnDiag = findViewById(R.id.btnDiag)
        tvQuestionGrade = findViewById(R.id.tvQuestionGrade)
        tvQuestionSubjects = findViewById(R.id.tvQuestionSubjects)
        tvSubjectWarning = findViewById(R.id.tvSubjectWarning)
        tvAccuracy = findViewById(R.id.tvAccuracy)
        btnResetStats = findViewById(R.id.btnResetStats)
        btnResetStats.setOnClickListener {
            pref.resetStats()
            updateStatus()
            toast(getString(R.string.toast_reset_stats))
        }
        tvPinStatus = findViewById(R.id.tvPinStatus)
        btnGrades[0] = findViewById(R.id.btnGrade0)
        btnGrades[1] = findViewById(R.id.btnGrade1)
        btnGrades[2] = findViewById(R.id.btnGrade2)
        btnGrades[3] = findViewById(R.id.btnGrade3)
        btnGrades[4] = findViewById(R.id.btnGrade4)
        btnGrades[5] = findViewById(R.id.btnGrade5)
        btnGrades[6] = findViewById(R.id.btnGrade6)
        btnSubjectPinyin = findViewById(R.id.btnSubjectPinyin)
        btnSubjectMath = findViewById(R.id.btnSubjectMath)
        btnSubjectEnglish = findViewById(R.id.btnSubjectEnglish)
        btnSubjectCitu = findViewById(R.id.btnSubjectCitu)
        btnQuestionTypes = findViewById(R.id.btnQuestionTypes)
        btnQuestionTypes.setOnClickListener { showQuestionTypesDialog() }

        btnPermissions = findViewById(R.id.btnPermissions)
        btnHealth = findViewById(R.id.btnHealth)
        btnHealth.setOnClickListener { showHealthDialog() }
        btnPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionActivity::class.java))
        }
        btnDiag.setOnClickListener { showDiagDialog() }

        btnGuardStatus.setOnClickListener {
            if (RecoveryAccessibilityService.isServiceEnabled(this)) {
                toast("守护服务已开启")
                return@setOnClickListener
            }
            if (RecoveryAccessibilityService.trySelfEnable(this)) {
                toast("守护服务已自动开启")
                updateStatus()
                return@setOnClickListener
            }
            try {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                toast("请在列表中找到「儿童锁屏」并开启")
            } catch (e: ActivityNotFoundException) {
                toast("请到系统设置的无障碍/辅助功能中开启「儿童锁屏」")
            }
        }

        layoutSettings = findViewById(R.id.settingsScroll)
        layoutGate = findViewById(R.id.layoutGate)
        tvGatePinDisplay = findViewById(R.id.tvPinDisplay)

        val gatePinButtonIds = intArrayOf(
            R.id.btnPin0, R.id.btnPin1, R.id.btnPin2, R.id.btnPin3,
            R.id.btnPin4, R.id.btnPin5, R.id.btnPin6, R.id.btnPin7,
            R.id.btnPin8, R.id.btnPin9
        )
        for (i in 0..9) {
            val digit = i
            findViewById<View>(gatePinButtonIds[i]).setOnClickListener { onGatePinDigit(digit) }
        }
        findViewById<View>(R.id.btnPinClear).setOnClickListener {
            gatePinInput.setLength(0)
            updateGatePinDisplay()
        }
        findViewById<View>(R.id.btnPinBack).setOnClickListener {
            gatePinInput.setLength(0)
            updateGatePinDisplay()
        }
    }

    private fun applyGate() {
        gatePinInput.setLength(0)
        updateGatePinDisplay()
        if (gateVerified) {
            layoutGate.visibility = View.GONE
            layoutSettings.visibility = View.VISIBLE
            findViewById<View>(R.id.btnStartTimer).post { findViewById<View>(R.id.btnStartTimer).requestFocus() }
        } else {
            layoutSettings.visibility = View.GONE
            layoutGate.visibility = View.VISIBLE
            findViewById<View>(R.id.btnPin1).post { findViewById<View>(R.id.btnPin1).requestFocus() }
        }
    }

    private fun onGatePinDigit(digit: Int) {
        if (gatePinInput.length < 4) {
            gatePinInput.append(digit)
            updateGatePinDisplay()
            if (gatePinInput.length == 4) {
                handler.postDelayed({ checkGatePin() }, 200)
            }
        }
    }

    private fun checkGatePin() {
        if (pref.isPinLocked()) {
            val remainingSec = pref.getPinLockRemainingMillis() / 1000
            toast("尝试次数过多，请 $remainingSec 秒后再试")
            gatePinInput.setLength(0)
            updateGatePinDisplay()
            return
        }
        if (pref.verifyPin(gatePinInput.toString())) {
            pref.resetPinFailures()
            // 家长验证通过：2 分钟内允许进入系统设置（调整权限/网络等），避免被自己的管控拦住
            pref.allowSettingsTemporarily()
            gateVerified = true
            applyGate()
            if (pendingWeeklyReport) {
                pendingWeeklyReport = false
                handler.post { showWeeklyReportDialog() }
            }
            if (pref.shouldForcePinChange()) {
                promptPinChange(true)
            }
        } else {
            pref.recordPinFailure()
            toast("PIN码错误")
            gatePinInput.setLength(0)
            updateGatePinDisplay()
        }
    }

    private fun updateGatePinDisplay() {
        val sb = StringBuilder()
        for (i in 0 until 4) {
            if (i < gatePinInput.length) {
                sb.append("●")
            } else {
                sb.append("○")
            }
            if (i < 3) sb.append("  ")
        }
        tvGatePinDisplay.text = sb.toString()
    }

    private fun setupListeners() {
        btnAutoStart.setOnClickListener {
            val newVal = !pref.isAutoStart()
            pref.setAutoStart(newVal)
            updateStatus()
            toast(if (newVal) "已开启开机自启" else "已关闭开机自启")
        }

        btnShowCountdown.setOnClickListener {
            val newVal = !pref.isShowCountdown()
            pref.setShowCountdown(newVal)
            updateStatus()
            if (newVal && !PermissionStatus.overlay(this)) {
                toast("已开启，但缺少悬浮窗权限，暂时看不到角标")
            } else {
                toast(if (newVal) "已开启屏幕角标" else "已关闭屏幕角标")
            }
        }

        btnEmergencyReboot.setOnClickListener {
            if (pref.isEmergencyRebootUnlock()) {
                pref.setEmergencyRebootUnlock(false)
                updateStatus()
                toast("已关闭紧急重启解锁")
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("开启紧急重启解锁？")
                .setMessage(
                    "开启后：锁屏状态下 3 分钟内连续重启 3 次设备即解锁。\n\n" +
                            "孩子若学会此技巧可绕过锁屏。忘记 PIN 建议改用 adb 紧急文件（见 README「紧急解锁」）。"
                )
                .setPositiveButton("开启") { _, _ ->
                    pref.setEmergencyRebootUnlock(true)
                    updateStatus()
                    toast("已开启紧急重启解锁")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        val timeButtons = intArrayOf(R.id.btnTime15, R.id.btnTime30, R.id.btnTime45, R.id.btnTime60, R.id.btnTime90, R.id.btnTime120)
        val timeValues = intArrayOf(15, 30, 45, 60, 90, 120)
        for (i in timeButtons.indices) {
            val minutes = timeValues[i]
            findViewById<View>(timeButtons[i]).setOnClickListener {
                pref.setWatchLimitMinutes(minutes)
                updateStatus()
                toast("观看时长设为 $minutes 分钟")
            }
        }

        val countButtons = intArrayOf(R.id.btnUnlock5, R.id.btnUnlock10, R.id.btnUnlock15)
        val countValues = intArrayOf(5, 10, 15)
        for (i in countButtons.indices) {
            val count = countValues[i]
            findViewById<View>(countButtons[i]).setOnClickListener {
                pref.unlockCount = count
                updateStatus()
                toast("解锁题数设为 $count 题")
            }
        }

        findViewById<View>(R.id.btnSetPin).setOnClickListener { showPinDialog() }

        for (g in 0..6) {
            val grade = g
            btnGrades[g]!!.setOnClickListener {
                pref.grade = grade
                updateStatus()
                toast(getString(R.string.toast_grade_set, Grade.fullLabel(grade)))
            }
        }

        btnSubjectPinyin.setOnClickListener { onSubjectToggle(Subject.PINYIN) }
        btnSubjectMath.setOnClickListener { onSubjectToggle(Subject.MATH) }
        btnSubjectEnglish.setOnClickListener { onSubjectToggle(Subject.ENGLISH) }
        btnSubjectCitu.setOnClickListener { onSubjectToggle(Subject.CITU) }

        findViewById<View>(R.id.btnPreviewQuestion).setOnClickListener {
            val preview = Intent(this, QuestionPreviewActivity::class.java)
            startActivity(preview)
        }

        findViewById<View>(R.id.btnImportBank).setOnClickListener { showImportBankDialog() }
        findViewById<View>(R.id.btnClearBank).setOnClickListener { showClearBankDialog() }
        findViewById<View>(R.id.btnBankManager).setOnClickListener { showBankManagerDialog() }
        findViewById<View>(R.id.btnExportSettings).setOnClickListener { beginSettingsExport() }
        findViewById<View>(R.id.btnImportSettings).setOnClickListener { beginSettingsImport() }

        findViewById<View>(R.id.btnStartTimer).setOnClickListener {
            val minutes = pref.effectiveUnlockMinutes()
            if (minutes <= 0) {
                toast("今天的时间已用完，明天再开始计时")
                return@setOnClickListener
            }
            pref.startTimer(minutes)
            pref.consumeBonusMinutes()
            Revival.schedule(this)
            startLockService()
            toast("计时开始：$minutes 分钟后锁屏")
            updateStatus()
        }

        findViewById<View>(R.id.btnStopTimer).setOnClickListener {
            if (pref.isLocked()) {
                pref.setLocked(false)
                pref.setHomeAliasEnabled(this, false)
            }
            pref.stopTimer()
            if (pref.isScheduleEnabled()) {
                // 使用时段守护继续常驻，只是不再计时
                toast("计时已停止，使用时段守护继续")
                startLockService()
            } else {
                Revival.cancel(this)
                stopService(Intent(this, LockService::class.java))
                toast("计时已停止")
            }
            updateStatus()
        }

        findViewById<View>(R.id.btnLockNow).setOnClickListener {
            pref.setLocked(true)
            pref.setLockReason(PrefManager.REASON_MANUAL)
            pref.setHomeAliasEnabled(this, true)
            pref.stopTimer()
            startLockService()
            val lockIntent = Intent(this, LockScreenActivity::class.java)
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(lockIntent)
        }
    }

    private fun promptPinChange(force: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (force) "请修改默认PIN码" else "设置家长PIN码（4位数字）")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        builder.setView(input)

        builder.setPositiveButton("确定") { _, _ ->
            val pin = input.text.toString().trim()
            if (!pin.matches(Regex("\\d{4}"))) {
                toast("PIN码必须是4位数字")
                return@setPositiveButton
            }
            try {
                if (pref.setParentPin(pin)) {
                    pref.resetPinFailures()
                    toast("PIN码已设置")
                    updateStatus()
                } else {
                    toast("PIN码设置失败，请重试")
                }
            } catch (e: RuntimeException) {
                toast("PIN码设置失败：${e.message}")
            }
        }
        if (!force) {
            builder.setNegativeButton("取消", null)
        }
        builder.show()
    }

    private fun showPinDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("设置家长PIN码（4位数字）")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        builder.setView(input)

        builder.setPositiveButton("确定") { _, _ ->
            val pin = input.text.toString().trim()
            if (!pin.matches(Regex("\\d{4}"))) {
                toast("PIN码必须是4位数字")
                return@setPositiveButton
            }
            try {
                if (pref.setParentPin(pin)) {
                    pref.resetPinFailures()
                    toast("PIN码已设置")
                } else {
                    toast("PIN码设置失败，请重试")
                }
            } catch (e: RuntimeException) {
                toast("PIN码设置失败：${e.message}")
            }
        }
        builder.setNegativeButton("取消", null)
        builder.show()
    }

    private fun startLockService() {
        val serviceIntent = Intent(this, LockService::class.java)
        serviceIntent.putExtra("action", "start_timer")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun showDailyLimitDialog() {
        DailyLimitDialog.show(this, pref) { updateStatus() }
    }

    private fun showStarExchangeDialog() {
        val need = pref.getStarExchangeStars()
        val reward = pref.getStarExchangeMinutes()
        val stars = pref.getStars()
        val pending = pref.getBonusPendingMinutes()
        val message = buildString {
            append("当前星星：$stars 颗\n")
            append("当前比例：$need ⭐ = $reward 分钟\n")
            if (pending > 0) append("待生效奖励：$pending 分钟（下次解锁自动并入）\n")
            append("\n兑换后孩子下次答题解锁会多 $reward 分钟。")
            if (stars < need) append("\n\n星星还不够，先让孩子继续答题攒一攒吧。")
        }
        AlertDialog.Builder(this)
            .setTitle("星星兑换时长（家长）")
            .setMessage(message)
            .setPositiveButton("兑换 $reward 分钟") { _, _ ->
                if (pref.redeemStars()) {
                    updateStatus()
                    toast("已兑换 $reward 分钟，下次解锁生效")
                } else {
                    toast("星星不足：需要 $need 颗，当前 $stars 颗")
                }
            }
            .setNeutralButton("修改比例") { _, _ -> showStarRateDialog() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showStarRateDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }
        val inputStars = EditText(this).apply {
            setText(pref.getStarExchangeStars().toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "需要多少颗星星"
        }
        val inputMinutes = EditText(this).apply {
            setText(pref.getStarExchangeMinutes().toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "兑换多少分钟"
        }
        container.addView(TextView(this).apply { text = "星星数量（1-999）" })
        container.addView(inputStars)
        container.addView(TextView(this).apply { text = "奖励分钟数（1-180）" })
        container.addView(inputMinutes)
        AlertDialog.Builder(this)
            .setTitle("自定义兑换比例")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val s = inputStars.text.toString().toIntOrNull()
                val m = inputMinutes.text.toString().toIntOrNull()
                if (s == null || m == null || s !in 1..999 || m !in 1..180) {
                    toast("请输入 1-999 颗星、1-180 分钟")
                } else {
                    pref.setStarExchange(s, m)
                    updateStatus()
                    toast("兑换比例已保存：$s ⭐ = $m 分钟")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUsageHistoryDialog() {
        val history = pref.getUsageHistory(7)
        val total = history.sumOf { it.second }
        val today = history.firstOrNull()?.first ?: ""
        val sb = StringBuilder()
        for ((date, millis) in history) {
            val minutes = (millis + 59_999) / 60_000
            val mark = if (date == today) "（今天）" else ""
            sb.append(date).append(mark).append("  ")
            if (millis <= 0) sb.append("未记录")
            else sb.append(formatMinutes(minutes))
            sb.append('\n')
        }
        sb.append("\n合计：").append(formatMinutes((total + 59_999) / 60_000))
        val textView = TextView(this).apply {
            text = sb.toString()
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("近 7 天使用记录")
            .setView(textView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showLanPanelDialog() {
        LanPanelDialog.show(this, pref) {
            startLockService()
            updateStatus()
        }
    }

    private fun formatMinutes(minutes: Long): String {
        return if (minutes < 60) "$minutes 分钟"
        else "${minutes / 60} 小时 ${minutes % 60} 分钟"
    }

    private fun showWeeklyReportDialog() {
        WeeklyReportDialog.show(this, pref) { updateStatus() }
    }

    private fun showExportBankDialog() {
        val labels = arrayOfNulls<String>(IMPORTABLE_SUBJECTS.size)
        for (i in labels.indices) {
            labels[i] = Subject.label(IMPORTABLE_SUBJECTS[i])
        }
        AlertDialog.Builder(this)
            .setTitle("导出题库 · 选择科目")
            .setItems(labels) { _, whichSubject ->
                val subject = IMPORTABLE_SUBJECTS[whichSubject]
                AlertDialog.Builder(this)
                    .setTitle("导出题库 · 选择年级")
                    .setItems(gradeLabels()) { _, whichGrade ->
                        val result = BankRepository.get(this).load(subject, whichGrade)
                        if (result.entries.isEmpty()) {
                            toast(
                                getString(
                                    R.string.export_empty,
                                    Subject.label(subject), Grade.fullLabel(whichGrade)
                                )
                            )
                        } else {
                            pendingExportSubject = subject
                            pendingExportGrade = whichGrade
                            val name =
                                "题库-${Subject.label(subject)}-${Grade.fullLabel(whichGrade)}.json"
                            try {
                                exportLauncher.launch(name)
                            } catch (e: ActivityNotFoundException) {
                                toast("没有可用的文件选择器")
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun beginSettingsExport() {
        pendingExportSettings = true
        pendingExportSubject = null
        pendingExportGrade = -1
        try {
            exportLauncher.launch("kidslock-settings.json")
        } catch (e: ActivityNotFoundException) {
            pendingExportSettings = false
            toast("没有可用的文件选择器")
        }
    }

    private fun beginSettingsImport() {
        pendingImportSettings = true
        pendingImportSubject = null
        pendingImportGrade = -1
        try {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        } catch (e: ActivityNotFoundException) {
            pendingImportSettings = false
            toast("没有可用的文件选择器")
        }
    }

    private fun confirmImportSettings(raw: String) {
        AlertDialog.Builder(this)
            .setTitle("导入设置")
            .setMessage(
                "会用备份里的设置覆盖当前配置（额度、时段、出题、星星、统计、错题池）。\n\n" +
                        "锁屏状态、计时、当日已用时长、局域网口令不会被改动；" +
                        "PIN 不会导入，恢复后仍是当前 PIN。"
            )
            .setPositiveButton("覆盖导入") { _, _ ->
                val count = SettingsBackup.importJson(this, raw)
                if (count < 0) {
                    toast("导入失败：文件格式不正确或版本不兼容")
                    return@setPositiveButton
                }
                BankRepository.get(this).clearCache()
                startLockService()
                WeeklyReportAlarm.schedule(this)
                updateStatus()
                toast("已导入 $count 项设置")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onExportFilePicked(uri: Uri?) {
        if (pendingExportSettings) {
            pendingExportSettings = false
            if (uri == null) return
            try {
                val output = contentResolver.openOutputStream(uri) ?: run {
                    toast(getString(R.string.export_failed))
                    return
                }
                BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { w ->
                    w.write(SettingsBackup.exportJson(this))
                }
                toast("设置已导出（不含 PIN，恢复后 PIN 为默认 1234）")
            } catch (e: Exception) {
                toast(getString(R.string.export_failed))
            }
            return
        }
        val subject = pendingExportSubject
        val grade = pendingExportGrade
        pendingExportSubject = null
        pendingExportGrade = -1
        if (uri == null || subject == null || grade < 0) return
        try {
            val entries = BankRepository.get(this).load(subject, grade).entries
            val arr = JSONArray()
            for (e in entries) {
                val o = JSONObject()
                o.put("k", e.k)
                o.put("v", e.v)
                arr.put(o)
            }
            val root = JSONObject()
            root.put("subject", subject)
            root.put("grade", grade)
            root.put("entries", arr)

            val output = contentResolver.openOutputStream(uri) ?: run {
                toast(getString(R.string.export_failed))
                return
            }
            BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { w ->
                w.write(root.toString(2))
            }
            toast(getString(R.string.export_done, entries.size))
        } catch (e: Exception) {
            toast(getString(R.string.export_failed))
        }
    }

    private val scheduleDayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private fun showScheduleDialog() {
        val labels = arrayOfNulls<String>(9)
        labels[0] = if (pref.isScheduleEnabled())
            "时段限制：已开启（点击关闭）"
        else
            "时段限制：已关闭（点击开启）"
        labels[1] = if (pref.isScheduleFreeMode())
            "时段内：仅时段限制（自由使用）"
        else
            "时段内：仍需答题换时长"
        for (day in 0..6) {
            labels[day + 2] = "${scheduleDayNames[day]}：${pref.scheduleWindowLabel(day)}"
        }
        AlertDialog.Builder(this)
            .setTitle("使用时段（只在这些时间段内可用）")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> {
                        pref.setScheduleEnabled(!pref.isScheduleEnabled())
                        updateStatus()
                        if (pref.isScheduleEnabled()) {
                            // 常驻守护：开启后立即把服务拉起来，不依赖「开始计时」
                            startLockService()
                            toast("已开启使用时段限制（常驻守护）")
                        } else {
                            ScheduleAlarm.cancel(this)
                            if (pref.isLockedBySchedule()) {
                                // 关掉时段限制时，别把孩子留在"时段锁"里出不来
                                pref.setLocked(false)
                                pref.setHomeAliasEnabled(this, false)
                                KioskManager.syncLockState(this)
                                startLockService()
                            }
                            toast("已关闭使用时段限制")
                        }
                    }
                    1 -> {
                        pref.setScheduleFreeMode(!pref.isScheduleFreeMode())
                        updateStatus()
                        startLockService()
                        toast(
                            if (pref.isScheduleFreeMode())
                                "时段内改为自由使用（仅时段限制）"
                            else "时段内仍需答题换时长"
                        )
                    }
                    else -> showScheduleDayDialog(which - 2)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showScheduleDayDialog(day: Int) {
        val presets = arrayOf(
            "不限（关闭这一天）",
            "07:00–20:00",
            "08:00–21:00",
            "09:00–21:00",
            "18:00–21:00",
            "20:00–22:00",
            "自定义…"
        )
        AlertDialog.Builder(this)
            .setTitle("${scheduleDayNames[day]} 使用时段")
            .setItems(presets) { _, which ->
                when (which) {
                    0 -> {
                        pref.clearScheduleDay(day)
                        updateStatus()
                        toast("${scheduleDayNames[day]}：已取消限制")
                    }
                    6 -> promptCustomScheduleWindow(day)
                    else -> {
                        val (start, end) = parsePresetWindow(presets[which])
                        pref.setScheduleWindow(day, start, end)
                        if (!pref.isScheduleEnabled()) {
                            pref.setScheduleEnabled(true)
                            startLockService()
                        }
                        updateStatus()
                        toast("${scheduleDayNames[day]}：${pref.scheduleWindowLabel(day)}")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun parsePresetWindow(text: String): Pair<Int, Int> {
        val parts = text.split("–")
        val start = parseTimeToMinutes(parts.getOrNull(0) ?: "") ?: (8 * 60)
        val end = parseTimeToMinutes(parts.getOrNull(1) ?: "") ?: (21 * 60)
        return start to end
    }

    private fun promptCustomScheduleWindow(day: Int) {
        val startInit = pref.getScheduleStart(day).takeIf { it >= 0 } ?: (8 * 60)
        promptTime("${scheduleDayNames[day]} 开始时间（HH:mm）", startInit) { start ->
            val endInit = pref.getScheduleEnd(day).takeIf { it >= 0 } ?: (21 * 60)
            promptTime("${scheduleDayNames[day]} 结束时间（HH:mm，可早于开始表示跨零点）", endInit) { end ->
                if (start == end) {
                    toast("开始时间和结束时间不能相同")
                } else {
                    pref.setScheduleWindow(day, start, end)
                    if (!pref.isScheduleEnabled()) {
                        pref.setScheduleEnabled(true)
                        startLockService()
                    }
                    updateStatus()
                    toast("${scheduleDayNames[day]}：${pref.scheduleWindowLabel(day)}")
                }
            }
        }
    }

    private fun promptTime(title: String, initialMinutes: Int, onPicked: (Int) -> Unit) {
        val input = EditText(this).apply {
            setText(String.format(java.util.Locale.US, "%02d:%02d", initialMinutes / 60, initialMinutes % 60))
            inputType = InputType.TYPE_CLASS_DATETIME
            setSelection(text.length)
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val minutes = parseTimeToMinutes(input.text.toString())
                if (minutes == null) {
                    toast("时间格式不对，请用 HH:mm，例如 08:30")
                } else {
                    onPicked(minutes)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun parseTimeToMinutes(text: String): Int? {
        val m = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").find(text.trim()) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    private fun showHealthDialog() {
        val items = HealthCheck.items(this)
        val labels = items.map {
            (if (it.ok) "✅ " else "⚠️ ") + it.title + "：" + it.state
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("管控体检（${items.count { !it.ok }} 项未就绪）")
            .setItems(labels) { _, which -> onHealthItem(items[which]) }
            .setPositiveButton("权限总览") { _, _ ->
                startActivity(Intent(this, PermissionActivity::class.java))
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun onHealthItem(item: HealthCheck.Item) {
        if (item.id == HealthCheck.ID_DEVICE_OWNER) {
            showDeviceOwnerHelp()
            return
        }
        HealthCheck.launch(this, item.id)
    }

    private fun showDeviceOwnerHelp() {
        val command = "adb shell dpm set-device-owner " +
                "$packageName/.KidsDeviceAdminReceiver"
        AlertDialog.Builder(this)
            .setTitle("设备所有者（可选）")
            .setMessage(
                "设置后可防卸载、进入锁定任务模式，拦截能力最强。\n\n" +
                        "1. 设备需无其他账号（必要时恢复出厂）\n" +
                        "2. 打开 USB 调试并连接电脑\n" +
                        "3. 执行：\n$command"
            )
            .setPositiveButton("复制命令") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("adb", command))
                toast("命令已复制")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showDiagDialog() {
        val lines = Diag.read(this, 300)
        val content = if (lines.isEmpty())
            "暂无日志。出现锁不住 / 不锁屏 / 声音没停等问题后，这里会记录关键事件。\n\n日志文件：${Diag.path(this)}"
        else
            lines.joinToString("\n")
        val textView = TextView(this).apply {
            text = content
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(32, 24, 32, 24)
        }
        val scroll = ScrollView(this).apply { addView(textView) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("诊断日志（最近 300 行）")
            .setView(scroll)
            .setPositiveButton("复制全部") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("kidslock-diagnostics", content))
                toast("日志已复制到剪贴板")
            }
            .setNeutralButton("清空") { _, _ ->
                Diag.clear(this)
                toast("日志已清空")
            }
            .setNegativeButton("关闭", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                (resources.displayMetrics.heightPixels * 0.70f).toInt()
            )
        }
        dialog.show()
    }

    private fun updateStatus() {
        val guardOn = RecoveryAccessibilityService.isServiceEnabled(this)
        val owner = KioskManager.isDeviceOwner(this)
        btnGuardStatus.text = when {
            guardOn && owner -> "守护服务：已开启 · 设备所有者"
            guardOn -> "守护服务：已开启"
            owner -> "守护服务：未开启，点击开启 · 设备所有者"
            else -> "守护服务：未开启，点击开启"
        }

        if (permStatusDirty) {
            permMissingCount = PermissionStatus.missingCount(this)
            permStatusDirty = false
        }
        btnPermissions.text = if (permMissingCount == 0)
            getString(R.string.perm_all_ok) else getString(R.string.perm_missing, permMissingCount)
        btnHealth.text = if (permMissingCount == 0)
            "管控体检：全部就绪" else "管控体检：$permMissingCount 项未就绪"
        val bypassRisk = PermissionStatus.bypassRiskCount(this)
        if (bypassRisk > 0) {
            btnHealth.append(" · 绕过风险 $bypassRisk")
        }

        // 一个拦截能力都没有时明确说「已失效」，而不是只显示缺几项
        val monitoring = PermissionStatus.overlay(this) ||
                PermissionStatus.usageAccess(this) ||
                PermissionStatus.accessibility(this)
        if (!monitoring) {
            btnPermissions.text = "权限：管控已失效（缺少悬浮窗/无障碍/使用情况）点此修复"
        }

        btnAutoStart.text = if (pref.isAutoStart()) "开机自启：已开启" else "开机自启：已关闭"
        btnEmergencyReboot.text =
            if (pref.isEmergencyRebootUnlock()) "紧急重启解锁：已开启" else "紧急重启解锁：已关闭"
        btnShowCountdown.text =
            if (pref.isShowCountdown()) "屏幕角标：已开启" else "屏幕角标：已关闭"
        tvWatchTime.text = "观看时长：${pref.getWatchLimitMinutes()} 分钟"
        val weekdayLimit = pref.getWeekdayDailyLimitMinutes()
        val weekendRaw = pref.getWeekendDailyLimitMinutes()
        val todayLimit = pref.getDailyLimitMinutes()
        val usedMinutes = (pref.getDailyUsedMs() + 59_999) / 60_000
        val dayLabel = if (pref.isWeekend()) "周末" else "平日"
        tvDailyUsage.text = if (todayLimit <= 0)
            "今日已用：$usedMinutes 分钟（未设上限）"
        else
            "今日已用：$usedMinutes / $todayLimit 分钟（$dayLabel）"
        val pendingBonus = pref.getBonusPendingMinutes()
        if (pendingBonus > 0) {
            tvDailyUsage.append(" · 奖励待生效 +$pendingBonus 分钟")
        }
        if (pref.wasClockRolledBack()) {
            tvDailyUsage.append(" · 检测到系统时间被改回，已保留额度（点此确认）")
        }
        val weekendText = if (weekendRaw < 0) "跟随平日" else DailyLimitDialog.shortLabel(weekendRaw)
        btnDailyLimit.text = "每日上限：平日 ${DailyLimitDialog.shortLabel(weekdayLimit)} · 周末 $weekendText"
        btnStarExchange.text =
            "星星兑换：${pref.getStarExchangeStars()} ⭐ = ${pref.getStarExchangeMinutes()} 分钟"
        btnLanPanel.text = if (pref.isLanEnabled()) "局域网家长面板：已开启" else "局域网家长面板：已关闭"
        btnWeeklyReport.text =
            if (pref.isWeeklyReportEnabled()) "近 7 天报告（每周日提醒）" else "近 7 天报告（周报提醒已关闭）"
        tvSchedule.text = if (!pref.isScheduleEnabled()) {
            "使用时段：未开启"
        } else {
            val today = pref.scheduleDayIndex()
            val suffix = when {
                pref.isScheduleBypassActive() -> " · 家长临时放行中"
                !pref.isWithinSchedule() -> " · 当前不在可用时段"
                pref.isScheduleFreeMode() -> " · 时段内自由使用"
                else -> ""
            }
            "使用时段：今天 ${pref.scheduleWindowLabel(today)}$suffix"
        }
        btnSchedule.text =
            if (pref.isScheduleEnabled()) "使用时段限制：已开启" else "使用时段限制：未开启"
        tvUnlockCount.text = "解锁题数：${pref.unlockCount} 道连续答对"

        tvQuestionGrade.text = "年级：${Grade.fullLabel(pref.grade)}" +
                if (pref.isAdaptiveDifficultyEnabled())
                    " · 自适应当前 ${Grade.fullLabel(pref.getAdaptiveGrade(pref.grade))}"
                else ""
        tvQuestionSubjects.text = "科目：${enabledSubjectsText()}"
        btnQuestionTypes.text = "题型与复习：判断" +
                (if (pref.isJudgmentEnabled()) "开" else "关") +
                " · 填空" + (if (pref.isFillBlankEnabled()) "开" else "关") +
                " · 错题加练" + (if (pref.isReviewBoostEnabled()) "开" else "关") +
                " · 自适应" + (if (pref.isAdaptiveDifficultyEnabled()) "开" else "关")
        for (g in 0..6) {
            setButtonSelected(btnGrades[g]!!, g == pref.grade, Grade.shortLabel(g))
        }
        setButtonSelected(btnSubjectPinyin, pref.isSubjectEnabled(Subject.PINYIN), Subject.label(Subject.PINYIN))
        setButtonSelected(btnSubjectMath, pref.isSubjectEnabled(Subject.MATH), Subject.label(Subject.MATH))
        setButtonSelected(btnSubjectEnglish, pref.isSubjectEnabled(Subject.ENGLISH), Subject.label(Subject.ENGLISH))
        setButtonSelected(btnSubjectCitu, pref.isSubjectEnabled(Subject.CITU), Subject.label(Subject.CITU))

        val blocked = QuestionEngine.unavailableSubjects(this, pref)
        if (blocked.isEmpty()) {
            tvSubjectWarning.visibility = View.GONE
        } else {
            val names = blocked.joinToString("、") { Subject.label(it) }
            tvSubjectWarning.text =
                getString(R.string.subject_unavailable_warning, names)
            tvSubjectWarning.visibility = View.VISIBLE
        }

        if (pref.isLocked()) {
            tvStatus.text = "当前状态：已锁屏"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
            tvRemaining.text = "请答题解锁"
        } else if (pref.isTimerActive()) {
            val remaining = pref.getRemainingMillis()
            if (remaining <= 0) {
                tvStatus.text = "当前状态：时间到"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
            } else {
                tvStatus.text = "当前状态：计时中"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
                val totalSec = remaining / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                tvRemaining.text = "剩余时间：" + String.format("%02d:%02d", min, sec)
            }
        } else {
            tvStatus.text = "当前状态：未计时"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tvRemaining.text = ""
        }

        val acc = pref.getAccuracyPercent()
        if (acc < 0) {
            tvAccuracy.visibility = View.GONE
            btnResetStats.visibility = View.GONE
        } else {
            tvAccuracy.text = getString(R.string.accuracy_text, acc) +
                    " · ⭐ ${pref.getStars()} 颗 · 最高连对 ${pref.getBestCombo()} 题" +
                    " · 错题待复习 ${ReviewStore.dueCount(this)} 道"
            tvAccuracy.visibility = View.VISIBLE
            btnResetStats.visibility = View.VISIBLE
        }

        if (pref.isPinModified()) {
            tvPinStatus.text = getString(R.string.pin_status_modified)
            tvPinStatus.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
        } else {
            tvPinStatus.text = getString(R.string.pin_status_default)
            tvPinStatus.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
        }
        tvPinStatus.visibility = View.VISIBLE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun enabledSubjectsText(): String {
        val sb = StringBuilder()
        for (s in Subject.ORDER) {
            if (pref.isSubjectEnabled(s)) {
                if (sb.isNotEmpty()) sb.append("、")
                sb.append(Subject.label(s))
            }
        }
        return sb.toString()
    }

    private fun setButtonSelected(btn: Button, selected: Boolean, label: String) {
        if (selected) {
            btn.text = "✓$label"
            btn.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
        } else {
            btn.text = label
            btn.setTextColor(ContextCompat.getColor(this, R.color.btn_text))
        }
    }

    private fun onSubjectToggle(subject: String) {
        val cur = pref.isSubjectEnabled(subject)
        if (cur && isOnlyEnabled(subject)) {
            toast(getString(R.string.toast_keep_one_subject))
            return
        }
        val ok = pref.setSubjectEnabled(subject, !cur)
        if (!ok) {
            toast(getString(R.string.toast_keep_one_subject))
            return
        }
        updateStatus()
        toast(
            getString(
                if (cur) R.string.toast_subject_off else R.string.toast_subject_on,
                Subject.label(subject)
            )
        )
    }

    private fun isOnlyEnabled(subject: String): Boolean {
        val s = pref.subjects
        return s.size == 1 && s.contains(subject)
    }

    private fun gradeLabels(): Array<String> {
        val arr = arrayOfNulls<String>(Grade.MAX - Grade.MIN + 1)
        for (g in Grade.MIN..Grade.MAX) arr[g] = Grade.fullLabel(g)
        return arr as Array<String>
    }

    private fun showQuestionTypesDialog() {
        QuestionTypeDialog.show(this, pref) { updateStatus() }
    }

    private fun beginBankImport(subject: String, grade: Int) {
        pendingImportSubject = subject
        pendingImportGrade = grade
        try {
            importLauncher.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            toast("没有可用的文件选择器")
        }
    }

    private fun beginBankExport(subject: String, grade: Int) {
        pendingExportSubject = subject
        pendingExportGrade = grade
        val name = "题库-${Subject.label(subject)}-${Grade.fullLabel(grade)}.json"
        try {
            exportLauncher.launch(name)
        } catch (e: ActivityNotFoundException) {
            toast("没有可用的文件选择器")
        }
    }

    private fun showBankManagerDialog() {
        BankDialogs.showManager(
            this,
            onImport = ::beginBankImport,
            onExport = ::beginBankExport
        ) { updateStatus() }
    }

    private fun showImportBankDialog() {
        BankDialogs.showImportPicker(this) { subject, grade ->
            pendingImportSubject = subject
            pendingImportGrade = grade
            try {
                importLauncher.launch(arrayOf("*/*"))
            } catch (e: ActivityNotFoundException) {
                toast("没有可用的文件选择器")
            }
        }
    }

    private fun showClearBankDialog() {
        BankDialogs.showClearPicker(this) { updateStatus() }
    }

    private fun onBankFilePicked(uri: Uri?) {
        if (pendingImportSettings) {
            pendingImportSettings = false
            if (uri == null) return
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: run {
                    toast(getString(R.string.import_read_failed))
                    return
                }
                val raw = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .use { it.readText() }
                confirmImportSettings(raw)
            } catch (e: Exception) {
                toast(getString(R.string.import_read_failed))
            }
            return
        }
        val subject = pendingImportSubject
        val grade = pendingImportGrade
        pendingImportSubject = null
        pendingImportGrade = -1
        if (uri == null || subject == null) return
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: run {
                toast(getString(R.string.import_read_failed))
                return
            }
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line)
                line = reader.readLine()
            }
            val ok = CustomBankStore.importBank(this, subject, grade, sb.toString())
            BankRepository.get(this).clearCache()
            toast(
                if (ok) getString(
                    R.string.import_done,
                    Subject.label(subject), Grade.fullLabel(grade)
                ) else getString(R.string.import_invalid)
            )
            if (ok) {
                BankDialogs.showDetail(
                    this, subject, grade, ::beginBankImport, ::beginBankExport
                ) { updateStatus() }
            }
        } catch (e: Exception) {
            toast(getString(R.string.import_read_failed))
        }
    }
}
