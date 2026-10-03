package com.example.homeworkscan

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.homeworkscan.data.DataManager
import com.example.homeworkscan.databinding.ActivitySheetMenuBinding
/*
 * 学生扫码作业提交与成绩统计（Android 版）
 * Copyright (C) 2026 白林不可燃
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
/** 功能选择页：作业统计 / 成绩录入，进入时询问是否继续上次数据 */
class SheetMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySheetMenuBinding
    private lateinit var data: DataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySheetMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sheetName = intent.getStringExtra("sheet") ?: run { finish(); return }
        data = DataManager(this, sheetName)

        binding.tvTitle.text = sheetName
        binding.tvCount.text = "共 ${data.students.size} 名学生"

        askResumeOrClear()  // 关键：询问是否继续

        binding.btnHomework.setOnClickListener {
            startActivity(Intent(this, HomeworkActivity::class.java).putExtra("sheet", sheetName))
        }
        binding.btnScore.setOnClickListener {
            startActivity(Intent(this, ScoreActivity::class.java).putExtra("sheet", sheetName))
        }
        binding.btnBack.setOnClickListener { finish() }
    }

    /** 与原 Python 版一致：检测到历史数据时询问继续还是清空 */
    private fun askResumeOrClear() {
        if (data.homework.isEmpty() && data.scores.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("继续上次操作？")
            .setMessage(
                "名单 '${data.sheetName}' 检测到上次未清空的数据：\n" +
                        "  • 作业记录：${data.homework.size} 条\n" +
                        "  • 成绩记录：${data.scores.size} 条\n\n" +
                        "【继续】保留数据，继续操作\n" +
                        "【清空】清空数据，重新开始"
            )
            .setPositiveButton("继续", null)
            .setNegativeButton("清空") { _, _ ->
                data.clearAll()
            }
            .setCancelable(false)
            .show()
    }

    override fun onPause() {
        super.onPause()
    }
}