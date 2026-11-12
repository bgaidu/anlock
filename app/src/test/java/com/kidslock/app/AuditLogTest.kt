package com.kidslock.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLogTest {

    @Test
    fun formatsSourceActionAndDetail() {
        val line = AuditLog.formatLine(
            timeMillis = 0L,
            source = "设置",
            action = "应用加时",
            detail = "「哔哩哔哩」+10 分钟"
        )
        assertTrue(line.contains(" | 设置 | 应用加时 | 「哔哩哔哩」+10 分钟"))
    }

    @Test
    fun flattensNewlinesIntoOneLine() {
        val line = AuditLog.formatLine(0L, "通知", "家长加时", "第一行\n第二行\r第三行")
        assertFalse(line.contains('\n'))
        assertFalse(line.contains('\r'))
        assertTrue(line.endsWith("第一行 第二行 第三行"))
    }

    @Test
    fun omitsDetailSeparatorWhenDetailIsBlank() {
        val line = AuditLog.formatLine(0L, "设置", "开启应用限额", "   ")
        assertTrue(line.endsWith(" | 设置 | 开启应用限额"))
        assertFalse(line.endsWith("|"))
    }
}
