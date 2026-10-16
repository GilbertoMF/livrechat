package com.livrechat.app.ui.common

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = 8.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = {
                    Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show()
                }
            )
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
