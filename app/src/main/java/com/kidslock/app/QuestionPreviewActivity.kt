package com.kidslock.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kidslock.app.question.QuestionEngine
import com.kidslock.app.question.QuestionUi

class QuestionPreviewActivity : AppCompatActivity() {

    private lateinit var engine: QuestionEngine
    private lateinit var current: com.kidslock.app.question.Question

    private lateinit var tvStem: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var optionButtons: Array<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        setContentView(R.layout.activity_question_preview)

        engine = QuestionEngine.create(this, PrefManager(this))

        tvStem = findViewById(R.id.tvPreviewStem)
        tvHint = findViewById(R.id.tvPreviewHint)
        tvFeedback = findViewById(R.id.tvPreviewFeedback)
        optionButtons = arrayOf(
            findViewById(R.id.btnPreviewOption1),
            findViewById(R.id.btnPreviewOption2),
            findViewById(R.id.btnPreviewOption3),
            findViewById(R.id.btnPreviewOption4)
        )
        for (i in optionButtons.indices) {
            val idx = i
            optionButtons[i].setOnClickListener { onOptionSelected(idx) }
        }

        findViewById<View>(R.id.btnPreviewNext).setOnClickListener { loadNext() }
        findViewById<View>(R.id.btnPreviewClose).setOnClickListener { finish() }

        loadNext()
    }

    @Suppress("DEPRECATION")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    private fun loadNext() {
        current = engine.nextPreviewQuestion()
        QuestionUi.render(this, current, tvStem, tvHint, optionButtons)
        tvFeedback.visibility = View.GONE
    }

    private fun onOptionSelected(index: Int) {
        for (b in optionButtons) b.isEnabled = false

        if (index == current.correctIndex) {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_correct)
            tvFeedback.text = getString(R.string.preview_correct)
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
        } else {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_wrong)
            optionButtons[current.correctIndex].setBackgroundResource(R.drawable.btn_option_correct)
            tvFeedback.text = getString(R.string.preview_show_answer)
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
        }
        tvFeedback.visibility = View.VISIBLE
    }
}
