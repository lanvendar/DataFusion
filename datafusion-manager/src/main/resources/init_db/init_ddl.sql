-- datafusion-manager\src\main\resources\init\sql\init_ddl.sql
-- 初始化ddl建表语句

-- 元数据模块
-- DROP TABLE metadata_datasource_info;
CREATE TABLE metadata_datasource_info (
id uuid NOT NULL, -- 主键
name varchar(200) NULL, -- 数据源名称
host varchar(200) NOT NULL, -- 数据源主机名
port int4 NOT NULL, -- 数据源端口
username varchar(64) NULL, -- 数据源用户
"password" varchar(255) NULL, -- 数据源密码
database_type varchar(10) NULL, -- 数据库类型
schema_name varchar(32) NULL, -- 数据库空间名称,等价于catlog_name
database_name varchar(32) NULL, -- 数据库名称
database_encode varchar(32) NULL, -- 数据库编码
jdbc_url varchar(256) NULL, -- jdbc连接串
connect_type varchar(10) NULL, -- 连接类型
driver_class varchar(128) NULL, -- 数据库驱动
table_count int2 NULL, -- 表总数量
sync_count int2 DEFAULT 0 NULL, -- 同步表数量
extend_param json NULL, -- 拓展参数
metadata_info json NULL, -- 元数据信息
tenant_code varchar(8) NULL, -- 租户编码
creator varchar(64) NOT NULL, -- 创建用户
create_time timestamp(6) NOT NULL, -- 创建时间
updater varchar(64) NOT NULL, -- 更新用户
update_time timestamp(6) NOT NULL, -- 更新时间
CONSTRAINT meta_schema_info_pk PRIMARY KEY (id)
);
COMMENT ON TABLE metadata_datasource_info IS '数据源表';

-- Column comments

COMMENT ON COLUMN metadata_datasource_info.id IS '主键';
COMMENT ON COLUMN metadata_datasource_info.name IS '数据源名称';
COMMENT ON COLUMN metadata_datasource_info.host IS '数据源主机名';
COMMENT ON COLUMN metadata_datasource_info.port IS '数据源端口';
COMMENT ON COLUMN metadata_datasource_info.username IS '数据源用户';
COMMENT ON COLUMN metadata_datasource_info."password" IS '数据源密码';
COMMENT ON COLUMN metadata_datasource_info.database_type IS '数据库类型';
COMMENT ON COLUMN metadata_datasource_info.schema_name IS '数据库空间名称,等价于catlog_name';
COMMENT ON COLUMN metadata_datasource_info.database_name IS '数据库名称';
COMMENT ON COLUMN metadata_datasource_info.database_encode IS '数据库编码';
COMMENT ON COLUMN metadata_datasource_info.jdbc_url IS 'jdbc连接串';
COMMENT ON COLUMN metadata_datasource_info.connect_type IS '连接类型';
COMMENT ON COLUMN metadata_datasource_info.driver_class IS '数据库驱动';
COMMENT ON COLUMN metadata_datasource_info.table_count IS '表总数量';
COMMENT ON COLUMN metadata_datasource_info.sync_count IS '同步表数量';
COMMENT ON COLUMN metadata_datasource_info.extend_param IS '拓展参数';
COMMENT ON COLUMN metadata_datasource_info.metadata_info IS '元数据信息';
COMMENT ON COLUMN metadata_datasource_info.tenant_code IS '租户编码';
COMMENT ON COLUMN metadata_datasource_info.creator IS '创建用户';
COMMENT ON COLUMN metadata_datasource_info.create_time IS '创建时间';
COMMENT ON COLUMN metadata_datasource_info.updater IS '更新用户';
COMMENT ON COLUMN metadata_datasource_info.update_time IS '更新时间';

-- DROP TABLE metadata_table_info;

CREATE TABLE metadata_table_info (
id uuid NOT NULL, -- 主键
datasource_id uuid NOT NULL, -- 数据库连接信息
table_name varchar(128) NOT NULL, -- 表名称
table_desc varchar(128) NULL, -- 表注释
table_properties json NULL, -- 表属性
is_modify bool NULL, -- 是否可修改:true是,false否
is_view bool NULL, -- 是否视图:true是,false否
view_def text NULL, -- 视图定义
catalog_name varchar(64) NULL, -- 表所属目录
is_equal bool DEFAULT true NULL, -- 表结构是否相同
check_time timestamp(6) NULL, -- 表结构检查时间
creator varchar(64) NOT NULL, -- 创建人
create_time timestamp(6) NOT NULL, -- 创建时间
updater varchar(64) NOT NULL, -- 修改人
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT metadata_table_info_pk PRIMARY KEY (id)
);
COMMENT ON TABLE metadata_table_info IS '表结构信息表';

-- Column comments

COMMENT ON COLUMN metadata_table_info.id IS '主键';
COMMENT ON COLUMN metadata_table_info.datasource_id IS '数据库连接信息';
COMMENT ON COLUMN metadata_table_info.table_name IS '表名称';
COMMENT ON COLUMN metadata_table_info.table_desc IS '表注释';
COMMENT ON COLUMN metadata_table_info.table_properties IS '表属性';
COMMENT ON COLUMN metadata_table_info.is_modify IS '是否可修改:true是,false否';
COMMENT ON COLUMN metadata_table_info.is_view IS '是否视图:true是,false否';
COMMENT ON COLUMN metadata_table_info.view_def IS '视图定义';
COMMENT ON COLUMN metadata_table_info.catalog_name IS '表所属目录';
COMMENT ON COLUMN metadata_table_info.is_equal IS '表结构是否相同';
COMMENT ON COLUMN metadata_table_info.check_time IS '表结构检查时间';
COMMENT ON COLUMN metadata_table_info.creator IS '创建人';
COMMENT ON COLUMN metadata_table_info.create_time IS '创建时间';
COMMENT ON COLUMN metadata_table_info.updater IS '修改人';
COMMENT ON COLUMN metadata_table_info.update_time IS '修改时间';


-- DROP TABLE metadata_table_info_his;

CREATE TABLE metadata_table_info_his (
id uuid NOT NULL, -- 主键
"version" varchar(8) NOT NULL, -- 版本号
datasource_id uuid NOT NULL, -- 数据库连接信息
table_name varchar(128) NOT NULL, -- 表名称
table_desc varchar(128) NULL, -- 表注释
table_properties json NULL, -- 表属性
is_modify bool NULL, -- 是否可修改:true是,false否
is_view bool NULL, -- 是否视图:true是,false否
view_def text NULL, -- 视图定义
catalog_name varchar(64) NULL, -- 表所属目录
is_equal bool DEFAULT true NULL, -- 表结构是否相同
check_time timestamp(6) NULL, -- 表结构检查时间
creator varchar(64) NOT NULL, -- 创建人
create_time timestamp(6) NOT NULL, -- 创建时间
updater varchar(64) NOT NULL, -- 修改人
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT metadata_table_info_his_pk PRIMARY KEY (id, version)
);
COMMENT ON TABLE metadata_table_info_his IS '表结构信息历史表';

-- Column comments

COMMENT ON COLUMN metadata_table_info_his.id IS '主键';
COMMENT ON COLUMN metadata_table_info_his."version" IS '版本号';
COMMENT ON COLUMN metadata_table_info_his.datasource_id IS '数据库连接信息';
COMMENT ON COLUMN metadata_table_info_his.table_name IS '表名称';
COMMENT ON COLUMN metadata_table_info_his.table_desc IS '表注释';
COMMENT ON COLUMN metadata_table_info_his.table_properties IS '表属性';
COMMENT ON COLUMN metadata_table_info_his.is_modify IS '是否可修改:true是,false否';
COMMENT ON COLUMN metadata_table_info_his.is_view IS '是否视图:true是,false否';
COMMENT ON COLUMN metadata_table_info_his.view_def IS '视图定义';
COMMENT ON COLUMN metadata_table_info_his.catalog_name IS '表所属目录';
COMMENT ON COLUMN metadata_table_info_his.is_equal IS '表结构是否相同';
COMMENT ON COLUMN metadata_table_info_his.check_time IS '表结构检查时间';
COMMENT ON COLUMN metadata_table_info_his.creator IS '创建人';
COMMENT ON COLUMN metadata_table_info_his.create_time IS '创建时间';
COMMENT ON COLUMN metadata_table_info_his.updater IS '修改人';
COMMENT ON COLUMN metadata_table_info_his.update_time IS '修改时间';

-- DROP TABLE metadata_column_info;

CREATE TABLE metadata_column_info (
id uuid NOT NULL, -- 主键
table_id uuid NULL, -- 表ID
table_name varchar(128) NULL, -- 表名称
column_serial int4 NULL, -- 字段序号
column_name varchar(128) NULL, -- 字段名称
column_desc varchar(128) NULL, -- 字段注释
column_type varchar(64) NULL, -- 字段类型
column_length int4 NULL, -- 字段长度
column_precision int4 NULL, -- 字段精度
is_primary bool NULL, -- 是否主键
is_nullable bool NULL, -- 是否非空
default_value varchar(128) NULL, -- 默认值
java_type varchar(64) NULL, -- java类型
view_type varchar(30) NULL, -- 查询类型
creator varchar(64) NOT NULL, -- 创建人
create_time timestamp(6) NOT NULL, -- 创建时间
updater varchar(64) NOT NULL, -- 修改人
update_time timestamp(6) NOT NULL, -- 修改时间
"scale" int4 NULL,
CONSTRAINT meta_table_column_info_pk PRIMARY KEY (id)
);
COMMENT ON TABLE metadata_column_info IS '表字段结构信息表';

-- Column comments

COMMENT ON COLUMN metadata_column_info.id IS '主键';
COMMENT ON COLUMN metadata_column_info.table_id IS '表ID';
COMMENT ON COLUMN metadata_column_info.table_name IS '表名称';
COMMENT ON COLUMN metadata_column_info.column_serial IS '字段序号';
COMMENT ON COLUMN metadata_column_info.column_name IS '字段名称';
COMMENT ON COLUMN metadata_column_info.column_desc IS '字段注释';
COMMENT ON COLUMN metadata_column_info.column_type IS '字段类型';
COMMENT ON COLUMN metadata_column_info.column_length IS '字段长度';
COMMENT ON COLUMN metadata_column_info.column_precision IS '字段精度';
COMMENT ON COLUMN metadata_column_info.is_primary IS '是否主键';
COMMENT ON COLUMN metadata_column_info.is_nullable IS '是否非空';
COMMENT ON COLUMN metadata_column_info.default_value IS '默认值';
COMMENT ON COLUMN metadata_column_info.java_type IS 'java类型';
COMMENT ON COLUMN metadata_column_info.view_type IS '查询类型';
COMMENT ON COLUMN metadata_column_info.creator IS '创建人';
COMMENT ON COLUMN metadata_column_info.create_time IS '创建时间';
COMMENT ON COLUMN metadata_column_info.updater IS '修改人';
COMMENT ON COLUMN metadata_column_info.update_time IS '修改时间';

-- DROP TABLE metadata_column_info_his;

CREATE TABLE metadata_column_info_his (
id uuid NOT NULL, -- 主键
"version" varchar(8) NOT NULL, -- 版本号
table_id uuid NULL, -- 表ID
table_name varchar(128) NULL, -- 表名称
column_serial int4 NULL, -- 字段序号
column_name varchar(128) NULL, -- 字段名称
column_desc varchar(128) NULL, -- 字段注释
column_type varchar(64) NULL, -- 字段类型
column_length int4 NULL, -- 字段长度
column_precision int4 NULL, -- 字段精度
is_primary bool NULL, -- 是否主键
is_nullable bool NULL, -- 是否非空
default_value varchar(128) NULL, -- 默认值
java_type varchar(64) NULL, -- java类型
view_type varchar(30) NULL, -- 查询类型
creator varchar(64) NOT NULL, -- 创建人
create_time timestamp(6) NOT NULL, -- 创建时间
updater varchar(64) NOT NULL, -- 修改人
update_time timestamp(6) NOT NULL, -- 修改时间
"scale" int4 NULL,
CONSTRAINT metadata_column_info_his_pk PRIMARY KEY (id, version)
);
COMMENT ON TABLE metadata_column_info_his IS '表字段结构信息历史表';

-- Column comments

COMMENT ON COLUMN metadata_column_info_his.id IS '主键';
COMMENT ON COLUMN metadata_column_info_his."version" IS '版本号';
COMMENT ON COLUMN metadata_column_info_his.table_id IS '表ID';
COMMENT ON COLUMN metadata_column_info_his.table_name IS '表名称';
COMMENT ON COLUMN metadata_column_info_his.column_serial IS '字段序号';
COMMENT ON COLUMN metadata_column_info_his.column_name IS '字段名称';
COMMENT ON COLUMN metadata_column_info_his.column_desc IS '字段注释';
COMMENT ON COLUMN metadata_column_info_his.column_type IS '字段类型';
COMMENT ON COLUMN metadata_column_info_his.column_length IS '字段长度';
COMMENT ON COLUMN metadata_column_info_his.column_precision IS '字段精度';
COMMENT ON COLUMN metadata_column_info_his.is_primary IS '是否主键';
COMMENT ON COLUMN metadata_column_info_his.is_nullable IS '是否非空';
COMMENT ON COLUMN metadata_column_info_his.default_value IS '默认值';
COMMENT ON COLUMN metadata_column_info_his.java_type IS 'java类型';
COMMENT ON COLUMN metadata_column_info_his.view_type IS '查询类型';
COMMENT ON COLUMN metadata_column_info_his.creator IS '创建人';
COMMENT ON COLUMN metadata_column_info_his.create_time IS '创建时间';
COMMENT ON COLUMN metadata_column_info_his.updater IS '修改人';
COMMENT ON COLUMN metadata_column_info_his.update_time IS '修改时间';

-- DROP TABLE metadata_table_operate_log;

CREATE TABLE metadata_table_operate_log (
id uuid NOT NULL, -- 主键
operate_type int4 NOT NULL, -- 操作类型0:批量创建|1:批量对比
source_datasource_id uuid NOT NULL, -- 源数据源
target_datasource_id uuid NOT NULL, -- 目标数据源
operate_time timestamp(6) NOT NULL, -- 操作时间
snapshot_step_1 json NULL, -- 范围快照
snapshot_step_2 json NULL, -- 对比快照
snapshot_step_3 json NULL, -- 执行快照
creator varchar(64) NOT NULL, -- 创建人
create_time timestamp(6) NOT NULL, -- 创建时间
updater varchar(64) NOT NULL, -- 修改人
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT metadata_table_operate_log_pk PRIMARY KEY (id)
);
COMMENT ON TABLE metadata_table_operate_log IS '表结构同步记录表';

-- Column comments

COMMENT ON COLUMN metadata_table_operate_log.id IS '主键';
COMMENT ON COLUMN metadata_table_operate_log.operate_type IS '操作类型0:批量创建|1:批量对比';
COMMENT ON COLUMN metadata_table_operate_log.source_datasource_id IS '源数据源';
COMMENT ON COLUMN metadata_table_operate_log.target_datasource_id IS '目标数据源';
COMMENT ON COLUMN metadata_table_operate_log.operate_time IS '操作时间';
COMMENT ON COLUMN metadata_table_operate_log.snapshot_step_1 IS '范围快照';
COMMENT ON COLUMN metadata_table_operate_log.snapshot_step_2 IS '对比快照';
COMMENT ON COLUMN metadata_table_operate_log.snapshot_step_3 IS '执行快照';
COMMENT ON COLUMN metadata_table_operate_log.creator IS '创建人';
COMMENT ON COLUMN metadata_table_operate_log.create_time IS '创建时间';
COMMENT ON COLUMN metadata_table_operate_log.updater IS '修改人';
COMMENT ON COLUMN metadata_table_operate_log.update_time IS '修改时间';

-- 数据资产模块
-- DROP TABLE asset_lineage_node;
CREATE TABLE asset_lineage_node (
id uuid NOT NULL, -- 唯一主键ID
node_urn varchar(1024) NOT NULL, -- 节点资源(全局唯一)
node_name varchar(256) NOT NULL, -- 节点名称
node_type varchar(32) NOT NULL, -- 节点类型
node_sub_type varchar(50) NOT NULL, -- 节点类型
node_prop jsonb NULL, -- 节点属性
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT asset_lineage_node_node_urn_key UNIQUE (node_urn),
CONSTRAINT asset_lineage_node_pk PRIMARY KEY (id)
);
COMMENT ON TABLE asset_lineage_node IS '血缘节点表';

-- Column comments

COMMENT ON COLUMN asset_lineage_node.id IS '唯一主键ID';
COMMENT ON COLUMN asset_lineage_node.node_urn IS '节点资源(全局唯一)';
COMMENT ON COLUMN asset_lineage_node.node_name IS '节点名称';
COMMENT ON COLUMN asset_lineage_node.node_type IS '节点类型';
COMMENT ON COLUMN asset_lineage_node.node_sub_type IS '节点类型';
COMMENT ON COLUMN asset_lineage_node.node_prop IS '节点属性';
COMMENT ON COLUMN asset_lineage_node.creator IS '创建人';
COMMENT ON COLUMN asset_lineage_node.updater IS '修改人';
COMMENT ON COLUMN asset_lineage_node.create_time IS '创建时间';
COMMENT ON COLUMN asset_lineage_node.update_time IS '修改时间';


-- DROP TABLE asset_lineage_edge;

CREATE TABLE asset_lineage_edge (
id uuid NOT NULL, -- 唯一主键ID
source_urn varchar(255) NOT NULL, -- 源资产的URN
target_urn varchar(255) NOT NULL, -- 目标资产的URN
edge_prop jsonb NULL, -- 属性
resource_id uuid NOT NULL, -- 资源id
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT asset_lineage_edge_pk PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_idx_asset_lineage_edge ON asset_lineage_edge USING btree (resource_id, source_urn, target_urn);
COMMENT ON TABLE asset_lineage_edge IS '血缘边关系表';

-- Column comments

COMMENT ON COLUMN asset_lineage_edge.id IS '唯一主键ID';
COMMENT ON COLUMN asset_lineage_edge.source_urn IS '源资产的URN';
COMMENT ON COLUMN asset_lineage_edge.target_urn IS '目标资产的URN';
COMMENT ON COLUMN asset_lineage_edge.edge_prop IS '属性';
COMMENT ON COLUMN asset_lineage_edge.resource_id IS '资源id';
COMMENT ON COLUMN asset_lineage_edge.creator IS '创建人';
COMMENT ON COLUMN asset_lineage_edge.updater IS '修改人';
COMMENT ON COLUMN asset_lineage_edge.create_time IS '创建时间';
COMMENT ON COLUMN asset_lineage_edge.update_time IS '修改时间';


-- DROP TABLE asset_lineage_resource;

CREATE TABLE asset_lineage_resource (
id uuid NOT NULL, -- 主键
resource_name varchar(255) NOT NULL, -- 资源名称
resource_tag varchar(128) NULL, -- 资源标签,1:节点,2:边关系,3:节点和边
resource_type varchar(10) NOT NULL, -- 资源类型
status int2 NOT NULL, -- 状态,0导入完成,1录入中,2录入完成
resource_snapshot jsonb NOT NULL, -- 录入资源快照
result_snapshot jsonb NULL, -- 解析结果快照
result jsonb NULL, -- 解析结果信息
creator varchar(50) NOT NULL, -- 创建人
create_time timestamp(6) NOT NULL, -- 创建时间
updater varchar(50) NOT NULL, -- 修改人
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT asset_lineage_resource_pk PRIMARY KEY (id)
);
CREATE INDEX idx_asset_lineage_resource_business_domain_btree ON asset_lineage_resource USING btree (((resource_snapshot -> 'businessDomain'::text)));
CREATE INDEX idx_asset_lineage_resource_env_btree ON asset_lineage_resource USING btree (((resource_snapshot -> 'env'::text)));
CREATE INDEX idx_asset_lineage_resource_organization_btree ON asset_lineage_resource USING btree (((resource_snapshot -> 'organization'::text)));
CREATE INDEX idx_asset_lineage_resource_parent_resource_id_btree ON asset_lineage_resource USING btree ((((resource_snapshot ->> 'parentResourceId'::text))::uuid));
CREATE INDEX idx_asset_lineage_resource_request_type_btree ON asset_lineage_resource USING btree (((resource_snapshot -> 'requestType'::text)));
CREATE INDEX idx_asset_lineage_resource_request_url_btree ON asset_lineage_resource USING btree (((resource_snapshot -> 'requestUrl'::text)));
CREATE INDEX idx_asset_lineage_resource_service_en_name_btree ON asset_lineage_resource USING btree (((resource_snapshot -> 'serviceEnName'::text)));
CREATE INDEX idx_asset_lineage_resource_service_type_btree ON asset_lineage_resource USING btree (((resource_snapshot -> 'serviceType'::text)));
CREATE UNIQUE INDEX unique_idx_asset_lineage_resource ON asset_lineage_resource USING btree (resource_name);
COMMENT ON TABLE asset_lineage_resource IS '血缘资源表';

-- Column comments

COMMENT ON COLUMN asset_lineage_resource.id IS '主键';
COMMENT ON COLUMN asset_lineage_resource.resource_name IS '资源名称';
COMMENT ON COLUMN asset_lineage_resource.resource_tag IS '资源标签,1:节点,2:边关系,3:节点和边';
COMMENT ON COLUMN asset_lineage_resource.resource_type IS '资源类型';
COMMENT ON COLUMN asset_lineage_resource.status IS '状态,0导入完成,1录入中,2录入完成';
COMMENT ON COLUMN asset_lineage_resource.resource_snapshot IS '录入资源快照';
COMMENT ON COLUMN asset_lineage_resource.result_snapshot IS '解析结果快照';
COMMENT ON COLUMN asset_lineage_resource.result IS '解析结果信息';
COMMENT ON COLUMN asset_lineage_resource.creator IS '创建人';
COMMENT ON COLUMN asset_lineage_resource.create_time IS '创建时间';
COMMENT ON COLUMN asset_lineage_resource.updater IS '修改人';
COMMENT ON COLUMN asset_lineage_resource.update_time IS '修改时间';

-- DROP TABLE asset_lineage_node_resource_relation;

CREATE TABLE asset_lineage_node_resource_relation (
id uuid NOT NULL, -- 唯一主键ID
resource_id uuid NOT NULL, -- 资源id
node_id uuid NOT NULL, -- 节点id
creator varchar(100) NOT NULL, -- 创建人
create_time timestamp(6) NOT NULL, -- 创建时间
updater varchar(100) NOT NULL, -- 修改人
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT asset_lineage_node_resource_relation_pk PRIMARY KEY (id)
);
CREATE UNIQUE INDEX unique_idx_res_node ON asset_lineage_node_resource_relation USING btree (resource_id, node_id);
COMMENT ON TABLE asset_lineage_node_resource_relation IS '血缘资源节点溯源关系表';

-- Column comments

COMMENT ON COLUMN asset_lineage_node_resource_relation.id IS '唯一主键ID';
COMMENT ON COLUMN asset_lineage_node_resource_relation.resource_id IS '资源id';
COMMENT ON COLUMN asset_lineage_node_resource_relation.node_id IS '节点id';
COMMENT ON COLUMN asset_lineage_node_resource_relation.creator IS '创建人';
COMMENT ON COLUMN asset_lineage_node_resource_relation.create_time IS '创建时间';
COMMENT ON COLUMN asset_lineage_node_resource_relation.updater IS '修改人';
COMMENT ON COLUMN asset_lineage_node_resource_relation.update_time IS '修改时间';

-- 数据调度模块
-- DROP TABLE scheduler_event_info;

CREATE TABLE scheduler_event_info (
id uuid NOT NULL, -- 主键
event_name varchar NOT NULL, -- 事件名称
event_type varchar NOT NULL, -- 事件类型
flow_id uuid NULL, -- 流程ID
task_id uuid NULL, -- 任务ID
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT event_info_pkey PRIMARY KEY (id)
);

-- Column comments

COMMENT ON COLUMN scheduler_event_info.id IS '主键';
COMMENT ON COLUMN scheduler_event_info.event_name IS '事件名称';
COMMENT ON COLUMN scheduler_event_info.event_type IS '事件类型';
COMMENT ON COLUMN scheduler_event_info.flow_id IS '流程ID';
COMMENT ON COLUMN scheduler_event_info.task_id IS '任务ID';
COMMENT ON COLUMN scheduler_event_info.creator IS '创建人';
COMMENT ON COLUMN scheduler_event_info.updater IS '修改人';
COMMENT ON COLUMN scheduler_event_info.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_event_info.update_time IS '修改时间';


-- DROP TABLE scheduler_event_instance;

CREATE TABLE scheduler_event_instance (
id uuid NOT NULL, -- 主键
event_id uuid NOT NULL, -- 事件id
event_name varchar NOT NULL, -- 事件名称
event_type varchar NOT NULL, -- 事件类型:TASK,FLOW
flow_instance_id uuid NULL, -- 流程实例id
task_instance_id uuid NULL, -- 任务实例id
effect_time int8 NULL, -- 事件生效时间
effect_begin_time int8 NULL, -- 事件开始生效时间
effect_end_time int8 NULL, -- 事件结束生效时间
CONSTRAINT event_instance_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE scheduler_event_instance IS '事件实例表';

-- Column comments

COMMENT ON COLUMN scheduler_event_instance.id IS '主键';
COMMENT ON COLUMN scheduler_event_instance.event_id IS '事件id';
COMMENT ON COLUMN scheduler_event_instance.event_name IS '事件名称';
COMMENT ON COLUMN scheduler_event_instance.event_type IS '事件类型:TASK,FLOW';
COMMENT ON COLUMN scheduler_event_instance.flow_instance_id IS '流程实例id';
COMMENT ON COLUMN scheduler_event_instance.task_instance_id IS '任务实例id';
COMMENT ON COLUMN scheduler_event_instance.effect_time IS '事件生效时间';
COMMENT ON COLUMN scheduler_event_instance.effect_begin_time IS '事件开始生效时间';
COMMENT ON COLUMN scheduler_event_instance.effect_end_time IS '事件结束生效时间';


-- DROP TABLE scheduler_flow_info;

CREATE TABLE scheduler_flow_info (
 id uuid NOT NULL, -- 主键
 flow_name varchar(255) NOT NULL, -- 流程名称
 flow_code varchar(255) NOT NULL, -- 流程编码
 group_id uuid NULL, -- 流程分组
 trigger_id uuid NOT NULL, -- 触发器ID
 description varchar(1000) NULL, -- 流程描述
 flow_type varchar NOT NULL, -- 流程类型
 flow_param json NULL, -- 流程变量参数信息
 start_time int8 NULL, -- 调度开始时间
 end_time int8 NULL, -- 调度结束时间
 enabled bool DEFAULT false NOT NULL, -- 是否调度:0-未调度,1-调度中
 dep_event_ids varchar NULL, -- 依赖事件id,逗号分割
 event_id uuid NULL, -- 事件id
 publish_state bool DEFAULT false NOT NULL, -- 发布状态:0-未发布,1-已发布
 publish_version int8 DEFAULT 0 NOT NULL, -- 发布版本(格式:时间戳,等价与发布时间)
 "view" json NULL, -- 流程前端视图信息
 creator varchar(100) NOT NULL, -- 创建人
 updater varchar(100) NOT NULL, -- 修改人
 create_time timestamp(6) NOT NULL, -- 创建时间
 update_time timestamp(6) NOT NULL, -- 修改时间
 CONSTRAINT flow_info_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE scheduler_flow_info IS '流程信息';

-- Column comments

COMMENT ON COLUMN scheduler_flow_info.id IS '主键';
COMMENT ON COLUMN scheduler_flow_info.flow_name IS '流程名称';
COMMENT ON COLUMN scheduler_flow_info.flow_code IS '流程编码';
COMMENT ON COLUMN scheduler_flow_info.group_id IS '流程分组';
COMMENT ON COLUMN scheduler_flow_info.trigger_id IS '触发器ID';
COMMENT ON COLUMN scheduler_flow_info.description IS '流程描述';
COMMENT ON COLUMN scheduler_flow_info.flow_type IS '流程类型';
COMMENT ON COLUMN scheduler_flow_info.flow_param IS '流程变量参数信息';
COMMENT ON COLUMN scheduler_flow_info.start_time IS '调度开始时间';
COMMENT ON COLUMN scheduler_flow_info.end_time IS '调度结束时间';
COMMENT ON COLUMN scheduler_flow_info.enabled IS '是否调度:0-未调度,1-调度中';
COMMENT ON COLUMN scheduler_flow_info.dep_event_ids IS '依赖事件id,逗号分割';
COMMENT ON COLUMN scheduler_flow_info.event_id IS '事件id';
COMMENT ON COLUMN scheduler_flow_info.publish_state IS '发布状态:0-未发布,1-已发布';
COMMENT ON COLUMN scheduler_flow_info.publish_version IS '发布版本(格式:时间戳,等价与发布时间)';
COMMENT ON COLUMN scheduler_flow_info."view" IS '流程前端视图信息';
COMMENT ON COLUMN scheduler_flow_info.creator IS '创建人';
COMMENT ON COLUMN scheduler_flow_info.updater IS '修改人';
COMMENT ON COLUMN scheduler_flow_info.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_flow_info.update_time IS '修改时间';


-- DROP TABLE scheduler_flow_instance;

CREATE TABLE scheduler_flow_instance (
id uuid NOT NULL, -- 实例id
flow_id uuid NOT NULL, -- 流程ID
flow_name varchar(64) NOT NULL, -- 流程名称
flow_code varchar(50) NULL, -- 流程编码
flow_type varchar NOT NULL, -- 流程类型
status varchar(50) NOT NULL, -- 流程实例状态
trigger_id varchar NOT NULL, -- 发布版本
publish_version int8 NULL, -- 发布版本
flow_param json NULL, -- 流程变量参数
dep_event_ids varchar NULL, -- 全局依赖事件ID，英文逗号分割
event_id uuid NULL, -- 事件ID
schedule_time int8 NULL, -- 调度时间
start_time int8 NULL, -- 开始时间
end_time int8 NULL, -- 结束时间
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
flow_dag_snapshot json NULL, -- 流程DAG快照
CONSTRAINT flow_instance_pkey PRIMARY KEY (id)
);

-- Column comments

COMMENT ON COLUMN scheduler_flow_instance.id IS '实例id';
COMMENT ON COLUMN scheduler_flow_instance.flow_id IS '流程ID';
COMMENT ON COLUMN scheduler_flow_instance.flow_name IS '流程名称';
COMMENT ON COLUMN scheduler_flow_instance.flow_code IS '流程编码';
COMMENT ON COLUMN scheduler_flow_instance.flow_type IS '流程类型';
COMMENT ON COLUMN scheduler_flow_instance.status IS '流程实例状态';
COMMENT ON COLUMN scheduler_flow_instance.trigger_id IS '发布版本';
COMMENT ON COLUMN scheduler_flow_instance.publish_version IS '发布版本';
COMMENT ON COLUMN scheduler_flow_instance.flow_param IS '流程变量参数';
COMMENT ON COLUMN scheduler_flow_instance.dep_event_ids IS '全局依赖事件ID，英文逗号分割';
COMMENT ON COLUMN scheduler_flow_instance.event_id IS '事件ID';
COMMENT ON COLUMN scheduler_flow_instance.schedule_time IS '调度时间';
COMMENT ON COLUMN scheduler_flow_instance.start_time IS '开始时间';
COMMENT ON COLUMN scheduler_flow_instance.end_time IS '结束时间';
COMMENT ON COLUMN scheduler_flow_instance.creator IS '创建人';
COMMENT ON COLUMN scheduler_flow_instance.updater IS '修改人';
COMMENT ON COLUMN scheduler_flow_instance.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_flow_instance.update_time IS '修改时间';
COMMENT ON COLUMN scheduler_flow_instance.flow_dag_snapshot IS '流程DAG快照';


-- DROP TABLE scheduler_flow_instance_his;

CREATE TABLE scheduler_flow_instance_his (
id uuid NOT NULL, -- 实例id
flow_id uuid NOT NULL, -- 流程ID
flow_name varchar(64) NOT NULL, -- 流程名称
flow_code varchar(50) NULL, -- 流程编码
flow_type varchar NOT NULL, -- 流程类型
status varchar(50) NOT NULL, -- 流程实例状态
trigger_id varchar NOT NULL, -- 发布版本
publish_version int8 NULL, -- 发布版本
flow_param json NULL, -- 流程变量参数
dep_event_ids varchar NULL, -- 全局依赖事件ID，英文逗号分割
event_id uuid NULL, -- 事件ID
schedule_time int8 NULL, -- 调度时间
start_time int8 NULL, -- 开始时间
end_time int8 NULL, -- 结束时间
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
flow_dag_snapshot json NULL, -- 流程DAG快照
CONSTRAINT flow_instance_his_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE scheduler_flow_instance_his IS '流程运行实例历史表';

COMMENT ON COLUMN scheduler_flow_instance_his.id IS '实例id';
COMMENT ON COLUMN scheduler_flow_instance_his.flow_id IS '流程ID';
COMMENT ON COLUMN scheduler_flow_instance_his.flow_name IS '流程名称';
COMMENT ON COLUMN scheduler_flow_instance_his.flow_code IS '流程编码';
COMMENT ON COLUMN scheduler_flow_instance_his.flow_type IS '流程类型';
COMMENT ON COLUMN scheduler_flow_instance_his.status IS '流程实例状态';
COMMENT ON COLUMN scheduler_flow_instance_his.trigger_id IS '发布版本';
COMMENT ON COLUMN scheduler_flow_instance_his.publish_version IS '发布版本';
COMMENT ON COLUMN scheduler_flow_instance_his.flow_param IS '流程变量参数';
COMMENT ON COLUMN scheduler_flow_instance_his.dep_event_ids IS '全局依赖事件ID，英文逗号分割';
COMMENT ON COLUMN scheduler_flow_instance_his.event_id IS '事件ID';
COMMENT ON COLUMN scheduler_flow_instance_his.schedule_time IS '调度时间';
COMMENT ON COLUMN scheduler_flow_instance_his.start_time IS '开始时间';
COMMENT ON COLUMN scheduler_flow_instance_his.end_time IS '结束时间';
COMMENT ON COLUMN scheduler_flow_instance_his.creator IS '创建人';
COMMENT ON COLUMN scheduler_flow_instance_his.updater IS '修改人';
COMMENT ON COLUMN scheduler_flow_instance_his.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_flow_instance_his.update_time IS '修改时间';
COMMENT ON COLUMN scheduler_flow_instance_his.flow_dag_snapshot IS '流程DAG快照';


-- DROP TABLE scheduler_task_info;

CREATE TABLE scheduler_task_info (
id uuid NOT NULL, -- 主键
task_name varchar(255) NOT NULL, -- 任务名称
task_code varchar(255) NOT NULL, -- 任务编码
description varchar(1000) NULL, -- 任务描述
task_type_id varchar(255) NOT NULL, -- 任务类型ID
task_type varchar(255) NOT NULL, -- 任务类型
task_param json NULL, -- 任务变量参数
definition json NULL, -- 任务定义
is_bound bool DEFAULT false NOT NULL, -- 是否绑定流程
flow_id uuid NULL, -- 流程ID
plugin_id uuid NOT NULL, -- 执行组件ID
"view" json NULL, -- 任务前端视图
dep_event_ids varchar NULL, -- 依赖事件ID
event_id uuid NULL, -- 事件ID
enabled bool DEFAULT false NOT NULL, -- 是否启用
sync_flag bool DEFAULT false NOT NULL, -- 任务同步标识:修改业务任务时更新false,提交时置为true
source_route text NULL, -- 原始业务跳转定位信息
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT task_info_pkey PRIMARY KEY (id)
);

-- Column comments

COMMENT ON COLUMN scheduler_task_info.id IS '主键';
COMMENT ON COLUMN scheduler_task_info.task_name IS '任务名称';
COMMENT ON COLUMN scheduler_task_info.task_code IS '任务编码';
COMMENT ON COLUMN scheduler_task_info.description IS '任务描述';
COMMENT ON COLUMN scheduler_task_info.task_type_id IS '任务类型ID';
COMMENT ON COLUMN scheduler_task_info.task_type IS '任务类型';
COMMENT ON COLUMN scheduler_task_info.task_param IS '任务变量参数';
COMMENT ON COLUMN scheduler_task_info.definition IS '任务定义';
COMMENT ON COLUMN scheduler_task_info.is_bound IS '是否绑定流程';
COMMENT ON COLUMN scheduler_task_info.flow_id IS '流程ID';
COMMENT ON COLUMN scheduler_task_info.plugin_id IS '执行组件ID';
COMMENT ON COLUMN scheduler_task_info."view" IS '任务前端视图';
COMMENT ON COLUMN scheduler_task_info.dep_event_ids IS '依赖事件ID';
COMMENT ON COLUMN scheduler_task_info.event_id IS '事件ID';
COMMENT ON COLUMN scheduler_task_info.enabled IS '是否启用';
COMMENT ON COLUMN scheduler_task_info.sync_flag IS '任务同步标识:修改业务任务时更新false,提交时置为true';
COMMENT ON COLUMN scheduler_task_info.source_route IS '原始业务跳转定位信息';
COMMENT ON COLUMN scheduler_task_info.creator IS '创建人';
COMMENT ON COLUMN scheduler_task_info.updater IS '修改人';
COMMENT ON COLUMN scheduler_task_info.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_task_info.update_time IS '修改时间';


-- DROP TABLE scheduler_task_instance;

CREATE TABLE scheduler_task_instance (
id uuid NOT NULL,
flow_id uuid NOT NULL, -- 流程ID
flow_instance_id uuid NOT NULL, -- 流程实例ID
task_id uuid NOT NULL, -- 任务ID
task_type varchar NOT NULL, --任务类型
task_name varchar NOT NULL, -- 任务名称
task_code varchar NOT NULL, -- 任务编码
description varchar NULL, -- 任务描述
task_param json NULL, -- 任务变量参数
task_data json NULL, -- 渲染后的任务定义
plugin_data json NULL, -- 组件数据
"view" json NULL, -- 任务视图
dep_event_ids text NULL, -- 依赖事件id
event_id uuid NULL, -- 产生事件id
status varchar NULL, -- 任务实例状态
start_time int8 NULL, -- 任务实例开始时间
end_time int8 NULL, -- 任务实例结束时间
cost_time int4 NULL, -- 耗时
last_instance_id text NULL, -- 上一个任务实例id
next_instance_id text NULL, -- 下一个任务实例id
worker_id uuid NULL, -- 执行节点id
worker_result json NULL, -- 返回值
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 更新人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 更新时间
CONSTRAINT task_instance_pkey PRIMARY KEY (id)
);

-- Column comments

COMMENT ON COLUMN scheduler_task_instance.flow_id IS '流程ID';
COMMENT ON COLUMN scheduler_task_instance.flow_instance_id IS '流程实例ID';
COMMENT ON COLUMN scheduler_task_instance.task_id IS '任务ID';
COMMENT ON COLUMN scheduler_task_instance.task_type IS ' 任务类型';
COMMENT ON COLUMN scheduler_task_instance.task_name IS '任务名称';
COMMENT ON COLUMN scheduler_task_instance.task_code IS '任务编码';
COMMENT ON COLUMN scheduler_task_instance.description IS '任务描述';
COMMENT ON COLUMN scheduler_task_instance.task_param IS '任务变量参数';
COMMENT ON COLUMN scheduler_task_instance.task_data IS '渲染后的任务定义';
COMMENT ON COLUMN scheduler_task_instance.plugin_data IS '组件数据';
COMMENT ON COLUMN scheduler_task_instance."view" IS '任务视图';
COMMENT ON COLUMN scheduler_task_instance.dep_event_ids IS '依赖事件id';
COMMENT ON COLUMN scheduler_task_instance.event_id IS '产生事件id';
COMMENT ON COLUMN scheduler_task_instance.status IS '任务实例状态';
COMMENT ON COLUMN scheduler_task_instance.start_time IS '任务实例开始时间';
COMMENT ON COLUMN scheduler_task_instance.end_time IS '任务实例结束时间';
COMMENT ON COLUMN scheduler_task_instance.cost_time IS '耗时';
COMMENT ON COLUMN scheduler_task_instance.last_instance_id IS '上一个任务实例id';
COMMENT ON COLUMN scheduler_task_instance.next_instance_id IS '下一个任务实例id';
COMMENT ON COLUMN scheduler_task_instance.worker_id IS '执行节点id';
COMMENT ON COLUMN scheduler_task_instance.worker_result IS '返回值';
COMMENT ON COLUMN scheduler_task_instance.creator IS '创建人';
COMMENT ON COLUMN scheduler_task_instance.updater IS '更新人';
COMMENT ON COLUMN scheduler_task_instance.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_task_instance.update_time IS '更新时间';


-- DROP TABLE scheduler_task_instance_his;

CREATE TABLE scheduler_task_instance_his (
id uuid NOT NULL,
flow_id uuid NOT NULL, -- 流程ID
flow_instance_id uuid NOT NULL, -- 流程实例ID
task_id uuid NOT NULL, -- 任务ID
task_type varchar NOT NULL, --任务类型
task_name varchar NOT NULL, -- 任务名称
task_code varchar NOT NULL, -- 任务编码
description varchar NULL, -- 任务描述
task_param json NULL, -- 任务变量参数
task_data json NULL, -- 渲染后的任务定义
plugin_data json NULL, -- 组件数据
"view" json NULL, -- 任务视图
dep_event_ids text NULL, -- 依赖事件id
event_id uuid NULL, -- 产生事件id
status varchar NULL, -- 任务实例状态
start_time int8 NULL, -- 任务实例开始时间
end_time int8 NULL, -- 任务实例结束时间
cost_time int4 NULL, -- 耗时
last_instance_id text NULL, -- 上一个任务实例id
next_instance_id text NULL, -- 下一个任务实例id
worker_id uuid NULL, -- 执行节点id
worker_result json NULL, -- 返回值
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 更新人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 更新时间
CONSTRAINT task_instance_his_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE scheduler_task_instance_his IS '任务运行实例历史表';

COMMENT ON COLUMN scheduler_task_instance_his.id IS '主键';
COMMENT ON COLUMN scheduler_task_instance_his.flow_id IS '流程ID';
COMMENT ON COLUMN scheduler_task_instance_his.flow_instance_id IS '流程实例ID';
COMMENT ON COLUMN scheduler_task_instance_his.task_id IS '任务ID';
COMMENT ON COLUMN scheduler_task_instance_his.task_type IS ' 任务类型';
COMMENT ON COLUMN scheduler_task_instance_his.task_name IS '任务名称';
COMMENT ON COLUMN scheduler_task_instance_his.task_code IS '任务编码';
COMMENT ON COLUMN scheduler_task_instance_his.description IS '任务描述';
COMMENT ON COLUMN scheduler_task_instance_his.task_param IS '任务变量参数';
COMMENT ON COLUMN scheduler_task_instance_his.task_data IS '渲染后的任务定义';
COMMENT ON COLUMN scheduler_task_instance_his.plugin_data IS '组件数据';
COMMENT ON COLUMN scheduler_task_instance_his."view" IS '任务视图';
COMMENT ON COLUMN scheduler_task_instance_his.dep_event_ids IS '依赖事件id';
COMMENT ON COLUMN scheduler_task_instance_his.event_id IS '产生事件id';
COMMENT ON COLUMN scheduler_task_instance_his.status IS '任务实例状态';
COMMENT ON COLUMN scheduler_task_instance_his.start_time IS '任务实例开始时间';
COMMENT ON COLUMN scheduler_task_instance_his.end_time IS '任务实例结束时间';
COMMENT ON COLUMN scheduler_task_instance_his.cost_time IS '耗时';
COMMENT ON COLUMN scheduler_task_instance_his.last_instance_id IS '上一个任务实例id';
COMMENT ON COLUMN scheduler_task_instance_his.next_instance_id IS '下一个任务实例id';
COMMENT ON COLUMN scheduler_task_instance_his.worker_id IS '执行节点id';
COMMENT ON COLUMN scheduler_task_instance_his.worker_result IS '返回值';
COMMENT ON COLUMN scheduler_task_instance_his.creator IS '创建人';
COMMENT ON COLUMN scheduler_task_instance_his.updater IS '更新人';
COMMENT ON COLUMN scheduler_task_instance_his.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_task_instance_his.update_time IS '更新时间';


-- DROP TABLE scheduler_task_link;

CREATE TABLE scheduler_task_link (
id uuid NOT NULL, -- 连接id
flow_id uuid NOT NULL, -- 流程id
start_id uuid NOT NULL, -- 开始节点id
end_id uuid NOT NULL, -- 结束节点id
"view" json NULL, -- 连线视图
CONSTRAINT task_link_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE scheduler_task_link IS '流程任务编排关系';

-- Column comments

COMMENT ON COLUMN scheduler_task_link.id IS '连接id';
COMMENT ON COLUMN scheduler_task_link.flow_id IS '流程id';
COMMENT ON COLUMN scheduler_task_link.start_id IS '开始节点id';
COMMENT ON COLUMN scheduler_task_link.end_id IS '结束节点id';
COMMENT ON COLUMN scheduler_task_link."view" IS '连线视图';


-- DROP TABLE scheduler_trigger_info;

CREATE TABLE scheduler_trigger_info (
id uuid NOT NULL, -- 主键
"name" varchar(255) NOT NULL, -- 触发器名称
"policy" varchar(255) NOT NULL, -- 调度策略
"type" varchar(255) NOT NULL, -- 触发器类型:0-CRON,1-INTERVAL
cron varchar(255) NULL, -- cron表达式
"interval" int4 NULL, -- 周期间隔时间,单位/分钟
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT trigger_info_pkey PRIMARY KEY (id)
);

-- Column comments

COMMENT ON COLUMN scheduler_trigger_info.id IS '主键';
COMMENT ON COLUMN scheduler_trigger_info."name" IS '触发器名称';
COMMENT ON COLUMN scheduler_trigger_info."policy" IS '调度策略';
COMMENT ON COLUMN scheduler_trigger_info."type" IS '触发器类型:0-CRON,1-INTERVAL';
COMMENT ON COLUMN scheduler_trigger_info.cron IS 'cron表达式';
COMMENT ON COLUMN scheduler_trigger_info."interval" IS '周期间隔时间,单位/分钟';
COMMENT ON COLUMN scheduler_trigger_info.creator IS '创建人';
COMMENT ON COLUMN scheduler_trigger_info.updater IS '修改人';
COMMENT ON COLUMN scheduler_trigger_info.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_trigger_info.update_time IS '修改时间';

-- 调度 worker 信息
-- DROP TABLE scheduler_worker_registry;

CREATE TABLE scheduler_worker_registry (
id uuid NOT NULL,
worker_code varchar(128) NOT NULL, -- worker编码
host_name varchar(128) NOT NULL, -- 主机名称
host varchar(45) NOT NULL, -- IP地址
port int4 NOT NULL, -- 端口
status int4 NOT NULL, -- 状态：0-下线 1-上线 2-清除
"zone" varchar(64) NULL, -- 区域/分组，预留字段
plugins varchar(256) NULL, -- 组件类型列表，逗号分隔
register_time timestamp(6) NULL, -- 注册时间
last_heartbeat_time timestamp(6) NULL, -- 最近心跳时间
is_active int2 NOT NULL, -- 是否有效：1-有效 0-无效
remark text NULL, -- 资源说明
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
tenant_id uuid NULL, -- 租户ID
CONSTRAINT scheduler_worker_registry_host_port_uk UNIQUE (host, port),
CONSTRAINT scheduler_worker_registry_pkey PRIMARY KEY (id),
CONSTRAINT scheduler_worker_registry_worker_code_uk UNIQUE (worker_code)
);
COMMENT ON TABLE scheduler_worker_registry IS '调度 worker 注册表，记录 worker 的注册状态、心跳时间、插件能力和运行元信息';

-- Column comments

COMMENT ON COLUMN scheduler_worker_registry.worker_code IS 'worker编码';
COMMENT ON COLUMN scheduler_worker_registry.host_name IS '主机名称';
COMMENT ON COLUMN scheduler_worker_registry.host IS 'IP地址';
COMMENT ON COLUMN scheduler_worker_registry.port IS '端口';
COMMENT ON COLUMN scheduler_worker_registry.status IS '状态：0-下线 1-上线 2-清除';
COMMENT ON COLUMN scheduler_worker_registry."zone" IS '区域/分组，预留字段';
COMMENT ON COLUMN scheduler_worker_registry.plugins IS '组件类型列表，逗号分隔';
COMMENT ON COLUMN scheduler_worker_registry.register_time IS '注册时间';
COMMENT ON COLUMN scheduler_worker_registry.last_heartbeat_time IS '最近心跳时间';
COMMENT ON COLUMN scheduler_worker_registry.is_active IS '是否有效：1-有效 0-无效';
COMMENT ON COLUMN scheduler_worker_registry.remark IS '资源说明';
COMMENT ON COLUMN scheduler_worker_registry.creator IS '创建人';
COMMENT ON COLUMN scheduler_worker_registry.updater IS '修改人';
COMMENT ON COLUMN scheduler_worker_registry.create_time IS '创建时间';
COMMENT ON COLUMN scheduler_worker_registry.update_time IS '修改时间';
COMMENT ON COLUMN scheduler_worker_registry.tenant_id IS '租户ID';


-- 系统配置模块
-- DROP TABLE system_variable_info;

CREATE TABLE system_variable_info (
id uuid NOT NULL,
code varchar(255) NOT NULL, -- 变量编码
"name" text NULL, -- 变量名称
"type" varchar(50) NOT NULL, -- 变量类型:CUSTOM(自定义);SYSTEM(系统全局)
value_type varchar(255) NOT NULL, -- 变量值类型
value text NULL, -- 值
remark text NULL, -- 参数备注
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT system_variable_info_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE system_variable_info IS '系统变量表';

COMMENT ON COLUMN system_variable_info.code IS '变量编码';
COMMENT ON COLUMN system_variable_info."name" IS '变量名称';
COMMENT ON COLUMN system_variable_info."type" IS '变量类型:CUSTOM(自定义);SYSTEM(系统全局)';
COMMENT ON COLUMN system_variable_info.value_type IS '变量值类型';
COMMENT ON COLUMN system_variable_info.value IS '值';
COMMENT ON COLUMN system_variable_info.remark IS '参数备注';
COMMENT ON COLUMN system_variable_info.creator IS '创建人';
COMMENT ON COLUMN system_variable_info.updater IS '修改人';
COMMENT ON COLUMN system_variable_info.create_time IS '创建时间';
COMMENT ON COLUMN system_variable_info.update_time IS '修改时间';


-- DROP TABLE system_plugin_config;

CREATE TABLE system_plugin_config (
id uuid NOT NULL,
plugin_name varchar(255) NOT NULL,
plugin_type varchar(255) NOT NULL,
run_mode varchar(255) NOT NULL,
description varchar(255) NULL,
plugin_param jsonb NULL,
is_template bool NOT NULL DEFAULT false,
is_del int2 NOT NULL DEFAULT 0,
creator varchar(100) NOT NULL,
updater varchar(100) NOT NULL,
create_time timestamp(6) NOT NULL,
update_time timestamp(6) NOT NULL,
tenant_id uuid NOT NULL,
CONSTRAINT system_plugin_config_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE system_plugin_config IS '系统插件配置表';
COMMENT ON COLUMN system_plugin_config.id IS '主键';
COMMENT ON COLUMN system_plugin_config.plugin_name IS '插件名称';
COMMENT ON COLUMN system_plugin_config.plugin_type IS '插件类型';
COMMENT ON COLUMN system_plugin_config.run_mode IS '运行模式';
COMMENT ON COLUMN system_plugin_config.description IS '描述';
COMMENT ON COLUMN system_plugin_config.plugin_param IS '插件配置';
COMMENT ON COLUMN system_plugin_config.is_template IS '模板数据标记';
COMMENT ON COLUMN system_plugin_config.is_del IS '删除状态：0-正常; 1-删除';
COMMENT ON COLUMN system_plugin_config.creator IS '创建人';
COMMENT ON COLUMN system_plugin_config.updater IS '修改人';
COMMENT ON COLUMN system_plugin_config.create_time IS '创建时间';
COMMENT ON COLUMN system_plugin_config.update_time IS '修改时间';
COMMENT ON COLUMN system_plugin_config.tenant_id IS '租户ID';


-- DROP TABLE system_task_type_config;

CREATE TABLE system_task_type_config (
id uuid NOT NULL,
task_type varchar(255) NOT NULL,
default_plugin_id uuid NOT NULL,
plugin_type varchar(255) NULL,
creator varchar(100) NOT NULL,
updater varchar(100) NOT NULL,
create_time timestamp(6) NOT NULL,
update_time timestamp(6) NOT NULL,
tenant_id uuid NOT NULL,
CONSTRAINT system_task_type_config_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE system_task_type_config IS '任务类型配置表';
COMMENT ON COLUMN system_task_type_config.id IS '主键';
COMMENT ON COLUMN system_task_type_config.task_type IS '任务类型';
COMMENT ON COLUMN system_task_type_config.default_plugin_id IS '默认插件ID';
COMMENT ON COLUMN system_task_type_config.plugin_type IS '插件类型';
COMMENT ON COLUMN system_task_type_config.creator IS '创建人';
COMMENT ON COLUMN system_task_type_config.updater IS '修改人';
COMMENT ON COLUMN system_task_type_config.create_time IS '创建时间';
COMMENT ON COLUMN system_task_type_config.update_time IS '修改时间';
COMMENT ON COLUMN system_task_type_config.tenant_id IS '租户ID';


-- 平台外部清洗表
