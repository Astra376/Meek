package com.example.aichat.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun DeferredLoadingContainer(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    delayMillis: Long = 500L,
    loadingContent: @Composable () -> Unit = { LoadingScreen() },
    content: @Composable () -> Unit
) {
    var isPastDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            isPastDelay = false
            delay(delayMillis)
            isPastDelay = true
        } else {
            isPastDelay = false
        }
    }

    if (isLoading) {
        if (isPastDelay) {
            loadingContent()
        } else {
            // Empty placeholder to delay page appearance
            Box(modifier = modifier.fillMaxSize())
        }
    } else {
        content()
    }
}
