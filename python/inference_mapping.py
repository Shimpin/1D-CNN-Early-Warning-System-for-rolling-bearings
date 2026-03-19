# -*- coding: utf-8 -*-
"""
推理标签映射工具（与 training/main.py 保持一致）
"""
import os


def build_fault_mapping():
    """
    返回：prefix -> (label_name, code, fault_type, load, fault_size)
    code 定义与 training/main.py 的 _build_fault_mapping 一致。
    """
    out = {
        "97": ("Normal_0HP", 0, "正常", 0, 0),
        "98": ("Normal_1HP", 1, "正常", 1, 0),
        "99": ("Normal_2HP", 2, "正常", 2, 0),
        "100": ("Normal_3HP", 3, "正常", 3, 0),
    }

    code = 4
    # 内圈：4~19
    for dia, files_12k, files_48k, files_fan in [
        (0.007, ["105", "106", "107", "108"], ["109", "110", "111", "112"], ["278", "279", "280", "281"]),
        (0.014, ["169", "170", "171", "172"], ["174", "175", "176", "177"], ["274", "275", "276", "277"]),
        (0.021, ["209", "210", "211", "212"], ["213", "214", "215", "217"], ["270", "271", "272", "273"]),
        (0.028, ["3001", "3002", "3003", "3004"], ["3001", "3002", "3003", "3004"], []),
    ]:
        for hp, f12, f48, ff in zip(range(4), files_12k, files_48k, (files_fan + [None] * 4)[:4]):
            ent = (f"Inner_{dia}_{hp}HP", code, "内圈故障", hp, dia)
            out[f12] = ent
            if f48:
                out[f48] = ent
            if ff:
                out[ff] = ent
            code += 1

    # 滚动体：20~35
    for dia, files_12k, files_48k, files_fan in [
        (0.007, ["118", "119", "120", "121"], ["122", "123", "124", "125"], ["282", "283", "284", "285"]),
        (0.014, ["185", "186", "187", "188"], ["189", "190", "191", "192"], ["286", "287", "288", "289"]),
        (0.021, ["222", "223", "224", "225"], ["226", "227", "228", "229"], ["290", "291", "292", "293"]),
        (0.028, ["3005", "3006", "3007", "3008"], ["3005", "3006", "3007", "3008"], []),
    ]:
        for hp, f12, f48, ff in zip(range(4), files_12k, files_48k, (files_fan + [None] * 4)[:4]):
            ent = (f"Ball_{dia}_{hp}HP", code, "滚动体故障", hp, dia)
            out[f12] = ent
            if f48:
                out[f48] = ent
            if ff:
                out[ff] = ent
            code += 1

    # 外圈：36~63
    for pos_name, dia, files_12k, files_48k, files_fan in [
        ("Outer6_0.007", 0.007, ["130", "131", "132", "133"], ["135", "136", "137", "138"], ["294", "295", "296", "297"]),
        ("Outer3_0.007", 0.007, ["144", "145", "146", "147"], ["148", "149", "150", "151"], ["298", "299", "300", "301"]),
        ("Outer12_0.007", 0.007, ["156", "158", "159", "160"], ["161", "162", "163", "164"], ["302", "305", "306", "307"]),
    ]:
        for hp, f12, f48, ff in zip(range(4), files_12k, files_48k, (files_fan + [None] * 4)[:4]):
            ent = (f"{pos_name}_{hp}HP", code, "外圈故障", hp, dia)
            out[f12] = ent
            if f48:
                out[f48] = ent
            if ff:
                out[ff] = ent
            code += 1

    # 0.014 外圈：48~51
    for hp, f12, f48 in zip(range(4), ["197", "198", "199", "200"], ["201", "202", "203", "204"]):
        ent = (f"Outer_0.014_{hp}HP", code, "外圈故障", hp, 0.014)
        out[f12] = ent
        out[f48] = ent
        code += 1
    for f, hp in [("313", 0), ("310", 0), ("309", 1), ("311", 2), ("312", 3)]:
        out[f] = (f"Outer_0.014_{hp}HP", 48 + hp, "外圈故障", hp, 0.014)

    code = 52
    for pos_name, dia, files_12k, files_48k, files_fan in [
        ("Outer6_0.021", 0.021, ["234", "235", "236", "237"], ["238", "239", "240", "241"], ["315", "316", "317", "318"]),
        ("Outer3_0.021", 0.021, ["246", "247", "248", "249"], ["250", "251", "252", "253"], []),
        ("Outer12_0.021", 0.021, ["258", "259", "260", "261"], ["262", "263", "264", "265"], []),
    ]:
        for hp, f12, f48, ff in zip(range(4), files_12k, files_48k, (files_fan + [None] * 4)[:4]):
            ent = (f"{pos_name}_{hp}HP", code, "外圈故障", hp, dia)
            out[f12] = ent
            if f48:
                out[f48] = ent
            if ff:
                out[ff] = ent
            code += 1
    return out


FAULT_MAPPING = build_fault_mapping()


def _format_size(size):
    """
    将故障尺寸格式化为与需求一致的字符串（如 0.007）。
    """
    text = f"{float(size):.3f}"
    # 兼容 0.000 这种值
    if text == "0.000":
        return "0"
    return text


def to_display_label(fault_type, load_hp, fault_size_inch):
    """
    转成中文可读标签：
    - 正常：正常_1HP
    - 故障：滚动体故障_0.007英寸_1HP
    """
    hp = int(load_hp)
    if fault_type == "正常":
        return f"正常_{hp}HP"
    size_text = _format_size(fault_size_inch)
    return f"{fault_type}_{size_text}英寸_{hp}HP"


def parse_fault_by_code(label_code):
    """
    通过训练时原始 code 解析故障信息。
    """
    for _prefix, (label_name, code, fault_type, load, fault_size) in FAULT_MAPPING.items():
        if code == label_code:
            display_name = to_display_label(fault_type, load, fault_size)
            return {
                "label_name": display_name,          # 对外展示（中文）
                "raw_label_name": label_name,        # 训练原始标签（英文）
                "fault_type": fault_type,
                "load_hp": load,
                "fault_size_inch": fault_size,
            }
    display_name = "未知故障"
    return {
        "label_name": display_name,
        "raw_label_name": f"Unknown_{label_code}",
        "fault_type": "未知故障",
        "load_hp": 0,
        "fault_size_inch": 0.0,
    }


def load_label_index_map(model, script_dir, np_module):
    """
    读取 label_index_to_original_code.npy。
    若不存在，则回退为 [0..num_classes-1]。
    """
    map_path = os.path.join(script_dir, "label_index_to_original_code.npy")
    if os.path.isfile(map_path):
        try:
            return np_module.load(map_path, allow_pickle=True).tolist()
        except Exception:
            pass
    num_classes = int(model.output_shape[-1])
    return list(range(num_classes))
