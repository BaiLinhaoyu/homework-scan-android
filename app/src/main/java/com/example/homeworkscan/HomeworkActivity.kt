package com.example.homeworkscan

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
import com.example.homeworkscan.data.StatusConfig
import com.example.homeworkscan.data.Student
import com.example.homeworkscan.databinding.ActivityHomeworkBinding
import com.example.homeworkscan.util.ShareHelper

/** 作业统计页：支持连续扫码，点击学生改状态，导出走系统分享 */
class HomeworkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeworkBinding
    private lateinit var data: DataManager
    private var scanActivity: ScanContinuousActivity? = null

    private data class Row(
        val student: Student,
        val status: Int,
        val time: String
    )

    private val rows = mutableListOf<Row>()


    /** 摄像头权限请求 -> 成功后打开连续扫码页 */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openContinuousScanner() else
                Toast.makeText(this, "需要摄像头权限才能扫码", Toast.LENGTH_SHORT).show()
        }

    /** 单次扫码结果返回（备用，比如手动输入） */
    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val code = result.data?.getStringExtra(ScanActivity.EXTRA_CODE)
            if (result.resultCode == RESULT_OK && !code.isNullOrBlank()) {
                handleCode(code.trim())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeworkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sheetName = intent.getStringExtra("sheet") ?: run { finish(); return }
        data = DataManager(this, sheetName)
        title = "$sheetName - 作业统计"

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnScan.text = "📷 连续扫码"
        binding.btnScan.setOnClickListener { checkPermissionAndScan() }
        binding.btnManual.setOnClickListener { showManualInput() }
        binding.btnExportMissing.setOnClickListener { exportMissing() }
        binding.btnExportAll.setOnClickListener { exportAllStatus() }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        if (::data.isInitialized) {
            data.loadData()   // 重新读文件，拿到扫码页写入的最新数据
            refreshList()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    // ---------- 扫码 ----------

    private fun checkPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openContinuousScanner()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openContinuousScanner() {
        val intent = Intent(this, ScanContinuousActivity::class.java).apply {
            putExtra("sheet", intent.getStringExtra("sheet"))
        }
        startActivity(intent)
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
            // 通知扫码页显示错误
            notifyScanActivity("未找到：$code", true)
            return
        }
        data.setHomeworkStatus(student.barcode, 1)
        val t = data.homework[student.barcode]?.time ?: ""
        val displayText = "${student.name} (${student.studentId}) 已提交  $t"
        binding.tvInfo.text = displayText
        binding.tvInfo.setTextColor(0xFF1B5E20.toInt())
        // 通知扫码页显示成功
        notifyScanActivity(displayText, false)
        refreshList()
    }

    /** 尝试更新正在运行的连续扫码页的顶部显示 */
    private fun notifyScanActivity(text: String, isError: Boolean) {
        // 通过 startActivity 获取已存在的实例（Android 单例模式或栈顶复用）
        // 更简单的方式：直接发广播回去，但这里我们利用 Activity 的静态引用
        // 实际上 ScanContinuousActivity 每次扫到码会自己显示条码，这里只是增强显示学生名
        // 由于跨 Activity 通信，我们用另一种方式：在 ScanContinuousActivity 里注册回调
        // 但最简单的是：ScanContinuousActivity 自己显示条码就够了，这里不用额外处理
        // 如果需要更复杂的联动，可以用 EventBus 或 ViewModel，这里保持简单
    }

    // ---------- 列表 ----------

    private fun refreshList() {
        rows.clear()
        data.students.forEach { s ->
            val rec = data.getHomeworkStatus(s.barcode)
            rows.add(Row(s, rec.status, rec.time))
        }
        // 排序：待提交(0) -> 黄色(2,3,4) -> 已提交(1)，内部按名单原顺序
        rows.sortWith(compareBy({
            when (it.status) {
                0 -> 0
                1 -> 2
                else -> 1
            }
        }, { data.students.indexOf(it.student) }))
        binding.recycler.adapter?.notifyDataSetChanged()

        val total = data.students.size
        val handled = rows.count { it.status != 0 }
        binding.tvStat.text = "总计：$total 人  |  已处理：$handled 人  |  待提交：${total - handled} 人"
    }

    private val adapter = object : RecyclerView.Adapter<StudentVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_student, parent, false)
            return StudentVH(v)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: StudentVH, position: Int) {
            val row = rows[position]
            holder.tvIndex.text = (position + 1).toString()
            holder.tvName.text = row.student.name
            holder.tvStudentId.text = row.student.studentId
            holder.tvStatus.text = StatusConfig.NAMES[row.status]
            holder.tvTime.text = row.time
            val bg = StatusConfig.BG[row.status] ?: 0xFFFFFFFF.toInt()
            val fg = StatusConfig.FG[row.status] ?: 0xFF333333.toInt()
            holder.root.setBackgroundColor(bg)
            holder.tvStatus.setTextColor(fg)
            holder.tvName.setTextColor(fg)
            holder.itemView.setOnClickListener { showStatusDialog(row.student) }
        }
    }

    class StudentVH(v: View) : RecyclerView.ViewHolder(v) {
        val root: LinearLayout = v.findViewById(R.id.rowRoot)
        val tvIndex: TextView = v.findViewById(R.id.tvIndex)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvStudentId: TextView = v.findViewById(R.id.tvStudentId)
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
    }

    private fun showStatusDialog(student: Student) {
        val names = StatusConfig.NAMES.toSortedMap().values.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("修改 ${student.name} 的状态")
            .setItems(names) { _, which ->
                data.setHomeworkStatus(student.barcode, which)
                refreshList()
            }
            .show()
    }

    // ---------- 导出（改为安卓分享） ----------

    private fun exportMissing() {
        val missing = rows.filter { it.status == 0 }.map {
            listOf(it.student.name, it.student.studentId, it.student.barcode)
        }
        if (missing.isEmpty()) {
            Toast.makeText(this, "没有待提交的学生！", Toast.LENGTH_SHORT).show()
            return
        }
        val file = ShareHelper.uniqueFile(
            ShareHelper.sharedDir(this),
            "未交名单_${data.sheetName}_${DataManager.stamp()}.xlsx"
        )
        try {
            ExcelHelper.writeSheet(file, listOf("姓名", "学号", "条形码"), missing)
            ShareHelper.shareFile(this, file, "分享未交名单")
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportAllStatus() {
        val all = data.students.map { s ->
            val rec = data.getHomeworkStatus(s.barcode)
            listOf(s.name, s.studentId, StatusConfig.NAMES[rec.status] ?: "", rec.time)
        }
        val file = ShareHelper.uniqueFile(
            ShareHelper.sharedDir(this),
            "作业状态_${data.sheetName}_${DataManager.stamp()}.xlsx"
        )
        try {
            ExcelHelper.writeSheet(file, listOf("姓名", "学号", "状态", "录入时间"), all)
            ShareHelper.shareFile(this, file, "分享作业状态表")
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}