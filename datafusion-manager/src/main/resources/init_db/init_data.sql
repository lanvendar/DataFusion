-- datafusion-manager/src/main/resources/init_db/init_data.sql
-- 初始化内置数据

-- 系统内置变量
INSERT INTO system_variable_info
(id, code, "name", "type", value_type, value, remark, creator, updater, create_time, update_time)
VALUES
('cb4b052a-9c32-3741-98bf-bfd8ed8ca606'::uuid, '_now_time_', '当前时间戳', 'SYSTEM', 'LONG', NULL,
 '当前系统时间，格式为毫秒时间戳，例如 1772012833904。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a2086d1b-dcb6-3d20-90be-776295010d07'::uuid, '_now_date_', '当前日期', 'SYSTEM', 'STRING', NULL,
 '当前系统日期，格式为 yyyyMMddHHmmss，例如 20260620100353。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ea21cd02-05be-3f43-985e-38c45160a661'::uuid, '_schedule_time_', '调度时间戳', 'SYSTEM', 'LONG', NULL,
 '原始调度时间，格式为毫秒时间戳，例如 1772012833904。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('7eb4961e-1e61-3dee-a9e8-b4b81744aa08'::uuid, '_biz_align_', '业务时间对齐枚举', 'SYSTEM', 'STRING', 'original',
 '业务时间对齐方式，格式为小写下划线编码。基础枚举值：original, minute_5, minute_10, minute_15, minute_30, hour_1, day_1, month_1, month_3, year_1, month_end, year_end；后缀规则：_next 表示取下一周期边界，_add_8 表示增加 8 小时时区偏移，_next_add_8 表示取下一周期边界后增加 8 小时时区偏移。',
 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a253b9de-9697-3be6-9e69-8381df243d7c'::uuid, '_biz_time_', '业务时间戳', 'SYSTEM', 'LONG', NULL,
 '业务时间，格式为毫秒时间戳；由 _schedule_time_ 按 _biz_align_ 对齐后生成。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0f87d2c9-60ad-3312-a15f-2215c8f9663a'::uuid, '_biz_date_', '业务日期', 'SYSTEM', 'STRING', NULL,
 '业务日期，格式为 yyyyMMddHHmmss；由 _biz_time_ 格式化后生成。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1e5c9a2c-7c02-38ad-be3c-cc2f9344f66a'::uuid, '_event_align_', '事件时间对齐枚举', 'SYSTEM', 'STRING', 'original',
 '事件时间对齐方式，格式为小写下划线编码。基础枚举值：original, minute_5, minute_10, minute_15, minute_30, hour_1, day_1, month_1, month_3, year_1, month_end, year_end；后缀规则：_next 表示取下一周期边界，_add_8 表示增加 8 小时时区偏移，_next_add_8 表示取下一周期边界后增加 8 小时时区偏移。',
 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('66fe0ceb-d840-30f7-93b5-0ccc11c819d6'::uuid, '_event_time_', '事件时间戳', 'SYSTEM', 'LONG', NULL,
 '事件匹配时间，格式为毫秒时间戳；由 _schedule_time_ 按 _event_align_ 对齐后生成。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('3ce27722-bae4-38cc-b847-c464530793c8'::uuid, '_event_date_', '事件日期', 'SYSTEM', 'STRING', NULL,
 '事件日期，格式为 yyyyMMddHHmmss；由 _event_time_ 格式化后生成。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
code = EXCLUDED.code,
"name" = EXCLUDED."name",
"type" = EXCLUDED."type",
value_type = EXCLUDED.value_type,
value = EXCLUDED.value,
remark = EXCLUDED.remark,
updater = EXCLUDED.updater,
update_time = CURRENT_TIMESTAMP;

-- 系统插件配置模板
INSERT INTO system_plugin_config
(id, plugin_name, plugin_type, run_mode, description, plugin_param, is_template, is_del, creator, updater, create_time,
 update_time, tenant_id)
VALUES
('81deb2e9-2c69-33d0-917a-dded2e73ce6d'::uuid, 'DataX LOCAL 模板', 'DATAX', 'LOCAL',
 'DataX 本地执行配置模板',
 '{
   "javaBin": "java",
   "dataxHome": "/opt/datafusion-builtin/plugins/datax",
   "dataxJar": "/opt/datafusion-builtin/plugins/datax/lib/datax-bundle-0.0.1.jar",
   "jobFile": "",
   "logConfigFile": "/opt/datafusion-builtin/plugins/datax/conf/logback.xml",
   "logLevel": "INFO",
   "logMaxSize": "100MB",
   "logMaxIndex": 100,
   "mainClass": "com.alibaba.datax.core.Engine",
   "jvmOptions": [
     "-Dfile.encoding=UTF-8",
     "-Dsun.jnu.encoding=UTF-8",
     "--add-opens",
     "java.base/java.lang=ALL-UNNAMED"
   ],
   "jobId": -1,
   "jobMode": "standalone",
   "defaultTaskData": {
     "job": {
       "setting": {
         "speed": {
           "channel": 1
         },
         "errorLimit": {
           "record": 0
         }
       },
       "content": [
         {
           "reader": {},
           "writer": {}
         }
       ]
     }
   }
 }'::jsonb,
 true, 0, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid),
('e9f668d7-7d7c-30e3-9143-3c5ab6019eb1'::uuid, 'DataX K8S 模板', 'DATAX', 'K8S',
 'DataX Kubernetes 执行配置模板',
 '{
	   "logLevel": "INFO",
	   "logMaxSize": "100MB",
	   "logMaxIndex": 100,
	   "jobId": -1,
	   "jvmOptions": [
	     "-Dfile.encoding=UTF-8",
	     "-Dsun.jnu.encoding=UTF-8",
	     "--add-opens",
	     "java.base/java.lang=ALL-UNNAMED"
	   ],
	   "defaultTaskData": {
	     "job": {
	       "setting": {
	         "speed": {
	           "channel": 1
	         },
	         "errorLimit": {
	           "record": 0
	         }
	       },
	       "content": [
	         {
	           "reader": {},
	           "writer": {}
	         }
	       ]
	     }
	   },
	   "kubernetes": {
	     "namespace": "datafusion",
	     "image": "jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/datawarehouse:datax-runtime-v1.0.0-20260622",
	     "imagePullPolicy": "IfNotPresent",
	     "serviceAccountName": "",
	     "backoffLimit": 0,
	     "activeDeadlineSeconds": null,
	     "ttlSecondsAfterFinished": 86400,
	     "jobNamePrefix": "df-datax-",
	     "secretNamePrefix": "df-datax-job-",
	     "logStorageUri": "",
	     "collectLogsOnFinish": true,
         "deleteJobOnFinish": false,
		 "labels": {},
		 "annotations": {},
		 "env": {
		    "TZ": "Asia/Shanghai",
		    "LANG": "C.UTF-8",
		    "LC_ALL": "C.UTF-8"
		 },
		 "nodeSelector": {},
		 "resources": {}
	   }
    }'::jsonb,
 true, 0, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid),
('82a2e64f-47cb-3545-96f1-be547a1f5253'::uuid, 'Shell LOCAL 模板', 'SHELL', 'LOCAL',
 'Shell 本地执行配置模板',
 '{
   "command": "sh",
   "args": [
     "-c"
   ],
   "env": {},
   "pluginLogUri": ""
 }'::jsonb,
 true, 0, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid),
('6625db40-f8a9-3a80-8bc0-2f2137165e4d'::uuid, 'API LOCAL 模板', 'API', 'LOCAL',
 'API 抽数本地执行配置模板，通过 java 启动 datafusion-plugin-api',
 '{
   "launchMode": "JAR",
   "javaBin": "java",
   "apiJar": "/opt/datafusion/plugins/api/datafusion-plugin-api-1.0.0-executable.jar",
   "classpath": "",
   "mainClass": "com.datafusion.plugin.api.ApiExtractApplication",
   "jvmOptions": [
     "-Dfile.encoding=UTF-8",
     "-Dsun.jnu.encoding=UTF-8",
     "--add-opens",
     "java.base/java.lang=ALL-UNNAMED"
   ],
   "logHome": "logs",
   "logLevel": "INFO",
   "logMaxSize": "100MB",
   "logMaxIndex": 100,
   "logConfigFile": "/opt/datafusion/plugins/api/conf/logback.xml",
   "defaultTaskData": {},
   "env": {},
   "pluginLogUri": ""
 }'::jsonb,
 true, 0, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid),
('feef4832-711c-35ce-a0b4-59f2f5ecfcd5'::uuid, 'Flink K8S_OPERATOR 模板', 'FLINK', 'K8S_OPERATOR',
 'Flink Kubernetes Operator 执行配置模板，通过 FlinkDeployment 提交 Flink Application 作业',
 '{
   "flinkAppDir": "/opt/datafusion/plugins/flink/datafusion-plugin-kafka-json",
   "launchMode": "JAR",
   "flinkAppJar": "datafusion-plugin-kafka-json-1.0.0-executable.jar",
   "classpath": "",
   "mainClass": "com.datafusion.plugin.kafka.json.KafkaJsonPaimonApplication",
   "flinkVersion": "2.2.0",
   "libDir": "lib",
   "flinkCheckpointRootDir": "s3a://data-lake-warehouse/flink",
   "flinkConfig": {
     "state.backend": "rocksdb",
     "parallelism.default": "2",
     "execution.checkpointing.interval": "60s",
     "execution.checkpointing.mode": "AT_LEAST_ONCE",
     "fs.s3a.endpoint": "172.26.185.200",
     "fs.s3a.path.style.access": "true",
     "fs.s3a.connection.ssl.enabled": "false",
     "fs.s3a.aws.credentials.provider": "com.amazonaws.auth.EnvironmentVariableCredentialsProvider"
   },
   "kubernetes": {
     "namespace": "datafusion",
     "image": "flink:2.2.0-scala_2.12-java17",
     "sharedPvcName": "datafusion-shared-data",
     "serviceAccountName": "flink",
     "env": {
       "HADOOP_CONF_DIR": "/opt/flink/conf"
     },
     "envFrom": [
       {
         "secretRef": {
           "name": "flink-objectstore"
         }
       }
     ],
     "jobManager": {
       "replicas": 1,
       "resource": {
         "memory": "2048m",
         "cpu": 1.0
       }
     },
     "taskManager": {
       "replicas": 1,
       "resource": {
         "memory": "4096m",
         "cpu": 2.0
       }
     },
     "nodeSelector": {
       "kubernetes.io/arch": "amd64"
     }
   },
   "defaultTaskData": {
     "job": {},
     "source": {},
     "flinkConfig": {},
     "sink": {},
     "bizRef": ""
   }
 }'::jsonb,
 true, 0, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (id) DO UPDATE SET
plugin_name = EXCLUDED.plugin_name,
plugin_type = EXCLUDED.plugin_type,
run_mode = EXCLUDED.run_mode,
description = EXCLUDED.description,
plugin_param = EXCLUDED.plugin_param,
is_template = EXCLUDED.is_template,
is_del = EXCLUDED.is_del,
updater = EXCLUDED.updater,
update_time = CURRENT_TIMESTAMP,
tenant_id = EXCLUDED.tenant_id;

-- 系统任务类型默认插件绑定
INSERT INTO system_task_type_config
(id, task_type, default_plugin_id, plugin_type, creator, updater, create_time, update_time, tenant_id)
VALUES
('d2f6659e-562a-350e-b926-d7812852e23d'::uuid, 'DATAX',
 'e9f668d7-7d7c-30e3-9143-3c5ab6019eb1'::uuid, 'DATAX',
 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid),
('28d568b3-892d-3e36-b283-3542693a1062'::uuid, 'SHELL',
 '82a2e64f-47cb-3545-96f1-be547a1f5253'::uuid, 'SHELL',
 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid),
('db974238-714c-38de-a34a-7ce1d083a14f'::uuid, 'API',
 '6625db40-f8a9-3a80-8bc0-2f2137165e4d'::uuid, 'API',
 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid),
('50be76ec-f595-3745-a665-758fbce78a83'::uuid, 'FLINK',
 'feef4832-711c-35ce-a0b4-59f2f5ecfcd5'::uuid, 'FLINK',
 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 '00000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (id) DO UPDATE SET
task_type = EXCLUDED.task_type,
default_plugin_id = EXCLUDED.default_plugin_id,
plugin_type = EXCLUDED.plugin_type,
updater = EXCLUDED.updater,
update_time = CURRENT_TIMESTAMP,
tenant_id = EXCLUDED.tenant_id;
