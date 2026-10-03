package com.example.homeworkscan.data

import java.io.File
/*
 * 学生扫码作业提交与成绩统计（Android 版）
 * Copyright (C) 2026 白林不可燃
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
/**
 * 钉钉成绩模板分析器，逻辑移植自原 Python 版 DingTalkAnalyzer。
 * 自动识别表头行（含"学号"+"学生姓名"）与科目列。
 */
object DingTalkAnalyzer {

    private val EXCLUDE_KEYWORDS = listOf("年级总分排名", "评论", "备注", "总分", "平均分", "班级排名")

    data class TemplateInfo(
        val headerRow: Int,                  // 表头行索引（0-based）
        val dataStartRow: Int,               // 数据起始行索引
        val colMap: Map<String, Pair<String, Int>>,  // 关键列：名称 -> (列名, 列索引)
        val subjects: List<Pair<String, Int>>,       // 识别出的科目：(科目名, 列索引)
        val table: List<MutableList<String>>         // 原始表格
    )

    fun analyze(file: File): TemplateInfo {
        val table = try {
            ExcelHelper.readTable(file)
        } catch (e: Exception) {
            throw Exception("读取模板失败：${e.message}")
        }

        var headerIdx = -1
        for (i in 0 until minOf(20, table.size)) {
            val rowText = table[i].filter { it.isNotEmpty() }.joinToString(" ")
            val hasId = listOf("学号", "编号", "序号").any { it in rowText }
            val hasName = listOf("学生姓名", "姓名", "名字").any { it in rowText }
            if (hasId && hasName) {
                headerIdx = i
                break
            }
        }
        if (headerIdx < 0) throw Exception("无法识别表头行，请确认模板包含\"学号\"和\"学生姓名\"列")

        val header = table[headerIdx]
        val colMap = mutableMapOf<String, Pair<String, Int>>()
        header.forEachIndexed { idx, raw ->
            val v = raw.trim()
            when {
                v.isEmpty() -> {}
                v in listOf("学号", "编号", "序号") -> colMap["学号"] = v to idx
                v in listOf("学生姓名", "姓名") -> colMap["学生姓名"] = v to idx
                v == "班级名称" -> colMap["班级名称"] = v to idx
                v == "年级总分排名" -> colMap["年级总分排名"] = v to idx
                v in listOf("评论", "评语", "备注") -> colMap["评论"] = v to idx
            }
        }
        if ("学号" !in colMap && "学生姓名" !in colMap) {
            throw Exception("表头中未找到\"学号\"或\"学生姓名\"列")
        }

        val nameColIdx = colMap["学生姓名"]?.second ?: -1
        val totalRankIdx = colMap["年级总分排名"]?.second ?: -1

        val subjects = mutableListOf<Pair<String, Int>>()
        header.forEachIndexed { idx, raw ->
            val v = raw.trim()
            if (v.isEmpty()) return@forEachIndexed
            if (nameColIdx >= 0 && idx <= nameColIdx) return@forEachIndexed
            if (totalRankIdx >= 0 && idx >= totalRankIdx) return@forEachIndexed
            if (isSubjectColumn(v)) subjects.add(v to idx)
        }

        return TemplateInfo(headerIdx, headerIdx + 1, colMap, subjects, table)
    }

    private fun isSubjectColumn(colName: String): Boolean {
        for (ex in EXCLUDE_KEYWORDS) if (ex in colName) return false
        if ("年级排名" in colName || "排名" in colName) return false
        if (colName in listOf("学号", "班级名称", "学生姓名", "姓名", "班级")) return false
        return true
    }
}
