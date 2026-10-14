package com.kidslock.app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.kidslock.app.question.Question;
import com.kidslock.app.question.QuestionEngine;
import com.kidslock.app.question.QuestionUi;

/**
 * 预览一道题（R24，原 P2 已提前到 P1）。
 *
 * <p>设置页的预览入口整体处于家长 PIN 门禁之后，故「过 PIN 门禁」由现有门禁天然满足，
 * 无需额外加锁。复用 {@link QuestionUi} 渲染一道当前年级/科目下的题；
 * 选项点击仅高亮对错（家长参考），<b>不解锁、不计入正确率、不进入防重复队列</b>。
 * 预览使用独立的 {@link QuestionEngine} 实例 + {@link QuestionEngine#nextPreviewQuestion()}，
 * 与锁屏真实出题完全隔离。</p>
 */
public class QuestionPreviewActivity extends AppCompatActivity {

    private QuestionEngine engine;
    private Question current;

    private TextView tvStem;
    private TextView tvHint;
    private TextView tvFeedback;
    private Button[] optionButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question_preview);

        // 独立引擎实例：其 recent 队列与锁屏实例天然隔离
        engine = QuestionEngine.create(this, new PrefManager(this));

        tvStem = findViewById(R.id.tvPreviewStem);
        tvHint = findViewById(R.id.tvPreviewHint);
        tvFeedback = findViewById(R.id.tvPreviewFeedback);
        optionButtons = new Button[]{
                findViewById(R.id.btnPreviewOption1),
                findViewById(R.id.btnPreviewOption2),
                findViewById(R.id.btnPreviewOption3),
                findViewById(R.id.btnPreviewOption4)
        };
        for (int i = 0; i < optionButtons.length; i++) {
            final int idx = i;
            optionButtons[i].setOnClickListener(v -> onOptionSelected(idx));
        }

        findViewById(R.id.btnPreviewNext).setOnClickListener(v -> loadNext());
        findViewById(R.id.btnPreviewClose).setOnClickListener(v -> finish());

        loadNext();
    }

    private void loadNext() {
        current = engine.nextPreviewQuestion();
        QuestionUi.render(this, current, tvStem, tvHint, optionButtons);
        tvFeedback.setVisibility(View.GONE);
    }

    /** 预览：仅高亮对错，不解锁不计数 */
    private void onOptionSelected(int index) {
        for (Button b : optionButtons) b.setEnabled(false);

        if (index == current.correctIndex) {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_correct);
            tvFeedback.setText(getString(R.string.preview_correct));
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green));
        } else {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_wrong);
            // 同时标出正确答案所在按钮
            optionButtons[current.correctIndex].setBackgroundResource(R.drawable.btn_option_correct);
            tvFeedback.setText(getString(R.string.preview_show_answer));
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.wrong_red));
        }
        tvFeedback.setVisibility(View.VISIBLE);
    }
}
