import { Card, Col, Row, Space, Steps, Tag, Typography } from "antd";
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeploymentUnitOutlined,
} from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";

interface OverviewPageProps {
  title: string;
  description: string;
  domain: "metrics" | "asset" | "development";
}

const domainTags = {
  metrics: ["指标目录", "权限治理", "调用分析"],
  asset: ["血缘图谱", "影响分析", "资源导入"],
  development: ["SQL 开发", "任务发布", "运行监控"],
};

const domainLabels = {
  metrics: "指标中心",
  asset: "数据资产",
  development: "数据开发",
};

export default function OverviewPage({ title, description, domain }: OverviewPageProps) {
  return (
    <Space direction="vertical" size={16} className="page-stack">
      <PageHeader
        breadcrumb={[{ label: domainLabels[domain] }, { label: title }]}
        title={title}
        description={description}
        actions={
          <Space wrap>
          {domainTags[domain].map((tag) => (
            <Tag color="blue" key={tag}>
              {tag}
            </Tag>
          ))}
          </Space>
        }
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card>
            <Space direction="vertical">
              <CheckCircleOutlined className="metric-icon green" />
              <Typography.Title level={4}>可迁移区域</Typography.Title>
              <Typography.Paragraph>
                当前页面保留路由、标题、业务说明和操作入口，后续可逐步迁入原页面的表单和图谱逻辑。
              </Typography.Paragraph>
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card>
            <Space direction="vertical">
              <DeploymentUnitOutlined className="metric-icon blue" />
              <Typography.Title level={4}>开源组件</Typography.Title>
              <Typography.Paragraph>
                复杂图谱可使用 @xyflow/react、ECharts 或 AntV G6，表格表单统一使用 Ant Design。
              </Typography.Paragraph>
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card>
            <Space direction="vertical">
              <ClockCircleOutlined className="metric-icon amber" />
              <Typography.Title level={4}>下一步</Typography.Title>
              <Typography.Paragraph>
                对齐后端接口返回结构，按页面迁移 api、dto、table-config 和 components。
              </Typography.Paragraph>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card title="建议迁移路径">
        <Steps
          current={1}
          items={[
            { title: "框架替换", description: "Vite、Ant Design、Axios" },
            { title: "页面骨架", description: "菜单、路由、通用表格" },
            { title: "业务迁移", description: "逐页迁移接口与组件" },
            { title: "联调发布", description: "接入 DataFusion Manager" },
          ]}
        />
      </Card>
    </Space>
  );
}
