# ChatGPT Context Pack

这个目录用于存放可上传到 ChatGPT 项目数据源的 SmartLab 代码上下文包。

建议上传文件：

- `smartlab_context.md`：全量源代码上下文，适合一次性导入。
- `smartlab_backend_management.md`
- `smartlab_backend_engine.md`
- `smartlab_backend_adapter.md`
- `smartlab_backend_global.md`
- `smartlab_frontend.md`
- `smartlab_sql_and_config.md`
- `smartlab_code_map.md`
- `architecture_rules.md`
- `device_design.md`
- `github_address.txt`

刷新方式：

```powershell
cd D:\SmartLab2.0
python chatgpt_context_pack\generate_context_pack.py
```

说明：

- 脚本只收集源代码、配置、SQL、Markdown、前端页面等文本文件。
- 会排除 `.git`、`node_modules`、`target`、`dist`、`build`、本目录等内容。
- 单个文件过长时会自动截断，避免上下文包过大。
- 数据库结构、JSON Schema、Adapter 配置文件格式是项目定稿资产，脚本只读取，不修改。
