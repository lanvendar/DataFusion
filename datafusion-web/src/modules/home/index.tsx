import {
  ApiOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  CloudSyncOutlined,
  DatabaseOutlined,
  ScheduleOutlined,
} from "@ant-design/icons";
import { Button, Card, Col, Row, Space, Statistic, Typography } from "antd";
import { useNavigate } from "react-router-dom";

const features = [
  {
    title: "指标中心",
    description: "统一指标、数仓指标维护、指标注册和调用分析。",
    icon: BarChartOutlined,
    path: "/unified-metric",
  },
  {
    title: "元数据管理",
    description: "管理数据源、表结构和结构同步任务。",
    icon: DatabaseOutlined,
    path: "/metadata-datasource",
  },
  {
    title: "数据资产",
    description: "维护血缘资源并查看表级、业务级血缘关系。",
    icon: ApartmentOutlined,
    path: "/asset-table-blood",
  },
  {
    title: "调度中心",
    description: "配置触发器、变量、流程、任务和事件。",
    icon: ScheduleOutlined,
    path: "/scheduler-flow",
  },
  {
    title: "数据集成",
    description: "规划离线和实时同步任务，管理字段映射。",
    icon: CloudSyncOutlined,
    path: "/datastudio-sync",
  },
  {
    title: "开放替代",
    description: "使用 Vite、Ant Design、Axios、TanStack Query 替代私有包。",
    icon: ApiOutlined,
    path: "/metric-registration",
  },
];

export default function HomePage() {
  const navigate = useNavigate();

  return (
    <Space direction="vertical" size={24} className="page-stack">
      <section className="hero-panel">
        <div>
          <Typography.Text className="eyebrow">DataFusion Web</Typography.Text>
          <Typography.Title level={1}>开源前端迁移框架</Typography.Title>
          <Typography.Paragraph>
            基于原 data-warehouse-view 的业务版图，移除 @gw/* 私有依赖，保留指标、元数据、资产、调度和数据开发的导航骨架。
          </Typography.Paragraph>
          <Space wrap>
            <Button type="primary" size="large" onClick={() => navigate("/metadata-datasource")}>
              进入数据源管理
            </Button>
            <Button size="large" onClick={() => navigate("/scheduler-flow")}>
              查看流程编排
            </Button>
          </Space>
        </div>
        <Row gutter={[16, 16]} className="hero-stats">
          <Col span={12}>
            <Statistic title="业务模块" value={6} />
          </Col>
          <Col span={12}>
            <Statistic title="路由页面" value={18} />
          </Col>
          <Col span={12}>
            <Statistic title="私有依赖" value={0} suffix="个" />
          </Col>
          <Col span={12}>
            <Statistic title="构建工具" value="Vite" />
          </Col>
        </Row>
      </section>

      <Row gutter={[16, 16]}>
        {features.map((feature) => (
          <Col xs={24} md={12} xl={8} key={feature.title}>
            <Card className="feature-card" hoverable onClick={() => navigate(feature.path)}>
              <feature.icon className="feature-icon" />
              <Typography.Title level={4}>{feature.title}</Typography.Title>
              <Typography.Paragraph>{feature.description}</Typography.Paragraph>
            </Card>
          </Col>
        ))}
      </Row>
    </Space>
  );
}
