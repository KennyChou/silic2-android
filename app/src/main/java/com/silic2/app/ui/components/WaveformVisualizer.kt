package com.silic2.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.silic2.app.ui.theme.SilicGreen
import com.silic2.app.ui.theme.TealAccent
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val animatedAmp by animateFloatAsState(
        targetValue = if (isRecording) amplitude.coerceIn(0.08f, 1f) else 0.05f,
        label = "amplitude"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val random = Random(42)
        for (i in 0 until barCount) {
            val scaleFactor = (0.4f + random.nextFloat() * 0.6f)
            val barHeightFraction = (animatedAmp * scaleFactor).coerceIn(0.08f, 1.0f)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(barHeightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i % 2 == 0) SilicGreen else TealAccent
                    )
            )
        }
    }
}
