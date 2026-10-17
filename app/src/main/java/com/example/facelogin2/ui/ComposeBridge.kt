package com.example.facelogin2.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.example.facelogin2.User
import com.example.facelogin2.ui.components.ModernUserInfoPanel
import com.example.facelogin2.ui.components.ScanningOverlay
import com.example.facelogin2.ui.theme.VGCameraTheme

object ComposeBridge {
    @JvmStatic
    fun setMainOverlayContent(
        view: ComposeView,
        user: User?,
        isPanelVisible: Boolean,
        uiState: Int,
        frozenBitmap: Bitmap?,
        faceRect: Rect?,
        meshPoints: FloatArray?,
        imageWidth: Int,
        imageHeight: Int,
        rotation: Int
    ) {
        view.setContent {
            VGCameraTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ScanningOverlay(
                        uiState = uiState,
                        frozenBitmap = frozenBitmap,
                        faceRect = faceRect,
                        meshPoints = meshPoints,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        rotation = rotation
                    )
                    ModernUserInfoPanel(user = user, isVisible = isPanelVisible, uiState = uiState)
                }
            }
        }
    }
}
