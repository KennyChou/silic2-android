package cc.kennydev.silic2.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kennydev.silic2.app.BuildConfig
import cc.kennydev.silic2.app.ui.theme.DarkForestBg
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.ForestSurfaceVariant
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.TealAccent
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary
import cc.kennydev.silic2.app.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 進場淡入動畫
    var alpha by remember { mutableFloatStateOf(0f) }
    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(durationMillis = 400),
        label = "fadeIn"
    )
    LaunchedEffect(Unit) { alpha = 1f }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "關於 SILIC 2",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = SilicGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestSurface)
            )
        },
        containerColor = DarkForestBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .alpha(animatedAlpha)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(24.dp))

            // ── App LOGO 區 ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2A5C48), Color(0xFF1A2E25))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App 名稱與版本
            Text(
                text = "SILIC 2 Mobile",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME}  （build ${BuildConfig.VERSION_CODE}）",
                fontSize = 13.sp,
                color = TealAccent,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 副標
            Text(
                text = "野生動物聲音離線辨識系統",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── 專案說明 ────────────────────────────────────────────────
            AboutSection(title = "專案簡介", icon = Icons.Default.Psychology) {
                AboutText(
                    "SILIC 2 Mobile 將上游 RedbirdTaiwan/silic2 的完整聲學辨識演算法移植至 Android，" +
                    "實現純離線端側推論（On-Device Inference）。"
                )
                Spacer(modifier = Modifier.height(8.dp))
                AboutText(
                    "專為深山、高山等完全無網路覆蓋的野外生態聲景調查環境設計，" +
                    "讓研究人員在沒有網路的情況下即時辨識野生動物聲音。"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 辨識能力 ────────────────────────────────────────────────
            AboutSection(title = "辨識能力", icon = Icons.Default.SignalCellularAlt) {
                AboutText("• 支援物種：398 類台灣野生動物")
                AboutText("• 鳥類、蛙類、哺乳類（含蝙蝠）")
                AboutText("• 基於 YOLOv8 物件偵測模型")
                AboutText("• 採用 Mel 頻譜圖視覺化辨識技術")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 技術參數 ────────────────────────────────────────────────
            AboutSection(title = "訊號處理參數", icon = Icons.Default.Code) {
                TechParamTable()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 連結 ────────────────────────────────────────────────────
            AboutSection(title = "相關連結", icon = Icons.Default.OpenInNew) {
                LinkItem(
                    label = "上游專案 RedbirdTaiwan/silic2",
                    url = "https://github.com/RedbirdTaiwan/silic2",
                    context = context
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinkItem(
                    label = "RedbirdTaiwan / 紅鳥台灣",
                    url = "https://github.com/RedbirdTaiwan",
                    context = context
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 版權聲明 ─────────────────────────────────────────────────
            HorizontalDivider(color = ForestSurfaceVariant, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "模型權重與聲音標籤資料源自 RedbirdTaiwan/silic2，\n請依上游專案之授權條款使用。",
                fontSize = 12.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Powered by Google AI Edge LiteRT",
                fontSize = 11.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── 子元件 ──────────────────────────────────────────────────────────────────

@Composable
private fun AboutSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ForestSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 標題列
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A5C48)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = ForestSurfaceVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun AboutText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = TextSecondary,
        lineHeight = 20.sp
    )
}

@Composable
private fun TechParamTable() {
    val params = listOf(
        "取樣率" to "32,000 Hz（16-bit PCM Mono）",
        "滑動窗口" to "3,000 ms（96,000 samples）",
        "步長" to "1,500 ms（50% overlap）",
        "FFT 大小" to "1,600（n_fft）",
        "Hop Length" to "400",
        "Mel 濾波器" to "240 個",
        "頻率範圍" to "100 Hz ～ 15,000 Hz",
        "模型輸入" to "480 × 480 px",
        "推論引擎" to "Google AI Edge LiteRT"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        params.forEachIndexed { index, (key, value) ->
            if (index > 0) {
                HorizontalDivider(color = ForestSurfaceVariant, thickness = 0.5.dp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(key, fontSize = 12.sp, color = TextTertiary, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Text(value, fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1.8f))
            }
        }
    }
}

@Composable
private fun LinkItem(label: String, url: String, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = SilicGreen, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 13.sp, color = SilicGreen, fontWeight = FontWeight.Medium)
            Text(url, fontSize = 11.sp, color = TextTertiary)
        }
    }
}
