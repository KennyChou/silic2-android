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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

@Composable
fun BackgroundSettingsDialog(
    enableBackgroundRecording: Boolean,
    maxDurationMinutes: Int,
    autoStopLowBattery: Boolean,
    onSettingsChanged: (Boolean, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var tempEnableBg by remember(enableBackgroundRecording) { mutableStateOf(enableBackgroundRecording) }
    var tempMaxMinutes by remember(maxDurationMinutes) { mutableIntStateOf(maxDurationMinutes) }
    var tempLowBattery by remember(autoStopLowBattery) { mutableStateOf(autoStopLowBattery) }

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
                // 標題
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
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SilicGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "背景聽音與防過熱安全保護",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Background Recording & Thermal Protection",
                            fontSize = 11.sp,
                            color = TextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 開關 1：背景持續聽音
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkForestBg, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "允許離開 App 時背景持續聽音",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (tempEnableBg)
                                "已開啟：退到背景或鎖定螢幕仍持續錄音與辨識"
                            else
                                "關閉中 (預設推薦)：離開 App 自動安全存檔並停止，徹底防過熱",
                            fontSize = 12.sp,
                            color = if (tempEnableBg) TealAccent else Color(0xFFFFB74D)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Switch(
                        checked = tempEnableBg,
                        onCheckedChange = { tempEnableBg = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkForestBg,
                            checkedTrackColor = SilicGreen,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = ForestSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 若開啟背景聽音，顯示時間上限保護設定
                if (tempEnableBg) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkForestBg, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = SilicGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "連續錄音保護上限 (防忘記與防過熱)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "超過設定時間後，App 將自動停止推論並儲存所有音訊，絕不無限期空轉。",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(30 to "30 分鐘", 60 to "60 分 (推薦)", 120 to "2 小時").forEach { (min, label) ->
                                FilterChip(
                                    selected = tempMaxMinutes == min,
                                    onClick = { tempMaxMinutes = min },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SilicGreen,
                                        selectedLabelColor = DarkForestBg,
                                        containerColor = ForestSurface,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 開關 2：低電量自動存檔
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkForestBg, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "低電量保護 (電量低於 15% 自動停止)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "避免手機錄音錄到斷電關機造成檔案損壞。",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = tempLowBattery,
                        onCheckedChange = { tempLowBattery = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkForestBg,
                            checkedTrackColor = SilicGreen,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = ForestSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 安全提示
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TealAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "安心提醒：若保持「關閉背景聽音」（預設狀態），只要跳出 App 就會自動存檔並停止所有計算，完全不會在背景耗電或發燙。",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 儲存套用按鈕
                Button(
                    onClick = {
                        onSettingsChanged(tempEnableBg, tempMaxMinutes, tempLowBattery)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SilicGreen,
                        contentColor = DarkForestBg
                    )
                ) {
                    Text("儲存並套用設定", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
