# SILIC 2 Mobile

**野生動物聲景 AI 離線辨識 — Android 行動端**

基於 [RedbirdTaiwan/silic2](https://github.com/RedbirdTaiwan/silic2) 聲學辨識演算法與模型，移植至 Android 平台的**純離線原生應用程式**。專為深山、高山等完全無網路覆蓋的野外生態聲景調查環境設計。

---

## 📋 目錄

- [專案背景](#專案背景)
- [核心特色](#核心特色)
- [系統需求](#系統需求)
- [演算法規格](#演算法規格)
- [專案架構](#專案架構)
- [建置與安裝](#建置與安裝)
- [模型部署](#模型部署)
- [物種涵蓋範圍](#物種涵蓋範圍)
- [上游專案引用](#上游專案引用)
- [授權條款](#授權條款)

---

## 專案背景

### 什麼是 SILIC？

**SILIC（Sound Identification and Labeling Intelligence for Creatures）** 是由[RedbirdTaiwan](https://github.com/RedbirdTaiwan) 開發的野生動物聲音辨識與標註管線系統。SILIC 第二版（silic2）採用 **YOLOv8 物件偵測模型**，將聲音訊號轉換為 Mel 頻譜圖後，以視覺偵測的方式精準定位並辨識野生動物的鳴叫聲。

上游專案提供了完整的 Python 命令列工具與桌面介面，適用於實驗室與工作站的批次分析場景。

### 為什麼需要行動版？

野外生態調查往往在深山、高山、離島等**完全無網路訊號**的環境下進行。研究人員需要一個能夠：

- 🏔️ **100% 離線運作**的辨識工具
- 📱 **隨身攜帶、即時辨識**的行動裝置方案
- 🎤 **即時錄音 + 即時分析**，而非事後帶回分析
- 📊 **現場產出調查數據**，加速田野作業效率

**SILIC 2 Mobile** 正是為此而生——將上游 silic2 的完整演算法管線移植到 Android 手機上，實現端側推論（On-Device Inference），讓研究人員在無網路的野外也能即時辨識野生動物聲音。

---

## 核心特色

### 🔌 100% 離線本地運算（On-Device Inference）

麥克風音訊採集 → Mel-Spectrogram 頻譜計算 → 彩虹頻譜圖渲染 → YOLOv8 物件偵測推論 → NMS 後處理，**全部在手機晶片本地執行**，無須任何網路連線。

### 🎯 完全對齊 SILIC 2 演算法標準

所有訊號處理參數與上游 [silic2](https://github.com/RedbirdTaiwan/silic2) Python 版完全一致，確保辨識結果可互相比對驗證。

### 🐦 支援 398 類野生動物聲音

內建 `soundclass.csv` 聲音標籤庫，涵蓋 279 種台灣野生動物，包括：
- **鳥類**：黑冠麻鷺、八色鳥、白耳畫眉、五色鳥、鷹鵑、貓頭鷹等
- **兩棲蛙類**：腹斑蛙、海蟾蜍、台北樹蛙等
- **哺乳類**：小鼯鼠、飛鼠、山羌、台灣獼猴等

### 🔍 目標物種靈活篩選

支援「全物種監聽」或「鎖定特定物種」模式（例如：僅監聽海蟾蜍入侵種或特定貓頭鷹），按鳥類、蛙類、哺乳類等分類快速篩選。

### 🎤 即時錄音與辨識

即開即錄，搭配即時 Mel 頻譜瀑布圖視覺化顯示，辨識結果即時呈現於螢幕。

### 📂 音訊檔案匯入分析

支援外部錄音 App 透過 Android `Intent` 分享音訊檔案至 SILIC 2 進行離線批次分析，支援 WAV、MP3、MP4、FLAC 等常見音訊格式。

### 📊 生態調查數據匯出

一鍵將辨識結果（時間戳記、物種名稱、學名、頻率範圍、信心度）匯出為 **UTF-8 CSV 檔案**，方便後續統計分析。

### 🌊 彩虹頻譜圖（Rainbow Spectrogram）

獨家 5 頻段彩虹色彩映射頻譜圖渲染，精確重現上游 silic2 使用的 Matplotlib `cm.rainbow` 配色方案，提供直覺的聲景視覺化效果。

---

## 系統需求

| 項目 | 需求 |
|---|---|
| **作業系統** | Android 8.0（API 26）以上 |
| **編譯目標** | Android 15（API 35） |
| **硬體** | 具備麥克風的 Android 裝置 |
| **儲存空間** | 約 50 MB（含 TFLite 模型約 40 MB） |
| **權限** | 麥克風錄音 (`RECORD_AUDIO`) |

---

## 演算法規格

以下參數完全對齊上游 [RedbirdTaiwan/silic2](https://github.com/RedbirdTaiwan/silic2) 的 Python 實作：

| 參數 | 值 | 說明 |
|---|---|---|
| **取樣率** | 32,000 Hz | 16-bit PCM Mono |
| **滑動窗口** | 3,000 ms（96,000 samples） | 每次送入模型的音訊長度 |
| **步長** | 1,500 ms（48,000 samples） | 窗口滑動間隔 |
| **FFT 大小** | 1,600 | n_fft 參數 |
| **Hop Length** | 400 | STFT 步長 |
| **Mel 濾波器數** | 240 | 頻譜解析度 |
| **頻率範圍** | 100 Hz ~ 15,000 Hz | fmin / fmax |
| **Mel 正規化** | norm=1（Slaney） | 三角濾波器歸一化 |
| **模型輸入尺寸** | 480 × 480 px | 彩虹頻譜圖解析度 |
| **彩虹頻段** | 5 bands × 32 色 | Rainbow Colormap |
| **推論引擎** | Google AI Edge LiteRT | TensorFlow Lite 後繼 |

---

## 專案架構

```
silic2_app/
├── app/src/main/
│   ├── assets/
│   │   ├── soundclass.csv              # 398 類野生動物聲音標籤庫
│   │   ├── model_classes.json          # YOLOv8 類別索引映射表
│   │   ├── best_float32.tflite         # YOLOv8 TFLite 模型（由上游模型匯出）
│   │   └── sample_owl.pcm             # 內建測試音訊樣本
│   │
│   ├── java/com/silic2/app/
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   ├── SoundClass.kt           # 聲音類別資料模型
│   │   │   │   ├── SilicDetection.kt       # 辨識結果資料模型
│   │   │   │   ├── AnimalCategory.kt       # 動物分類列舉（鳥/蛙/哺乳/其他）
│   │   │   │   └── RecordingSession.kt     # 錄音紀錄 Session 模型
│   │   │   └── repository/
│   │   │       ├── SoundClassRepository.kt # CSV 標籤庫讀取
│   │   │       └── RecordingRepository.kt  # 錄音歷史管理
│   │   │
│   │   ├── domain/
│   │   │   ├── audio/
│   │   │   │   ├── AudioRecorder.kt            # 32kHz AudioRecord 即時採集器
│   │   │   │   ├── AudioPlaybackManager.kt     # 音訊播放管理器
│   │   │   │   └── WavFileWriter.kt            # WAV 檔案寫入器
│   │   │   ├── processor/
│   │   │   │   ├── MelSpectrogramConverter.kt      # Mel 頻譜轉換器（對齊 nnAudio）
│   │   │   │   ├── RainbowRenderer.kt              # 5 頻段彩虹色彩映射渲染器
│   │   │   │   ├── RollingSpectrogramProcessor.kt  # 即時滾動頻譜處理器
│   │   │   │   ├── BatchAudioClassifier.kt         # 批次音訊分類器
│   │   │   │   ├── WavSpectrogramGenerator.kt      # WAV 檔案頻譜產生器
│   │   │   │   └── UniversalAudioDecoder.kt        # 通用音訊解碼器
│   │   │   └── inference/
│   │   │       └── SilicDetector.kt            # TFLite YOLOv8 推論器與 NMS
│   │   │
│   │   └── ui/
│   │       ├── MainActivity.kt                 # 應用程式入口
│   │       ├── SilicViewModel.kt               # 核心 ViewModel
│   │       ├── screens/
│   │       │   ├── LiveDetectionScreen.kt      # 即時錄音辨識主畫面
│   │       │   ├── RecordingsListScreen.kt     # 錄音歷史紀錄列表
│   │       │   ├── RecordingDetailScreen.kt    # 單筆錄音詳細檢視
│   │       │   └── SpeciesFilterScreen.kt      # 目標物種篩選設定
│   │       ├── components/
│   │       │   ├── SpectrogramView.kt              # 頻譜圖元件
│   │       │   ├── SpectrogramWaterfallView.kt     # 瀑布式頻譜圖
│   │       │   ├── DetectionCard.kt                # 辨識結果卡片
│   │       │   └── WaveformVisualizer.kt           # 波形視覺化
│   │       └── theme/                          # Material 3 主題
│   │
│   └── AndroidManifest.xml
│
├── export_tflite_model.py      # 上游模型 → TFLite 匯出工具
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### 技術堆疊

| 類別 | 技術 |
|---|---|
| **語言** | Kotlin |
| **UI 框架** | Jetpack Compose + Material 3 |
| **推論引擎** | Google AI Edge LiteRT 1.4.x |
| **FFT 運算** | JTransforms |
| **架構模式** | MVVM（ViewModel + Coroutines） |
| **最低 SDK** | Android 8.0（API 26） |

---

## 建置與安裝

### 使用 Android Studio

1. 以 Android Studio 開啟專案根目錄
2. 等待 Gradle 同步完成
3. 連接 Android 裝置或啟動模擬器
4. 點選 **Run ▶** 安裝至裝置

### 使用命令列

```bash
cd /path/to/silic2_app
./gradlew assembleDebug
```

產出的 APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 模型部署

本專案使用的 TFLite 模型由上游 [RedbirdTaiwan/silic2](https://github.com/RedbirdTaiwan/silic2) 的 PyTorch 權重檔（`best.pt`）轉換而來。

### 匯出步驟

```bash
# 需要在安裝有 ultralytics 的 Python 環境下執行
python export_tflite_model.py --model /path/to/silic2/model/v2026.1/best.pt
```

匯出腳本會自動將產出的 `best_float32.tflite` 複製至 `app/src/main/assets/` 目錄。

### 匯出選項

| 參數 | 預設值 | 說明 |
|---|---|---|
| `--model` | — | 輸入的 `best.pt` 模型路徑 |
| `--imgsz` | 480 | 模型輸入影像尺寸 |
| `--int8` | 關閉 | 啟用 INT8 量化（產出 `best_int8.tflite`） |

---

## 物種涵蓋範圍

目前搭載的模型版本為 **v2026.1**，涵蓋 **398 個聲音類別、279 個物種**。

### 分類統計

| 類群 | 說明 |
|---|---|
| 🐦 **鳥類** | 台灣常見與稀有鳥種，包含鳴聲（Song）、叫聲（Call）、未知聲型（Unknown）等多種聲音類別 |
| 🐸 **蛙類** | 腹斑蛙、海蟾蜍、台北樹蛙等，感謝吳昭頤、陳惇聿及楊懿如老師和海蟾蜍監測團隊提供聲音資料 |
| 🦌 **哺乳類** | 小鼯鼠、飛鼠、山羌、台灣獼猴等，感謝翁國精老師及鄭佳馨同學提供珍貴的聲音資料 |
| 🦎 **其他** | 蝎虎等爬蟲類 |

> 物種清單詳見 [`soundclass.csv`](app/src/main/assets/soundclass.csv)

---

## 上游專案引用

本專案的聲學辨識演算法與模型權重來自以下開源專案：

### SILIC 2 — Sound Identification and Labeling Intelligence for Creatures V2

- **GitHub**：[https://github.com/RedbirdTaiwan/silic2](https://github.com/RedbirdTaiwan/silic2)
- **開發團隊**：[RedbirdTaiwan（紅鳥團隊）](https://github.com/RedbirdTaiwan)
- **專案描述**：Sound identification and labeling pipeline for audio and video recordings.
- **模型版本**：v2026.1（398 sound classes of 279 species，2026 年 8 月更新）

### 本專案與上游的關係

| 面向 | 上游 silic2（Python 版） | 本專案 SILIC 2 Mobile（Android 版） |
|---|---|---|
| **平台** | Python CLI / Desktop UI | Android 原生 App（Kotlin） |
| **運行環境** | 工作站 / 伺服器 | 手機 / 平板（離線） |
| **推論引擎** | PyTorch (Ultralytics YOLO) | Google AI Edge LiteRT (TFLite) |
| **使用場景** | 實驗室批次分析 | 野外即時辨識 |
| **頻譜計算** | nnAudio (Python) | JTransforms (JVM) |
| **模型格式** | `best.pt`（PyTorch） | `best_float32.tflite`（TFLite） |
| **演算法參數** | — | ✅ 完全對齊 |

### 致謝

感謝 RedbirdTaiwan 團隊開源 SILIC 2 專案，以及所有提供野生動物聲音資料的研究者：

- **蛙類聲音資料**：吳昭頤、陳惇聿及楊懿如老師和海蟾蜍監測團隊
- **小鼯鼠及飛鼠聲音資料**：翁國精老師及鄭佳馨同學

---

## 授權條款

本專案的模型權重與聲音標籤資料源自 [RedbirdTaiwan/silic2](https://github.com/RedbirdTaiwan/silic2)，請依上游專案之授權條款使用。
