package com.example.homeworkscan
/*
 * 学生扫码作业提交与成绩统计（Android 版）
 * Copyright (C) 2026 白林不可燃
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.homeworkscan.data.DataManager
import com.example.homeworkscan.databinding.ActivityScanContinuousBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
/*
 * 学生扫码作业提交与成绩统计（Android 版）
 * Copyright (C) 2026 白林不可燃
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
/**
 * 连续扫码页：扫完一个自动继续扫下一个，顶部实时显示上一个录入的学生
 */
class ScanContinuousActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanContinuousBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastCode: String? = null
    private lateinit var data: DataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanContinuousBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 从 intent 获取名单名，初始化数据
        val sheetName = intent.getStringExtra("sheet") ?: run { finish(); return }
        data = DataManager(this, sheetName)

        binding.btnDone.setOnClickListener { finish() }
        startCamera()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val scanner = BarcodeScanning.getClient()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(
                    mediaImage, imageProxy.imageInfo.rotationDegrees
                )
                scanner.process(image)
                    .addOnSuccessListener { barcodes: List<Barcode> ->
                        val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        if (!value.isNullOrBlank() && value != lastCode) {
                            lastCode = value
                            handleCode(value.trim())
                            // 1.5秒后允许重复扫同一个码（比如扫错了想重新扫）
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (lastCode == value) lastCode = null
                            }, 1500)
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (_: Exception) {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** 处理扫码结果：查找学生、更新状态、显示在顶部 */
    private fun handleCode(code: String) {
        val student = data.findByCode(code)
        if (student == null) {
            runOnUiThread {
                binding.tvLastResult.text = "未找到：$code"
                binding.tvLastResult.setTextColor(0xFFF44336.toInt()) // 红色
            }
            return
        }

        // 标记已提交
        data.setHomeworkStatus(student.barcode, 1)
        val t = data.homework[student.barcode]?.time ?: ""
        val displayText = "${student.name} (${student.studentId}) 已提交  $t"

        runOnUiThread {
            binding.tvLastResult.text = displayText
            binding.tvLastResult.setTextColor(0xFF4CAF50.toInt()) // 绿色
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}