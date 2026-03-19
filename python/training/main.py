import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import logging
from scipy.io import loadmat
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, f1_score
from sklearn.utils.class_weight import compute_class_weight
import tensorflow as tf
from tensorflow.keras.models import Sequential, load_model
from tensorflow.keras.layers import Conv1D, MaxPooling1D, Flatten, Dense, Dropout, BatchNormalization
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from tensorflow.keras.regularizers import l2
from tensorflow.keras.utils import to_categorical
import warnings

warnings.filterwarnings('ignore')

# ====================== 配置项（根据你的数据集调整）======================
_data_root = os.path.dirname(os.path.abspath(__file__))
DATA_ROOT = os.path.join(_data_root, "data")
# 1. 工业级全场景：三个 CWRU 数据源一起训练，模型可适配任意采样率与传感器位置
DATA_SOURCES = [
    ("12k Drive End Bearing Fault Data", 12000),
    ("12k Fan End Bearing Fault Data", 12000),
    ("48k Drive End Bearing Fault Data", 48000),
    ("Normal Baseline Data", 12000),
]
TARGET_SAMPLE_RATE = 12000
# 2. 样本配置
SAMPLE_LENGTH = 2048
STEP = 1024
# 3. 数据量控制
USE_SUBSET = True
MAX_SAMPLES_PER_CLASS = 800
# 4. 训练配置
BATCH_SIZE = 32
EPOCHS = 80
LEARNING_RATE = 0.0005
PATIENCE = 12
MODEL_SAVE_PATH = "final_cwru_model.h5"
# 5. 日志配置
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
# 优先使用 GPU 训练
gpus = tf.config.list_physical_devices("GPU")
if gpus:
    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        logging.info("使用 GPU 训练: %s", [g.name for g in gpus])
    except RuntimeError:
        pass
else:
    logging.warning("未检测到 GPU，将使用 CPU 训练")

# ====================== CWRU 故障映射（严格按 Bearing_Data_Center_Seeded_Fault_Test_Data.md）======================
# 四类：正常、内圈 Inner Race、滚动体 Ball、外圈 Outer Race（外圈分 6点/3点/12点）
# 格式：文件名前缀 -> (标签名, 标签编码, 故障类型, 负载HP, 故障尺寸英寸)
# 同一逻辑故障在 12k Drive / 48k Drive / 12k Fan 用不同文件号，这里统一为同一 code，便于多源训练
def _build_fault_mapping():
    # 规范：(label_name, fault_type, load, diameter), code = 顺序
    # 正常 0-3
    out = {
        "97": ("Normal_0HP", 0, "正常", 0, 0),
        "98": ("Normal_1HP", 1, "正常", 1, 0),
        "99": ("Normal_2HP", 2, "正常", 2, 0),
        "100": ("Normal_3HP", 3, "正常", 3, 0),
    }
    # 内圈 Inner Race：0.007/0.014/0.021/0.028，各 4 负载 -> code 4~19
    code = 4
    for dia, files_12k, files_48k, files_fan in [
        (0.007, ["105","106","107","108"], ["109","110","111","112"], ["278","279","280","281"]),
        (0.014, ["169","170","171","172"], ["174","175","176","177"], ["274","275","276","277"]),
        (0.021, ["209","210","211","212"], ["213","214","215","217"], ["270","271","272","273"]),
        (0.028, ["3001","3002","3003","3004"], ["3001","3002","3003","3004"], []),
    ]:
        for hp, f12, f48, ff in zip(range(4), files_12k, files_48k, (files_fan + [None]*4)[:4]):
            name = f"Inner_{dia}_{hp}HP"
            ent = (name, code, "内圈故障", hp, dia)
            out[f12] = ent
            if f48:
                out[f48] = ent
            if ff:
                out[ff] = ent
            code += 1
    # 滚动体 Ball：0.007/0.014/0.021/0.028，各 4 负载 -> code 20~35
    for dia, files_12k, files_48k, files_fan in [
        (0.007, ["118","119","120","121"], ["122","123","124","125"], ["282","283","284","285"]),
        (0.014, ["185","186","187","188"], ["189","190","191","192"], ["286","287","288","289"]),
        (0.021, ["222","223","224","225"], ["226","227","228","229"], ["290","291","292","293"]),
        (0.028, ["3005","3006","3007","3008"], ["3005","3006","3007","3008"], []),
    ]:
        for hp, f12, f48, ff in zip(range(4), files_12k, files_48k, (files_fan + [None]*4)[:4]):
            name = f"Ball_{dia}_{hp}HP"
            ent = (name, code, "滚动体故障", hp, dia)
            out[f12] = ent
            if f48:
                out[f48] = ent
            if ff:
                out[ff] = ent
            code += 1
    # 外圈 Outer：0.007 分 6点/3点/12点，各 4 负载 -> 36~47；0.014 仅一组 48~51；0.021 分 6/3/1.2 -> 52~63
    for pos_name, pos_code, dia, files_12k, files_48k, files_fan in [
        ("Outer6_0.007", 0, 0.007, ["130","131","132","133"], ["135","136","137","138"], ["294","295","296","297"]),
        ("Outer3_0.007", 1, 0.007, ["144","145","146","147"], ["148","149","150","151"], ["298","299","300","301"]),
        ("Outer12_0.007", 2, 0.007, ["156","158","159","160"], ["161","162","163","164"], ["302","305","306","307"]),
    ]:
        for hp, f12, f48, ff in zip(range(4), files_12k, files_48k, (files_fan + [None]*4)[:4]):
            name = f"{pos_name}_{hp}HP"
            ent = (name, code, "外圈故障", hp, dia)
            out[f12] = ent
            if f48:
                out[f48] = ent
            if ff:
                out[ff] = ent
            code += 1
    # 0.014 外圈：197-200(12k), 201-204(48k), Fan 313/310@0HP, 309@1, 311@2, 312@3
    for hp, f12, f48 in zip(range(4), ["197","198","199","200"], ["201","202","203","204"]):
        ent = (f"Outer_0.014_{hp}HP", code, "外圈故障", hp, 0.014)
        out[f12] = ent
        out[f48] = ent
        code += 1
    for f, hp in [("313",0), ("310",0), ("309",1), ("311",2), ("312",3)]:
        out[f] = (f"Outer_0.014_{hp}HP", 48 + hp, "外圈故障", hp, 0.014)
    code = 52
    for pos_name, dia, files_12k, files_48k, files_fan in [
        ("Outer6_0.021", 0.021, ["234","235","236","237"], ["238","239","240","241"], ["315","316","317","318"]),
        ("Outer3_0.021", 0.021, ["246","247","248","249"], ["250","251","252","253"], []),
        ("Outer12_0.021", 0.021, ["258","259","260","261"], ["262","263","264","265"], []),
    ]:
        flist = list(zip(range(4), files_12k, files_48k, (files_fan + [None]*4)[:4]))
        for hp, f12, f48, ff in flist:
            name = f"{pos_name}_{hp}HP"
            ent = (name, code, "外圈故障", hp, dia)
            out[f12] = ent
            if f48:
                out[f48] = ent
            if ff:
                out[ff] = ent
            code += 1
    return out

FAULT_MAPPING = _build_fault_mapping()


def get_ordered_class_names(num_classes):
    code_to_name = {}
    for _prefix, (label_name, code, _ft, _load, _size) in FAULT_MAPPING.items():
        if code < num_classes:
            code_to_name[code] = label_name
    return [code_to_name.get(i, f"Class_{i}") for i in range(num_classes)]


def get_class_names_from_codes(codes):
    code_to_name = {}
    for _prefix, (label_name, code, _ft, _load, _size) in FAULT_MAPPING.items():
        code_to_name[code] = label_name
    return [code_to_name.get(c, f"Class_{c}") for c in codes]


def _resample_to_target(signal, source_rate_hz, target_rate_hz):
    if source_rate_hz <= 0 or target_rate_hz <= 0:
        return signal
    ratio = source_rate_hz / target_rate_hz
    if abs(ratio - 1.0) < 1e-6:
        return signal
    if ratio >= 1:
        step = max(1, int(round(ratio)))
        return signal[::step].copy()
    new_len = int(round(len(signal) * ratio))
    indices = np.linspace(0, len(signal) - 1, new_len).astype(int)
    return signal[indices]


def _load_mat_signals(mat_data, file_name):
    signals = []
    for pattern in ("DE_time", "FE_time"):
        keys = [k for k in mat_data.keys() if pattern in k and not k.startswith("__")]
        for k in keys:
            data = mat_data[k].flatten()
            if len(data) >= SAMPLE_LENGTH:
                signals.append(data)
    if not signals:
        logging.warning(f"文件{file_name}中未找到 DE_time/FE_time 或长度不足")
    return signals


def load_multi_source_cwru(data_root, data_sources, target_rate_hz=12000):
    if not os.path.isdir(data_root):
        raise FileNotFoundError(f"数据根目录不存在：{data_root}，请检查 DATA_ROOT。")
    X_all = []
    y_all = []
    label_names = []

    for subdir, source_rate_hz in data_sources:
        data_dir = os.path.join(data_root, subdir)
        if not os.path.isdir(data_dir):
            logging.warning(f"跳过不存在的目录：{data_dir}")
            continue
        for file_name in sorted(os.listdir(data_dir)):
            if not file_name.endswith(".mat"):
                continue
            file_prefix = file_name.split(".")[0]
            if file_prefix not in FAULT_MAPPING:
                logging.warning(f"跳过未定义的文件：{file_name}")
                continue
            label_name, label_code, fault_type, load, fault_size = FAULT_MAPPING[file_prefix]
            file_path = os.path.join(data_dir, file_name)
            try:
                mat_data = loadmat(file_path)
                signal_list = _load_mat_signals(mat_data, file_name)
                if not signal_list:
                    continue
                total_samples = 0
                for raw_signal in signal_list:
                    signal = _resample_to_target(raw_signal, source_rate_hz, target_rate_hz)
                    samples = split_into_samples(signal, SAMPLE_LENGTH, STEP)
                    if len(samples) == 0:
                        continue
                    X_all.append(samples)
                    total_samples += len(samples)
                if total_samples > 0:
                    y_all.extend([label_code] * total_samples)
                    label_names.extend([label_name] * total_samples)
                    logging.info(f"加载：{subdir}/{file_name} -> {label_name}，样本数：{total_samples}")
            except Exception as e:
                logging.error(f"加载失败 {file_path}：{e}")
                continue

    if len(X_all) == 0:
        raise ValueError(
            "未在任一数据源中找到可用 .mat 文件。"
            "请检查 DATA_ROOT、DATA_SOURCES 及 FAULT_MAPPING。"
        )
    X_combined = np.concatenate(X_all, axis=0)
    y_combined = np.array(y_all)
    label_names_arr = np.array(label_names)

    if USE_SUBSET and MAX_SAMPLES_PER_CLASS > 0:
        X_sub, y_sub, ln_sub = [], [], []
        for c in np.unique(y_combined):
            idx = np.where(y_combined == c)[0]
            if len(idx) > MAX_SAMPLES_PER_CLASS:
                rng = np.random.RandomState(42)
                idx = rng.choice(idx, MAX_SAMPLES_PER_CLASS, replace=False)
            X_sub.append(X_combined[idx])
            y_sub.append(y_combined[idx])
            ln_sub.append(label_names_arr[idx])
        X_combined = np.concatenate(X_sub, axis=0)
        y_combined = np.concatenate(y_sub, axis=0)
        label_names_arr = np.concatenate(ln_sub, axis=0)
        logging.info(f"已使用子集：每类最多 {MAX_SAMPLES_PER_CLASS} 样本")

    logging.info("多源加载完成 - 总样本数：%d，类别数：%d", len(X_combined), len(np.unique(y_combined)))
    return X_combined, y_combined, label_names_arr


def split_into_samples(data, sample_length=1024, step=256):
    samples = []
    if len(data) < sample_length:
        return np.array(samples)
    for i in range(0, len(data) - sample_length + 1, step):
        samples.append(data[i:i + sample_length])
    return np.array(samples)


def data_augmentation(X, noise_level=0.01):
    noise = np.random.normal(0, noise_level, X.shape)
    return np.concatenate([X, X + noise], axis=0)


def preprocess_data(X, y):
    unique_codes = np.unique(y)
    num_classes = len(unique_codes)
    code_to_idx = {c: i for i, c in enumerate(unique_codes)}
    y_remapped = np.array([code_to_idx[yi] for yi in y])
    ordered_class_names = get_class_names_from_codes(unique_codes)
    label_index_to_original_code = unique_codes.tolist()

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)
    X_augmented = data_augmentation(X_scaled)
    y_augmented = np.concatenate([y_remapped, y_remapped], axis=0)
    X_reshaped = X_augmented.reshape(-1, SAMPLE_LENGTH, 1)

    idx_1hp = next((i for i, n in enumerate(ordered_class_names) if n == 'Normal_1HP'), None)
    idx_2hp = next((i for i, n in enumerate(ordered_class_names) if n == 'Normal_2HP'), None)
    if idx_1hp is not None or idx_2hp is not None:
        mask = np.zeros(len(y_augmented), dtype=bool)
        if idx_1hp is not None:
            mask |= (y_augmented == idx_1hp)
        if idx_2hp is not None:
            mask |= (y_augmented == idx_2hp)
        if mask.any():
            X_reshaped = np.concatenate([X_reshaped, X_reshaped[mask]], axis=0)
            y_augmented = np.concatenate([y_augmented, y_augmented[mask]], axis=0)
            logging.info("已对 Normal_1HP / Normal_2HP 过采样一次，训练样本数：%d", len(y_augmented))

    y_onehot = to_categorical(y_augmented, num_classes=num_classes)
    X_train, X_test, y_train, y_test = train_test_split(
        X_reshaped, y_onehot, test_size=0.2, random_state=42, stratify=y_augmented
    )
    logging.info("预处理完成 - 训练集：%s，测试集：%s，类别数：%d", X_train.shape, X_test.shape, num_classes)
    return X_train, X_test, y_train, y_test, scaler, num_classes, ordered_class_names, label_index_to_original_code


def build_advanced_1d_cnn(input_shape, num_classes):
    model = Sequential([
        Conv1D(64, 32, activation='relu', input_shape=input_shape, kernel_regularizer=l2(0.001)),
        BatchNormalization(),
        MaxPooling1D(4),
        Dropout(0.2),
        Conv1D(128, 16, activation='relu', kernel_regularizer=l2(0.001)),
        BatchNormalization(),
        MaxPooling1D(4),
        Dropout(0.3),
        Conv1D(256, 8, activation='relu', kernel_regularizer=l2(0.001)),
        BatchNormalization(),
        MaxPooling1D(2),
        Dropout(0.4),
        Conv1D(512, 4, activation='relu', kernel_regularizer=l2(0.001)),
        BatchNormalization(),
        MaxPooling1D(2),
        Dropout(0.4),
        Flatten(),
        Dense(256, activation='relu', kernel_regularizer=l2(0.001)),
        BatchNormalization(),
        Dropout(0.5),
        Dense(128, activation='relu', kernel_regularizer=l2(0.001)),
        Dropout(0.5),
        Dense(num_classes, activation='softmax'),
    ])
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=LEARNING_RATE),
        loss='categorical_crossentropy',
        metrics=['accuracy', tf.keras.metrics.Precision(), tf.keras.metrics.Recall()],
    )
    return model


def _get_class_weights(y_train_onehot, num_classes, ordered_class_names):
    y_train_classes = np.argmax(y_train_onehot, axis=1)
    weights = compute_class_weight('balanced', classes=np.arange(num_classes), y=y_train_classes)
    class_weight = {i: float(w) for i, w in enumerate(weights)}
    CONFUSED_PAIR_BOOST = 1.8
    for name in ('Normal_2HP', 'Normal_1HP'):
        idx = next((i for i, n in enumerate(ordered_class_names) if n == name), None)
        if idx is not None:
            class_weight[idx] = class_weight.get(idx, 1.0) * CONFUSED_PAIR_BOOST
    return class_weight


def train_model(X_train, y_train, num_classes, ordered_class_names=None):
    callbacks = [
        EarlyStopping(monitor='val_loss', patience=PATIENCE, restore_best_weights=True),
        ReduceLROnPlateau(monitor='val_accuracy', factor=0.5, patience=5, min_lr=1e-6),
        ModelCheckpoint(MODEL_SAVE_PATH, monitor='val_accuracy', save_best_only=True, verbose=1),
    ]
    class_weight = None
    if ordered_class_names is not None:
        class_weight = _get_class_weights(y_train, num_classes, ordered_class_names)
    model = build_advanced_1d_cnn((SAMPLE_LENGTH, 1), num_classes)
    model.summary()
    fit_kw = dict(batch_size=BATCH_SIZE, epochs=EPOCHS, validation_split=0.2, callbacks=callbacks, shuffle=True, verbose=1)
    if class_weight is not None:
        fit_kw['class_weight'] = class_weight
    history = model.fit(X_train, y_train, **fit_kw)
    return model, history


def evaluate_model(model, X_test, y_test, ordered_class_names):
    num_classes = len(ordered_class_names)
    y_pred = model.predict(X_test, verbose=0)
    y_pred_classes = np.argmax(y_pred, axis=1)
    y_true_classes = np.argmax(y_test, axis=1)
    target_names = ordered_class_names
    accuracy = accuracy_score(y_true_classes, y_pred_classes)
    f1_macro = f1_score(y_true_classes, y_pred_classes, average='macro', zero_division=0)
    f1_weighted = f1_score(y_true_classes, y_pred_classes, average='weighted', zero_division=0)
    report = classification_report(y_true_classes, y_pred_classes, target_names=target_names, zero_division=0)
    print("\n========== 模型评估结果 ==========")
    print(f"测试集准确率 Accuracy: {accuracy:.4f}")
    print(f"宏平均 F1 (Macro F1):   {f1_macro:.4f}")
    print(f"加权 F1 (Weighted F1):  {f1_weighted:.4f}")
    print(f"\n分类报告：\n{report}")
    cm = confusion_matrix(y_true_classes, y_pred_classes)
    n_show = min(10, num_classes)
    plt.figure(figsize=(12, 10))
    sns.heatmap(cm[:n_show, :n_show], annot=True, fmt='d', cmap='Blues',
                xticklabels=target_names[:n_show], yticklabels=target_names[:n_show])
    plt.title(f'混淆矩阵（前 {n_show} 类）')
    plt.xlabel('预测标签')
    plt.ylabel('真实标签')
    plt.tight_layout()
    plt.savefig('confusion_matrix.png', dpi=300)
    plt.show()
    plt.figure(figsize=(14, 12))
    ax = plt.gca()
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', ax=ax,
                xticklabels=target_names, yticklabels=target_names, annot_kws={'size': 7})
    ax.set_title('混淆矩阵（全部类别）')
    ax.set_xlabel('预测标签')
    ax.set_ylabel('真实标签')
    plt.xticks(rotation=45, ha='right')
    plt.yticks(rotation=0)
    plt.tight_layout()
    plt.savefig('confusion_matrix_full.png', dpi=300)
    plt.show()
    if hasattr(model, "history") and model.history is not None:
        plot_training_history(model.history)
    return accuracy, report


def plot_training_history(history):
    h = history.history
    acc = h.get("accuracy", h.get("acc", []))
    val_acc = h.get("val_accuracy", h.get("val_acc", []))
    prec, val_prec = h.get("precision", []), h.get("val_precision", [])
    rec, val_rec = h.get("recall", []), h.get("val_recall", [])
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    axes[0, 0].plot(acc, label='训练准确率')
    axes[0, 0].plot(val_acc, label='验证准确率')
    axes[0, 0].set_title('模型准确率')
    axes[0, 0].legend()
    axes[0, 1].plot(h["loss"], label='训练损失')
    axes[0, 1].plot(h["val_loss"], label='验证损失')
    axes[0, 1].set_title('模型损失')
    axes[0, 1].legend()
    if prec or val_prec or rec or val_rec:
        axes[1, 0].plot(prec or [0], label='训练精确率')
        axes[1, 0].plot(val_prec or [0], label='验证精确率')
        axes[1, 0].set_title('精确率')
        axes[1, 0].legend()
        axes[1, 1].plot(rec or [0], label='训练召回率')
        axes[1, 1].plot(val_rec or [0], label='验证召回率')
        axes[1, 1].set_title('召回率')
        axes[1, 1].legend()
    else:
        axes[1, 0].set_visible(False)
        axes[1, 1].set_visible(False)
    plt.tight_layout()
    plt.savefig('training_history.png', dpi=300)
    plt.show()


class BearingFaultWarningSystem:
    def __init__(self, model_path, scaler, sample_length=1024, label_index_to_original_code=None):
        self.model = load_model(model_path)
        self.scaler = scaler
        self.sample_length = sample_length
        if label_index_to_original_code is not None:
            self.label_index_to_original_code = label_index_to_original_code
        else:
            map_path = os.path.join(os.path.dirname(model_path), "label_index_to_original_code.npy")
            self.label_index_to_original_code = np.load(map_path, allow_pickle=True).tolist() if os.path.isfile(map_path) else list(range(64))
        self.fault_severity = {"正常": 0, "内圈故障": 1, "外圈故障": 1, "滚动体故障": 2}

    def predict_fault(self, new_data, threshold=0.85):
        samples = split_into_samples(new_data, self.sample_length)
        if len(samples) == 0:
            return {"status": "error", "message": "数据长度不足"}
        samples_scaled = self.scaler.transform(samples)
        samples_reshaped = samples_scaled.reshape(-1, self.sample_length, 1)
        pred_probs = self.model.predict(samples_reshaped, verbose=0)
        avg_probs = np.mean(pred_probs, axis=0)
        max_prob = np.max(avg_probs)
        pred_index = np.argmax(avg_probs)
        pred_label = self.label_index_to_original_code[pred_index] if pred_index < len(self.label_index_to_original_code) else pred_index
        fault_info = self._parse_fault_label(pred_label)
        fault_type = fault_info["fault_type"]
        severity = self.fault_severity.get(fault_type, 0)
        result = {"fault_type": fault_type, "confidence": max_prob, "load": fault_info["load"], "fault_size": fault_info["fault_size"], "severity": severity, "warning": False, "message": ""}
        if fault_type == "正常":
            result["message"] = f"轴承状态正常（置信度：{max_prob:.4f}）"
        else:
            result["warning"] = max_prob >= threshold
            result["message"] = f"⚠️ 预警！检测到{fault_type}（负载：{fault_info['load']}HP，故障尺寸：{fault_info['fault_size']}英寸，置信度：{max_prob:.4f}）" if result["warning"] else f"⚠️ 疑似{fault_type}（置信度：{max_prob:.4f}）"
        return result

    def _parse_fault_label(self, label_code):
        for prefix, (label_name, code, fault_type, load, fault_size) in FAULT_MAPPING.items():
            if code == label_code:
                return {"fault_type": fault_type, "load": load, "fault_size": fault_size, "label_name": label_name}
        return {"fault_type": "未知", "load": 0, "fault_size": 0, "label_name": "Unknown"}


if __name__ == "__main__":
    X, y, label_names = load_multi_source_cwru(DATA_ROOT, DATA_SOURCES, TARGET_SAMPLE_RATE)
    X_train, X_test, y_train, y_test, scaler, num_classes, ordered_class_names, label_index_to_original_code = preprocess_data(X, y)
    model, history = train_model(X_train, y_train, num_classes, ordered_class_names)
    accuracy, report = evaluate_model(model, X_test, y_test, ordered_class_names)
    model.save(MODEL_SAVE_PATH)
    np.save("scaler_mean.npy", scaler.mean_)
    np.save("scaler_scale.npy", scaler.scale_)
    np.save("label_index_to_original_code.npy", np.array(label_index_to_original_code))
    logging.info("模型和标准化器已保存 -> 部署用：%s + 3 个 .npy", MODEL_SAVE_PATH)
    warning_system = BearingFaultWarningSystem(MODEL_SAVE_PATH, scaler, SAMPLE_LENGTH, label_index_to_original_code)
    test_data = X_test[0].reshape(-1)
    print("\n========== 故障预警测试结果 ==========")
    print(warning_system.predict_fault(test_data))
