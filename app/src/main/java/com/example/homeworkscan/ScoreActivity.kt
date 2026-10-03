package com.example.homeworkscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homeworkscan.data.DataManager
import com.example.homeworkscan.data.ExcelHelper
import com.example.homeworkscan.data.Student
import com.example.homeworkscan.databinding.ActivityScoreBinding
import com.example.homeworkscan.util.ShareHelper
/*
 * 学生扫码作业提交与成绩统计（Android 版）
 * Copyright (C) 2026 白林不可燃
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
/** 成绩录入页：摄像头扫码后弹窗输入成绩，按成绩排序，导出走系统分享 */
class ScoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScoreBinding
    private lateinit var data: DataManager

    private data class Row(
        val student: Student,
        val score: Double?,
        val time: String
    )

    private val rows = mutableListOf<Row>()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openScanner() else
                Toast.makeText(this, "需要摄像头权限才能扫码", Toast.LENGTH_SHORT).show()
        }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val code = result.data?.getStringExtra(ScanActivity.EXTRA_CODE)
            if (result.resultCode == RESULT_OK && !code.isNullOrBlank()) {
                handleCode(code.trim())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sheetName = intent.getStringExtra("sheet") ?: run { finish(); return }
        data = DataManager(this, sheetName)
        title = "$sheetName - 成绩录入"

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnScan.setOnClickListener { checkPermissionAndScan() }
        binding.btnManual.setOnClickListener { showManualInput() }
        binding.btnExportScores.setOnClickListener { exportScores() }
        binding.btnDingTalk.setOnClickListener {
            startActivity(
                Intent(this, DingTalkExportActivity::class.java).putExtra("sheet", sheetName)
            )
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        if (::data.isInitialized) refreshList()
    }

    // ---------- 扫码 ----------

    private fun checkPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openScanner()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openScanner() {
        scanLauncher.launch(Intent(this, ScanActivity::class.java))
    }

    private fun showManualInput() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "输入条码/学号"
        }
        AlertDialog.Builder(this)
            .setTitle("手动输入")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) handleCode(code)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleCode(code: String) {
        val student = data.findByCode(code)
        if (student == null) {
            binding.tvInfo.text = "未找到：$code"
            binding.tvInfo.setTextColor(0xFFB71C1C.toInt())
            return
        }
        val current = data.getScore(student.barcode)?.score
        showScoreDialog(student, current)
    }

    private fun showScoreDialog(student: Student, current: Double?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = "请输入成绩"
            current?.let { setText(trimNum(it)) }
            setSelectAllOnFocus(true)
        }
        binding.tvInfo.text = if (current != null) {
            "${student.name} 当前成绩：${trimNum(current)} 分，请输入新成绩"
        } else {
            "${student.name} (${student.studentId}) 请输入成绩"
        }
        binding.tvInfo.setTextColor(0xFF333333.toInt())

        AlertDialog.Builder(this)
            .setTitle("录入成绩")
            .setView(input)
            .setPositiveButton("确认") { _, _ ->
                val scoreStr = input.text.toString().trim()
                val score = scoreStr.toDoubleOrNull()
                if (score == null) {
                    Toast.makeText(this, "成绩必须是数字！", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                data.setScore(student.barcode, score)
                val t = data.scores[student.barcode]?.time ?: ""
                binding.tvInfo.text = "${student.name} 成绩录入：${trimNum(score)} 分  $t"
                binding.tvInfo.setTextColor(0xFF1B5E20.toInt())
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 列表 ----------

    private fun refreshList() {
        rows.clear()
        data.students.forEach { s ->
            val rec = data.getScore(s.barcode)
            rows.add(Row(s, rec?.score, rec?.time ?: ""))
        }
        // 按成绩降序，无成绩的排最后（与原 Python 版一致）
        rows.sortWith(compareBy({ it.score == null }, { -(it.score ?: 0.0) }))
        binding.recycler.adapter?.notifyDataSetChanged()

        val total = data.students.size
        val scored = rows.filter { it.score != null }
        val avg = if (scored.isNotEmpty()) scored.sumOf { it.score!! } / scored.size else 0.0
        val max = scored.maxOfOrNull { it.score!! } ?: 0.0
        val min = scored.minOfOrNull { it.score!! } ?: 0.0
        binding.tvStat.text =
            "总计：$total 人  |  已录入：${scored.size} 人  |  平均分：%.1f  |  最高：%s  |  最低：%s"
                .format(avg, trimNum(max), trimNum(min))
    }

    private val adapter = object : RecyclerView.Adapter<ScoreVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_score, parent, false)
            return ScoreVH(v)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: ScoreVH, position: Int) {
            val row = rows[position]
            holder.tvRank.text = (position + 1).toString()
            holder.tvName.text = row.student.name
            holder.tvStudentId.text = row.student.studentId
            holder.tvScore.text = row.score?.let { trimNum(it) } ?: "—"
            holder.tvTime.text = row.time
            if (row.score != null) {
                holder.root.setBackgroundColor(0xFFC8E6C9.toInt())
                holder.tvName.setTextColor(0xFF1B5E20.toInt())
                holder.tvScore.setTextColor(0xFF1B5E20.toInt())
            } else {
                holder.root.setBackgroundColor(0xFFFFCDD2.toInt())
                holder.tvName.setTextColor(0xFFB71C1C.toInt())
                holder.tvScore.setTextColor(0xFFB71C1C.toInt())
            }
            holder.itemView.setOnClickListener {
                showScoreDialog(row.student, row.score)
            }
        }
    }

    class ScoreVH(v: View) : RecyclerView.ViewHolder(v) {
        val root: LinearLayout = v.findViewById(R.id.rowRoot)
        val tvRank: TextView = v.findViewById(R.id.tvRank)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvStudentId: TextView = v.findViewById(R.id.tvStudentId)
        val tvScore: TextView = v.findViewById(R.id.tvScore)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
    }

    // ---------- 导出（改为安卓分享） ----------

    private fun exportScores() {
        // 与原 Python 版一致：按成绩降序导出
        val sorted = data.students.map { s ->
            val rec = data.getScore(s.barcode)
            Triple(s, rec?.score, rec?.time ?: "")
        }.sortedByDescending { it.second ?: Double.NEGATIVE_INFINITY }

        val rowsOut = sorted.map { (s, sc, t) ->
            listOf(s.name, s.studentId, sc ?: "", t)
        }
        val file = ShareHelper.uniqueFile(
            ShareHelper.sharedDir(this),
            "成绩表_${data.sheetName}_${DataManager.stamp()}.xlsx"
        )
        try {
            ExcelHelper.writeSheet(file, listOf("姓名", "学号", "成绩", "录入时间"), rowsOut)
            ShareHelper.shareFile(this, file, "分享成绩表")
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun trimNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    override fun onPause() {
        super.onPause()
        if (::data.isInitialized) data.saveData()
    }
}
