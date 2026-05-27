import { MinusCircleOutlined, PlusOutlined } from "@ant-design/icons";
import { Button, Form, Input, Space } from "antd";

export function JsonParamsEditor() {
  return (
    <Form.List name="jsonParams">
      {(fields, { add, remove }) => (
        <Space direction="vertical" className="page-stack">
          {fields.map((field) => (
            <Space key={field.key} align="baseline">
              <Form.Item name={[field.name, "paramName"]} rules={[{ required: true }]}>
                <Input placeholder="参数名" />
              </Form.Item>
              <Form.Item name={[field.name, "paramValue"]}>
                <Input placeholder="参数值" />
              </Form.Item>
              <Button icon={<MinusCircleOutlined />} onClick={() => remove(field.name)} />
            </Space>
          ))}
          <Button icon={<PlusOutlined />} onClick={() => add()}>
            添加参数
          </Button>
        </Space>
      )}
    </Form.List>
  );
}
