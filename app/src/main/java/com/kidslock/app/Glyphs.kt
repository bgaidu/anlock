package com.kidslock.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build

/**
 * 图标字符兜底。
 *
 * 各厂商系统字体覆盖差异很大：例如小米电视 4A（Android 6）的系统字体里既没有 ✓ ⭐ ✅ ❌ ⚠️，
 * 也没有 emoji 字体，这些字符会画成空心方框（豆腐块）。这里在运行时检查当前设备能否真正
 * 画出某个字符，按「好看 → 简单 → ASCII」的顺序挑第一个可用的，换到别的机器上也不会再出现方框。
 *
 * 约定：只在 UI 层（Activity / 弹窗文案）使用；纯逻辑层（如 QuestionEngine）请直接用中文或 ASCII，
 * 避免把 Android 依赖带进 JVM 单测。
 */
object Glyphs {
    /** 勾选标记（年级 / 科目按钮的已选前缀） */
    val check: String by lazy { pick("\u2713", "\u221A", "v") }
    /** 叉号（否定状态） */
    val cross: String by lazy { pick("\u2717", "\u00D7", "x") }
    /** 星星（积分单位） */
    val star: String by lazy { pick("\u2B50", "\u2605", "\u2606", "*") }
    /** 管控体检：已通过 */
    val ok: String by lazy { pick("\u2705", "\u2713", "\u221A", "+") }
    /** 管控体检：风险 / 警告 */
    val warn: String by lazy { pick("\u26A0", "!", "(!)") }
    /** 路径箭头（设置 → 应用 → …） */
    val arrow: String by lazy { pick("\u2192", "->") }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun pick(vararg candidates: String): String {
        for (candidate in candidates) {
            if (canRender(candidate)) return candidate
        }
        return candidates.last()
    }

    private fun canRender(text: String): Boolean {
        if (text.isEmpty()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            paint.hasGlyph(text)
        } else {
            // API 21/22 没有 Paint.hasGlyph()：见 looksLikeTofu()
            !looksLikeTofu(text)
        }
    }

    /**
     * 把字符和一个私有区码位（几乎所有字体都没有，必定画成缺字方块）各画一遍，
     * 像素完全一致就说明这个字符也是缺字。
     */
    private fun looksLikeTofu(text: String): Boolean {
        val glyph = render(text) ?: return false
        val notdef = render("\uE000") ?: return false
        return glyph.sameAs(notdef)
    }

    private fun render(text: String): Bitmap? = try {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        paint.textSize = 40f
        paint.color = Color.WHITE
        canvas.drawText(text, 4f, 40f, paint)
        bitmap
    } catch (_: Throwable) {
        null
    }
}
