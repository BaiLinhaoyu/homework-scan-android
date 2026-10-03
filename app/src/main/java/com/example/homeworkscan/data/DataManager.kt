package com.example.homeworkscan.data

import android.content.Context
import org.json.JSONObject
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
/**
 * 数据管理：名单来自 Excel（filesDir/data/xxx.xlsx），
 * 作业/成绩记录存 JSON（filesDir/data/xxx_data.json），与原 Python 版逻辑一致。
 */
class DataManager(context: Context, val sheetName: String) {

    data class HwRecord(val status: Int, val time: String)
    data class ScoreRecord(val score: Double, val time: String)

    val excelFile: File = File(context.filesDir, "data/$sheetName.xlsx")
    private val dataFile: File = File(context.filesDir, "data/${sheetName}_data.json")

    var students: List<Student> = emptyList()
        private set
    val homework = mutableMapOf<String, HwRecord>()
    val scores = mutableMapOf<String, ScoreRecord>()

    init {
        students = ExcelHelper.readStudents(excelFile)
        loadData()
    }

    fun loadData() {
        if (!dataFile.exists()) return
        try {
            val root = JSONObject(dataFile.readText(Charsets.UTF_8))
            root.optJSONObject("homework")?.let { hw ->
                for (key in hw.keys()) {
                    val o = hw.getJSONObject(key)
                    homework[key] = HwRecord(o.getInt("status"), o.optString("time", ""))
                }
            }
            root.optJSONObject("scores")?.let { sc ->
                for (key in sc.keys()) {
                    val o = sc.getJSONObject(key)
                    scores[key] = ScoreRecord(o.getDouble("score"), o.optString("time", ""))
                }
            }
        } catch (_: Exception) {
        }
    }

    fun saveData() {
        try {
            dataFile.parentFile?.mkdirs()
            val hw = JSONObject()
            for ((k, v) in homework) {
                hw.put(k, JSONObject().put("status", v.status).put("time", v.time))
            }
            val sc = JSONObject()
            for ((k, v) in scores) {
                sc.put(k, JSONObject().put("score", v.score).put("time", v.time))
            }
            val root = JSONObject().put("homework", hw).put("scores", sc)
            dataFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun clearAll() {
        homework.clear()
        scores.clear()
        saveData()
    }

    /** 与原 Python 版一致：依次按 条形码编号 -> 校内学号 -> 班级学号 匹配 */
    fun findByCode(code: String): Student? {
        students.firstOrNull { it.barcode == code }?.let { return it }
        students.firstOrNull { it.studentId == code }?.let { return it }
        students.firstOrNull { it.classId == code }?.let { return it }
        return null
    }

    fun setHomeworkStatus(barcode: String, status: Int) {
        homework[barcode] = HwRecord(status, now())
        saveData()
    }

    fun getHomeworkStatus(barcode: String): HwRecord = homework[barcode] ?: HwRecord(0, "")

    fun setScore(barcode: String, score: Double) {
        scores[barcode] = ScoreRecord(score, now())
        saveData()
    }

    fun getScore(barcode: String): ScoreRecord? = scores[barcode]

    companion object {
        fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        fun stamp(): String = SimpleDateFormat("MMdd_HHmm", Locale.getDefault()).format(Date())

        fun dataDir(context: Context): File =
            File(context.filesDir, "data").apply { mkdirs() }

        /** 列出全部名单名（不含模板文件） */
        fun listSheets(context: Context): List<String> =
            dataDir(context).listFiles()
                ?.filter { it.name.endsWith(".xlsx") && !it.name.endsWith("_template.xlsx") }
                ?.map { it.name.removeSuffix(".xlsx") }
                ?.sorted()
                ?: emptyList()

        fun excelFile(context: Context, name: String) = File(dataDir(context), "$name.xlsx")

        fun deleteSheet(context: Context, name: String) {
            excelFile(context, name).delete()
            File(dataDir(context), "${name}_data.json").delete()
        }
    }
}
