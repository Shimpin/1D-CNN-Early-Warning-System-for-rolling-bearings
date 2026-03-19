# 基于1D-CNN的滚动轴承故障智能预警系统 - 开发说明

## 一、环境与依赖

- JDK 17+（项目当前为 Java 25，可改为 17 以兼容常见环境）
- Maven 3.6+
- MySQL 5.7+（库名：`early_warning`，账号密码见 `application.properties`）
- Redis（可选，用于缓存 Token 与统计图表；未启动时仅 JWT 校验生效，退出后 Token 仍有效至过期）
- Python 3.8+（用于 1D-CNN 预测脚本，需安装 tensorflow、scipy、scikit-learn、numpy）

## 二、数据库初始化

在 MySQL 中创建数据库并执行建表脚本：

```sql
CREATE DATABASE IF NOT EXISTS early_warning DEFAULT CHARACTER SET utf8mb4;
USE early_warning;
-- 执行 src/main/resources/schema-mysql.sql 中的语句
```

若已有 `prediction_record` 表且无 `user_id` 列，请执行：

```sql
ALTER TABLE prediction_record ADD COLUMN user_id BIGINT NULL;
```

## 三、配置说明

- **application.properties**
  - `spring.datasource.*`：MySQL 连接（库名、用户名、密码）
  - `app.upload-dir`：上传文件目录
  - `app.bearing-model-dir`：Python 脚本与模型目录（默认项目下 `python`）
  - `app.bearing-python-executable`：Python 可执行命令（如 `python` 或 `python3`）
  - `app.jwt.secret`、`app.jwt.expire-hours`：JWT 密钥与有效期（2 小时）
  - `spring.data.redis.*`：Redis 地址（默认 192.168.137.107:6379），无密码可留空
  - **AI 助理（LM Studio 本地）**：`app.llm.api-url`（默认 http://127.0.0.1:1234）、`app.llm.api-key`（LM Studio 设置中生成的 Token）、`app.llm.model`（与 LM Studio 中已加载模型一致）；需先执行 `lms server start --port 1234` 或从 LM Studio 界面启动 Local Server

## 四、运行方式

1. 启动 Redis（可选）。
2. 启动 MySQL，并完成上述数据库与表结构初始化。
3. 在项目根目录执行：
   ```bash
   mvn spring-boot:run
   ```
4. 浏览器访问：`http://localhost:8080`，未登录会跳转到 `/login`。
5. 先注册用户，再登录；登录后可使用首页、故障预测、预测历史、工具（检测日志、AI 问答）等功能。

## 五、功能概览

| 模块       | 说明 |
|------------|------|
| 用户       | 注册、登录（JWT）、退出；未登录无法访问核心页面与 API。 |
| 首页       | Echarts：故障类型占比（饼图）、近期预测次数（折线图）、故障预警数量（柱状图）；数据来自 Redis 或后端接口。 |
| 故障预测   | 上传 txt/csv/mat 振动数据（≤10MB），调用 1D-CNN 预测；结果弹窗+页面展示，并写入预测历史。 |
| 预测历史   | 表格展示：预测时间、数据文件名称、预测结果、预警状态。 |
| 工具       | 检测日志：最近 100 条（操作时间、操作类型、操作结果）；AI 问答：固定问答库匹配。 |

## 六、Python 预测脚本

- **predict_single.py**：对 `.mat`（CWRU 格式）文件预测。
- **predict_txt.py**：对 `.txt` / `.csv` 一维振动数据预测（每行或逗号分隔的数值，总长度≥2048）。
- **inference_mapping.py**：推理标签映射工具；与 `python/training/main.py` 的故障编码规则一致，支持将模型输出索引映射回原始标签编码。

模型与标准化文件需放在 `python` 目录：`final_cwru_model.h5`、`scaler_mean.npy`、`scaler_scale.npy`。

> 若存在 `label_index_to_original_code.npy`，也请放在 `python` 目录，用于严格对齐训练阶段的类别重映射关系。

## 七、变更记录（开发操作记录）

### 2026-03-18

1. **工具模块改造**
   - 左侧“工具”改为二级菜单：`日志`、`AI助理`。
   - 拆分页面：`tools-logs.html` 与 `tools-ai.html`。
   - `ToolsController` 调整为：`/tools -> /tools/logs`，新增 `/tools/assistant`。

2. **日志查询增强**
   - `GET /api/tools/logs` 支持按时间范围查询。
   - 支持分页，默认每页 `20` 条。
   - 日志页支持开始时间/结束时间筛选、搜索、重置、分页跳转。

3. **统计缓存一致性修复**
   - 在预测成功并写入记录后，主动清理当前用户首页统计缓存：
     - API 预测：`PredictApiController`
     - 页面预测：`PredictController`
   - 新增 `StatsService.evictChartsCache(userId)`。

4. **前端样式优化**
   - 新增统一样式文件：`src/main/resources/static/css/app.css`。
   - 优化头部、侧栏、卡片、表格、登录/注册页视觉风格。

5. **推理映射对齐训练代码**
   - 读取 `python/training/main.py` 后，将推理逻辑改为使用训练同源映射。
   - 新增 `python/inference_mapping.py`，复用训练故障编码规则。
   - `predict_single.py` / `predict_txt.py` 使用 `label_index_to_original_code.npy`（若存在）进行“输出索引 -> 原始故障编码”反查。

6. **模型输出标签中文化**
   - 输出 `fault_name` 统一改为中文可读形式（示例：`滚动体故障_0.007英寸_1HP`）。
   - 保留 `raw_label_name` 字段用于调试（训练内部标签，如 `Outer12_0.007_1HP`）。
