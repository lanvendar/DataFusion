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
  ods_shys_gb_account_td.json \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/target/datax-logs
```

脚本参数：

```text
1. resources 根目录
2. job JSON 文件名
3. 日志根目录
```

日志路径：

```text
<日志根目录>/<运行日期>/<job JSON 文件名>.log.1
<日志根目录>/<运行日期>/<job JSON 文件名>.log.2
<日志根目录>/<运行日期>/<job JSON 文件名>.log.3
```

日志控制：

```bash
DATAX_LOG_LEVEL=INFO DATAX_LOG_CHUNK_BYTES=104857600 \
/Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources \
  ods_shys_gb_account_td.json \
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/target/datax-logs
```

```text
DATAX_LOG_LEVEL 默认 INFO，可改为 WARN 或 ERROR。
DATAX_LOG_CHUNK_BYTES 默认 104857600，单个日志分片超过该大小后切到下一个序号文件。
DATAX_LOG_CHUNK_BYTES 设置为 0 表示不分片。
同一天重复执行同一个 job 时，脚本会先清理旧 .log 和旧分片，再重新生成 .1、.2、.3 等日志文件。
```

批量运行全部 job：

```bash
for job_file in /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/job/*.json; do
  /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/run-datax-job.sh \
    /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources \
    "$(basename "${job_file}")" \
    /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/target/datax-logs
done
```

## Job 规则

本批 job 来自：

```text
/Users/lanvendar/Downloads/石化预算/石化预算管理报表-gb-ddl.sql
```
    
命名规则：

```text
MySQL 源表: shys_xxx_gb
Paimon 目标表: ods_shys_gb_xxx
Job 文件: ods_shys_gb_xxx.json
```

字段规则：

```text
level -> tree_level
baseCount -> base_count
dataLength -> data_length
驼峰字段统一转下划线字段
```

查看 job 清单：

```bash
find /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/job \
  -maxdepth 1 \
  -type f \
  -name '*.json' \
  -exec basename {} \; | sort
```

## 注意事项

- 当前脚本已内置 Java 17 需要的 `--add-opens java.base/java.lang=ALL-UNNAMED`。
- DataX 控制台日志级别默认 `INFO`，可看到导入统计；脚本日志默认按 100 MiB 分片滚动。
- job 文件放在 `resources/job`，不要放进 `resources/datax`，避免同步 DataX bundle 时被覆盖。
