import { Button, Input, Space } from "antd";
import { useEffect, useMemo, useState } from "react";
import { compressJsonText, formatJsonText } from "../../utils";

interface JsonEditorProps {
  value?: string;
  onChange?: (value: string) => void;
  title: string;
  placeholder?: string;
  rows?: number;
  disabled?: boolean;
  collapsible?: boolean;
  defaultExpanded?: boolean;
}

export function JsonEditor({
  value,
  onChange,
  title,
  placeholder,
  rows = 8,
  disabled = false,
  collapsible = true,
  defaultExpanded = true,
}: JsonEditorProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const hasValue = useMemo(() => Boolean(value?.trim()), [value]);

  useEffect(() => {
    setExpanded(defaultExpanded);
  }, [defaultExpanded, title]);

  const handleFormat = () => {
    onChange?.(formatJsonText(value));
  };

  const handleCompress = () => {
    onChange?.(compressJsonText(value));
  };

  return (
    <div className="json-editor">
      <div className="json-editor-header">
        <span>{title}</span>
        <Space>
          {collapsible ? (
            <Button size="small" onClick={() => setExpanded((prev) => !prev)}>
              {expanded ? "收起" : "展开"}
            </Button>
          ) : null}
          {!disabled ? (
            <>
              <Button size="small" onClick={handleFormat} disabled={!hasValue}>
                格式化
              </Button>
              <Button size="small" onClick={handleCompress} disabled={!hasValue}>
                压缩
              </Button>
            </>
          ) : null}
        </Space>
      </div>
      {expanded ? (
        <div className="json-editor-body">
          <Input.TextArea
            rows={rows}
            value={value}
            onChange={(event) => onChange?.(event.target.value)}
            placeholder={placeholder}
            disabled={disabled}
          />
        </div>
      ) : null}
    </div>
  );
}
