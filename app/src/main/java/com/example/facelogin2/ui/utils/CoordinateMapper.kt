package com.example.facelogin2.ui.utils

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

object CoordinateMapper {

    /**
     * Precision mapping using Android Matrix for FILL_CENTER (CenterCrop)
     */
    fun mapPoint(
        point: android.graphics.PointF,
        imageSize: IntSize,
        previewSize: IntSize,
        rotationDegrees: Int = 0,
        isFrontCamera: Boolean = false
    ): Offset {
        val iw = imageSize.width.toFloat()
        val ih = imageSize.height.toFloat()
        val vw = previewSize.width.toFloat()
        val vh = previewSize.height.toFloat()

        if (iw <= 0 || ih <= 0 || vw <= 0 || vh <= 0) return Offset(point.x, point.y)

        val matrix = Matrix()

        // 1. Calculate FILL_CENTER scale (CenterCrop)
        // Ensure the entire view is covered by the image
        val scale = maxOf(vw / iw, vh / ih)
        matrix.postScale(scale, scale)

        // 2. Calculate offsets to center the scaled image in the view
        val dx = (vw - iw * scale) / 2f
        val dy = (vh - ih * scale) / 2f
        matrix.postTranslate(dx, dy)

        // 3. Handle Front Camera Mirroring
        // Mirroring happens across the center of the VIEW (0..vw)
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, vw / 2f, vh / 2f)
        }

        // 4. Transform the point
        val pts = floatArrayOf(point.x, point.y)
        matrix.mapPoints(pts)

        return Offset(pts[0], pts[1])
    }

    /**
     * Map a BoundingBox using Matrix
     */
    fun mapRect(
        rect: android.graphics.Rect,
        imageSize: IntSize,
        previewSize: IntSize,
        rotationDegrees: Int = 0,
        isFrontCamera: Boolean = false
    ): androidx.compose.ui.geometry.Rect {
        val pLeftTop = mapPoint(
            android.graphics.PointF(rect.left.toFloat(), rect.top.toFloat()),
            imageSize, previewSize, rotationDegrees, isFrontCamera
        )
        val pRightBottom = mapPoint(
            android.graphics.PointF(rect.right.toFloat(), rect.bottom.toFloat()),
            imageSize, previewSize, rotationDegrees, isFrontCamera
        )

        return androidx.compose.ui.geometry.Rect(
            left = minOf(pLeftTop.x, pRightBottom.x),
            top = minOf(pLeftTop.y, pRightBottom.y),
            right = maxOf(pLeftTop.x, pRightBottom.x),
            bottom = maxOf(pLeftTop.y, pRightBottom.y)
        )
    }
}
