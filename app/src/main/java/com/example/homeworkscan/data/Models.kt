package com.example.homeworkscan.data

/** 学生信息，字段与原 Python 版一致 */
data class Student(
    val name: String,
    val studentId: String,   // 学号[学校] 校内学号
    val barcode: String,     // 条形码编号（扫码匹配的主键）
    val classId: String      // 学号[班级] 班级学号
)

/** 作业提交状态配置（与原 Python 版颜色一致） */
object StatusConfig {
    val NAMES = mapOf(
        0 to "待提交",
        1 to "已提交",
        2 to "已提交未完成",
        3 to "已提交完成部分",
        4 to "已退回待重新提交"
    )
    val BG = mapOf(
        0 to 0xFFFFCDD2.toInt(),
        1 to 0xFFC8E6C9.toInt(),
        2 to 0xFFFFF9C4.toInt(),
        3 to 0xFFFFF9C4.toInt(),
        4 to 0xFFFFF9C4.toInt()
    )
    val FG = mapOf(
        0 to 0xFFB71C1C.toInt(),
        1 to 0xFF1B5E20.toInt(),
        2 to 0xFFF57F17.toInt(),
        3 to 0xFFF57F17.toInt(),
        4 to 0xFFF57F17.toInt()
    )
}
