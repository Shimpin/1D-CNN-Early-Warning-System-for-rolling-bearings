# -*- coding: utf-8 -*-
"""
txt/csv 振动数据预测：供 Java 调用。
用法: python -u predict_txt.py <txt或csv文件绝对路径>
输出: 仅一行 JSON 到 stdout。
"""
import json
import sys
import os
from inference_mapping import parse_fault_by_code, load_label_index_map

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
os.chdir(SCRIPT_DIR)
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'


def _out(obj):
    s = json.dumps(obj, ensure_ascii=False)
    sys.stdout.buffer.write((s + '\n').encode('utf-8'))
    sys.stdout.flush()


def main():
    try:
        _run()
    except Exception as e:
        _out({"status": "error", "message": str(e)})


def _run():
    if len(sys.argv) < 2:
        _out({"status": "error", "message": "缺少参数：请传入 txt/csv 文件路径"})
        return

    data_path = sys.argv[1]
    if not os.path.isfile(data_path):
        _out({"status": "error", "message": f"文件不存在: {data_path}"})
        return

    model_path = os.path.join(SCRIPT_DIR, "final_cwru_model.h5")
    if not os.path.isfile(model_path):
        _out({"status": "error", "message": "未找到模型文件 final_cwru_model.h5"})
        return

    scaler_mean_path = os.path.join(SCRIPT_DIR, "scaler_mean.npy")
    scaler_scale_path = os.path.join(SCRIPT_DIR, "scaler_scale.npy")
    if not os.path.isfile(scaler_mean_path) or not os.path.isfile(scaler_scale_path):
        _out({"status": "error", "message": "未找到 scaler_mean.npy 或 scaler_scale.npy"})
        return

    try:
        import numpy as np
        from sklearn.preprocessing import StandardScaler
        from tensorflow.keras.models import load_model
    except Exception as e:
        _out({"status": "error", "message": f"加载依赖失败: {e}"})
        return

    # 读取 txt/csv：支持每行一个数，或逗号分隔
    data_list = []
    with open(data_path, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            for part in line.replace(',', ' ').split():
                try:
                    data_list.append(float(part))
                except ValueError:
                    pass
    if len(data_list) < 2048:
        _out({"status": "error", "message": "数据长度不足 2048 点，无法分帧预测"})
        return

    data = np.array(data_list, dtype=np.float64)
    model = load_model(model_path)
    scaler = StandardScaler()
    scaler.mean_ = np.load(scaler_mean_path)
    scaler.scale_ = np.load(scaler_scale_path)

    def split_into_samples(arr, sample_length=2048, step=1024):
        samples = []
        for i in range(0, len(arr) - sample_length + 1, step):
            samples.append(arr[i:i + sample_length])
        return np.array(samples)

    sample_length = 2048
    samples = split_into_samples(data, sample_length)
    samples_scaled = scaler.transform(samples)
    samples_reshaped = samples_scaled.reshape(-1, sample_length, 1)
    pred_probs = model.predict(samples_reshaped, verbose=0)
    avg_probs = np.mean(pred_probs, axis=0)
    pred_index = int(np.argmax(avg_probs))
    label_index_to_original_code = load_label_index_map(model, SCRIPT_DIR, np)
    pred_label_code = (
        label_index_to_original_code[pred_index]
        if pred_index < len(label_index_to_original_code)
        else pred_index
    )
    max_prob = float(np.max(avg_probs))

    fault_info = parse_fault_by_code(int(pred_label_code))
    fault_name = fault_info["label_name"]
    raw_label_name = fault_info["raw_label_name"]
    load_hp = int(fault_info["load_hp"])
    fault_size = float(fault_info["fault_size_inch"])
    fault_type = fault_info["fault_type"]
    is_warning = fault_type != "正常"

    result = {
        "status": "success",
        "file_path": data_path,
        "fault_name": fault_name,
        "raw_label_name": raw_label_name,
        "fault_type": fault_type,
        "load_hp": load_hp,
        "fault_size_inch": fault_size,
        "confidence": round(max_prob, 4),
        "warning": is_warning,
        "pred_index": pred_index,
        "pred_label_code": int(pred_label_code),
        "message": f"{'故障预警：' if is_warning else '正常：'}{fault_name}（置信度：{max_prob:.4f}）"
    }
    _out(result)


if __name__ == "__main__":
    main()
