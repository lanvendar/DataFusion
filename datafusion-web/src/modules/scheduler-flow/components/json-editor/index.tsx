import { Button, Collapse, Input, Space } from "antd";
import type { TextAreaProps } from "antd/es/input";
import { compressJsonText, formatJsonText } from "../../utils";

interface JsonEditorProps extends Omit<TextAreaProps, "onChange"> {
  title: string;
  value?: string;
  rows?: number;
  onChange?: (value?: string) => void;
}

export function JsonEditor({
  title,
  value,
  rows = 8,
  placeholder,
  onChange,
  ...rest
}: JsonEditorProps) {
  const hasValue = Boolean(value?.trim());

  const format = () => {
    if (!value) return;
    onChange?.(formatJsonText(value));
  };

  const compress = () => {
    if (!value) return;
    onChange?.(compressJsonText(value));
  };

  return (
    <div className="json-editor">
      <div className="json-editor-header">
        <span>{title}</span>
        <Space size={8}>
          <Button size="small" onClick={format} disabled={!hasValue}>
            格式化
          </Button>
          <Button size="small" onClick={compress} disabled={!hasValue}>
            压缩
          </Button>
        </Space>
      </div>
      <Collapse
        size="small"
        defaultActiveKey={["content"]}
        items={[
          {
            key: "content",
            label: "内容",
            children: (
              <Input.TextArea
                {...rest}
                value={value}
                rows={rows}
                placeholder={placeholder}
                onChange={(event) => onChange?.(event.target.value)}
              />
            ),
          },
        ]}
      />
    </div>
  );
}
