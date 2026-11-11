package com.kidslock.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.max

/**
 * 近 7 天使用时长柱状图（纯 Canvas，无第三方依赖）。
 * 数据按时间倒序传入（今天在前），绘制时从左到右改为时间正序。
 */
class WeeklyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<Pair<String, Long>> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33888888")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setData(history: List<Pair<String, Long>>) {
        data = history.asReversed()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val density = resources.displayMetrics.density
        textPaint.textSize = 10f * density
        valuePaint.textSize = 10f * density

        val labelArea = 16f * density
        val valueArea = 14f * density
        val chartTop = valueArea
        val chartBottom = height - labelArea
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val slot = width.toFloat() / data.size
        val barWidth = slot * 0.55f

        val maxMs = max(1L, data.maxOfOrNull { it.second } ?: 0L)
        // 至少按 10 分钟作为满格刻度，避免只有几分钟时柱子顶满失真
        val scaleMax = max(maxMs, 10 * 60_000L)

        for ((index, item) in data.withIndex()) {
            val centerX = slot * index + slot / 2f
            val ratio = item.second.toFloat() / scaleMax
            val barHeight = (chartHeight * ratio).coerceAtLeast(if (item.second > 0) 2f * density else 0f)
            val rect = RectF(
                centerX - barWidth / 2f,
                chartBottom - barHeight,
                centerX + barWidth / 2f,
                chartBottom
            )
            canvas.drawRoundRect(
                rect,
                4f * density,
                4f * density,
                if (item.second > 0) barPaint else emptyPaint
            )

            val minutes = (item.second + 59_999) / 60_000
            if (minutes > 0) {
                canvas.drawText("$minutes", centerX, rect.top - 3f * density, valuePaint)
            }

            // 日期标签：MM-dd
            val day = item.first.takeLast(5)
            canvas.drawText(day, centerX, height - 3f * density, textPaint)
        }

        if (valueArea > 0) {
            val unit = String.format(Locale.US, "满格 %d 分钟", (scaleMax / 60_000).toInt())
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(unit, 0f, 10f * density, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
        }
    }
}
