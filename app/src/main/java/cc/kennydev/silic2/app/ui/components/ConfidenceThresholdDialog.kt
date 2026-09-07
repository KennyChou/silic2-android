package cc.kennydev.silic2.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.kennydev.silic2.app.ui.theme.DarkForestBg
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.ForestSurfaceVariant
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.TealAccent
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary
import cc.kennydev.silic2.app.ui.theme.TextTertiary
import kotlin.math.roundToInt

private const val DEFAULT_THRESHOLD = 0.20f

/**
 * 辨識信賴度門檻（信心度門檻 / Confidence Threshold）設定對話框
 */
@Composable
fun ConfidenceThresholdDialog(
    currentThreshold: Float,
    onThresholdChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var tempThreshold by remember(currentThreshold) { mutableFloatStateOf(currentThreshold) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = ForestSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 標題列
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(ForestSurfaceVariant, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = SilicGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "辨識信心分數設定",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Confidence Threshold",
                            fontSize = 11.sp,
                            color = TextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 當前數值與狀態徽章
                val percentage = (tempThreshold * 100).roundToInt()
                val isDefault = (tempThreshold * 100).roundToInt() == (DEFAULT_THRESHOLD * 100).roundToInt()

                val (modeName, modeColor, modeDesc) = when {
                    tempThreshold < 0.15f -> Triple("高靈敏模式", Color(0xFFFFB74D), "易捕捉微弱遠處叫聲，但環境雜音可能引起誤報")
                    tempThreshold <= 0.25f -> Triple("標準平衡 (預設推薦)", SilicGreen, "野外聲景生態監測推薦值，平衡檢出率與準確度")
                    tempThreshold <= 0.40f -> Triple("嚴謹模式", TealAccent, "過濾背景雜音干擾，僅記錄清晰確鑿的鳴叫")
                    else -> Triple("高特異性模式", Color(0xFF81D4FA), "要求鳴聲極度清晰，可能遺漏遠距微弱叫聲")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$percentage",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "% (${String.format("%.2f", tempThreshold)})",
                                fontSize = 16.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                            )
                        }
                    }

                    // 模式徽章
                    Box(
                        modifier = Modifier
                            .background(modeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = modeName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = modeColor
                        )
                    }
                }

                Text(
                    text = modeDesc,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                // 滑桿 Slider (0.05 ~ 0.70，步進 0.05)
                Slider(
                    value = tempThreshold,
                    onValueChange = { tempThreshold = (it * 20).roundToInt() / 20f },
                    valueRange = 0.05f..0.70f,
                    steps = 12,
                    colors = SliderDefaults.colors(
                        thumbColor = SilicGreen,
                        activeTrackColor = SilicGreen,
                        inactiveTrackColor = ForestSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("5% (最靈敏)", fontSize = 11.sp, color = TextTertiary)
                    Text("預設 20%", fontSize = 11.sp, color = if (isDefault) SilicGreen else TextTertiary, fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal)
                    Text("70% (最嚴謹)", fontSize = 11.sp, color = TextTertiary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 說明資訊卡片
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkForestBg, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "信心分數說明",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• 什麼是信心分數？\n  SILIC 2 深度學習模型推論每個聲音特徵時會輸出 0%～100% 的信心評分。只有高於此分數的辨識結果才會觸發顯示並納入紀錄。",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• 預設推薦值：20% (0.20)\n  這是針對野外長時錄音推薦的平衡值。能在確保良好檢出率（不遺漏叫聲）的同時，有效過濾風聲與蟲鳴雜音。",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• 調整情境指引：\n  - 空曠安靜、想搜尋稀有微弱鳥音：調低至 10%～15%\n  - 風強雨大、溪流嘈雜或環境音吵雜：調高至 25%～35%+",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 操作按鈕列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 重設預設值
                    OutlinedButton(
                        onClick = { tempThreshold = DEFAULT_THRESHOLD },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("回復預設", fontSize = 13.sp)
                    }

                    // 確認套用
                    Button(
                        onClick = {
                            onThresholdChanged(tempThreshold)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SilicGreen,
                            contentColor = DarkForestBg
                        )
                    ) {
                        Text("套用設定", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
