# DataX 插件使用手册

## 路径

```text
DataX 源码: /Users/lanvendar/Projects/DataX
DataX 编译产物: /Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax
插件 resources: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources
插件 DataX: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/datax
插件 job: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/job
```

## 构建和同步

DataX 执行报错需要改源码时，先在 `/Users/lanvendar/Projects/DataX` 修改并编译：

```bash
cd /Users/lanvendar/Projects/DataX
mvn -pl packaging/datax-bundle -am -DskipTests package
```

编译成功后同步到插件 resources：

```bash
rsync -a --delete \
  /Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax/ \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/datax/
```

## 运行 Job

推荐使用封装脚本：

```bash
/Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources \
  shys/ods_shys_gb_account_td.json \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/target/datax-logs
```

脚本参数：

```text
1. resources 根目录
2. job JSON 文件名、job 下的相对路径，或 job JSON 绝对路径
3. 日志根目录
```

第二个参数支持三种写法：

```text
ods_shys_gb_account_td.json
shys/ods_shys_gb_account_td.json
/Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/job/shys/ods_shys_gb_account_td.json
```

只传文件名时，脚本会在 `resources/job` 下递归查找唯一匹配；如果同名文件不唯一，会直接报错并列出匹配路径。

## 日志

日志路径：

```text
<日志根目录>/<运行日期>/<job JSON 文件名>.log
<日志根目录>/<运行日期>/<job JSON 文件名>.log.1
<日志根目录>/<运行日期>/<job JSON 文件名>.log.2
```

日志由 `datax/conf/logback.xml` 控制大小和切分，不在 shell 中手动分片。

```bash
DATAX_LOG_LEVEL=INFO \
DATAX_LOG_MAX_SIZE=100MB \
DATAX_LOG_MAX_INDEX=100 \
/Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources \
  shys/ods_shys_gb_account_td.json \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/target/datax-logs
```

```text
DATAX_LOG_LEVEL 默认 INFO，可改为 WARN 或 ERROR。
DATAX_LOG_MAX_SIZE 默认 100MB。
DATAX_LOG_MAX_INDEX 默认 100。
```

## 批量运行

运行 `shys` 目录下全部 job：

```bash
for job_file in /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/job/shys/*.json; do
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
    /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources \
    "shys/$(basename "${job_file}")" \
    /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/target/datax-logs
done
```

运行指定前缀的 job，例如只跑 JC：

```bash
for job_file in /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/job/shys/ods_shys_jc_*.json; do
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
    /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources \
    "shys/$(basename "${job_file}")" \
    /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/target/datax-logs
done
```

不要使用 `resources/job/*.json` 批量跑，因为当前 job 已按业务目录放在 `job/shys`、`job/shcw` 等子目录下。

## Job 规则

job 文件放在 `resources/job` 下，按业务系统分目录，例如：

```text
resources/job/shys
resources/job/shcw
```

命名规则：

```text
GB: shys_xxx_gb -> ods_shys_gb_xxx
YS: shys_xxx_ys -> ods_shys_ys_xxx
JC: shjc_ods_xxx_jc -> ods_shys_jc_xxx
SHCW: 按目标系统约定生成，例如 ods_shcw_scux_shsh_budget_subject
```

字段规则：

```text
level -> tree_level
baseCount -> base_count
dataLength -> data_length
驼峰字段统一转下划线字段
```

JC 全量自增表规则：

```text
_id BIGINT NOT NULL AUTO_INCREMENT 且非 day_pt 分区表:
  loadMode = OVERWRITE_TABLE

带 day_pt 的增量表:
  loadMode = UPSERT
  partitionKey = day_pt
```

查看 job 清单：

```bash
find /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/job \
  -type f \
  -name '*.json' \
  -exec basename {} \; | sort
```

## 注意事项

- 当前脚本已内置 Java 17 需要的 `--add-opens java.base/java.lang=ALL-UNNAMED`。
- DataX 日志级别默认 `INFO`，可看到导入统计。
- job 文件不要放进 `resources/datax`，避免同步 DataX bundle 时被覆盖。
