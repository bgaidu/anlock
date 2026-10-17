package com.kidslock.app

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
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
import android.widget.Button
import android.widget.EditText
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
import com.kidslock.app.question.Subject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_POST_NOTIFICATIONS = 1001
        private val IMPORTABLE_SUBJECTS = arrayOf(Subject.PINYIN, Subject.ENGLISH, Subject.CITU)
    }

    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private var pendingImportSubject: String? = null
    private var pendingImportGrade = -1

    private lateinit var pref: PrefManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvStatus: TextView
    private lateinit var tvRemaining: TextView
    private lateinit var btnAutoStart: Button
    private lateinit var tvWatchTime: TextView
    private lateinit var tvUnlockCount: TextView
    private lateinit var btnGuardStatus: Button
    private lateinit var tvQuestionGrade: TextView
    private lateinit var tvQuestionSubjects: TextView
    private lateinit var tvAccuracy: TextView
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

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = PrefManager(this)

        importLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> onBankFilePicked(uri) }

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
        pref = PrefManager(this)
        if (pref.isLocked()) {
            val lockIntent = Intent(this, LockScreenActivity::class.java)
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(lockIntent)
            finish()
            return
        }
        if (pref.isTimerActive()) {
            startLockService()
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
        handler.removeCallbacks(updateRunnable)
        gateVerified = false
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        // 显示当前版本号，方便确认设备上装的是哪个版本
        findViewById<TextView>(R.id.tvVersion).text = try {
            "版本 v" + packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "" }
        tvRemaining = findViewById(R.id.tvRemaining)
        btnAutoStart = findViewById(R.id.btnAutoStart)
        tvWatchTime = findViewById(R.id.tvWatchTime)
        tvUnlockCount = findViewById(R.id.tvUnlockCount)
        btnGuardStatus = findViewById(R.id.btnGuardStatus)
        tvQuestionGrade = findViewById(R.id.tvQuestionGrade)
        tvQuestionSubjects = findViewById(R.id.tvQuestionSubjects)
        tvAccuracy = findViewById(R.id.tvAccuracy)
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
            gateVerified = true
            applyGate()
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

        findViewById<View>(R.id.btnStartTimer).setOnClickListener {
            val minutes = pref.getWatchLimitMinutes()
            pref.startTimer(minutes)
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
            stopService(Intent(this, LockService::class.java))
            toast("计时已停止")
            updateStatus()
        }

        findViewById<View>(R.id.btnLockNow).setOnClickListener {
            pref.setLocked(true)
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

    private fun updateStatus() {
        btnGuardStatus.text = if (RecoveryAccessibilityService.isServiceEnabled(this))
            "守护服务：已开启" else "守护服务：未开启，点击开启"

        btnAutoStart.text = if (pref.isAutoStart()) "开机自启：已开启" else "开机自启：已关闭"
        tvWatchTime.text = "观看时长：${pref.getWatchLimitMinutes()} 分钟"
        tvUnlockCount.text = "解锁题数：${pref.unlockCount} 道连续答对"

        tvQuestionGrade.text = "年级：${Grade.fullLabel(pref.grade)}"
        tvQuestionSubjects.text = "科目：${enabledSubjectsText()}"
        for (g in 0..6) {
            setButtonSelected(btnGrades[g]!!, g == pref.grade, Grade.shortLabel(g))
        }
        setButtonSelected(btnSubjectPinyin, pref.isSubjectEnabled(Subject.PINYIN), Subject.label(Subject.PINYIN))
        setButtonSelected(btnSubjectMath, pref.isSubjectEnabled(Subject.MATH), Subject.label(Subject.MATH))
        setButtonSelected(btnSubjectEnglish, pref.isSubjectEnabled(Subject.ENGLISH), Subject.label(Subject.ENGLISH))
        setButtonSelected(btnSubjectCitu, pref.isSubjectEnabled(Subject.CITU), Subject.label(Subject.CITU))

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
        } else {
            tvAccuracy.text = getString(R.string.accuracy_text, acc)
            tvAccuracy.visibility = View.VISIBLE
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

    private fun showImportBankDialog() {
        val labels = arrayOfNulls<String>(IMPORTABLE_SUBJECTS.size)
        for (i in labels.indices) {
            labels[i] = Subject.label(IMPORTABLE_SUBJECTS[i])
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.import_choose_subject)
            .setItems(labels) { _, whichSubject ->
                val subject = IMPORTABLE_SUBJECTS[whichSubject]
                AlertDialog.Builder(this)
                    .setTitle(R.string.import_choose_grade)
                    .setItems(gradeLabels()) { _, whichGrade ->
                        pendingImportSubject = subject
                        pendingImportGrade = whichGrade
                        importLauncher.launch(arrayOf("*/*"))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showClearBankDialog() {
        val labels = arrayOfNulls<String>(IMPORTABLE_SUBJECTS.size)
        for (i in labels.indices) {
            labels[i] = Subject.label(IMPORTABLE_SUBJECTS[i])
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_choose_subject)
            .setItems(labels) { _, whichSubject ->
                val subject = IMPORTABLE_SUBJECTS[whichSubject]
                AlertDialog.Builder(this)
                    .setTitle(R.string.clear_choose_grade)
                    .setItems(gradeLabels()) { _, whichGrade ->
                        val ok = CustomBankStore.clear(this, subject, whichGrade)
                        BankRepository.get(this).clearCache()
                        toast(
                            if (ok) getString(
                                R.string.clear_done,
                                Subject.label(subject), Grade.fullLabel(whichGrade)
                            ) else getString(R.string.clear_failed)
                        )
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onBankFilePicked(uri: Uri?) {
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
        } catch (e: Exception) {
            toast(getString(R.string.import_read_failed))
        }
    }
}
