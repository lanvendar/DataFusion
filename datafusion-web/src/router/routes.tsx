import {
  ApiOutlined,
  AppstoreOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  BranchesOutlined,
  CloudSyncOutlined,
  CodeOutlined,
  ControlOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  FieldTimeOutlined,
  FileSearchOutlined,
  FunctionOutlined,
  HomeOutlined,
  NodeIndexOutlined,
  PartitionOutlined,
  ProfileOutlined,
  ScheduleOutlined,
  TableOutlined,
} from "@ant-design/icons";
import HomePage from "@/pages/home";
import OverviewPage from "@/pages/common/overview-page";
import DataTablePage from "@/pages/common/data-table-page";
import MetadataDatasourcePage from "@/modules/metadata-datasource";
import MetadataTableStructurePage from "@/modules/metadata-table-structure";
import MetadataTableSyncPage from "@/modules/metadata-table-sync";
import SchedulerTriggerPage from "@/modules/scheduler-trigger";
import SchedulerVariablePage from "@/modules/scheduler-variable";
import SchedulerFlowPage from "@/pages/scheduler-flow";
import SchedulerTaskPage from "@/modules/scheduler-task";
import SchedulerEventPage from "@/modules/scheduler-event";
import type { AppRouteGroup } from "@/types/navigation";

export const routeGroups: AppRouteGroup[] = [
  {
    key: "home",
    label: "工作台",
    icon: HomeOutlined,
    children: [
      {
        path: "home",
        label: "首页",
        icon: HomeOutlined,
        component: HomePage,
      },
    ],
  },
  {
    key: "metrics",
    label: "指标中心",
    icon: BarChartOutlined,
    children: [
      {
        path: "unified-metric",
        label: "统一指标",
        icon: FunctionOutlined,
        component: () => (
          <OverviewPage
            title="统一指标"
            description="沉淀可查询、可复用、可治理的统一指标目录。"
            domain="metrics"
          />
        ),
      },
      {
        path: "warehouse-metric",
        label: "数仓指标维护",
        icon: DatabaseOutlined,
        component: () => (
          <DataTablePage
            title="数仓指标维护"
            description="维护数仓指标定义、启用状态、负责人和业务归属。"
            entityName="指标"
            apiPath="/api/biz-data/dw_tag/manager/list"
            breadcrumb={["指标中心", "数仓指标维护"]}
          />
        ),
      },
      {
        path: "metric-registration",
        label: "指标注册",
        icon: ProfileOutlined,
        component: () => (
          <DataTablePage
            title="指标注册"
            description="注册业务指标并维护开发信息、权限和生命周期状态。"
            entityName="指标"
            apiPath="/api/biz-data/tag/manager/list"
            breadcrumb={["指标中心", "指标注册"]}
          />
        ),
      },
      {
        path: "metric-invocation",
        label: "指标调用",
        icon: ApiOutlined,
        component: () => (
          <OverviewPage
            title="指标调用"
            description="查看指标接口调用、低频指标和服务使用情况。"
            domain="metrics"
          />
        ),
      },
    ],
  },
  {
    key: "metadata",
    label: "元数据管理",
    icon: DatabaseOutlined,
    children: [
      {
        path: "metadata-datasource",
        label: "数据源管理",
        icon: CloudSyncOutlined,
        component: MetadataDatasourcePage,
      },
      {
        path: "metadata-table-structure",
        label: "表结构管理",
        icon: TableOutlined,
        component: MetadataTableStructurePage,
      },
      {
        path: "metadata-table-sync",
        label: "表结构同步",
        icon: FileSearchOutlined,
        component: MetadataTableSyncPage,
      },
    ],
  },
  {
    key: "asset",
    label: "数据资产",
    icon: ApartmentOutlined,
    children: [
      {
        path: "asset-table-blood",
        label: "表级血缘",
        icon: BranchesOutlined,
        component: () => (
          <OverviewPage
            title="表级血缘"
            description="以表为中心追踪上下游依赖、字段关系和资源影响范围。"
            domain="asset"
          />
        ),
      },
      {
        path: "asset-business-blood",
        label: "业务血缘",
        icon: NodeIndexOutlined,
        component: () => (
          <OverviewPage
            title="业务血缘"
            description="串联菜单、接口、指标、服务和表资源的业务链路。"
            domain="asset"
          />
        ),
      },
      {
        path: "asset-resource",
        label: "血缘资源导入",
        icon: PartitionOutlined,
        component: () => (
          <DataTablePage
            title="血缘资源导入"
            description="导入菜单、接口、ETL、表和指标资源，进入血缘图谱。"
            entityName="资源"
            apiPath="/api/asset/resource/pageResouces"
            breadcrumb={["数据资产", "血缘资源导入"]}
          />
        ),
      },
    ],
  },
  {
    key: "scheduler",
    label: "调度中心",
    icon: ScheduleOutlined,
    children: [
      {
        path: "scheduler-trigger",
        label: "调度器配置",
        icon: FieldTimeOutlined,
        component: SchedulerTriggerPage,
      },
      {
        path: "scheduler-variable",
        label: "变量配置",
        icon: ControlOutlined,
        component: SchedulerVariablePage,
      },
      {
        path: "scheduler-flow",
        label: "流程管理",
        icon: DeploymentUnitOutlined,
        component: SchedulerFlowPage,
      },
      {
        path: "scheduler-task",
        label: "任务管理",
        icon: AppstoreOutlined,
        component: SchedulerTaskPage,
      },
      {
        path: "scheduler-event",
        label: "事件管理",
        icon: ApiOutlined,
        component: SchedulerEventPage,
      },
    ],
  },
  {
    key: "datastudio",
    label: "数据开发",
    icon: CodeOutlined,
    children: [
      {
        path: "datastudio-sync",
        label: "数据集成",
        icon: CloudSyncOutlined,
        component: () => (
          <DataTablePage
            title="数据集成"
            description="创建离线/实时同步任务，维护源端、目标端和字段映射。"
            entityName="同步任务"
            apiPath="/api/ingestion/datasync-task/page"
            breadcrumb={["数据开发", "数据集成"]}
          />
        ),
      },
      {
        path: "development",
        label: "数据开发",
        icon: CodeOutlined,
        component: () => (
          <OverviewPage
            title="数据开发"
            description="编写、调试并提交离线或实时 SQL 开发任务。"
            domain="development"
          />
        ),
      },
    ],
  },
];
