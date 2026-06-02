import { Layout, Menu, Typography } from "antd";
import type { MenuProps } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { DatabaseOutlined, GithubOutlined } from "@ant-design/icons";
import { routeGroups } from "@/router/routes";

const { Sider, Content } = Layout;

const menuItems: MenuProps["items"] = routeGroups.map((group) => ({
  key: group.key,
  icon: <group.icon />,
  label: group.label,
  children: group.children.map((route) => ({
    key: `/${route.path}`,
    icon: <route.icon />,
    label: route.label,
  })),
}));

const pathToOpenKey = new Map<string, string>(
  routeGroups.flatMap((group) =>
    group.children.map((route) => [`/${route.path}`, group.key] as const),
  ),
);

export default function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const selectedKey = location.pathname === "/" ? "/home" : location.pathname;
  const openKey = pathToOpenKey.get(selectedKey);

  return (
    <Layout className="app-shell">
      <Sider width={264} className="app-sider">
        <div className="brand">
          <div className="brand-mark">
            <DatabaseOutlined />
          </div>
          <div className="brand-copy">
            <Typography.Text className="brand-title">DataFusion</Typography.Text>
            <Typography.Text className="brand-subtitle">数据集成平台</Typography.Text>
          </div>
          <a className="brand-link" href="https://github.com/" target="_blank" rel="noreferrer" aria-label="开源组件栈">
            <GithubOutlined />
          </a>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          items={menuItems}
          selectedKeys={[selectedKey]}
          defaultOpenKeys={openKey ? [openKey] : ["home", "metrics"]}
          onClick={({ key }) => navigate(String(key))}
        />
      </Sider>
      <Layout>
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
