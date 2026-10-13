package com.kidslock.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kidslock.app.question.Grade;
import com.kidslock.app.question.Subject;

import java.util.Set;

/**
 * 设置主界面。
 * 家长可以在这里配置：开机自启、观看时长、解锁题数、家长PIN码。
 * 还可以开始/停止计时、立即锁屏、查看剩余时间。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIFICATIONS = 1001;

    private PrefManager pref;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvStatus;
    private TextView tvRemaining;
    private Button btnAutoStart;
    private TextView tvWatchTime;
    private TextView tvUnlockCount;
    private Button btnGuardStatus;

    // 出题设置（年级 / 科目）
    private TextView tvQuestionGrade;
    private TextView tvQuestionSubjects;
    private Button[] btnGrades = new Button[7];
    private Button btnSubjectPinyin;
    private Button btnSubjectMath;
    private Button btnSubjectEnglish;
    private Button btnSubjectCitu;

    // 家长 PIN 门禁：设置页每次进入（含从后台返回）都要求重新验证，
    // 否则孩子从桌面打开应用就能改参数、停计时
    private View layoutSettings;
    private View layoutGate;
    private TextView tvGatePinDisplay;
    private final StringBuilder gatePinInput = new StringBuilder();
    private boolean gateVerified = false;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = new PrefManager(this);

        // 如果当前处于锁屏状态，禁止进入设置页（防止从桌面图标绕过锁屏解锁）
        if (pref.isLocked()) {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        requestNotificationPermissionIfNeeded();

        initViews();
        setupListeners();
        updateStatus();
        applyGate();
    }

    /**
     * Android 13+ 通知需要运行时权限，不授予则前台服务的倒计时通知不可见。
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        pref = new PrefManager(this);
        // 如果处于锁屏状态，强制跳转到锁屏界面（防止从后台/最近任务绕过）
        if (pref.isLocked()) {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
            finish();
            return;
        }
        // 计时进行中但服务不在运行（实测 MIUI 熄屏会杀后台进程）：拉起服务恢复计时。
        // 服务启动后 deductOfflineIfAny 会扣除服务死亡期间屏幕亮着的时间，
        // 剩余时间归零则立即触发锁屏。此处是前台 Activity，启动服务不受后台限制。
        if (pref.isTimerActive()) {
            startLockService();
        }
        // 守护服务（无障碍）未启用：持有 WRITE_SECURE_SETTINGS（adb 授权一次后
        // 永久有效）时自行写回，否则由设置页的守护按钮引导用户手动开启
        if (!RecoveryAccessibilityService.isServiceEnabled(this)) {
            RecoveryAccessibilityService.trySelfEnable(this);
        }
        // 先移除旧的，避免快速切回时重复 post 多个 Runnable
        handler.removeCallbacks(updateRunnable);
        handler.post(updateRunnable);
        // 从后台/其他界面返回时重新验证 PIN（onPause 已清除验证标记）
        applyGate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable);
        // 离开界面即失效，下次进入需重新输入 PIN
        gateVerified = false;
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvRemaining = findViewById(R.id.tvRemaining);
        btnAutoStart = findViewById(R.id.btnAutoStart);
        tvWatchTime = findViewById(R.id.tvWatchTime);
        tvUnlockCount = findViewById(R.id.tvUnlockCount);
        btnGuardStatus = findViewById(R.id.btnGuardStatus);

        tvQuestionGrade = findViewById(R.id.tvQuestionGrade);
        tvQuestionSubjects = findViewById(R.id.tvQuestionSubjects);
        btnGrades[0] = findViewById(R.id.btnGrade0);
        btnGrades[1] = findViewById(R.id.btnGrade1);
        btnGrades[2] = findViewById(R.id.btnGrade2);
        btnGrades[3] = findViewById(R.id.btnGrade3);
        btnGrades[4] = findViewById(R.id.btnGrade4);
        btnGrades[5] = findViewById(R.id.btnGrade5);
        btnGrades[6] = findViewById(R.id.btnGrade6);
        btnSubjectPinyin = findViewById(R.id.btnSubjectPinyin);
        btnSubjectMath = findViewById(R.id.btnSubjectMath);
        btnSubjectEnglish = findViewById(R.id.btnSubjectEnglish);
        btnSubjectCitu = findViewById(R.id.btnSubjectCitu);
        btnGuardStatus.setOnClickListener(v -> {
            if (RecoveryAccessibilityService.isServiceEnabled(this)) {
                toast("守护服务已开启");
                return;
            }
            // 先尝试自行启用（持有 WRITE_SECURE_SETTINGS 时直接写回）
            if (RecoveryAccessibilityService.trySelfEnable(this)) {
                toast("守护服务已自动开启");
                updateStatus();
                return;
            }
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                toast("请在列表中找到「儿童锁屏」并开启");
            } catch (ActivityNotFoundException e) {
                // 个别电视没有标准无障碍设置页（如小米 TV），只能人工引导
                toast("请到系统设置的无障碍/辅助功能中开启「儿童锁屏」");
            }
        });

        // 家长 PIN 门禁（与锁屏界面共用 view_pin_pad 面板）
        layoutSettings = findViewById(R.id.settingsScroll);
        layoutGate = findViewById(R.id.layoutGate);
        tvGatePinDisplay = findViewById(R.id.tvPinDisplay);

        int[] gatePinButtonIds = {
                R.id.btnPin0, R.id.btnPin1, R.id.btnPin2, R.id.btnPin3,
                R.id.btnPin4, R.id.btnPin5, R.id.btnPin6, R.id.btnPin7,
                R.id.btnPin8, R.id.btnPin9
        };
        for (int i = 0; i <= 9; i++) {
            final int digit = i;
            findViewById(gatePinButtonIds[i]).setOnClickListener(v -> onGatePinDigit(digit));
        }
        findViewById(R.id.btnPinClear).setOnClickListener(v -> {
            gatePinInput.setLength(0);
            updateGatePinDisplay();
        });
        findViewById(R.id.btnPinBack).setOnClickListener(v -> {
            // 门禁界面没有可返回的上一屏，等同清除重输
            gatePinInput.setLength(0);
            updateGatePinDisplay();
        });
    }

    // ==================== 家长 PIN 门禁 ====================

    /** 按验证状态切换"门禁 / 设置页"的可见性 */
    private void applyGate() {
        gatePinInput.setLength(0);
        updateGatePinDisplay();
        if (gateVerified) {
            layoutGate.setVisibility(View.GONE);
            layoutSettings.setVisibility(View.VISIBLE);
            // TV：门禁隐藏后把焦点交给设置页第一个按钮
            findViewById(R.id.btnStartTimer).post(() -> findViewById(R.id.btnStartTimer).requestFocus());
        } else {
            layoutSettings.setVisibility(View.GONE);
            layoutGate.setVisibility(View.VISIBLE);
            // TV：显式定位到 PIN 键盘第一个数字键
            findViewById(R.id.btnPin1).post(() -> findViewById(R.id.btnPin1).requestFocus());
        }
    }

    private void onGatePinDigit(int digit) {
        if (gatePinInput.length() < 4) {
            gatePinInput.append(digit);
            updateGatePinDisplay();
            if (gatePinInput.length() == 4) {
                handler.postDelayed(this::checkGatePin, 200);
            }
        }
    }

    private void checkGatePin() {
        if (pref.isPinLocked()) {
            long remainingSec = pref.getPinLockRemainingMillis() / 1000;
            toast("尝试次数过多，请 " + remainingSec + " 秒后再试");
            gatePinInput.setLength(0);
            updateGatePinDisplay();
            return;
        }
        if (pref.verifyPin(gatePinInput.toString())) {
            pref.resetPinFailures();
            gateVerified = true;
            applyGate();
        } else {
            pref.recordPinFailure();
            toast("PIN码错误");
            gatePinInput.setLength(0);
            updateGatePinDisplay();
        }
    }

    private void updateGatePinDisplay() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < gatePinInput.length()) {
                sb.append("●");
            } else {
                sb.append("○");
            }
            if (i < 3) sb.append("  ");
        }
        tvGatePinDisplay.setText(sb.toString());
    }

    private void setupListeners() {
        // 开机自启
        btnAutoStart.setOnClickListener(v -> {
            boolean newVal = !pref.isAutoStart();
            pref.setAutoStart(newVal);
            updateStatus();
            toast(newVal ? "已开启开机自启" : "已关闭开机自启");
        });

        // 观看时长预设
        int[] timeButtons = {R.id.btnTime15, R.id.btnTime30, R.id.btnTime45,
                R.id.btnTime60, R.id.btnTime90, R.id.btnTime120};
        int[] timeValues = {15, 30, 45, 60, 90, 120};
        for (int i = 0; i < timeButtons.length; i++) {
            final int minutes = timeValues[i];
            findViewById(timeButtons[i]).setOnClickListener(v -> {
                pref.setWatchLimitMinutes(minutes);
                updateStatus();
                toast("观看时长设为 " + minutes + " 分钟");
            });
        }

        // 解锁题数预设
        int[] countButtons = {R.id.btnUnlock5, R.id.btnUnlock10, R.id.btnUnlock15};
        int[] countValues = {5, 10, 15};
        for (int i = 0; i < countButtons.length; i++) {
            final int count = countValues[i];
            findViewById(countButtons[i]).setOnClickListener(v -> {
                pref.setUnlockCount(count);
                updateStatus();
                toast("解锁题数设为 " + count + " 题");
            });
        }

        // 设置PIN码
        findViewById(R.id.btnSetPin).setOnClickListener(v -> showPinDialog());

        // 出题年级（7 档单选）
        for (int g = 0; g <= 6; g++) {
            final int grade = g;
            btnGrades[g].setOnClickListener(v -> {
                pref.setGrade(grade);
                updateStatus();
                toast(getString(R.string.toast_grade_set, Grade.fullLabel(grade)));
            });
        }

        // 出题科目（toggle，至少保留一个）
        btnSubjectPinyin.setOnClickListener(v -> onSubjectToggle(Subject.PINYIN));
        btnSubjectMath.setOnClickListener(v -> onSubjectToggle(Subject.MATH));
        btnSubjectEnglish.setOnClickListener(v -> onSubjectToggle(Subject.ENGLISH));
        btnSubjectCitu.setOnClickListener(v -> onSubjectToggle(Subject.CITU));

        // 开始计时
        findViewById(R.id.btnStartTimer).setOnClickListener(v -> {
            int minutes = pref.getWatchLimitMinutes();
            pref.startTimer(minutes);
            startLockService();
            toast("计时开始：" + minutes + " 分钟后锁屏");
            updateStatus();
        });

        // 停止计时
        findViewById(R.id.btnStopTimer).setOnClickListener(v -> {
            // 若仍处于锁定状态（正常 UI 流程进不到这里），顺带解除锁定，
            // 让"停止计时"始终是家长可用的逃生通道
            if (pref.isLocked()) {
                pref.setLocked(false);
                pref.setHomeAliasEnabled(this, false);
            }
            pref.stopTimer();
            stopService(new Intent(this, LockService.class));
            toast("计时已停止");
            updateStatus();
        });

        // 立即锁屏
        findViewById(R.id.btnLockNow).setOnClickListener(v -> {
            pref.setLocked(true);
            pref.setHomeAliasEnabled(this, true);
            pref.stopTimer();
            startLockService();
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
        });
    }

    private void showPinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置家长PIN码（4位数字）");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String pin = input.getText().toString().trim();
            if (!pin.matches("\\d{4}")) {
                toast("PIN码必须是4位数字");
                return;
            }
            // setParentPin 在 SHA 异常时会抛 IllegalStateException——不吞掉，
            // 但也不能让弹窗回调崩溃整个设置页。
            try {
                if (pref.setParentPin(pin)) {
                    pref.resetPinFailures();
                    toast("PIN码已设置");
                } else {
                    toast("PIN码设置失败，请重试");
                }
            } catch (RuntimeException e) {
                toast("PIN码设置失败：" + e.getMessage());
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void startLockService() {
        Intent serviceIntent = new Intent(this, LockService.class);
        serviceIntent.putExtra("action", "start_timer");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void updateStatus() {
        // 守护服务（无障碍）状态
        btnGuardStatus.setText(RecoveryAccessibilityService.isServiceEnabled(this)
                ? "守护服务：已开启" : "守护服务：未开启，点击开启");

        // 开机自启
        btnAutoStart.setText(pref.isAutoStart() ? "开机自启：已开启" : "开机自启：已关闭");

        // 观看时长
        tvWatchTime.setText("观看时长：" + pref.getWatchLimitMinutes() + " 分钟");

        // 解锁题数
        tvUnlockCount.setText("解锁题数：" + pref.getUnlockCount() + " 道连续答对");

        // 出题年级 / 科目（权威显示 + 按钮选中态走文字通道）
        tvQuestionGrade.setText("年级：" + Grade.fullLabel(pref.getGrade()));
        tvQuestionSubjects.setText("科目：" + enabledSubjectsText());
        for (int g = 0; g <= 6; g++) {
            setButtonSelected(btnGrades[g], g == pref.getGrade(), Grade.shortLabel(g));
        }
        setButtonSelected(btnSubjectPinyin, pref.isSubjectEnabled(Subject.PINYIN), Subject.label(Subject.PINYIN));
        setButtonSelected(btnSubjectMath, pref.isSubjectEnabled(Subject.MATH), Subject.label(Subject.MATH));
        setButtonSelected(btnSubjectEnglish, pref.isSubjectEnabled(Subject.ENGLISH), Subject.label(Subject.ENGLISH));
        setButtonSelected(btnSubjectCitu, pref.isSubjectEnabled(Subject.CITU), Subject.label(Subject.CITU));

        // 状态
        if (pref.isLocked()) {
            tvStatus.setText("当前状态：已锁屏");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.wrong_red));
            tvRemaining.setText("请答题解锁");
        } else if (pref.isTimerActive()) {
            long remaining = pref.getRemainingMillis();
            if (remaining <= 0) {
                tvStatus.setText("当前状态：时间到");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.wrong_red));
            } else {
                tvStatus.setText("当前状态：计时中");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.correct_green));
                long totalSec = remaining / 1000;
                long min = totalSec / 60;
                long sec = totalSec % 60;
                tvRemaining.setText("剩余时间：" + String.format("%02d:%02d", min, sec));
            }
        } else {
            tvStatus.setText("当前状态：未计时");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvRemaining.setText("");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 按 Subject.ORDER 罗列已启用科目（中文名，以 "、" 连接） */
    @NonNull
    private String enabledSubjectsText() {
        StringBuilder sb = new StringBuilder();
        for (String s : Subject.ORDER) {
            if (pref.isSubjectEnabled(s)) {
                if (sb.length() > 0) sb.append("、");
                sb.append(Subject.label(s));
            }
        }
        return sb.toString();
    }

    /** 按钮选中态走文字通道：✓+label 绿字 / 普通 label+默认字色（R17，不复用 state_selected） */
    private void setButtonSelected(@NonNull Button btn, boolean selected, @NonNull String label) {
        if (selected) {
            btn.setText("✓" + label);
            btn.setTextColor(ContextCompat.getColor(this, R.color.correct_green));
        } else {
            btn.setText(label);
            btn.setTextColor(ContextCompat.getColor(this, R.color.btn_text));
        }
    }

    /** 科目 toggle：最后一个是唯一启用科目时不生效（R03 L1） */
    private void onSubjectToggle(@NonNull String subject) {
        boolean cur = pref.isSubjectEnabled(subject);
        if (cur && isOnlyEnabled(subject)) {
            toast(getString(R.string.toast_keep_one_subject));
            return; // 保持选中
        }
        boolean ok = pref.setSubjectEnabled(subject, !cur);
        if (!ok) {
            toast(getString(R.string.toast_keep_one_subject));
            return;
        }
        updateStatus();
        toast(getString(cur ? R.string.toast_subject_off : R.string.toast_subject_on, Subject.label(subject)));
    }

    /** subject 是否为当前唯一启用的科目 */
    private boolean isOnlyEnabled(@NonNull String subject) {
        Set<String> s = pref.getSubjects();
        return s.size() == 1 && s.contains(subject);
    }
}
