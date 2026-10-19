package com.livrechat.app.ui.livretube

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.livrechat.app.data.remote.LivreTubeVideoDto

object LivreTubePlayerManager {
    var currentVideo by mutableStateOf<LivreTubeVideoDto?>(null)
    var isMinimized by mutableStateOf(false)
    var isLandscape by mutableStateOf(false)

    fun play(video: LivreTubeVideoDto) {
        currentVideo = video
        isMinimized = false
        isLandscape = false
    }

    fun minimize(activity: Activity? = null) {
        isMinimized = true
        if (isLandscape) {
            isLandscape = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun expand() {
        isMinimized = false
    }

    fun toggleLandscape(activity: Activity?) {
        isLandscape = !isLandscape
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun setLandscape(activity: Activity?, landscape: Boolean) {
        isLandscape = landscape
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun close(activity: Activity? = null) {
        currentVideo = null
        isMinimized = false
        if (isLandscape) {
            isLandscape = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
