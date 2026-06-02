import { Typography } from "antd";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

interface BreadcrumbItem {
  label: string;
  path?: string;
}

interface PageHeaderProps {
  breadcrumb?: BreadcrumbItem[];
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children?: ReactNode;
}

export function PageHeader({
  breadcrumb,
  title,
  description,
  actions,
  children,
}: PageHeaderProps) {
  return (
    <div className="page-heading">
      <div className="page-heading-main">
        {breadcrumb?.length ? (
          <div className="page-breadcrumb">
            {breadcrumb.map((item, index) => (
              <span className="page-breadcrumb-item" key={`${item.label}-${index}`}>
                {index > 0 ? <span className="page-breadcrumb-separator">/</span> : null}
                {item.path ? <Link to={item.path}>{item.label}</Link> : <span>{item.label}</span>}
              </span>
            ))}
          </div>
        ) : null}
        <Typography.Title level={2}>{title}</Typography.Title>
        {description ? <Typography.Paragraph>{description}</Typography.Paragraph> : null}
        {children ? <div className="page-heading-extra-content">{children}</div> : null}
      </div>
      {actions ? <div className="page-heading-actions">{actions}</div> : null}
    </div>
  );
}
