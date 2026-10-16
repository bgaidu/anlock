package com.kidslock.app.question

import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.kidslock.app.R

object QuestionUi {
    @JvmStatic
    fun render(
        act: Activity, q: Question,
        tvStem: TextView, tvHint: TextView,
        optionButtons: Array<Button>
    ) {
        tvStem.text = q.stem
        tvHint.text = q.hint

        val textSize = act.resources.getDimension(
            if (q.stemStyle == Question.StemStyle.HANZI) R.dimen.text_hanzi else R.dimen.text_stem
        )
        tvStem.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)

        for (i in optionButtons.indices) {
            if (i < q.options.size) {
                optionButtons[i].text = q.options[i]
                optionButtons[i].setBackgroundResource(R.drawable.btn_option_bg)
                optionButtons[i].isEnabled = true
                optionButtons[i].visibility = View.VISIBLE
            }
        }
    }
}
