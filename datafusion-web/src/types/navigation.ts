import type { ComponentType } from "react";
import type { AntdIconProps } from "@ant-design/icons/lib/components/AntdIcon";

export type IconComponent = ComponentType<AntdIconProps>;

export interface AppRoute {
  path: string;
  label: string;
  icon: IconComponent;
  component: ComponentType;
}

export interface AppRouteGroup {
  key: string;
  label: string;
  icon: IconComponent;
  children: AppRoute[];
}
