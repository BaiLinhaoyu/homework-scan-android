package com.example.homeworkscan

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.homeworkscan.data.DataManager
import com.example.homeworkscan.data.DingTalkAnalyzer
import com.example.homeworkscan.data.ExcelHelper
import com.example.homeworkscan.databinding.ActivityDingtalkBinding
import com.example.homeworkscan.util.ShareHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/*
 * 学生扫码作业提交与成绩统计（Android 版）
 * Copyright (C) 2026 白林不可燃
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
/** 钉钉成绩导出页：选模板 -> 识别科目 -> 填充成绩 -> 系统分享 */
class DingTalkExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDingtalkBinding
    private lateinit var data: DataManager

    private var templateFile: File? = null
    private var templateInfo: DingTalkAnalyzer.TemplateInfo? = null
    private var selectedSubjects: List<Pair<String, Int>> = emptyList()

    private val pickTemplate =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onTemplatePicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDingtalkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sheetName = intent.getStringExtra("sheet") ?: run { finish(); return }
        data = DataManager(this, sheetName)
        title = "$sheetName - 钉钉成绩导出"

        binding.btnPickTemplate.setOnClickListener {
            pickTemplate.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "application/octet-stream"
                )
            )
        }
        binding.btnExport.setOnClickListener { exportGrades() }
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.tvLog.append("[$time] $msg\n")
    }

    private fun onTemplatePicked(uri: android.net.Uri) {
        try {
            // 拷贝到缓存再解析，避免 SAF 流只能读一次的问题
            val dest = File(cacheDir, "dingtalk_template.xlsx")
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: throw Exception("无法读取所选文件")

            templateFile = dest
            binding.tvTemplate.text = uri.lastPathSegment ?: "已选择"
            log("已选择模板：${uri.lastPathSegment}")

            val info = DingTalkAnalyzer.analyze(dest)
            templateInfo = info
            log("识别到表头在第 ${info.headerRow + 1} 行")
            log("识别到 ${info.subjects.size} 个科目：${info.subjects.map { it.first }}")

            when {
                info.subjects.isEmpty() -> {
                    binding.tvSubjects.text = "未识别到科目列，请检查模板格式"
                    log("错误：未识别到科目列")
                }
                info.subjects.size == 1 -> {
                    selectedSubjects = info.subjects
                    binding.tvSubjects.text = "自动选中科目：${info.subjects[0].first}（共1个科目）"
                    log("自动选中科目：${info.subjects[0].first}")
                    binding.btnExport.isEnabled = true
                    updatePreview()
                }
                else -> {
                    binding.tvSubjects.text =
                        "识别到 ${info.subjects.size} 个科目：${info.subjects.joinToString("、") { it.first }}"
                    showSubjectDialog(info.subjects)
                }
            }
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("模板识别失败")
                .setMessage(e.message)
                .setPositiveButton("确定", null)
                .show()
            log("识别失败：${e.message}")
        }
    }

    private fun showSubjectDialog(subjects: List<Pair<String, Int>>) {
        val names = subjects.map { it.first }.toTypedArray()
        val checked = BooleanArray(subjects.size)
        AlertDialog.Builder(this)
            .setTitle("检测到多个科目，请选择要导出的科目（可多选）")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("确认") { _, _ ->
                val chosen = subjects.filterIndexed { i, _ -> checked[i] }
                if (chosen.isEmpty()) {
                    log("用户未选择科目")
                    return@setPositiveButton
                }
                selectedSubjects = chosen
                binding.tvSubjects.text =
                    "已选择 ${chosen.size} 个科目：${chosen.joinToString("、") { it.first }}"
                log("用户选择了：${chosen.map { it.first }}")
                binding.btnExport.isEnabled = true
                updatePreview()
            }
            .setNegativeButton("取消") { _, _ -> log("用户取消了科目选择") }
            .show()
    }

    private fun updatePreview() {
        val total = data.students.size
        val scored = data.students.count { data.getScore(it.barcode) != null }
        log("当前名单：${data.sheetName}（${total}人），已录入成绩：${scored}人 / 未录入：${total - scored}人")
    }

    /** 生成成绩表并调起系统分享（替代原来的"保存到本地+打开文件夹"） */
    private fun exportGrades() {
        val info = templateInfo ?: return
        if (selectedSubjects.isEmpty()) return

        try {
            // 建立 学号/姓名 -> 成绩 映射（与原 Python 版一致）
            val scoresMap = mutableMapOf<String, Double>()
            for (s in data.students) {
                val rec = data.getScore(s.barcode) ?: continue
                if (s.studentId.isNotEmpty()) scoresMap[s.studentId] = rec.score
                scoresMap[s.name] = rec.score
            }
            log("已录入成绩的学生：${data.students.count { data.getScore(it.barcode) != null }}人")

            val idColIdx = info.colMap["学号"]?.second ?: -1
            val nameColIdx = info.colMap["学生姓名"]?.second ?: -1
            val totalRankIdx = info.colMap["年级总分排名"]?.second ?: -1

            // 复制模板，填充成绩
            val result: MutableList<MutableList<Any?>> =
                info.table.map { row -> row.map { it as Any? }.toMutableList() }.toMutableList()

            fun cell(row: List<Any?>, idx: Int): String =
                if (idx in row.indices) (row[idx]?.toString() ?: "").trim() else ""

            var filledCount = 0
            for (r in info.dataStartRow until result.size) {
                val row = result[r]
                val sid = cell(row, idColIdx)
                val sname = cell(row, nameColIdx)
                if ((sid.isEmpty() || sid == "nan" || sid == "None") &&
                    (sname.isEmpty() || sname == "nan" || sname == "None")
                ) continue

                val score = scoresMap[sid] ?: scoresMap[sname] ?: continue
                for ((_, subjIdx) in selectedSubjects) {
                    while (row.size <= subjIdx) row.add("")
                    row[subjIdx] = score
                }
                filledCount++
            }
            log("成功填充 $filledCount 名学生的成绩")

            // 年级总分排名（与原 Python 版一致：选中科目求和后排名）
            if (totalRankIdx >= 0) {
                val rowScores = mutableListOf<Pair<Int, Double>>()
                for (r in info.dataStartRow until result.size) {
                    val row = result[r]
                    val sid = cell(row, idColIdx)
                    if (sid.isEmpty() || sid == "nan" || sid == "None") continue
                    var total = 0.0
                    var hasScore = false
                    for ((_, subjIdx) in selectedSubjects) {
                        val v = cell(row, subjIdx)
                        v.toDoubleOrNull()?.let {
                            total += it
                            hasScore = true
                        }
                    }
                    if (hasScore) rowScores.add(r to total)
                }
                rowScores.sortByDescending { it.second }
                rowScores.forEachIndexed { rank, (rowIdx, _) ->
                    val row = result[rowIdx]
                    while (row.size <= totalRankIdx) row.add("")
                    row[totalRankIdx] = rank + 1
                }
                log("已计算并填充 ${rowScores.size} 人的年级总分排名")
            }

            val outFile = ShareHelper.uniqueFile(
                ShareHelper.sharedDir(this),
                "钉钉成绩_${data.sheetName}_${DataManager.stamp()}.xlsx"
            )
            ExcelHelper.writeRawTable(outFile, result)
            log("文件已生成：${outFile.name}")

            ShareHelper.shareFile(this, outFile, "分享钉钉成绩表")
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("导出失败")
                .setMessage(e.message)
                .setPositiveButton("确定", null)
                .show()
            log("导出失败：${e.message}")
        }
    }
}
