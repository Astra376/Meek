package com.example.aichat.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aichat.core.design.PrimaryButton

@Composable
fun LoadingScreen(paddingValues: PaddingValues = PaddingValues()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.width(220.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(18.dp),
                    shape = RoundedCornerShape(9.dp)
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(
    title: String,
    body: String,
    actionLabel: String,
    paddingValues: PaddingValues = PaddingValues(),
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppChrome.screenBottomPadding)
            .padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            modifier = Modifier.padding(
                top = AppChrome.compactHeaderVerticalPadding,
                bottom = AppChrome.listRowGap
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryButton(
            text = actionLabel,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAction
        )
    }
}
