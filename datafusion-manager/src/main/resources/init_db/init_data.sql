-- datafusion-manager/src/main/resources/init_db/init_data.sql
-- 初始化内置数据

-- 系统内置变量
INSERT INTO system_variable_info
(id, code, "name", "type", value_type, value, remark, creator, updater, create_time, update_time)
VALUES
('cb4b052a-9c32-3741-98bf-bfd8ed8ca606'::uuid, '_now_time_', 'now_time', 'SYSTEM', 'LONG', NULL,
 '当前系统时间，格式为毫秒时间戳，例如 1772012833904。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a2086d1b-dcb6-3d20-90be-776295010d07'::uuid, '_now_date_', 'now_date', 'SYSTEM', 'STRING', NULL,
 '当前系统日期，格式为 yyyyMMddHHmmss，例如 20260620100353。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ea21cd02-05be-3f43-985e-38c45160a661'::uuid, '_schedule_time_', 'schedule_time', 'SYSTEM', 'LONG', NULL,
 '原始调度时间，格式为毫秒时间戳，例如 1772012833904。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('7eb4961e-1e61-3dee-a9e8-b4b81744aa08'::uuid, '_biz_align_', 'biz_align', 'SYSTEM', 'STRING', 'original',
 '业务时间对齐方式，格式为小写下划线编码。基础枚举值：original, minute_5, minute_10, minute_15, minute_30, hour_1, day_1, month_1, month_3, year_1, month_end, year_end；后缀规则：_next 表示取下一周期边界，_add_8 表示增加 8 小时时区偏移，_next_add_8 表示取下一周期边界后增加 8 小时时区偏移。',
 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a253b9de-9697-3be6-9e69-8381df243d7c'::uuid, '_biz_time_', 'biz_time', 'SYSTEM', 'LONG', NULL,
 '业务时间，格式为毫秒时间戳；由 schedule_time 按 biz_align 对齐后生成。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0f87d2c9-60ad-3312-a15f-2215c8f9663a'::uuid, '_biz_date_', 'biz_date', 'SYSTEM', 'STRING', NULL,
 '业务日期，格式为 yyyyMMddHHmmss；由 biz_time 格式化后生成。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1e5c9a2c-7c02-38ad-be3c-cc2f9344f66a'::uuid, '_event_align_', 'event_align', 'SYSTEM', 'STRING', 'original',
 '事件时间对齐方式，格式为小写下划线编码。基础枚举值：original, minute_5, minute_10, minute_15, minute_30, hour_1, day_1, month_1, month_3, year_1, month_end, year_end；后缀规则：_next 表示取下一周期边界，_add_8 表示增加 8 小时时区偏移，_next_add_8 表示取下一周期边界后增加 8 小时时区偏移。',
 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('66fe0ceb-d840-30f7-93b5-0ccc11c819d6'::uuid, '_event_time_', 'event_time', 'SYSTEM', 'LONG', NULL,
 '事件匹配时间，格式为毫秒时间戳；由 schedule_time 按 event_align 对齐后生成。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('3ce27722-bae4-38cc-b847-c464530793c8'::uuid, '_event_date_', 'event_date', 'SYSTEM', 'STRING', NULL,
 '事件日期，格式为 yyyyMMddHHmmss；由 event_time 格式化后生成。', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
code = EXCLUDED.code,
"name" = EXCLUDED."name",
"type" = EXCLUDED."type",
value_type = EXCLUDED.value_type,
value = EXCLUDED.value,
remark = EXCLUDED.remark,
updater = EXCLUDED.updater,
update_time = CURRENT_TIMESTAMP;
