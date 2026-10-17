package com.example.facelogin2.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.facelogin2.ui.utils.CoordinateMapper
import androidx.compose.ui.unit.IntSize

// Professional Cyan Theme (from VG-CAMERA)
val VGCyan = Color(0xFF00E5FF)
val VGCyanDim = Color(0x6600E5FF)

/**
 * Aesthetic Scanning Overlay - Copied directly from VG-CAMERA
 */
@Composable
fun ScanningOverlay(
    uiState: Int,
    frozenBitmap: Bitmap?,
    faceRect: Rect?,
    meshPoints: FloatArray?,
    imageWidth: Int,
    imageHeight: Int,
    rotation: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TechnoScan")
    
    val meshAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MeshAlpha"
    )

    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScanLine"
    )

    // Smooth State Animations
    val isLocked = uiState == 2 || uiState == 4
    val freezeAlpha by animateFloatAsState(
        targetValue = if (isLocked) 1.0f else 0.0f, 
        animationSpec = tween(400),
        label = "FreezeAlpha"
    )
    val freezeScale by animateFloatAsState(
        targetValue = if (isLocked) 1.05f else 1.0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "FreezeScale"
    )
    val targetColor by animateColorAsState(
        targetValue = when (uiState) {
            2, 3, 4 -> VGCyan
            5 -> Color(0xFFFF1744) // Màu đỏ lỗi
            else -> Color.White.copy(alpha = 0.3f)
        },
        animationSpec = tween(400),
        label = "TargetColor"
    )
    val meshEntranceAlpha by animateFloatAsState(
        targetValue = if (isLocked) 1.0f else 0.0f,
        animationSpec = tween(500),
        label = "MeshEntrance"
    )

    // Pulse for Target Box during Countdown
    val targetPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TargetPulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 0. HIỂN THỊ ẢNH ĐÓNG BĂNG (FREEZE) VỚI ANIMATION MƯỢT
        if (frozenBitmap != null && freezeAlpha > 0.01f) {
            Image(
                bitmap = frozenBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        alpha = freezeAlpha,
                        scaleX = freezeScale,
                        scaleY = freezeScale
                    ),
                contentScale = ContentScale.FillBounds
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val previewSize = IntSize(size.width.toInt(), size.height.toInt())
            val imageSize = IntSize(imageWidth, imageHeight)

            // 1. Draw Target Box
            val finalTargetScale = if (uiState == 3) targetPulse else 1.0f
            drawTargetBox(previewSize, targetColor, finalTargetScale)

            // 2. Draw Aesthetic Mesh (Line + Dot)
            if (isLocked && meshPoints != null && imageWidth > 0) {
                drawAestheticMesh(meshPoints, imageSize, previewSize, rotation, meshAlpha * meshEntranceAlpha, isFront = true)
            }

            // 3. Scanning Bar (Only in PROCESSING state)
            if (uiState == 2 && faceRect != null && imageWidth > 0) {
                val mappedRect = CoordinateMapper.mapRect(faceRect, imageSize, previewSize, rotation, isFrontCamera = true)
                val scanY = mappedRect.top + (mappedRect.height * scanProgress)
                
                drawLine(
                    color = VGCyan,
                    start = androidx.compose.ui.geometry.Offset(mappedRect.left, scanY),
                    end = androidx.compose.ui.geometry.Offset(mappedRect.right, scanY),
                    strokeWidth = 2.dp.toPx()
                )
                
                drawRect(
                    color = VGCyan.copy(alpha = 0.12f * scanProgress),
                    topLeft = androidx.compose.ui.geometry.Offset(mappedRect.left, mappedRect.top),
                    size = androidx.compose.ui.geometry.Size(mappedRect.width, scanY - mappedRect.top)
                )
            }
        }
    }
}

private fun DrawScope.drawAestheticMesh(
    points: FloatArray,
    imageSize: IntSize,
    previewSize: IntSize,
    rotation: Int,
    alpha: Float,
    isFront: Boolean
) {
    val meshColor = VGCyan.copy(alpha = alpha)
    val lineAlpha = alpha * 0.4f

    // Advanced Loop-based Indices
    val eyeLeft = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
    val eyeRight = listOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398)
    val eyebrowLeft = listOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46)
    val eyebrowRight = listOf(300, 293, 334, 296, 336, 285, 295, 282, 283, 276)
    val noseBridge = listOf(168, 6, 197, 195, 5, 4, 1)
    val noseBase = listOf(98, 97, 2, 326, 327)
    val upperLip = listOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 415, 310, 311, 312, 13, 82, 81, 80, 191)
    val lowerLip = listOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95, 78)
    val faceOutline = listOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109)

    fun drawGroup(indices: List<Int>, closed: Boolean = true) {
        val path = Path()
        var moved = false
        indices.forEach { idx ->
            if (idx * 2 + 1 < points.size) {
                val p = android.graphics.PointF(points[idx * 2], points[idx * 2 + 1])
                val mapped = CoordinateMapper.mapPoint(p, imageSize, previewSize, rotation, isFrontCamera = isFront)
                if (!moved) { path.moveTo(mapped.x, mapped.y); moved = true }
                else path.lineTo(mapped.x, mapped.y)
                
                drawCircle(color = meshColor, radius = 1.1.dp.toPx(), center = mapped)
            }
        }
        if (closed) path.close()
        drawPath(path, VGCyanDim.copy(alpha = lineAlpha), style = Stroke(width = 0.8.dp.toPx()))
    }

    drawGroup(eyeLeft); drawGroup(eyeRight)
    drawGroup(eyebrowLeft, false); drawGroup(eyebrowRight, false)
    drawGroup(noseBridge, false); drawGroup(noseBase, false)
    drawGroup(upperLip); drawGroup(lowerLip)
    drawGroup(faceOutline)
    
    // Scattered cyan nodes
    for (i in 0 until points.size / 2 step 21) {
        val p = android.graphics.PointF(points[i * 2], points[i * 2 + 1])
        val mapped = CoordinateMapper.mapPoint(p, imageSize, previewSize, rotation, isFrontCamera = isFront)
        drawCircle(color = VGCyanDim.copy(alpha = alpha * 0.2f), radius = 0.7.dp.toPx(), center = mapped)
    }
}

private fun DrawScope.drawTargetBox(previewSize: IntSize, color: Color, scale: Float = 1.0f) {
    val boxWidth = previewSize.width * 0.65f * scale
    val boxHeight = boxWidth * 1.3f
    val left = (previewSize.width - (previewSize.width * 0.65f * scale)) / 2f
    val top = (previewSize.height - (previewSize.width * 0.65f * 1.3f * scale)) / 2.5f
    
    val strokeWidth = 2.5.dp.toPx()
    val cornerLen = 30.dp.toPx()

    fun drawCorner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx, y), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + dy), strokeWidth)
    }

    drawCorner(left, top, cornerLen, cornerLen)
    drawCorner(left + boxWidth, top, -cornerLen, cornerLen)
    drawCorner(left, top + boxHeight, cornerLen, -cornerLen)
    drawCorner(left + boxWidth, top + boxHeight, -cornerLen, -cornerLen)
}
