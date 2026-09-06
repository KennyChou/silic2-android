#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SILIC 2 YOLOv8 -> TFLite Model Exporter for Android
將 silic2 的 PyTorch 模型權重 (best.pt) 轉換並匯出至 Android App 的 assets 目錄。
"""

import os
import shutil
import argparse
from ultralytics import YOLO

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
ASSETS_DIR = os.path.join(CURRENT_DIR, "app", "src", "main", "assets")

def export_model(pt_path, imgsz=480, int8=False):
    print("=" * 60)
    print(f"正在匯出 SILIC 2 模型至 TFLite:")
    print(f"  • 輸入模型: {pt_path}")
    print(f"  • 影像尺寸: {imgsz}x{imgsz}")
    print(f"  • INT8 量化: {int8}")
    print("=" * 60)

    if not os.path.isfile(pt_path):
        raise FileNotFoundError(f"找不到模型檔案: {pt_path}")

    model = YOLO(pt_path)

    # 匯出為 TFLite
    exported_path = model.export(
        format="tflite",
        imgsz=imgsz,
        int8=int8
    )

    print(f"\n匯出完成: {exported_path}")

    # 自動複製至 assets 目錄
    os.makedirs(ASSETS_DIR, exist_ok=True)
    target_name = "best_int8.tflite" if int8 else "best_float32.tflite"
    target_path = os.path.join(ASSETS_DIR, target_name)

    shutil.copy2(exported_path, target_path)
    print(f"✅ 已自動部署至 Android assets: {target_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="SILIC 2 TFLite 匯出工具")
    parser.add_argument(
        "--model",
        type=str,
        default="/home/kenny/work/python/silic2/model/v2026.1/best.pt",
        help="輸入的 best.pt 路徑"
    )
    parser.add_argument("--imgsz", type=int, default=480, help="輸入影像尺寸 (預設 480)")
    parser.add_argument("--int8", action="store_true", help="啟用 INT8 量化")
    args = parser.parse_args()

    export_model(args.model, args.imgsz, args.int8)
