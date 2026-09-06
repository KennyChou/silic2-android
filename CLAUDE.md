# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案概觀

SILIC 2 Mobile：把上游 [RedbirdTaiwan/silic2](https://github.com/RedbirdTaiwan/silic2)（Python + PyTorch YOLOv8）的野生動物聲音辨識管線移植到 Android，100% 離線端側推論。單模組 Gradle 專案（`:app`），Kotlin + Jetpack Compose + LiteRT。

程式碼註解、UI 文案、commit message 皆為繁體中文（zh-TW）。

## 常用指令

```bash
./gradlew assembleDebug                 # 產出 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                  # 安裝到連線裝置
./gradlew assembleRelease               # 需要 key.properties（見下）
./gradlew connectedDebugAndroidTest     # 全部 instrumented 測試（需實機/模擬器）

# 單一測試
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=cc.kennydev.silic2.app.SilicInferenceTest#testRealDeviceInferenceOnSampleOwl
```

沒有 JVM 單元測試（`src/test/` 不存在）。所有測試都在 `app/src/androidTest/` 且需要真實裝置——模型推論、Bitmap、`AudioRecord` 都依賴 Android runtime。

### 建置前置條件（容易踩雷）

- `app/src/main/assets/best_float32.tflite` 與 `sample_owl.pcm` **被 .gitignore 排除**，clone 後不存在。缺模型時 App 仍可啟動但 `SilicDetector.isModelLoaded == false`、偵測永遠回傳空清單；`testRealDeviceInferenceOnSampleOwl` 會失敗。用 `python export_tflite_model.py --model /path/to/silic2/model/v2026.1/best.pt` 重新匯出（需要 ultralytics 環境，腳本會自動複製進 assets）。
- `assembleRelease` 讀 `rootProject.file("key.properties")`（同樣被 gitignore，含 `storeFile`/`storePassword`/`keyAlias`/`keyPassword`）。檔案不存在時 `keyProperties["storeFile"] as String` 會直接 NPE，整個 configuration phase 掛掉——debug 建置也會受影響。
- release 的 `proguard-rules.pro` 在 `app/build.gradle.kts` 有引用但**檔案不存在**；`isMinifyEnabled = true`，第一次做 release 建置前要先建立它（LiteRT / TFLite 反射類別需要 keep 規則）。
- `test_litert.gradle.kts` 是遺留的空殼檔，未被任何地方 include。

## 架構

### 推論管線（核心）

```
麥克風 AudioRecord (32kHz/mono/16-bit)
  → MelSpectrogramConverter  峰值正規化 → 100Hz 高通 → STFT → Mel 濾波 → log(log(x))
  → RainbowRenderer          5 頻段各自 min/max 正規化 → 32 色 rainbow palette → 480×480 Bitmap
  → SilicDetector            LiteRT Interpreter → [1, 300, 6] → 閾值 + 物種過濾
  → SilicDetection           時間/頻率反算（x → ms、y → Mel 逆轉換 Hz）
```

三種進入管線的路徑，共用同一組 processor 實例（都建在 `SilicViewModel`）：

1. **即時錄音**：`AudioRecorder` 以 3000ms 窗、1500ms 步長切 clip → `processAudioClip()`。
2. **檔案匯入**（Intent SEND/VIEW，`audio/*`）：`UniversalAudioDecoder`（MediaCodec，任意格式 → 32kHz mono）→ `BatchAudioClassifier` 滑動視窗 + 同物種重疊合併。
3. **範例驗證**：`runSampleTest()` 讀 `sample_owl.pcm`。

### 演算法參數必須與上游對齊

`sr=32000, n_fft=1600, hop=400, n_mels=240, fmin=100, fmax=15000, Slaney norm=1`、3000ms 窗 / 1500ms 步長、480×480 輸入、5 頻段 ×32 色。這些不是可調參數——改了就無法和上游 Python 結果比對。`MelSpectrogramConverter` 刻意複製 Python 端的怪癖：`pydub` 峰值正規化到 32000、單極 RC 高通、reflect padding、雙重 log（`ln(ln(spec))`）。

**Mel filterbank 的初始化程式碼在三個檔案各有一份**：`MelSpectrogramConverter`、`RollingSpectrogramProcessor`、`WavSpectrogramGenerator`。rainbow palette 也在 `RainbowRenderer` 和 `RollingSpectrogramProcessor` 各有一份。動到 DSP 參數或配色時要同步全部，否則即時瀑布圖與送模型的特徵會不一致。

### 類別 ID 兩層映射

模型輸出的是 0..402 的 YOLO class index，**不是** soundclass_id。`model_classes.json`（403 個 int 的陣列）把 index 映到 `soundclass.csv` 的 `soundclass_id`；`SoundClassRepository`（object 單例、記憶體快取、處理 UTF-8 BOM）再把 id 映到物種名/學名/頻率範圍。UI 的物種過濾 `targetClassIds` 用的是 soundclass_id。

`AnimalCategory.fromSpecies()` 是中文關鍵字啟發式（含「蛙/蟾」→ FROG，「鼯鼠/獼猴/山羌…」→ MAMMAL，「蝎虎/壁虎…」→ OTHER，其餘一律 BIRD）。`SilicInferenceTest.testCategoryClassification` 對計數做斷言（蛙 40、哺乳 22），改關鍵字會讓測試失敗。

### 模型輸出格式

`SilicDetector` 只處理 shape `[1, N, 6]` 的輸出（NMS 已 baked 進 graph，`[x1, y1, x2, y2, score, class]`，座標是 0..480 像素）。輸入以 `TensorImage` + 手動 `/255.0f` 正規化，CPU 4 threads、NNAPI 關閉（`litert-gpu` 有拉進來但沒啟用 GPU delegate）。

### 狀態與儲存

`SilicViewModel`（656 行，AndroidViewModel）持有所有 domain 物件與多個 `StateFlow`，`MainActivity` 用 `Screen` enum 手動切畫面（無 Navigation 元件）。

**沒有資料庫**。錄音 session 由檔名慣例還原：`getExternalFilesDir(MUSIC)` 下的 `SILIC2_<yyyyMMdd_HHmmss>.wav` 加上同名 sidecar `.txt`（Raven selection table，tab 分隔）與 `.csv`（UTF-8 BOM，中文表頭）。`RecordingRepository.loadSessions()` 掃目錄、用 `(檔案大小 - 44) / (32000 * 2)` 推算時長、優先解析 `.txt` 再退回 `.csv`。改動任一輸出格式時，`writeRavenFile`/`writeCsvFile`（ViewModel）與 `parseRavenFile`/`parseCsvFile`（Repository）必須同步。

分享/匯出走 `FileProvider`（authority `${applicationId}.fileprovider`，路徑見 `res/xml/file_paths.xml`）。

## 其他

- `namespace` / `applicationId` 為 `cc.kennydev.silic2.app`（曾由 `com.silic2.app` 改名；README 的目錄樹仍寫舊路徑）。
- `versionCode` 由 `git rev-list --count HEAD` 自動產生（每次 commit 自動遞增）；`versionName` 由 `git describe --tags --always` 產生。上架新版本前只需打 `git tag vX.Y.Z` 即可，**無需手動修改 build.gradle.kts**。
