package com.example.homeworkscan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homeworkscan.data.DataManager
import com.example.homeworkscan.data.ExcelHelper
import com.example.homeworkscan.databinding.ActivityMainBinding
import java.io.File
/*
 * 学生扫码作业提交与成绩统计（Android 版）
 * Copyright (C) 2026 白林不可燃
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
/** 名单管理页：新建/导入/删除/进入名单 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sheets = mutableListOf<String>()

    /** 从系统文件选择器导入 Excel 名单 */
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) askNameAndImport(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerSheets.layoutManager = LinearLayoutManager(this)
        binding.recyclerSheets.adapter = sheetAdapter

        binding.btnCreate.setOnClickListener { createSheet() }
        binding.btnImport.setOnClickListener {
            importLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "application/octet-stream"
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        sheets.clear()
        sheets.addAll(DataManager.listSheets(this))
        binding.recyclerSheets.adapter?.notifyDataSetChanged()
    }

    private val sheetAdapter = object : RecyclerView.Adapter<SheetVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SheetVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sheet, parent, false)
            return SheetVH(v)
        }

        override fun getItemCount() = sheets.size

        override fun onBindViewHolder(holder: SheetVH, position: Int) {
            val name = sheets[position]
            holder.tvName.text = name
            val count = try {
                ExcelHelper.readStudents(DataManager.excelFile(this@MainActivity, name)).size
            } catch (e: Exception) {
                0
            }
            holder.tvCount.text = "$count 人"
            holder.itemView.setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, SheetMenuActivity::class.java)
                        .putExtra("sheet", name)
                )
            }
            holder.itemView.setOnLongClickListener {
                confirmDelete(name)
                true
            }
        }
    }

    class SheetVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvSheetName)
        val tvCount: TextView = v.findViewById(R.id.tvSheetCount)
    }

    private fun createSheet() {
        val input = EditText(this).apply { hint = "如：高二3班、数学课" }
        AlertDialog.Builder(this)
            .setTitle("新建名单")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (name in DataManager.listSheets(this)) {
                    Toast.makeText(this, "名单 '$name' 已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                try {
                    ExcelHelper.createTemplate(DataManager.excelFile(this, name))
                    Toast.makeText(
                        this,
                        "名单 '$name' 已创建（含示例行），可长按删除或导入正式名单替换",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshList()
                } catch (e: Exception) {
                    Toast.makeText(this, "创建失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun askNameAndImport(uri: Uri) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "为此名单命名"
        }
        AlertDialog.Builder(this)
            .setTitle("导入名单")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (name in DataManager.listSheets(this)) {
                    Toast.makeText(this, "名单 '$name' 已存在，请重新命名", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                try {
                    val dest = DataManager.excelFile(this, name)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        dest.outputStream().use { inputStream.copyTo(it) }
                    } ?: throw Exception("无法读取所选文件")
                    Toast.makeText(this, "名单 '$name' 已导入", Toast.LENGTH_SHORT).show()
                    refreshList()
                } catch (e: Exception) {
                    Toast.makeText(this, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除名单 '$name' 吗？\n（作业与成绩记录将一并删除，不可恢复）")
            .setPositiveButton("删除") { _, _ ->
                DataManager.deleteSheet(this, name)
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
