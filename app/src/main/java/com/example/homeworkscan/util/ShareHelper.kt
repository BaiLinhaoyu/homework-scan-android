package com.example.homeworkscan.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** 安卓分享工具：把导出的文件通过系统分享面板转发（钉钉/微信/QQ/邮件等） */
object ShareHelper {

    /** 导出文件的缓存目录（已在 file_paths.xml 中注册给 FileProvider） */
    fun sharedDir(context: Context): File =
        File(context.cacheDir, "shared").apply { mkdirs() }

    /** 生成不重复的文件名：xxx.xlsx -> xxx(1).xlsx -> xxx(2).xlsx */
    fun uniqueFile(dir: File, fileName: String): File {
        var f = File(dir, fileName)
        if (!f.exists()) return f
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var counter = 1
        while (f.exists()) {
            f = File(dir, "$base($counter)$ext")
            counter++
        }
        return f
    }

    /** 调起系统分享面板发送文件 */
    fun shareFile(context: Context, file: File, title: String = "分享文件") {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
