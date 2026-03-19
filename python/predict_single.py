# -*- coding: utf-8 -*-
"""
单文件预测接口：供 Java 调用。
用法: python -u predict_single.py <mat文件绝对路径>
输出: 仅一行 JSON 到 stdout，无其他打印。
"""
import json
import sys
import os
from inference_mapping import parse_fault_by_code, load_label_index_map

# 脚本所在目录为工作目录，便于加载同目录下的模型和 npy
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
os.chdir(SCRIPT_DIR)

# 抑制 TensorFlow 日志输出
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'  # 0=all, 1=info, 2=warning, 3=error
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'  # 禁用 oneDNN 日志


def _out(obj):
    """唯一输出入口：打印一行 JSON 并立即刷新，供 Java 读取。"""
    s = json.dumps(obj, ensure_ascii=False)
    # 确保使用 UTF-8 编码输出
    sys.stdout.buffer.write((s + '\n').encode('utf-8'))
    sys.stdout.flush()


def main():
    try:
        _run()
    except Exception as e:
        _out({"status": "error", "message": str(e)})


def _run():
    if len(sys.argv) < 2:
        _out({"status": "error", "message": "缺少参数：请传入 .mat 文件路径"})
        return

    mat_path = sys.argv[1]
    if not os.path.isfile(mat_path):
        _out({"status": "error", "message": f"文件不存在: {mat_path}"})
        return

    model_path = os.path.join(SCRIPT_DIR, "final_cwru_model.h5")
    if not os.path.isfile(model_path):
        _out({"status": "error", "message": "未找到模型文件 final_cwru_model.h5"})
        return

    scaler_mean_path = os.path.join(SCRIPT_DIR, "scaler_mean.npy")
    scaler_scale_path = os.path.join(SCRIPT_DIR, "scaler_scale.npy")
    if not os.path.isfile(scaler_mean_path) or not os.path.isfile(scaler_scale_path):
        _out({"status": "error", "message": "未找到 scaler_mean.npy 或 scaler_scale.npy，请从训练环境复制到本目录"})
        return

    try:
        # 在导入 TensorFlow 前设置日志级别
        os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
        os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'

        import numpy as np
        from scipy.io import loadmat
        from sklearn.preprocessing import StandardScaler
        from tensorflow.keras.models import load_model
    except Exception as e:
        _out({"status": "error", "message": f"加载依赖失败: {e}"})
        return

    model = load_model(model_path)
    scaler = StandardScaler()
    scaler.mean_ = np.load(scaler_mean_path)
    scaler.scale_ = np.load(scaler_scale_path)

    def split_into_samples(data, sample_length=2048, step=1024):
        samples = []
        if len(data) < sample_length:
            return np.array(samples)
        for i in range(0, len(data) - sample_length + 1, step):
            samples.append(data[i:i + sample_length])
        return np.array(samples)

    try:
        mat_data = loadmat(mat_path)
        de_keys = [k for k in mat_data.keys() if 'DE_time' in k]
        if not de_keys:
            _out({"status": "error", "message": "未找到 DE_time 振动信号"})
            return
        de_data = mat_data[de_keys[0]].flatten()

        sample_length = 2048
        samples = split_into_samples(de_data, sample_length)
        if len(samples) == 0:
            _out({"status": "error", "message": "数据长度不足，无法分帧"})
            return

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
            "file_path": mat_path,
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
    except Exception as e:
        _out({"status": "error", "message": f"预测执行失败：{e}"})


if __name__ == "__main__":
    main()
