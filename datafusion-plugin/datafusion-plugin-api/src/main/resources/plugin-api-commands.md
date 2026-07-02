# API 抽数任务运行命令

## 本地运行 API 任务

将任务 JSON 放在当前工作目录的 `api-job.json` 后执行：

```bash
java -jar datafusion-plugin/datafusion-plugin-api/target/datafusion-plugin-api-1.0.0-executable.jar \
  -job api-job.json
```

调度系统负责触发时机，程序启动后读取 `-job` 指定的任务文件并执行一次。
