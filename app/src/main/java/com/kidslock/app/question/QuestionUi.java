package com.kidslock.app.question;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.kidslock.app.R;

/**
 * 题目渲染复用层（R24 预览 / R25 锁屏共用）。
 *
 * <p>将一道 {@link Question} 渲染到「认字模式」控件组上：题干 + 提示语 + 4 个选项按钮。
 * 题干字号按 {@link Question.StemStyle} 切档（HANZI 大字 130sp / NORMAL 算式 56sp）。</p>
 */
public final class QuestionUi {

    private QuestionUi() {
        // 工具类，禁止实例化
    }

    /**
     * 渲染一道题到给定控件。选项点击的高亮/反馈由调用方自行处理（预览仅高亮、锁屏含解锁计数）。
     */
    public static void render(@NonNull Activity act, @NonNull Question q,
                             @NonNull TextView tvStem, @NonNull TextView tvHint,
                             @NonNull Button[] optionButtons) {
        tvStem.setText(q.stem);
        tvHint.setText(q.hint);

        // 题干字号按 stemStyle 切档（HANZI 大字 130sp / NORMAL 算式 56sp）
        float textSize = act.getResources().getDimension(
                q.stemStyle == Question.StemStyle.HANZI
                        ? R.dimen.text_hanzi : R.dimen.text_stem);
        tvStem.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);

        for (int i = 0; i < optionButtons.length && i < q.options.size(); i++) {
            optionButtons[i].setText(q.options.get(i));
            optionButtons[i].setBackgroundResource(R.drawable.btn_option_bg);
            optionButtons[i].setEnabled(true);
            optionButtons[i].setVisibility(View.VISIBLE);
        }
    }
}
