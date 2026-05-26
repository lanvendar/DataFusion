CREATE DATABASE IF NOT EXISTS {database};
use {database};

SET PASSWORD = PASSWORD('sgk@8Lou');

-- 基于 starRocks
--  ====================   ods建表语句 ======================================================
-- 共建表 97张，其中流表 11张
-- 时序数据流表
CREATE TABLE IF NOT EXISTS ods_kv_ts_latest
(
    device_id                string COMMENT '设备id',
    `key`                    string COMMENT '属性标识符',
    product_id               string COMMENT '产品id',
    data_type                string COMMENT '属性值数据类型',
    value                    string COMMENT '属性值',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间'
)
PRIMARY KEY (device_id,`key`)
COMMENT '设备属性-最新值表'
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

CREATE TABLE IF NOT EXISTS ods_kv_ts_five_min
(
    device_id                string COMMENT '设备id',
    product_id               string COMMENT '产品id',
    `key`                      string COMMENT '属性标识符',
    dimension_ts             BIGINT COMMENT '维度时间戳',
    day_pt                   string COMMENT '日分区',
    data_type                string COMMENT '属性值数据类型',
    value                    string COMMENT '属性值',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间',
    dimension_tm             string COMMENT '维度显示时间'
)
PRIMARY KEY (device_id,product_id,`key`,dimension_ts,day_pt)
COMMENT '设备属性-5分钟维度实时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

CREATE TABLE IF NOT EXISTS ods_kv_ts_quarter_hour
(
    device_id                string COMMENT '设备id',
    product_id               string COMMENT '产品id',
    `key`                      string COMMENT '属性标识符',
    dimension_ts             BIGINT COMMENT '维度时间戳',
    day_pt                   string COMMENT '日分区',
    data_type                string COMMENT '属性值数据类型',
    value                    string COMMENT '属性值',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间',
    dimension_tm             string COMMENT '维度显示时间'
)
PRIMARY KEY (device_id,product_id,`key`,dimension_ts,day_pt)
COMMENT '设备属性-15分钟维度实时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

CREATE TABLE IF NOT EXISTS ods_kv_ts_one_hour
(
    device_id                string COMMENT '设备id',
    product_id               string COMMENT '产品id',
    `key`                      string COMMENT '属性标识符',
    dimension_ts             BIGINT COMMENT '维度时间戳',
    day_pt                   string COMMENT '日分区',
    data_type                string COMMENT '属性值数据类型',
    value                    string COMMENT '属性值',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间',
    dimension_tm             string COMMENT '维度显示时间'
)
PRIMARY KEY (device_id,product_id,`key`,dimension_ts,day_pt)
COMMENT '设备属性-1小时维度实时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

CREATE TABLE IF NOT EXISTS ods_kv_ts_day
(
    device_id                string COMMENT '设备id',
    product_id               string COMMENT '产品id',
    `key`                    string COMMENT '属性值',
    dimension_ts             BIGINT COMMENT '维度时间戳',
    day_pt                   string COMMENT '日分区',
    data_type                string COMMENT '属性值数据类型',
    value                    string COMMENT '属性标识符',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间',
    dimension_tm             string COMMENT '维度显示时间'
)
PRIMARY KEY (device_id,product_id,`key`,dimension_ts,day_pt)
COMMENT '设备属性-1天维度实时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

CREATE TABLE IF NOT EXISTS ods_kv_ts_his
(
    device_id                string COMMENT '设备id',
    `key`                    string COMMENT '属性标识符',
    ts                       BIGINT COMMENT '时间戳',
    day_pt                   string COMMENT '日分区',
    product_id               string COMMENT '产品id',
    data_type                string COMMENT '属性值数据类型',
    value                    string COMMENT '属性值',
    tm                       string COMMENT '显示时间'
)
PRIMARY KEY (device_id,`key`,ts,day_pt)
COMMENT '设备属性历史表'
PARTITION BY RANGE(str2date(day_pt, '%Y%m%d'))
(PARTITION p20200321 VALUES LESS THAN ("2020-03-22"),
 PARTITION p20200322 VALUES LESS THAN ("2020-03-23"),
 PARTITION p20200323 VALUES LESS THAN ("2020-03-24"),
 PARTITION p20200324 VALUES LESS THAN ("2020-03-25"))
DISTRIBUTED BY HASH(device_id)
PROPERTIES (
    "enable_persistent_index" = "true",
    "dynamic_partition.enable" = "true",
    "dynamic_partition.end" = "2",
    "dynamic_partition.history_partition_num" = "7",
    "dynamic_partition.prefix" = "p",
    "dynamic_partition.start" = "-7",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.time_zone" = "Asia/Shanghai"
);

-- 停车场 车位最新状态
CREATE TABLE IF NOT EXISTS ods_parking_parkingdetector_latest
(
    device_id                string COMMENT '设备id',
    entity_id                string COMMENT '实例id',
    entity_name              string COMMENT '实例名称',
    device_name              string COMMENT '设备名称',
    status                   string COMMENT '车位状态',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间',
    tenant_id                string COMMENT '租户id',
    project_id               string COMMENT '项目id',
    code                     string COMMENT '设备编号',
    loc_building_id          string COMMENT '建筑id',
    loc_floor_id             string COMMENT '楼层id',
    loc_space_id             string COMMENT '房间id',
    loc_unit_id              string COMMENT '点位id',
    service_area             string COMMENT '服务范围'
)
PRIMARY KEY (device_id,entity_id)
COMMENT '停车场-车位状态-最新值表'
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

-- 停车场 闸机进出记录
CREATE TABLE IF NOT EXISTS ods_parking_gate_flow_record
(
    id                       string COMMENT '记录id',
    device_id                string COMMENT '设备id',
    day_pt                   string COMMENT '分区字段',
    entity_id                string COMMENT '实例id',
    entity_name              string COMMENT '实例名称',
    tenant_id                string COMMENT '租户id',
    project_id               string COMMENT '项目id',
    device_name              string COMMENT '设备名称',
    plate_number             string COMMENT '车牌号',
    record_time              string COMMENT '进场/出场时间',
    place_image              string COMMENT '进场/出场图片',
    type                     string COMMENT '进出类型，0进场记录/1出场记录',
    in_id                    string COMMENT '出场记录的进场id',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间'
)
PRIMARY KEY (id,device_id,day_pt)
COMMENT '停车场-闸机流水进出记录表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

-- 门禁 -门禁记录表
CREATE TABLE IF NOT EXISTS ods_ac_access_record
(
    access_record_id         string COMMENT '记录id',
    device_id                string COMMENT '设备id',
    day_pt                   string COMMENT '分区字段',
    entity_id                string COMMENT '实例id',
    entity_name              string COMMENT '实例名称',
    tenant_id                string COMMENT '租户id',
    project_id               string COMMENT '项目id',
    device_name              string COMMENT '设备名称',
    input_type               string COMMENT '开门类型',
    access_type              string COMMENT '开门类型描述',
    event_type               string COMMENT '事件类型',
    event_type_desc          string COMMENT '事件类型描述',
    card_no                  string COMMENT '卡号',
    organization_name        string COMMENT '部门名称',
    person_name              string COMMENT '员工名称',
    snapshot                 string COMMENT '开门快照',
    pass_time                string COMMENT '通过时间显示时间',
    pass_direction           string COMMENT '通行方向',
    code                     string COMMENT '设备编号',
    loc_building_id          string COMMENT '建筑id',
    loc_floor_id             string COMMENT '楼层id',
    loc_space_id             string COMMENT '房间id',
    loc_unit_id              string COMMENT '点位id',
    service_area             string COMMENT '服务范围',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间'
)
PRIMARY KEY (access_record_id,device_id,day_pt)
COMMENT '门禁-门禁记录表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

-- 充电桩订单表
CREATE TABLE IF NOT EXISTS ods_charging_order_record
(
    device_id                string COMMENT '设备id',
    charging_order_number    string COMMENT '订单号',
    day_pt                   string COMMENT '日分区',
    product_id               string COMMENT '产品id',
    start_time               string COMMENT '充电开始时间',
    end_time                 string COMMENT '充电结束时间',
    total_power              DOUBLE COMMENT '总电费',
    total_elec_money         DOUBLE COMMENT '总电费金额',
    total_service_money      DOUBLE COMMENT '服务费',
    total_money              DOUBLE COMMENT '总金额',
    charging_order_status    string COMMENT '充电订单状态',
    ts                       BIGINT COMMENT '时间戳',
    tm                       string COMMENT '显示时间'
)
PRIMARY KEY (device_id,charging_order_number,day_pt)
COMMENT '充电桩-订单数据明细表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

-- 设备状态时长
CREATE TABLE IF NOT EXISTS ods_healthy_ts_day
(
    `device_id`              string  COMMENT '设备id',
    `product_id`             string  COMMENT '产品id',
    `key`                    string  COMMENT '模型属性标识符',
    status                   string  COMMENT '状态值',
    start_time               string  COMMENT '状态开始时间  时间戳',
    day_pt                   string  COMMENT '日分区',
    end_time                 string  COMMENT '状态结束时间 时间戳',
    st_tm                    string  COMMENT '状态开始时间 显示时间',
    ed_tm                    string  COMMENT '状态结束时间  显示时间',
    ts                       BIGINT  COMMENT '时间戳',
    tm                       string  COMMENT '显示时间',
    dimension_ts             BIGINT  COMMENT '维度时间戳',
    dimension_tm             string  COMMENT '维度显示时间'
)
PRIMARY KEY (device_id,product_id,`key`,status,start_time,day_pt)
COMMENT '健康率和运行状态-拉链表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "true"
);

--- ============================
-- 时序数据汇总
-- 未使用
CREATE TABLE IF NOT EXISTS ods_kv_ts_week
(
    device_id    string COMMENT '设备id',
    `key`        string COMMENT '属性标识符',
    week_pt      string COMMENT '时间分区 yyyyMMdd',
    product_id   string COMMENT '产品id',
    value        string COMMENT '属性值',
    data_type    string COMMENT '属性值数据类型',
    ts           BIGINT COMMENT '时间戳',
    tm           string COMMENT '显示时间',
    dimension_ts BIGINT COMMENT '维度时间戳',
    dimension_tm string COMMENT '维度显示时间'
)
PRIMARY KEY (device_id,`key`,week_pt)
COMMENT '设备属性-周维度表'
PARTITION BY (week_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- 批任务插入数据
CREATE TABLE IF NOT EXISTS ods_kv_ts_month
(
    device_id    string COMMENT '设备id',
    `key`          string COMMENT '属性标识符',
    month_pt     string COMMENT '月时间分区',
    product_id   string COMMENT '产品id',
    value        string COMMENT '属性值',
    data_type    string COMMENT '属性值数据类型',
    ts           BIGINT COMMENT '时间戳',
    tm           string COMMENT '显示时间',
    dimension_ts BIGINT COMMENT '维度时间戳',
    dimension_tm string COMMENT '维度显示时间'
)
PRIMARY KEY (device_id,`key`,month_pt)
COMMENT '设备属性-月维度表'
PARTITION BY (month_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- 批任务插入数据
CREATE TABLE IF NOT EXISTS ods_kv_ts_year
(
    device_id    string COMMENT '设备id',
    `key`          string COMMENT '属性标识符',
    year_pt      string COMMENT '年时间分区',
    product_id   string COMMENT '产品id',
    value        string COMMENT '属性值',
    data_type    string COMMENT '属性值数据类型',
    ts           BIGINT COMMENT '时间戳',
    tm           string COMMENT '显示时间',
    dimension_ts BIGINT COMMENT '维度时间戳',
    dimension_tm string COMMENT '维度显示时间'
)
PRIMARY KEY (device_id,`key`,year_pt)
COMMENT '设备属性-年维度表'
PARTITION BY (year_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

--- ============================
-- 产品设备信息
CREATE TABLE IF NOT EXISTS ods_devicemgr_device
(
    id                     string COMMENT '设备id',
    created_time           BIGINT COMMENT '创建事时间',
    type                   string COMMENT '设备类型',
    name                   string COMMENT '设备名称',
    label                  string COMMENT '备注',
    tenant_id              string COMMENT '租户id',
    product_id             string COMMENT '产品id',
    model_status           SMALLINT COMMENT '逻辑删除状态',
    project_id             string COMMENT '项目id',
    code                   string COMMENT '设备编码',
    parent_id              string COMMENT '父级设备id',
    group_id               string COMMENT '设备分组id',
    location_id            string COMMENT '位置id',
    update_user            string COMMENT '最后一次修改人',
    update_time            BIGINT COMMENT '最后一次修改时间',
    create_user            string COMMENT '创建人',
    biz_type               string COMMENT '子系统类型',
    display_name           string COMMENT '设备展示名字',
    driver_identifier      string COMMENT '设备驱动标识',
    service_area           string COMMENT '服务区域',
    last_connected_gateway string COMMENT '最近一次设备连接依赖的网关id'
)
PRIMARY KEY (id)
COMMENT '设备表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_devicemgr_device_attribute
(
    id             string COMMENT '主键',
    device_id      string COMMENT '设备id',
    scope          string COMMENT '属性类型',
    identifier     string COMMENT '属性标识符',
    default_attr   string COMMENT '是否默认属性',
    definition     string COMMENT '属性定义',
    create_time    string COMMENT '创建时间',
    create_user    string COMMENT '创建人',
    update_time    string COMMENT '更新时间',
    update_user    string COMMENT '更新人',
    model_status   SMALLINT COMMENT '逻辑删除状态',
    synced         string COMMENT '同步标识',
    last_update_ts BIGINT COMMENT '最新更新时间',
    enabled        string COMMENT '产品属性-启用禁用'
)
PRIMARY KEY (id)
COMMENT '设备属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_devicemgr_product
(
    id             string COMMENT '主键',
    code           string COMMENT '产品编码',
    name           string COMMENT '产品名字',
    remark         string COMMENT '备注',
    template_id    string COMMENT '物模板id',
    type           string COMMENT '产品类型',
    identifier     string COMMENT '产品标识符',
    protocol       string COMMENT '产品使用的协议',
    tenant_id      string COMMENT '租户id',
    project_id     string COMMENT '项目id',
    key_attributes string COMMENT '关键属性',
    model_status   INT COMMENT '逻辑删除标志：1-正常，11-删除',
    create_user    string COMMENT '创建人',
    update_user    string COMMENT '更新人',
    create_time    string COMMENT '创建时间',
    update_time    string COMMENT '更新时间',
    group_id       string COMMENT '分组id',
    biz_type       string COMMENT '子系统类型',
    drivers        string COMMENT '支持的驱动',
    version        string COMMENT '产品版本号-来源的物模板版本号',
    version_time   string COMMENT '产品版本时间'
)
PRIMARY KEY (id)
COMMENT '产品表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_devicemgr_product_attribute
(
    id                 string COMMENT '主键',
    identifier         string COMMENT '属性标识符',
    scope              string COMMENT '属性类型',
    product_id         string COMMENT '产品id',
    definition         string COMMENT '属性定义',
    create_time        string COMMENT '创建时间',
    create_user        string COMMENT '创建人',
    update_time        string COMMENT '更新时间',
    update_user        string COMMENT '更新人',
    model_status       SMALLINT COMMENT '逻辑删除标识',
    default_attr       string COMMENT '是否默认属性',
    synced             string COMMENT '是否已被应用到设备',
    driver_point_attrs string COMMENT '驱动点属性',
    version            string COMMENT '产品属性-来源版本号',
    enabled            string COMMENT '产品属性-启用禁用',
    grpc_sync_ts       BIGINT COMMENT '更新时间'
)
PRIMARY KEY (id)
COMMENT '产品属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

--- ============================
-- 智慧机电
CREATE TABLE IF NOT EXISTS ods_electromech_biz_type
(
    id              string COMMENT '主键',
    name            string COMMENT '模型名称',
    identifier      string COMMENT '类型标识符',
    remark          string COMMENT '备注',
    product_id      string COMMENT '产品id',
    key_attrs       string COMMENT '关键属性',
    virtual         string COMMENT '是否虚拟类型',
    group_id        string COMMENT '属性分组标识符',
    app_identifier  string COMMENT '应用标识',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建人',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新人',
    model_status    SMALLINT COMMENT '模型状态'
)
PRIMARY KEY (id)
COMMENT '智慧机电-模型表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false");

CREATE TABLE IF NOT EXISTS ods_electromech_biz_type_attr
(
    id              	string COMMENT '主键',
    name            	string COMMENT '属性名称',
    identifier      	string COMMENT '属性标识符',
    type_id          	string COMMENT '模型id',
    val               	string COMMENT '自定义属性值',
    attr_type          	string COMMENT '属性数据类型',
    attr_unit          	string COMMENT '属性单位',
    type          		string COMMENT '属性类型',
    clean_dimensions    string COMMENT '数据清洗维度',
    create_time     	string COMMENT '创建时间',
    create_user     	string COMMENT '创建人',
    update_time     	string COMMENT '更新时间',
    update_user     	string COMMENT '更新人',
    model_status    	SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '智慧机电-模型属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false");

CREATE TABLE IF NOT EXISTS ods_electromech_electromech
(
    id              string COMMENT '实例id',
    name            string COMMENT '实例名称',
    code            string COMMENT '实例标识符',
    type_id         string COMMENT '模型id',
    remark          string COMMENT '备注',
    group_id        string COMMENT '分组id',
    service_area    string COMMENT '服务区域',
    virtual         string COMMENT '是否图表模型，true为图表模型',
    parent_id       string COMMENT '父id',
    loc_building_id string COMMENT '建筑id',
    loc_floor_id    string COMMENT '楼层id',
    loc_space_id    string COMMENT '房间id',
    loc_unit_id     string COMMENT '点位id',
	app_identifier  string COMMENT '应用标识',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建者',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新者',
    model_status    SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '智慧机电-实例表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_electromech_electromech_attr
(
    id           string COMMENT '实例属性id',
    name         string COMMENT '实例属性名称',
    identifier   string COMMENT '属性标识符',
    entity_id    string COMMENT '实例id',
    dev_id       string COMMENT '设备id',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性标识符',
    attr_name    string COMMENT '设备属性名称',
    val          string COMMENT '自定义属性值',
    attr_type    string COMMENT '属性数据类型',
    attr_mode    string COMMENT '属性读写权限',
    attr_unit    string COMMENT '属性单位',
    attr_specs   string COMMENT '属性定义',
    type         string COMMENT '属性类型',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建者',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新者',
    model_status SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '智慧机电-实例属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_electromech_biz_attr_sum_relation
(
    id           string COMMENT '主键',
    attr_id      string COMMENT '实例属性id',
    dev_id       string COMMENT '设备id',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性标识',
    attr_name    string COMMENT '设备属性名称',
    sign         SMALLINT COMMENT '比例',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建者',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新者',
    model_status SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '智慧机电-累积属性关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_electromech_biz_rate_condition
(
    id           string COMMENT '主键',
    attr_id      string COMMENT '属性id',
    product_id   string COMMENT '产品id',
    attr_key     string COMMENT '属性key',
    vals         string COMMENT '设备属性期望值列表',
    model_status SMALLINT COMMENT '逻辑删除标识，1-正常，11-删除',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建人',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新人'
)
PRIMARY KEY (id)
COMMENT '智慧机电-条件属性关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_electromech_biz_rate_cond_dev_relation
(
    id           string COMMENT '属性标识符',
    condition_id string COMMENT '属性名称',
    dev_id       string COMMENT '属性名称',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '属性标识符',
    attr_name    string COMMENT '属性名称',
    model_status SMALLINT COMMENT '模型状态',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建人',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新人'
)
PRIMARY KEY (id)
COMMENT '智慧机电-条件属性设备关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

--- ============================
-- 综合管理
CREATE TABLE IF NOT EXISTS ods_integration_biz_type
(
    id              string COMMENT '主键',
    name            string COMMENT '模型名称',
    identifier      string COMMENT '类型标识符',
    remark          string COMMENT '备注',
    product_id      string COMMENT '产品id',
    key_attrs       string COMMENT '关键属性',
    virtual         string COMMENT '是否虚拟类型',
    group_id        string COMMENT '属性分组标识符',
    app_identifier  string COMMENT '应用标识',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建人',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新人',
    model_status    SMALLINT COMMENT '模型状态'
)
PRIMARY KEY (id)
COMMENT '综合管理-模型表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_integration_biz_type_attr
(
    id              	string COMMENT '主键',
    name            	string COMMENT '属性名称',
    identifier      	string COMMENT '属性标识符',
    type_id          	string COMMENT '模型id',
    val               	string COMMENT '自定义属性值',
    attr_type          	string COMMENT '属性数据类型',
    attr_unit          	string COMMENT '属性单位',
    type          		string COMMENT '属性类型',
    clean_dimensions    string COMMENT '数据清洗维度',
    create_time     	string COMMENT '创建时间',
    create_user     	string COMMENT '创建人',
    update_time     	string COMMENT '更新时间',
    update_user     	string COMMENT '更新人',
    model_status    	SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '综合管理-模型属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_integration_integration
(
    id              string COMMENT '实例id',
    name            string COMMENT '实例名称',
    code            string COMMENT '实例标识符',
    type_id         string COMMENT '模型id',
    remark          string COMMENT '备注',
    group_id        string COMMENT '分组id',
    service_area    string COMMENT '服务区域',
    virtual         string COMMENT '是否图表模型，true为图表模型',
    parent_id       string COMMENT '父id',
    loc_building_id string COMMENT '建筑id',
    loc_floor_id    string COMMENT '楼层id',
    loc_space_id    string COMMENT '房间id',
    loc_unit_id     string COMMENT '点位id',
    room_type_id    string COMMENT '房型id',
	app_identifier  string COMMENT '应用标识',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建者',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新者',
    model_status    SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '综合管理-模型实例表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_integration_integration_attr
(
    id           string COMMENT '实例属性id',
    name         string COMMENT '实例属性名称',
    identifier   string COMMENT '属性标识符',
    entity_id    string COMMENT '实例id',
    dev_id       string COMMENT '设备id',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性标识符',
    attr_name    string COMMENT '设备属性名称',
    val          string COMMENT '自定义属性值',
    attr_type    string COMMENT '属性数据类型',
    attr_mode    string COMMENT '属性读写权限',
    attr_unit    string COMMENT '属性单位',
    attr_specs   string COMMENT '属性定义',
    type         string COMMENT '属性类型',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建者',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新者',
    model_status SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '综合管理-实例属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_integration_biz_attr_sum_relation
(
    id           string COMMENT '主键',
    attr_id      string COMMENT '实例属性id',
    dev_id       string COMMENT '设备id',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性标识',
    attr_name    string COMMENT '设备属性名称',
    sign         SMALLINT COMMENT '比例',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建者',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新者',
    model_status SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '综合管理-累积属性关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_integration_biz_rate_condition
(
    id           string COMMENT '主键',
    attr_id      string COMMENT '属性id',
    product_id   string COMMENT '产品id',
    attr_key     string COMMENT '属性key',
    vals         string COMMENT '设备属性期望值列表',
    model_status SMALLINT COMMENT '逻辑删除标识，1-正常，11-删除',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建人',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新人'
)
PRIMARY KEY (id)
COMMENT '综合管理-条件属性关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_integration_biz_rate_cond_dev_relation
(
    id           string COMMENT '属性标识符',
    condition_id string COMMENT '属性名称',
    dev_id       string COMMENT '属性名称',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '属性标识符',
    attr_name    string COMMENT '属性名称',
    model_status SMALLINT COMMENT '模型状态',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建人',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新人'
)
PRIMARY KEY (id)
COMMENT '综合管理-条件属性设备关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

--- ============================
-- 通行管理
CREATE TABLE IF NOT EXISTS ods_itraffic_biz_type
(
    id              string COMMENT '主键',
    name            string COMMENT '模型名称',
    identifier      string COMMENT '类型标识符',
    remark          string COMMENT '备注',
    product_id      string COMMENT '产品id',
    key_attrs       string COMMENT '关键属性',
    virtual         string COMMENT '是否虚拟类型',
    group_id        string COMMENT '属性分组标识符',
    app_identifier  string COMMENT '应用标识',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建人',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新人',
    model_status    SMALLINT COMMENT '模型状态'
)
PRIMARY KEY (id)
COMMENT '通行管理-模型表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_itraffic_biz_type_attr
(
    id              	string COMMENT '主键',
    name            	string COMMENT '属性名称',
    identifier      	string COMMENT '属性标识符',
    type_id          	string COMMENT '模型id',
    val               	string COMMENT '自定义属性值',
    attr_type          	string COMMENT '属性数据类型',
    attr_unit          	string COMMENT '属性单位',
    type          		string COMMENT '属性类型',
    clean_dimensions    string COMMENT '数据清洗维度',
    create_time     	string COMMENT '创建时间',
    create_user     	string COMMENT '创建人',
    update_time     	string COMMENT '更新时间',
    update_user     	string COMMENT '更新人',
    model_status    	SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '通行管理-模型属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_itraffic_itraffic
(
    id              string COMMENT '实例id',
    name            string COMMENT '实例名称',
    code            string COMMENT '实例标识符',
    type_id         string COMMENT '模型id',
    remark          string COMMENT '备注',
    group_id        string COMMENT '分组id',
    service_area    string COMMENT '服务区域',
    virtual         string COMMENT '是否图表模型，true为图表模型',
    parent_id       string COMMENT '父id',
    loc_building_id string COMMENT '建筑id',
    loc_floor_id    string COMMENT '楼层id',
    loc_space_id    string COMMENT '房间id',
    loc_unit_id     string COMMENT '点位id',
	app_identifier  string COMMENT '应用标识',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建者',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新者',
    model_status    SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '通行管理-实例表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_itraffic_itraffic_attr
(
    id           string COMMENT '实例属性id',
    name         string COMMENT '实例属性名称',
    identifier   string COMMENT '属性标识符',
    entity_id    string COMMENT '实例id',
    dev_id       string COMMENT '设备id',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性标识符',
    attr_name    string COMMENT '设备属性名称',
    val          string COMMENT '自定义属性值',
    attr_type    string COMMENT '属性数据类型',
    attr_mode    string COMMENT '属性读写权限',
    attr_unit    string COMMENT '属性单位',
    attr_specs   string COMMENT '属性定义',
    type         string COMMENT '属性类型',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建者',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新者',
    model_status SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '通行管理-实例属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_itraffic_biz_attr_sum_relation
(
    id           string COMMENT '主键',
    attr_id      string COMMENT '实例属性id',
    dev_id       string COMMENT '设备id',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性标识',
    attr_name    string COMMENT '设备属性名称',
    sign         SMALLINT COMMENT '比例',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建者',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新者',
    model_status SMALLINT COMMENT '逻辑删，1存在，11删除'
)
PRIMARY KEY (id)
COMMENT '通行管理-累积属性关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_itraffic_biz_rate_condition
(
    id           string COMMENT '主键',
    attr_id      string COMMENT '属性id',
    product_id   string COMMENT '产品id',
    attr_key     string COMMENT '属性key',
    vals         string COMMENT '设备属性期望值列表',
    model_status SMALLINT COMMENT '逻辑删除标识，1-正常，11-删除',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建人',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新人'
)
PRIMARY KEY (id)
COMMENT '通行管理-条件属性关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_itraffic_biz_rate_cond_dev_relation
(
    id           string COMMENT '属性标识符',
    condition_id string COMMENT '属性名称',
    dev_id       string COMMENT '属性名称',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '属性标识符',
    attr_name    string COMMENT '属性名称',
    model_status SMALLINT COMMENT '模型状态',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建人',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新人'
)
PRIMARY KEY (id)
COMMENT '通行管理-条件属性设备关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

--- ============================
-- 智慧能源
CREATE TABLE IF NOT EXISTS ods_energy_biz_type
(
    id              string COMMENT '主键',
    name            string COMMENT '模型名称',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建人',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新人',
    model_status    SMALLINT COMMENT '模型状态',
    identifier      string COMMENT '类型标识符',
    remark          string COMMENT '备注',
    product_id      string COMMENT '产品id',
    key_attrs       string COMMENT '关键属性',
    virtual         string COMMENT '是否虚拟类型',
    group_id        string COMMENT '属性分组标识符',
    app_identifier  string COMMENT '应用标识'
)
PRIMARY KEY (id)
COMMENT '智慧能源-模型表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false");

CREATE TABLE IF NOT EXISTS ods_energy_biz_type_attr
(
    id              	string COMMENT '主键',
    name            	string COMMENT '属性名称',
    identifier      	string COMMENT '属性标识符',
    type_id          	string COMMENT '模型id',
    create_time     	string COMMENT '创建时间',
    create_user     	string COMMENT '创建人',
    update_time     	string COMMENT '更新时间',
    update_user     	string COMMENT '更新人',
    model_status    	SMALLINT COMMENT '逻辑删，1存在，11删除',
    val               	string COMMENT '自定义属性值',
    attr_type          	string COMMENT '属性数据类型',
    column_key          string COMMENT '',
    attr_unit          	string COMMENT '属性单位',
    type          		string COMMENT '属性类型',
    clean_dimensions    string COMMENT '数据清洗维度',
    group_identifier    string COMMENT '属性分组标识符'
)
PRIMARY KEY (id)
COMMENT '智慧能源-模型属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false");

CREATE TABLE IF NOT EXISTS ods_energy_energy
(
    id              string COMMENT '实例id',
    name            string COMMENT '实例名称',
    type_id         string COMMENT '模型id',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建者',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新者',
    model_status    SMALLINT COMMENT '逻辑删，1存在，11删除',
    device_id       string COMMENT '设备id',
    type_identifier string COMMENT '模型标识符',
    remark          string COMMENT '备注',
    group_id        string COMMENT '分组id',
    service_area    string COMMENT '服务区域',
    code            string COMMENT '实例标识符',
    parent_id       string COMMENT '父id',
    virtual         string COMMENT '是否图表模型，true为图表模型',
    loc_floor_id    string COMMENT '楼层id',
    loc_space_id    string COMMENT '房间id',
    loc_unit_id     string COMMENT '点位id',
    loc_building_id string COMMENT '建筑id',
    app_identifier  string COMMENT '应用标识'
)
PRIMARY KEY (id)
COMMENT '智慧能源-实例表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_energy_attr
(
    id           string COMMENT '实例属性id',
    name         string COMMENT '实例属性名称',
    identifier   string COMMENT '属性标识符',
    entity_id    string COMMENT '实例id',
    dev_id       string COMMENT '设备id',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性标识符',
    attr_name    string COMMENT '设备属性名称',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建者',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新者',
    model_status SMALLINT COMMENT '逻辑删，1存在，11删除',
    val          string COMMENT '自定义属性值',
    attr_type    string COMMENT '属性数据类型',
    attr_mode    string COMMENT '属性读写权限',
    attr_unit    string COMMENT '属性单位',
    attr_specs   string COMMENT '属性定义',
    column_key   string COMMENT '列key',
    product_id   string COMMENT '产品id',
    type         string COMMENT '属性类型'
)
PRIMARY KEY (id)
COMMENT '智慧能源-实例属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_biz_attr_sum_relation
(
    id           string COMMENT '主键',
    attr_id      string COMMENT '实例属性id',
    dev_id       string COMMENT '设备id',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性标识',
    attr_name    string COMMENT '设备属性名称',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建者',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新者',
    model_status SMALLINT COMMENT '逻辑删，1存在，11删除',
    sign         SMALLINT COMMENT '比例'
)
PRIMARY KEY (id)
COMMENT '智慧能源-累积属性关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_biz_rate_condition
(
    id           string COMMENT '主键',
    vals         string COMMENT '设备属性期望值列表',
    model_status SMALLINT COMMENT '逻辑删除标识，1-正常，11-删除',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建人',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新人',
    attr_id      string COMMENT '属性id',
    product_id   string COMMENT '产品id',
    attr_key     string COMMENT '属性key'
)
PRIMARY KEY (id)
COMMENT '智慧能源-条件属性关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_biz_rate_cond_dev_relation
(
    id           string COMMENT '属性标识符',
    condition_id string COMMENT '属性名称',
    dev_id       string COMMENT '属性名称',
    create_time  string COMMENT '创建时间',
    create_user  string COMMENT '创建人',
    update_time  string COMMENT '更新时间',
    update_user  string COMMENT '更新人',
    model_status SMALLINT COMMENT '模型状态',
    dev_name     string COMMENT '设备名称',
    attr_key     string COMMENT '属性标识符',
    attr_name    string COMMENT '属性名称'
)
PRIMARY KEY (id)
COMMENT '智慧能源-条件属性设备关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
-- 能源-能耗模型
CREATE TABLE IF NOT EXISTS ods_energy_energy_node_device_relation
(
    id          	string COMMENT '主键',
    node_id     	string COMMENT '节点id',
    model_type  	string COMMENT '类型标识符',
    dev_id      	string COMMENT '设备id',
    attr_key    	string COMMENT '设备属性',
    coefficient 	string COMMENT '系数',
    bottom_node_id  string COMMENT '直接绑定设备的底层能耗模型节点id'
)
PRIMARY KEY (id)
COMMENT '能源-能耗模型-节点设备关联关系表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_time_dimension
(
    granularity string COMMENT '表类型',
    ts_diff     BIGINT COMMENT '递增时间刻度'
)
COMMENT '能源-能耗模型-时间码表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
-- 能源-节能诊断-设备行为诊断
CREATE TABLE IF NOT EXISTS ods_energy_behavior_diagnostic
(
    id              string COMMENT 'id',
    name            string COMMENT '诊断名字',
    product_id      string COMMENT '产品id',
    group_id        string COMMENT '分组id',
    alarm_level     string COMMENT '告警级别',
    notify_period   string COMMENT '通知周期',
    user_ids        string COMMENT '通知人id列表',
    enabled         boolean COMMENT '启用标识，true-启用，false-禁用',
    create_user     string COMMENT '创建人id',
    create_time     string COMMENT '创建时间',
    update_user     string COMMENT '更新人id',
    update_time     string COMMENT '更新时间',
    project_id      string COMMENT '项目id',
    tenant_id       string COMMENT '租户id',
    model_status    int COMMENT '逻辑删除标识，1-正常，11-删除',
    template_ids    string COMMENT '通知模板id'
)
PRIMARY KEY (id)
COMMENT '能源-节能诊断-设备行为诊断-诊断配置主表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_behavior_diagnostic_device
(
    id              string COMMENT 'id',
    diagnostic_id   string COMMENT '诊断配置id',
    device_id       string COMMENT '设备id',
    device_name     string COMMENT '设备名称',
    sort            string ,
    create_user     string COMMENT '创建人id',
    create_time     string COMMENT '创建时间',
    update_user     string COMMENT '更新人id',
    update_time     string COMMENT '更新时间',
    model_status    string COMMENT '逻辑删除标识，1-正常，11-删除'
)
PRIMARY KEY (id)
COMMENT '能源-节能诊断-设备行为诊断-诊断配置设备关联表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_behavior_diagnostic_group
(
    id              string COMMENT 'id',
    name            string COMMENT '分组名称',
    parent_id       string COMMENT '父级分组id',
    create_user     string COMMENT '创建人id',
    create_time     string COMMENT '创建时间',
    update_user     string COMMENT '更新人id',
    update_time     string COMMENT '更新时间',
    project_id      string COMMENT '项目id',
    tenant_id       string COMMENT '租户id',
    model_status    string COMMENT '逻辑删除标识，1-正常，11-删除'
)
PRIMARY KEY (id)
COMMENT '能源-节能诊断-设备行为诊断-行为诊断分组'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_behavior_diagnostic_runtime
(
    id              string COMMENT 'id',
    diagnostic_id   string COMMENT '诊断配置id',
    start_date      string COMMENT '起始时间 yyyy-MM-dd',
	end_date        string COMMENT '截止时间 yyyy-MM-dd',
    days_of_week    string COMMENT '有效日期',
	sort            string COMMENT '排序',
	create_user     string COMMENT '创建人id',
	create_time     string COMMENT '创建时间',
	update_user     string COMMENT '更新人id',
	update_time     string COMMENT '更新时间',
	model_status    int COMMENT '逻辑删除标识，1-正常，11-删除'
)
PRIMARY KEY (id)
COMMENT '能源-节能诊断-设备行为诊断-规则时间表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_behavior_runtime_valid_times
(
    id              string COMMENT 'id',
    runtime_id      string COMMENT '规则时间表id',
    start_time      string COMMENT '开始时间 hh:mm',
    end_time        string COMMENT '结束时间 hh:mm',
	sort            string COMMENT '排序',
	create_user     string COMMENT '创建人id',
	create_time     string COMMENT '创建时间',
	update_user     string COMMENT '更新人id',
	update_time     string COMMENT '更新时间',
	model_status    int COMMENT '逻辑删除标识，1-正常，11-删除'
)
PRIMARY KEY (id)
COMMENT '能源-节能诊断-设备行为诊断-规则时间表-明细开始结束时间'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_behavior_diagnostic_device_runtime_conf
(
    diagnostic_id       string COMMENT '规则id',
    device_id           string COMMENT '设备id',
	start_data          string COMMENT '开始日期 yyyyMMdd',
	start_time          string COMMENT '开始时间 HH:mm',
    days_of_week        string COMMENT '有效日期',
    diagnostic_name     string COMMENT '规则名称',
	alarm_level         string COMMENT '告警级别',
    device_name         string COMMENT '设备名称',
	project_id          string COMMENT '项目id',
    tenant_id           string COMMENT '租户id',
    end_data            string COMMENT '结束日期 yyyyMMdd',
    end_time            string COMMENT '结束时间 HH:mm',
	is_run              int COMMENT '设定是否运行 1运行 0 停机',
	set_run_time        string COMMENT '设定运行时间',
	set_stop_time       string COMMENT '设定停止时间',
    question_content    string COMMENT '问题描述',
    advice_content      string COMMENT '专家建议',
    model_status        int COMMENT '是否在用 1在用 0不在用'
)
PRIMARY KEY (diagnostic_id,device_id,start_data,start_time,days_of_week)
COMMENT '能源-节能诊断-设备行为诊断-规则设备时间加工表'
DISTRIBUTED BY HASH(diagnostic_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
-- 能源-能源预测
CREATE TABLE IF NOT EXISTS ods_energy_energy_model_node
(
    id              string COMMENT '节点id',
    name            string COMMENT '节点名称',
    tenant_id       string COMMENT '租户id',
    project_id      string COMMENT '项目id',
    create_time     string COMMENT '创建时间',
    create_user     string COMMENT '创建人',
    update_time     string COMMENT '更新时间',
    update_user     string COMMENT '更新人',
    model_status    int COMMENT '逻辑删除字段 11删除 1在用',
    code            string COMMENT '统一编码'
)
PRIMARY KEY (id)
COMMENT '能源-能源预测-能耗节点表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_energy_node_forecast_model_mid
(
    id                      string COMMENT 'id',
    node_id                 string COMMENT '能耗模型节点id',
    status                  boolean COMMENT '能耗模型预测开启状态',
    forecast_model_name     string COMMENT '预测模型名称',
    first_train_date        string COMMENT '训练开始日期 yyyy-MM-dd HH:mm:ss',
    min_train_days          int COMMENT '最小训练周期',
    create_time             string COMMENT '创建时间',
    create_user             string COMMENT '创建人',
    update_time             string COMMENT '更新时间',
    update_user             string COMMENT '更新人',
    model_status            int COMMENT '逻辑删除字段 11删除 1在用',
    tenant_id               string COMMENT '租户id',
    project_id              string COMMENT '项目id'
)
PRIMARY KEY (id)
COMMENT '能源-能耗模型-AI预测模型配置表-中间表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);


CREATE TABLE IF NOT EXISTS ods_energy_energy_node_forecast_model
(
    id                      string COMMENT 'id',
    node_id                 string COMMENT '能耗模型节点id',
    node_name               string COMMENT '能耗模型节点名称',
    status                  boolean COMMENT '能耗模型预测开启状态',
    forecast_model_name     string COMMENT '预测模型名称',
    first_train_date        string COMMENT '训练开始日期 yyyy-MM-dd',
    min_train_days          int COMMENT '最小训练周期',
    create_time             string COMMENT '创建时间',
    create_user             string COMMENT '创建人',
    update_time             string COMMENT '更新时间',
    update_user             string COMMENT '更新人',
    model_status            int COMMENT '逻辑删除字段 11删除 1在用',
    tenant_id               string COMMENT '租户id',
    project_id              string COMMENT '项目id',
    first_train_time        string COMMENT '模型训练对应业务更新时间'
)
PRIMARY KEY (id,node_id)
COMMENT '能源-能耗模型-AI预测模型配置表'
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_energy_energy_prediction_device_conf
(
    node_id             string COMMENT '节点id' ,
    device_id           string COMMENT '设备id' ,
    attr_key            string COMMENT '设备属性' ,
    node_name           string COMMENT '节点名称' ,
    device_name         string COMMENT '设备名称' ,
    check_val           double comment '1小时上限',
    model_type          string comment '类型标识符 Electricity 电',
    first_train_date    string comment '模型训练开始日期 yyyyMMdd',
    coefficient         string comment '系数 '
)
PRIMARY KEY (node_id,device_id,attr_key)
COMMENT '能源-能源预测-统计设备配置表'
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

--- ============================
-- 全应用-业务维度清洗信息汇总表
CREATE TABLE IF NOT EXISTS ods_biz_info
(
    clean_dimensions       string COMMENT '模型属性清洗维度',
    clean_dimensions_type  string COMMENT '模型属性清洗维度类型',
    app_identifier         string COMMENT '应用标识',
    type_identifier        string COMMENT '模型标识符',
    attr_identifier        string COMMENT '属性标识符',
    entity_id              string COMMENT '实例id',
    device_id              string COMMENT '设备id',
    device_attr            string COMMENT '统计设备属性',
    type_name              string COMMENT '模型名称',
    entity_name            string COMMENT '实例名称',
    entity_code            string COMMENT '实例编码',
    attr_name              string COMMENT '模型属性名称',
    condition_value        string COMMENT '条件属性-统计值',
    sign                   int    COMMENT '累积属性-比例',
    attr_val               string COMMENT '属性-自定义值'
)
DUPLICATE KEY (clean_dimensions,clean_dimensions_type)
COMMENT '全应用-业务维度清洗信息汇总表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);
--- ============================
-- 监控
CREATE TABLE IF NOT EXISTS ods_quality_app_iotmonitor_rule
(
    id                  string COMMENT '主键',
    product_id          string COMMENT '产品id',
    product_name        string COMMENT '产品名称',
    attr_key            string COMMENT '属性key',
    attr_name           string COMMENT '属性名称',
    project_id          string COMMENT '项目ID',
    tenant_id           string COMMENT '租户ID',
    model_status        SMALLINT COMMENT '逻辑删，1存在，11删除',
    create_user         string COMMENT '创建用户id',
    create_time         string COMMENT '创建时间',
    update_user         string COMMENT '更新用户id',
    update_time         string COMMENT '更新时间'
)
PRIMARY KEY (id)
COMMENT '数据中台监控项表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS ods_quality_app_iotmonitor_rule_device_relation
(
    id                  string COMMENT '主键',
    rule_id             string COMMENT '监控项id',
    device_id           string COMMENT '设备id',
    device_name         string COMMENT '设备名称'
)
COMMENT '数据中台监控项-设备明细表表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);


--  ====================   dwm建表语句 ======================================================
-- 健康率
CREATE TABLE IF NOT EXISTS dwm_healthy_ts_day
(
    biz_type       string COMMENT '业务类型',
    ts             BIGINT COMMENT '时间戳',
    day_pt         string COMMENT '天分区',
    total_count    INT COMMENT '设备总数',
    unhealth_count INT COMMENT '故障数量',
    tm             string COMMENT '显示时间'
)
PRIMARY KEY (biz_type,ts,day_pt)
COMMENT '健康率-天维度统计'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(biz_type)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
--  故障时长
CREATE TABLE IF NOT EXISTS dwm_healthy_top_ts_month
(
    device_id     string COMMENT '设备id',
    unhealth_time DOUBLE COMMENT '故障时长 小时'
)
COMMENT '设备故障时长统计-月表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_healthy_ts_month
(
    month_pt      string COMMENT '月分区',
    device_id     string COMMENT '设备id',
    unhealth_time DOUBLE COMMENT '故障时长 小时'
)
PRIMARY KEY (month_pt,device_id)
COMMENT '设备故障时长统计-月表'
PARTITION BY (month_pt)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_healthy_top_ts_year
(
    device_id     string COMMENT '设备id',
    unhealth_time DOUBLE COMMENT '故障时长 小时'
)
COMMENT '设备故障时长统计-年表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
--  公共清洗加工表
CREATE TABLE IF NOT EXISTS dwm_model_kv_ts_quarter_hour
(
    day_pt                     string COMMENT '日分区',
    app_type                   string COMMENT '应用标识符',
    entity_id                  string COMMENT '实例id',
    ts                         BIGINT COMMENT '维度时间戳',
    attr_key                   string COMMENT '属性标识符',
    attr_val                   string COMMENT '属性值',
    tm                         string COMMENT '维度显示时间'
)
PRIMARY KEY (day_pt,app_type,entity_id,ts,attr_key)
COMMENT '公共清洗加工-15分钟维度表'
PARTITION BY (day_pt,app_type)
DISTRIBUTED BY HASH(entity_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_model_kv_ts_one_hour
(
    day_pt                     string COMMENT '日分区',
    app_type                   string COMMENT '应用标识符',
    entity_id                  string COMMENT '实例id',
    ts                         BIGINT COMMENT '维度时间戳',
    attr_key                   string COMMENT '属性标识符',
    attr_val                   string COMMENT '属性值',
    tm                         string COMMENT '维度显示时间'
)
PRIMARY KEY (day_pt,app_type,entity_id,ts,attr_key)
COMMENT '公共清洗加工-1小时维度表'
PARTITION BY (day_pt,app_type)
DISTRIBUTED BY HASH(entity_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_model_kv_ts_day
(
    day_pt                     string COMMENT '日分区',
    app_type                   string COMMENT '应用标识符',
    entity_id                  string COMMENT '实例id',
    ts                         BIGINT COMMENT '维度时间戳',
    attr_key                   string COMMENT '属性标识符',
    attr_val                   string COMMENT '属性值',
    tm                         string COMMENT '维度显示时间'
)
PRIMARY KEY (day_pt,app_type,entity_id,ts,attr_key)
COMMENT '公共清洗加工-1天维度表'
PARTITION BY (day_pt,app_type)
DISTRIBUTED BY HASH(entity_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_model_kv_ts_month
(
    month_pt                   string COMMENT '月分区',
    app_type                   string COMMENT '应用标识符',
    entity_id                  string COMMENT '实例id',
    ts                         BIGINT COMMENT '维度时间戳',
    attr_key                   string COMMENT '属性标识符',
    attr_val                   string COMMENT '属性值',
    tm                         string COMMENT '维度显示时间'
)
PRIMARY KEY (month_pt,app_type,entity_id,ts,attr_key)
COMMENT '公共清洗加工-1月维度表'
PARTITION BY (month_pt,app_type)
DISTRIBUTED BY HASH(entity_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_model_kv_ts_year
(
    year_pt                    string COMMENT '年分区',
    app_type                   string COMMENT '应用标识符',
    entity_id                  string COMMENT '实例id',
    ts                         BIGINT COMMENT '维度时间戳',
    attr_key                   string COMMENT '属性标识符',
    attr_val                   string COMMENT '属性值',
    tm                         string COMMENT '维度显示时间'
)
PRIMARY KEY (year_pt,app_type,entity_id,ts,attr_key)
COMMENT '公共清洗加工-1年维度表'
PARTITION BY (year_pt,app_type)
DISTRIBUTED BY HASH(entity_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
-- 充电桩
CREATE TABLE IF NOT EXISTS dwm_charging_order_record_ts_day
(
    `time`                  string COMMENT '查询时间 yyyy-MM-dd',
    day_pt                  string COMMENT '天分区',
    tenant_id               string COMMENT '租户id',
    project_id              string COMMENT '项目id',
    total_power             DOUBLE COMMENT '总充电量',
    total_money             DOUBLE COMMENT '总费用',
    elec_money              DOUBLE COMMENT '总电量',
    service_money           DOUBLE COMMENT '总服务费',
    charge_count            INT COMMENT '充电次数',
    charge_duration         INT COMMENT '充电时长 秒',
    average_charge_duration DOUBLE COMMENT '平均充电时长 秒',
    ts                      BIGINT COMMENT '维度时间戳',
    tm                      string COMMENT '维度显示时间'
)
PRIMARY KEY (`time`,day_pt)
COMMENT '充电桩-数据清洗统计-天表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(`time`)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_charging_order_record_ts_month
(
    `time`                  string COMMENT '查询时间 yyyy-MM',
    month_pt                string COMMENT '月分区',
    tenant_id               string COMMENT '租户id',
    project_id              string COMMENT '项目id',
    total_power             DOUBLE COMMENT '总充电量',
    total_money             DOUBLE COMMENT '总费用',
    elec_money              DOUBLE COMMENT '总电量',
    service_money           DOUBLE COMMENT '总服务费',
    charge_count            BIGINT COMMENT '充电次数',
    charge_duration         BIGINT COMMENT '充电时长',
    average_charge_duration DOUBLE COMMENT '平均充电时长',
    ts                      BIGINT COMMENT '维度时间戳',
    tm                      string COMMENT '维度显示时间'
)
PRIMARY KEY (`time`,month_pt)
COMMENT '充电桩-数据清洗统计-月表'
PARTITION BY (month_pt)
DISTRIBUTED BY HASH(`time`)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_charging_order_record_ts_year
(
    `time`                  string COMMENT '查询时间 yyyy',
    year_pt                 string COMMENT '年分区',
    tenant_id               string COMMENT '租户id',
    project_id              string COMMENT '项目id',
    total_power             DOUBLE COMMENT '总充电量',
    total_money             DOUBLE COMMENT '总费用',
    elec_money              DOUBLE COMMENT '总电量',
    service_money           DOUBLE COMMENT '总服务费',
    charge_count            BIGINT COMMENT '充电次数',
    charge_duration         BIGINT COMMENT '充电时长',
    average_charge_duration DOUBLE COMMENT '平均充电时长',
    ts                      BIGINT COMMENT '维度时间戳',
    tm                      string COMMENT '维度显示时间'
)
PRIMARY KEY (`time`,year_pt)
COMMENT '充电桩-数据清洗统计-年表'
PARTITION BY (year_pt)
DISTRIBUTED BY HASH(`time`)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
CREATE TABLE IF NOT EXISTS dwm_quality_app_iotmonitor_rule_detail_count_ts_one_hour
(
    rule_id        string COMMENT '监控项id',
    device_id      string COMMENT '设备id',
	attr_key       string COMMENT '属性key',
    dimension_ts   BIGINT COMMENT '维度',
    day_pt         string COMMENT '天分区',
    device_name    string COMMENT '设备name',
	product_id     string COMMENT '产品id',
    product_name   string COMMENT '产品名称',
    attr_name      string COMMENT '属性名称',
    data_count     BIGINT COMMENT '每小时数据条数',
    dimension_tm   string COMMENT '维度'
)
PRIMARY KEY (rule_id,device_id,attr_key,dimension_ts,day_pt)
COMMENT '数据中台-统计监控项设备数据条数-小时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(rule_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_quality_app_iotmonitor_rule_count_ts_one_hour
(
    rule_id                   string COMMENT '监控项id',
    dimension_ts              BIGINT COMMENT '维度',
    day_pt                    string COMMENT '天分区',
    data_count                BIGINT COMMENT '每小时数据条数',
    abnormal_device_count     BIGINT COMMENT '每小时异常设备数量',
    abnormal_devices          string COMMENT '每小时未上报设备名称（逗号拼接）',
	product_id                string COMMENT '产品id',
    product_name              string COMMENT '产品名称',
	attr_key                  string COMMENT '属性key',
    attr_name                 string COMMENT '属性名称',
    dimension_tm              string COMMENT '维度'
)
PRIMARY KEY (rule_id,dimension_ts,day_pt)
COMMENT '数据中台-统计监控项结果-小时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(rule_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_bdmm_data_clean_rule
(
    product_id   string COMMENT '产品id',
    product_key  string COMMENT '产品key',
    product_name string COMMENT '产品名称',
    device_id    string COMMENT '设备id',
    device_name  string COMMENT '设备名称',
    attr_key     string COMMENT '设备属性key',
    attr_name    string COMMENT '设备属性名称',
    attr_type    string COMMENT '设备属性值原始数据类型',
    attr_unit    string COMMENT '设备属性值数据单位',
    data_type    string COMMENT '设备属性值存储数据类型',
    time_rule    string COMMENT '时间存储维度',
    stat_time    string COMMENT '统计时间',
    project_id   string COMMENT '项目id',
    tenant_id    string COMMENT '租户id'
)
COMMENT '数据中他-清洗产品设备属性表'
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
-- 能源 - 能耗模型
CREATE TABLE IF NOT EXISTS dwm_energy_energytreenode_ts_quarter_hour
(
    node_id         string COMMENT '节点id',
    day_pt          string COMMENT '日分区',
    min_15_pt       string COMMENT '15min分区',
    electricity_kwh DOUBLE COMMENT '电',
    water_m3        DOUBLE COMMENT '水',
    gas_m3          DOUBLE COMMENT '气',
    ts              BIGINT COMMENT '维度时间戳',
    tm              string COMMENT '维度显示时间'
)
PRIMARY KEY (node_id,day_pt,min_15_pt)
COMMENT '能源-能耗模型-15分钟表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_energytreenode_ts_half_hour
(
    node_id         string COMMENT '节点id',
    day_pt          string COMMENT '日分区',
    min_30_pt       string COMMENT '半小时分区',
    electricity_kwh DOUBLE COMMENT '电',
    water_m3        DOUBLE COMMENT '水',
    gas_m3          DOUBLE COMMENT '气',
    ts              BIGINT COMMENT '维度时间戳',
    tm              string COMMENT '维度显示时间'
)
PRIMARY KEY (node_id,day_pt,min_30_pt)
COMMENT '能源-能耗模型-30分钟表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_energytreenode_ts_one_hour
(
    node_id         string COMMENT '节点id',
    day_pt          string COMMENT '日分区',
    hour_pt         string COMMENT '小时分区',
    electricity_kwh DOUBLE COMMENT '电',
    water_m3        DOUBLE COMMENT '水',
    gas_m3          DOUBLE COMMENT '气',
    ts              BIGINT COMMENT '维度时间戳',
    tm              string COMMENT '维度显示时间'
)
PRIMARY KEY (node_id,day_pt,hour_pt)
COMMENT '能源-能耗模型-小时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_energytreenode_ts_day
(
    node_id         string COMMENT '节点id',
    day_pt          string COMMENT '天分区',
    electricity_kwh DOUBLE COMMENT '电',
    water_m3        DOUBLE COMMENT '水',
    gas_m3          DOUBLE COMMENT '气',
    ts              BIGINT COMMENT '维度时间戳',
    tm              string COMMENT '维度显示时间'
)
PRIMARY KEY (node_id,day_pt)
COMMENT '能源-能耗模型-天表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_energytreenode_ts_week
(
    node_id         string COMMENT '节点id',
    week_pt         string COMMENT '周分区',
    electricity_kwh DOUBLE COMMENT '电',
    water_m3        DOUBLE COMMENT '水',
    gas_m3          DOUBLE COMMENT '气',
    ts              BIGINT COMMENT '维度时间戳',
    tm              string COMMENT '维度显示时间'
)
PRIMARY KEY (node_id,week_pt)
COMMENT '能源-能耗模型-周表'
PARTITION BY (week_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_energytreenode_ts_month
(
    node_id         string COMMENT '节点id',
    month_pt        string COMMENT '月分区',
    electricity_kwh DOUBLE COMMENT '电',
    water_m3        DOUBLE COMMENT '水',
    gas_m3          DOUBLE COMMENT '气',
    ts              BIGINT COMMENT '维度时间戳',
    tm              string COMMENT '维度显示时间'
)
PRIMARY KEY (node_id,month_pt)
COMMENT '能源-能耗模型-月表'
PARTITION BY (month_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_energytreenode_ts_year
(
    node_id         string COMMENT '节点id',
    year_pt         string COMMENT '年分区',
    electricity_kwh DOUBLE COMMENT '电',
    water_m3        DOUBLE COMMENT '水',
    gas_m3          DOUBLE COMMENT '气',
    ts              BIGINT COMMENT '维度时间戳',
    tm              string COMMENT '维度显示时间'
)
PRIMARY KEY (node_id,year_pt)
COMMENT '能源-能耗模型-年表'
PARTITION BY (year_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_abnormal_data_ts_day
(
    device_id    string COMMENT '设备id',
    day_pt       string COMMENT '天分区',
    ts           BIGINT COMMENT '时间戳',
    tm           string COMMENT '显示时间',
    abnormal_val DOUBLE COMMENT '异常值'
)
PRIMARY KEY (device_id,day_pt,ts)
COMMENT '能源-能耗模型异常数据-天表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
-- 能源-节能诊断-设备行为诊断
CREATE TABLE IF NOT EXISTS dwm_energy_behavior_diagnostic_result_detail_ts_day
(
	diagnostic_id       string COMMENT '规则id',
    day_pt              string COMMENT '天分区',
    device_id           string COMMENT '设备id',
    diag_time           string COMMENT '诊断时间 yyyy-MM-dd HH:mm:ss',
    diagnostic_name     string COMMENT '规则名称',
	alarm_level         string COMMENT '告警级别',
    device_name         string COMMENT '设备名称',
	project_id          string COMMENT '项目id',
    tenant_id           string COMMENT '租户id',
    set_run_time        string COMMENT '设定运行时间',
    set_stop_time       string COMMENT '设定停止时间',
    diag_value          string COMMENT '设备值',
    is_normal           boolean COMMENT '是否符合规则',
	question_content    string COMMENT '问题描述',
    advice_content      string COMMENT '专家建议'
)
PRIMARY KEY (diagnostic_id,day_pt,device_id,diag_time)
COMMENT '能源-节能诊断-设备行为诊断结果明细-天表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(diagnostic_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_behavior_diagnostic_result_display_detail_ts_day
(
	diagnostic_id       string COMMENT '规则id',
    device_id           string COMMENT '设备id',
    day_pt              string COMMENT '日分区yyyyMMdd',
    diagnostic_name     string COMMENT '规则名称',
    device_name         string COMMENT '设备名称',
    diag_result         string COMMENT '诊断结果',
    set_run_time        string COMMENT '设定运行时间',
    set_stop_time       string COMMENT '设定停止时间'
)
PRIMARY KEY (diagnostic_id,device_id,day_pt)
COMMENT '能源-节能诊断-诊断结果展示明细表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(diagnostic_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_behavior_diagnostic_result_ts_day
(
	day_pt              string COMMENT '日分区yyyyMMdd',
    device_id           string COMMENT '设备id',
    abnormal_times      int COMMENT '无业务含义',
    diagnostic_id       string COMMENT '规则id',
    abnormal_id         string COMMENT '规则异常id',
    diagnostic_name     string COMMENT '规则名称',
    alarm_level         string COMMENT '告警等级',
    device_name         string COMMENT '设备名称',
    question_content    string COMMENT '问题',
    advice_content      string COMMENT '操作建议',
    abnormal_duration   LARGEINT COMMENT '异常时间 秒',
    start_time          string COMMENT '发生时间 yyyy-MM-dd hh:mm:ss',
    end_time            string COMMENT '结束日期 yyyy-MM-dd hh:mm:ss',
    is_over             boolean COMMENT '是否结束 ture结束 false未结束',
    diagnostic_type     string COMMENT '诊断类型 BEHAVIOR 行为诊断和 FAULT 异常诊断'
)
PRIMARY KEY (day_pt,device_id,abnormal_times)
COMMENT '能源-节能诊断-诊断规则结果-天表(1条持续异常1条数据)'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(device_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_behavior_diagnostic_result_statistic_ts_day
(
    diagnostic_id       string COMMENT '规则id',
	day_pt              string COMMENT '日分区yyyyMMdd',
    device_id           string COMMENT '设备id',
    diagnostic_name     string COMMENT '规则名称',
    alarm_level         string COMMENT '告警等级',
    device_name         string COMMENT '设备名称',
    abnormal_duration   LARGEINT COMMENT '异常时间 秒',
    abnormal_cnt        LARGEINT COMMENT '异常次数',
    diagnostic_type     string COMMENT '诊断类型 BEHAVIOR 行为诊断和 FAULT 异常诊断'
)
PRIMARY KEY (diagnostic_id,day_pt,device_id)
COMMENT
'能源-节能诊断-诊断结果规则设备异常时间次数统计-天表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(diagnostic_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_behavior_diagnostic_result_statistic_ts_month
(
    diagnostic_id       string COMMENT '规则id',
	month_pt            string COMMENT '月分区yyyyMM',
    device_id           string COMMENT '设备id',
    diagnostic_name     string COMMENT '规则名称',
    alarm_level         string COMMENT '告警等级',
    device_name         string COMMENT '设备名称',
    abnormal_duration   LARGEINT COMMENT '异常时间 秒',
    abnormal_cnt        LARGEINT COMMENT '异常次数',
    diagnostic_type     string COMMENT '诊断类型 BEHAVIOR 行为诊断和 FAULT 异常诊断'
)
PRIMARY KEY (diagnostic_id,month_pt,device_id)
COMMENT
'能源-节能诊断-诊断结果规则设备异常时间次数统计-月表'
PARTITION BY (month_pt)
DISTRIBUTED BY HASH(diagnostic_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_behavior_diagnostic_result_statistic_ts_year
(
    diagnostic_id       string COMMENT '规则id',
	year_pt             string COMMENT '年分区yyyy',
    device_id           string COMMENT '设备id',
    diagnostic_name     string COMMENT '规则名称',
    alarm_level         string COMMENT '告警等级',
    device_name         string COMMENT '设备名称',
    abnormal_duration   LARGEINT COMMENT '异常时间 秒',
    abnormal_cnt        LARGEINT COMMENT '异常次数',
    diagnostic_type     string COMMENT '诊断类型 BEHAVIOR 行为诊断和 FAULT 异常诊断'
)
PRIMARY KEY (diagnostic_id,year_pt,device_id)
COMMENT
'能源-节能诊断-诊断结果规则设备异常时间次数统计-年表'
PARTITION BY (year_pt)
DISTRIBUTED BY HASH(diagnostic_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

-- ===================================
-- 能源预测
CREATE TABLE IF NOT EXISTS dwm_energy_energy_prediction_data_base_ts_one_hour
(
    node_id         string COMMENT '节点id',
    day_pt          string COMMENT '天分区',
    attr_key        string COMMENT '类型 电能 Electricity / 气象-属性值',
    dimension_tm    string COMMENT '时间-显示时间 yyyy-MM-dd HH:00:00',
    node_name       string COMMENT '节点名称',
    attr_val        double COMMENT '属性值',
    is_revise       Boolean COMMENT '是否修正值'
)
PRIMARY KEY (node_id,day_pt,attr_key,dimension_tm)
COMMENT '能源-能源预测-基础数据小时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_env_atmosphere_api_future_24h_ts_one_hour
(
    day_pt          string COMMENT '天分区',
    pre_dimension_tm    string COMMENT '预测时间 yyyy-MM-dd HH:mm:ss' ,
    dimension_tm    string COMMENT '业务时间 yyyy-MM-dd HH:mm:ss',
    updatetime      string COMMENT '接口数据发布时间 yyyy-MM-dd HH:mm:ss' ,
    condition       string COMMENT '实况天气现象' ,
    conditionid     string COMMENT '实况天气现象 id' ,
    humidity        double COMMENT '湿度' ,
    iconday         string COMMENT '天气 icon 白天' ,
    iconnight       string COMMENT '天气 icon 晚上' ,
    pop             string COMMENT '' ,
    pressure        double COMMENT '气压' ,
    qpf             string COMMENT '' ,
    realfeel        double COMMENT '体感温度' ,
    snow            string COMMENT '降雨量类型' ,
    temperature     double COMMENT '温度 摄氏度' ,
    uvi             double COMMENT '紫外线' ,
    winddegrees     double COMMENT '风向角度' ,
    winddir         string COMMENT '风向' ,
    windspeed       double COMMENT '风速' ,
    windlevel       double COMMENT '风级'
)
PRIMARY KEY (day_pt,pre_dimension_tm,dimension_tm)
COMMENT '环境监测-api接口-未来24小时气象数据-小时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(dimension_tm)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_env_atmosphere_api_real_time_ts_one_hour
(
    day_pt        string COMMENT '天分区',
    dimension_tm  string COMMENT '业务时间 yyyy-MM-dd HH:mm:ss',
    updatetime    string COMMENT '发布时间' ,
    condition     string COMMENT '实况天气现象' ,
    conditionid   string COMMENT '实况天气现象 id' ,
    humidity      double COMMENT '湿度' ,
    icon          string COMMENT 'icon' ,
    pressure      double COMMENT '气压' ,
    realfeel      double COMMENT '体感温度' ,
    sunrise       string COMMENT '日出时间' ,
    sunset        string COMMENT '日落时间' ,
    temperature   double COMMENT '温度' ,
    tips          string COMMENT '一句话提示' ,
    uvi           double COMMENT '紫外线强度' ,
    vis           double COMMENT '能见度' ,
    winddegrees   double COMMENT '风向角度' ,
    winddir       string COMMENT '风向' ,
    windlevel     double COMMENT '风级' ,
    windspeed     double COMMENT '风速'
)
PRIMARY KEY (day_pt,dimension_tm)
COMMENT '环境监测-api接口-实时气象数据-小时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(dimension_tm)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_energy_prediction_atmosphere_identity_ts_one_hour
(
    day_pt              string COMMENT '天分区',
    dimension_tm        string COMMENT '时间-显示时间 yyyy-MM-dd HH:mm:ss',
    humidity            double COMMENT '当前真实湿度',
    last_1h_humidity    double COMMENT '过去1小时湿度',
    last_2h_mean_humidity   double COMMENT '过去2小时平均湿度',
    last_3h_mean_humidity   double COMMENT '过去3小时平均湿度',
    last_6h_mean_humidity   double COMMENT '过去6小时平均湿度',
    last_12h_mean_humidity  double COMMENT '过去12小时平均湿度',
    last_24h_mean_humidity  double COMMENT '过去24小时平均湿度',
    last_2h_max_humidity    double COMMENT '过去2小时最大湿度',
    last_3h_max_humidity    double COMMENT '过去3小时最大湿度',
    last_6h_max_humidity    double COMMENT '过去6小时最大湿度',
    last_12h_max_humidity   double COMMENT '过去12小时最大湿度',
    last_24h_max_humidity   double COMMENT '过去24小时最大湿度',
    last_2h_min_humidity    double COMMENT '过去2小时最小湿度',
    last_3h_min_humidity    double COMMENT '过去3小时最小湿度',
    last_6h_min_humidity    double COMMENT '过去6小时最小湿度',
    last_12h_min_humidity   double COMMENT '过去12小时最小湿度',
    last_24h_min_humidity   double COMMENT '过去24小时最小湿度',
    last_6h_fluctuation_humidity    double COMMENT '过去6小时湿度波动',
    last_12h_fluctuation_humidity   double COMMENT '过去12小时湿度波动',
    last_24h_fluctuation_humidity   double COMMENT '过去24小时湿度波动',
    1h_vs_24h_humidity              double COMMENT '过去1小时与过去24小时平均湿度差',
    temperature                     double COMMENT '当前真实温度',
    last_1h_temperature             double COMMENT '过去1小时温度',
    last_2h_mean_temperature        double COMMENT '过去2小时平均温度',
    last_3h_mean_temperature        double COMMENT '过去3小时平均温度',
    last_6h_mean_temperature        double COMMENT '过去6小时平均温度',
    last_12h_mean_temperature       double COMMENT '过去12小时平均温度',
    last_24h_mean_temperature       double COMMENT '过去24小时平均温度',
    last_2h_max_temperature         double COMMENT '过去2小时最大温度',
    last_3h_max_temperature         double COMMENT '过去3小时最大温度',
    last_6h_max_temperature         double COMMENT '过去6小时最大温度',
    last_12h_max_temperature        double COMMENT '过去12小时最大温度',
    last_24h_max_temperature        double COMMENT '过去24小时最大温度',
    last_2h_min_temperature         double COMMENT '过去2小时最小温度',
    last_3h_min_temperature         double COMMENT '过去3小时最小温度',
    last_6h_min_temperature         double COMMENT '过去6小时最小温度',
    last_12h_min_temperature        double COMMENT '过去12小时最小温度',
    last_24h_min_temperature        double COMMENT '过去24小时最小温度',
    last_6h_fluctuation_temperature    double COMMENT '过去6小时温度波动',
    last_12h_fluctuation_temperature   double COMMENT '过去12小时温度波动',
    last_24h_fluctuation_temperature   double COMMENT '过去24小时温度波动',
    1h_vs_24h_temperature              double COMMENT '过去1小时与过去24小时平均温度差'
)
PRIMARY KEY (day_pt,dimension_tm)
COMMENT '能源-能源预测-气象-特征值结果-1小时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(dimension_tm)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_energy_prediction_electricity_identity_ts_one_hour
(
    node_id         string COMMENT '节点id',
    day_pt          string COMMENT '天分区',
    dimension_tm    string COMMENT '时间-显示时间 yyyy-MM-dd HH:mm:ss',
    node_name       string COMMENT '节点名称',
    energy          double COMMENT '当前时间能耗',
    last_1h_energy   double COMMENT '过去1小时能耗',
    last_2h_energy   double COMMENT '过去2小时能耗',
    last_3h_energy   double COMMENT '过去3小时能耗',
    last_4h_energy   double COMMENT '过去4小时能耗',
    last_5h_energy   double COMMENT '过去5小时能耗',
    last_6h_energy   double COMMENT '过去6小时能耗',
    last_2h_mean_energy  double COMMENT '过去2小时平均能耗',
    last_3h_mean_energy  double COMMENT '过去3小时平均能耗',
    last_4h_mean_energy  double COMMENT '过去4小时平均能耗',
    last_6h_mean_energy  double COMMENT '过去6小时平均能耗',
    last_9h_mean_energy  double COMMENT '过去9小时平均能耗',
    last_12h_mean_energy  double COMMENT '过去12小时平均能耗',
    last_15h_mean_energy  double COMMENT '过去15小时平均能耗',
    last_18h_mean_energy  double COMMENT '过去18小时平均能耗',
    last_21h_mean_energy  double COMMENT '过去21小时平均能耗',
    last_24h_mean_energy  double COMMENT '过去24小时平均能耗',
    last_2h_max_energy  double COMMENT '过去2小时最大能耗',
    last_3h_max_energy  double COMMENT '过去3小时最大能耗',
    last_4h_max_energy  double COMMENT '过去4小时最大能耗',
    last_6h_max_energy  double COMMENT '过去6小时最大能耗',
    last_9h_max_energy  double COMMENT '过去9小时最大能耗',
    last_12h_max_energy  double COMMENT '过去12小时最大能耗',
    last_15h_max_energy  double COMMENT '过去15小时最大能耗',
    last_18h_max_energy  double COMMENT '过去18小时最大能耗',
    last_21h_max_energy  double COMMENT '过去21小时最大能耗',
    last_24h_max_energy  double COMMENT '过去24小时最大能耗',
    last_2h_min_energy  double COMMENT '过去2小时最小能耗',
    last_3h_min_energy  double COMMENT '过去3小时最小能耗',
    last_4h_min_energy  double COMMENT '过去4小时最小能耗',
    last_6h_min_energy  double COMMENT '过去6小时最小能耗',
    last_9h_min_energy  double COMMENT '过去9小时最小能耗',
    last_12h_min_energy  double COMMENT '过去12小时最小能耗',
    last_15h_min_energy  double COMMENT '过去15小时最小能耗',
    last_18h_min_energy  double COMMENT '过去18小时最小能耗',
    last_21h_min_energy  double COMMENT '过去21小时最小能耗',
    last_24h_min_energy  double COMMENT '过去24小时最小能耗',
    last_2h_fluctuation_energy  double COMMENT '过去2小时能耗波动', -- last_2h_max_energy - last_2h_min_energy
    last_3h_fluctuation_energy  double COMMENT '过去3小时能耗波动',
    last_4h_fluctuation_energy  double COMMENT '过去4小时能耗波动',
    last_6h_fluctuation_energy  double COMMENT '过去6小时能耗波动',
    last_9h_fluctuation_energy  double COMMENT '过去9小时能耗波动',
    last_12h_fluctuation_energy  double COMMENT '过去12小时能耗波动',
    last_15h_fluctuation_energy  double COMMENT '过去15小时能耗波动',
    last_18h_fluctuation_energy  double COMMENT '过去18小时能耗波动',
    last_21h_fluctuation_energy  double COMMENT '过去21小时能耗波动',
    last_24h_fluctuation_energy  double COMMENT '过去24小时能耗波动',
    1h_vs_24h_energy  double COMMENT '过去1小时与过去24小时平均能耗差', -- last_1h_energy - last_24h_mean_energy
    1h_vs_2h_energy   double COMMENT '过去1小时与过去2小时能耗差' -- last_1h_energy - last_2h_energy
)
PRIMARY KEY (node_id,day_pt,dimension_tm)
COMMENT '能源-能源预测-电能-特征值结果-1小时表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_forecast_history_ts_one_hour
(
        node_id        string COMMENT '能耗节点id' ,
        type           string COMMENT '能耗类型 Electricity' ,
        predict_ts     bigint COMMENT '预测时间 秒时间戳' ,
        stat_ts        bigint comment '数据创建时间-秒时间戳',
        day_pt         string comment '天分区 yyyyMMdd',
        node_name      string COMMENT '能耗节点名称' ,
        predict_tm     string comment '预测时间 yyyy-MM-dd HH:mm:ss',
        predict_val    double comment '预测值',
        nth_hour       int  comment '本次预测的第几小时 1-24',
        stat_tm        string comment '数据创建时间 yyyy-MM-dd HH:mm:ss'
)
PRIMARY KEY (node_id,type,predict_ts,stat_ts,day_pt)
COMMENT '能源-能源预测-预测结果-小时记录表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS dwm_energy_forecast_history_ts_day
(
        node_id        string COMMENT '能耗节点id' ,
        type           string COMMENT '能耗类型 Electricity' ,
        predict_ts     bigint COMMENT '预测时间 秒时间戳' ,
        stat_ts        bigint comment '数据创建时间-秒时间戳',
        day_pt         string comment '天分区 yyyyMMdd',
        node_name      string COMMENT '能耗节点名称' ,
        predict_tm     string comment '预测时间 yyyy-MM-dd HH:mm:ss',
        predict_val    double comment '预测值',
        nth_hour       int  comment '本次预测的第几小时 1-24',
        stat_tm        string comment '数据创建时间 yyyy-MM-dd HH:mm:ss'
)
PRIMARY KEY (node_id,type,predict_ts,stat_ts,day_pt)
COMMENT '能源-能源预测-预测结果-天记录表'
PARTITION BY (day_pt)
DISTRIBUTED BY HASH(node_id)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);

CREATE TABLE IF NOT EXISTS `dwm_bdmm_repair_data_ts_day` (
  `product_id` string NOT NULL COMMENT "产品id",
  `device_id` string NOT NULL COMMENT "设备id",
  `attr_key` string NOT NULL COMMENT "产品属性标识符",
  `ts` bigint(20) NOT NULL COMMENT "原始数据时间戳",
  `day_pt` string NOT NULL COMMENT "天分区",
  `rule_id` string NULL COMMENT "规则id",
  `rule_name` string NULL COMMENT "规则名称",
  `product_name` string NULL COMMENT "产品名称",
  `attr_name` string NULL COMMENT "产品属性名称",
  `attr_type` string NULL COMMENT "产品属性类型",
  `monitor_dimension` string NULL COMMENT "监控维度",
  `point_rule_template` string NULL COMMENT "点位规则模板",
  `repair_type` string NULL COMMENT "修复类型",
  `origin_value` string NULL COMMENT "原始值",
  `repair_value` string NULL COMMENT "修复值",
  `repair_success` boolean NULL COMMENT "是否修复成功",
  `error_message` string NULL COMMENT "错误信息",
  `tm` string NULL COMMENT "原始数据显示时间",
  `repair_ts` bigint(20) NULL COMMENT "数据修复时间戳",
  `repair_tm` string NULL COMMENT "数据修复显示时间"
) ENGINE=OLAP
PRIMARY KEY(`product_id`, `device_id`, `attr_key`, `ts`, `day_pt`)
COMMENT "时序数据修复日志表"
PARTITION BY (`day_pt`)
DISTRIBUTED BY HASH(`device_id`)
PROPERTIES ("replication_num" = "3", "enable_persistent_index" = "false"
);
