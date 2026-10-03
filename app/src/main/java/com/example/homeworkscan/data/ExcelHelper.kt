package com.example.homeworkscan.data

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
/*
 * 学生扫码作业提交与成绩统计（Android 版）
 * Copyright (C) 2026 白林不可燃
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
/** Excel 读写工具（Apache POI），对应原 Python 版的 pandas 读写 */
object ExcelHelper {

    private val NAME_COLS = listOf("姓名", "名字", "学生姓名", "name", "Name")
    private val SCHOOL_ID_COLS = listOf("学号[学校]", "校内学号", "学号", "编号", "ID", "id", "student_id")
    private val BARCODE_COLS = listOf("条形码编号", "条码", "条形码", "barcode", "Barcode")
    private val CLASS_ID_COLS = listOf("学号[班级]", "班级学号", "班级编号", "class_id")

    /** 读取第一个工作表为字符串二维表（保留空单元格位置） */
    fun readTable(file: File): List<MutableList<String>> {
        FileInputStream(file).use { fis ->
            val wb = WorkbookFactory.create(fis)
            val sheet = wb.getSheetAt(0)
            val formatter = DataFormatter()
            val evaluator = wb.creationHelper.createFormulaEvaluator()
            val result = mutableListOf<MutableList<String>>()
            for (row in sheet) {
                val cells = MutableList(row.lastCellNum.coerceAtLeast(0).toInt()) { "" }
                for (cell in row) {
                    val text = try {
                        if (cell.cellType == CellType.FORMULA) {
                            formatter.formatCellValue(cell, evaluator)
                        } else {
                            formatter.formatCellValue(cell)
                        }
                    } catch (_: Exception) {
                        ""
                    }
                    cells[cell.columnIndex] = text.trim()
                }
                result.add(cells)
            }
            wb.close()
            return result
        }
    }

    /** 读取学生名单，列识别规则与原 Python 版一致 */
    fun readStudents(file: File): List<Student> {
        if (!file.exists()) return emptyList()
        val table = readTable(file)
        if (table.isEmpty()) return emptyList()
        val header = table[0]

        fun findCol(candidates: List<String>): Int =
            header.indexOfFirst { it.trim() in candidates }

        var nameCol = findCol(NAME_COLS)
        var schoolIdCol = findCol(SCHOOL_ID_COLS)
        var barcodeCol = findCol(BARCODE_COLS)
        var classIdCol = findCol(CLASS_ID_COLS)

        if (nameCol < 0) nameCol = 0
        if (schoolIdCol < 0) schoolIdCol = if (header.size >= 2) 1 else 0
        if (barcodeCol < 0) barcodeCol = if (header.size > 2) 2 else schoolIdCol
        if (classIdCol < 0) classIdCol = if (header.size > 3) 3 else -1

        val students = mutableListOf<Student>()
        for (i in 1 until table.size) {
            val row = table[i]
            fun cell(idx: Int): String = if (idx in row.indices) row[idx].trim() else ""
            val name = cell(nameCol)
            if (name.isEmpty() || name == "nan" || name == "None") continue
            val sid = cell(schoolIdCol)
            val bc = cell(barcodeCol).ifEmpty { sid }
            val cid = cell(classIdCol)
            students.add(Student(name, sid, bc, cid))
        }
        return students
    }

    /** 新建名单模板（与原 Python 版一致，带一行示例数据） */
    fun createTemplate(file: File) {
        writeSheet(
            file,
            listOf("姓名", "学号[学校]", "条形码编号", "学号[班级]"),
            listOf(listOf("张三", "2023001", "89568974", "01"))
        )
    }

    /** 写出简单 Excel 表格：表头 + 数据行 */
    fun writeSheet(file: File, headers: List<String>, rows: List<List<Any?>>) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Sheet1")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        rows.forEachIndexed { r, row ->
            val excelRow = sheet.createRow(r + 1)
            row.forEachIndexed { c, v ->
                val cell = excelRow.createCell(c)
                when (v) {
                    null -> cell.setCellValue("")
                    is Double -> cell.setCellValue(v)
                    is Int -> cell.setCellValue(v.toDouble())
                    else -> cell.setCellValue(v.toString())
                }
            }
        }
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { wb.write(it) }
        wb.close()
    }

    /** 按字符串二维表原样写出（用于钉钉模板导出，保留模板行结构） */
    fun writeRawTable(file: File, table: List<List<Any?>>) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Sheet1")
        table.forEachIndexed { r, row ->
            val excelRow = sheet.createRow(r)
            row.forEachIndexed { c, v ->
                val cell = excelRow.createCell(c)
                when (v) {
                    null -> cell.setCellValue("")
                    is Double -> cell.setCellValue(v)
                    is Int -> cell.setCellValue(v.toDouble())
                    else -> cell.setCellValue(v.toString())
                }
            }
        }
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { wb.write(it) }
        wb.close()
    }
}
