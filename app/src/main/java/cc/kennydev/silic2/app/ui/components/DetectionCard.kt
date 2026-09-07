package cc.kennydev.silic2.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kennydev.silic2.app.data.model.AnimalCategory
import cc.kennydev.silic2.app.data.model.SilicDetection
import cc.kennydev.silic2.app.ui.theme.AmberWarning
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.ForestSurfaceVariant
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.TealAccent
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary
import cc.kennydev.silic2.app.ui.theme.TextTertiary

@Composable
fun DetectionCard(
    detection: SilicDetection,
    isSelected: Boolean = false,
    onCardClick: () -> Unit = {},
    onPlayClip: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ForestSurfaceVariant else ForestSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 物種名稱 + 分類標籤 + 聲音型態
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    CategoryBadge(category = detection.category)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = detection.speciesName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SoundClassBadge(soundClass = detection.soundClass)
                }

                // 播放此段按鈕與信心度
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onPlayClip != null) {
                        FilledTonalIconButton(
                            onClick = onPlayClip,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = ForestSurfaceVariant,
                                contentColor = SilicGreen
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "試聽片段",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = "信心分數",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = detection.formattedConfidence,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (detection.confidence >= 0.5f) SilicGreen else AmberWarning
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 學名
            if (detection.scientificName.isNotBlank()) {
                Text(
                    text = detection.scientificName,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 時間區間與頻率範圍
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "時間: ${detection.formattedTime}",
                    fontSize = 12.sp,
                    color = TextTertiary
                )
                Text(
                    text = "頻率: ${detection.formattedFreq}",
                    fontSize = 12.sp,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 信心度 Progress Bar
            LinearProgressIndicator(
                progress = { detection.confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (detection.confidence >= 0.5f) SilicGreen else AmberWarning,
                trackColor = ForestSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryBadge(category: AnimalCategory) {
    val (bgColor, textColor) = when (category) {
        AnimalCategory.BIRD -> Pair(Color(0xFF1E382B), SilicGreen)
        AnimalCategory.FROG -> Pair(Color(0xFF163E30), TealAccent)
        AnimalCategory.MAMMAL -> Pair(Color(0xFF3E2D16), Color(0xFFFFB74D))
        AnimalCategory.OTHER -> Pair(Color(0xFF2E1C38), Color(0xFFCE93D8))
        AnimalCategory.ALL -> Pair(ForestSurfaceVariant, TextSecondary)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${category.emoji} ${category.displayName}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
fun SoundClassBadge(soundClass: String) {
    val (bgColor, textColor) = when {
        soundClass.startsWith("S") -> Pair(Color(0xFF1B3B2B), SilicGreen) // Song 鳴唱
        soundClass.startsWith("C") -> Pair(Color(0xFF1C343B), TealAccent) // Call 呼叫
        else -> Pair(Color(0xFF332B1C), AmberWarning)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = soundClass,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
