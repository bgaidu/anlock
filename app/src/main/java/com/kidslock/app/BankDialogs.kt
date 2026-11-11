package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.kidslock.app.question.BankRepository
import com.kidslock.app.question.CustomBankStore
import com.kidslock.app.question.Grade
import com.kidslock.app.question.Subject

/**
 * 题库管理相关弹窗：总量总览、单科目/年级详情、导入/导出/清除。
 *
 * 文件选择由调用方（MainActivity）负责，因为 ActivityResultLauncher 必须注册在 Activity 上；
 * 这里只把「选了哪个科目/年级」回调出去。
 */
object BankDialogs {
    private val SUBJECTS = arrayOf(Subject.PINYIN, Subject.ENGLISH, Subject.CITU)

    fun showManager(
        activity: Activity,
        onImport: (String, Int) -> Unit,
        onExport: (String, Int) -> Unit,
        onChanged: () -> Unit
    ) {
        val repo = BankRepository.get(activity)
        val subjects = ArrayList<String>()
        val grades = ArrayList<Int>()
        val labels = ArrayList<String>()
        for (subject in SUBJECTS) {
            for (grade in Grade.MIN..Grade.MAX) {
                val result = repo.load(subject, grade)
                val custom = if (CustomBankStore.hasCustom(activity, subject, grade)) " · 已自定义" else ""
                labels.add(
                    "${Subject.label(subject)} · ${Grade.fullLabel(grade)}：${result.entries.size} 条" +
                            "（${sourceLabel(result)}$custom）"
                )
                subjects.add(subject)
                grades.add(grade)
            }
        }
        AlertDialog.Builder(activity)
            .setTitle("题库管理")
            .setItems(labels.toTypedArray()) { _, which ->
                showDetail(activity, subjects[which], grades[which], onImport, onExport, onChanged)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /** 从「导入题库」按钮进入：先选科目再选年级。 */
    fun showImportPicker(activity: Activity, onPick: (String, Int) -> Unit) {
        showSubjectPicker(activity, activity.getString(R.string.import_choose_subject)) { subject ->
            AlertDialog.Builder(activity)
                .setTitle(R.string.import_choose_grade)
                .setItems(gradeLabels()) { _, whichGrade ->
                    onPick(subject, whichGrade)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    /** 从「清除自定义题库」按钮进入：选科目/年级后直接清除。 */
    fun showClearPicker(activity: Activity, onChanged: () -> Unit) {
        showSubjectPicker(activity, activity.getString(R.string.clear_choose_subject)) { subject ->
            AlertDialog.Builder(activity)
                .setTitle(R.string.clear_choose_grade)
                .setItems(gradeLabels()) { _, whichGrade ->
                    val ok = CustomBankStore.clear(activity, subject, whichGrade)
                    BankRepository.get(activity).clearCache()
                    Toast.makeText(
                        activity,
                        if (ok) activity.getString(
                            R.string.clear_done,
                            Subject.label(subject), Grade.fullLabel(whichGrade)
                        ) else activity.getString(R.string.clear_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    onChanged()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    fun showDetail(
        activity: Activity,
        subject: String,
        grade: Int,
        onImport: (String, Int) -> Unit,
        onExport: (String, Int) -> Unit,
        onChanged: () -> Unit
    ) {
        val result = BankRepository.get(activity).load(subject, grade)
        val hasCustom = CustomBankStore.hasCustom(activity, subject, grade)
        val usedGrade = result.gradesUsed.firstOrNull() ?: grade
        val preview = StringBuilder()
        result.entries.take(12).forEach { preview.append(it.k).append(" → ").append(it.v).append('\n') }
        if (result.entries.size > 12) preview.append("…（仅显示前 12 条）")
        if (result.entries.isEmpty()) preview.append("（暂无可用题目）")
        val message = buildString {
            append("条数：").append(result.entries.size).append('\n')
            append("来源：").append(sourceLabel(result)).append('\n')
            if (usedGrade != grade) {
                append("兜底：使用 ").append(Grade.fullLabel(usedGrade)).append(" 的题库\n")
            }
            append("自定义：").append(if (hasCustom) "有（优先使用）" else "无").append("\n\n")
            append("预览：\n").append(preview)
        }
        val actions = ArrayList<String>()
        actions.add("导入/替换自定义题库")
        actions.add("导出当前题库")
        if (hasCustom) actions.add("清除自定义（恢复内置）")
        AlertDialog.Builder(activity)
            .setTitle("${Subject.label(subject)} · ${Grade.fullLabel(grade)}")
            .setMessage(message)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "导入/替换自定义题库" -> onImport(subject, grade)
                    "导出当前题库" -> onExport(subject, grade)
                    "清除自定义（恢复内置）" -> confirmClear(
                        activity, subject, grade, onImport, onExport, onChanged
                    )
                    else -> showManager(activity, onImport, onExport, onChanged)
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun confirmClear(
        activity: Activity,
        subject: String,
        grade: Int,
        onImport: (String, Int) -> Unit,
        onExport: (String, Int) -> Unit,
        onChanged: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("清除自定义题库")
            .setMessage("将删除 ${Subject.label(subject)} · ${Grade.fullLabel(grade)} 的自定义题库，恢复使用内置题库。")
            .setPositiveButton("清除") { _, _ ->
                val ok = CustomBankStore.clear(activity, subject, grade)
                BankRepository.get(activity).clearCache()
                Toast.makeText(
                    activity,
                    if (ok) "已清除自定义题库" else "清除失败",
                    Toast.LENGTH_SHORT
                ).show()
                onChanged()
                showDetail(activity, subject, grade, onImport, onExport, onChanged)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSubjectPicker(activity: Activity, title: String, onPick: (String) -> Unit) {
        val labels = SUBJECTS.map { Subject.label(it) }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(labels) { _, which -> onPick(SUBJECTS[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun gradeLabels(): Array<String> =
        (Grade.MIN..Grade.MAX).map { Grade.fullLabel(it) }.toTypedArray()

    private fun sourceLabel(result: BankRepository.LoadResult): String = when {
        result.entries.isEmpty() -> "缺失"
        result.level == 0 -> "自定义"
        result.level == 1 -> "内置题库"
        result.level == 2 -> "邻近年级兜底"
        result.level == 3 -> "代码兜底"
        else -> "未知"
    }
}
