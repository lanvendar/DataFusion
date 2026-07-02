# API 抽数任务运行命令

## 运行 ODS 产品信息 Paimon 任务

在仓库根目录执行：

```bash
java -jar datafusion-plugin/datafusion-plugin-api/target/datafusion-plugin-api-1.0.0-executable.jar \
  --config datafusion-plugin/datafusion-plugin-api/src/main/resources/plugins/api/jobs/ods_hqpl_product_info-paimon-job.json
```

该任务配置为 `trigger.mode=CRON`，表达式为 `0 0 1 * * ?`，进程会常驻并在 `Asia/Shanghai` 时区每天凌晨 1 点执行一次。

## 运行 ODS 产品基准价 Paimon 任务

在仓库根目录执行：

```bash
java -jar datafusion-plugin/datafusion-plugin-api/target/datafusion-plugin-api-1.0.0-executable.jar \
  --config datafusion-plugin/datafusion-plugin-api/src/main/resources/plugins/api/jobs/ods_hqpl_product_benchmark_price-paimon-job.json
```

该任务配置为 `trigger.mode=CRON`，表达式为 `0 0 1 * * ?`，进程会常驻并在 `Asia/Shanghai` 时区每天凌晨 1 点执行一次。

## 使用 JSON 字符串运行

```bash
java -jar datafusion-plugin/datafusion-plugin-api/target/datafusion-plugin-api-1.0.0-executable.jar \
  --config-json '{"job":{},"trigger":{},"steps":[],"sink":{}}'
```
