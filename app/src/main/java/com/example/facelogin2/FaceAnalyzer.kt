package com.example.facelogin2

import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.facelogin2.FaceAnalyzer.FaceDetectionListener
import com.google.mlkit.vision.common.InputImage
import kotlin.math.abs

import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions

class FaceAnalyzer(
    private val context: Context,
    private val overlay: FaceGraphicOverlay?,
    private val listener: FaceDetectionListener?
) : ImageAnalysis.Analyzer {

    interface FaceDetectionListener {
        fun onFaceMeshDetected(mesh: FaceMesh, imageWidth: Int, imageHeight: Int, rotation: Int, isLookingStraight: Boolean)
        fun onNoFaceDetected()
        fun onFaceInTarget(mesh: FaceMesh, isLookingStraight: Boolean)
        fun onFaceNotInTarget()
        fun onAverageLuminance(luminance: Double)
    }

    private val detector: FaceMeshDetector by lazy {
        FaceMeshDetection.getClient(
            FaceMeshDetectorOptions.Builder()
                .build()
        )
    }

    override fun analyze(imageProxy: ImageProxy) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val recognizeThreshold = prefs.getInt("recognize_threshold", 5000)

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees

        // --- Average Luminance ---
        if (mediaImage.format == ImageFormat.YUV_420_888) {
            val buffer = mediaImage.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            var sum: Long = 0
            for (b in data) sum += (b.toInt() and 0xFF).toLong()
            listener?.onAverageLuminance(sum.toDouble() / data.size)
        }

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(image)
            .addOnSuccessListener { meshes ->
                processMeshes(meshes, recognizeThreshold, image.width, image.height, rotation)
            }
            .addOnFailureListener { e ->
                Log.e("FaceAnalyzer", "Face Mesh detection failed", e)
                listener?.onNoFaceDetected()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processMeshes(
        meshes: List<FaceMesh>,
        recognizeThreshold: Int,
        width: Int,
        height: Int,
        rotation: Int
    ) {
        if (meshes.isEmpty()) {
            listener?.onNoFaceDetected()
            listener?.onFaceNotInTarget()
            return
        }

        var mainMesh: FaceMesh? = null
        var maxArea = -1f
        for (mesh in meshes) {
            val area = (mesh.boundingBox.width() * mesh.boundingBox.height()).toFloat()
            if (area > maxArea) {
                maxArea = area
                mainMesh = mesh
            }
        }

        if (mainMesh == null) {
            listener?.onNoFaceDetected()
            listener?.onFaceNotInTarget()
            return
        }

        // --- 🔎 KIỂM TRA NHÌN THẲNG (SYMMETRY CHECK) ---
        val pts = mainMesh.allPoints
        var isStraight = false
        try {
            val nose = pts[6].position
            val leftEye = pts[133].position
            val rightEye = pts[362].position
            
            val distL = abs(nose.x - leftEye.x)
            val distR = abs(rightEye.x - nose.x)
            val ratio = if (distL > distR) distR / distL else distL / distR
            
            val eyeDiffY = abs(leftEye.y - rightEye.y)
            isStraight = ratio > 0.75f && eyeDiffY < (mainMesh.boundingBox.height() * 0.05f)
        } catch (e: Exception) {
            isStraight = true // Fallback
        }

        val isRotated = rotation == 90 || rotation == 270
        val analyzedW = if (isRotated) height else width
        val analyzedH = if (isRotated) width else height

        val box = mainMesh.boundingBox
        val centerX = box.centerX()
        val centerY = box.centerY()

        val safetyMarginW = analyzedW * 0.20
        val safetyMarginH = analyzedH * 0.20

        val isInTarget = centerX > safetyMarginW && centerX < (analyzedW - safetyMarginW) &&
                         centerY > safetyMarginH && centerY < (analyzedH - safetyMarginH) &&
                         maxArea > recognizeThreshold

        listener?.onFaceMeshDetected(mainMesh, analyzedW, analyzedH, rotation, isStraight)

        if (isInTarget) {
            listener?.onFaceInTarget(mainMesh, isStraight)
        } else {
            listener?.onFaceNotInTarget()
        }
    }
}
