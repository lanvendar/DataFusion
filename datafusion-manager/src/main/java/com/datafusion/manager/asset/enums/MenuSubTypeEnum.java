package com.datafusion.manager.asset.enums;

/**
 * 新增指标实体.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/4/2
 * @since 2026/4/2
 */
public enum MenuSubTypeEnum {

    /**
     * 界面.
     */
    PAGE((byte) 0, "界面"),

    /**
     * 子tab页.
     */
    SUBTAB((byte) 1, "子tab页"),

    /**
     * 按钮.
     */
    BUTTON((byte) 2, "按钮"),
    /**
     * 目录.
     */
    DIRECTORY((byte) 3, "目录");

    /** 组件类型. */
    byte componentType;

    /** 组件名称. */
    String componentName;

    MenuSubTypeEnum(byte componentType, String componentName) {
        this.componentType = componentType;
        this.componentName = componentName;
    }

    public String getComponentName() {
        return componentName;
    }

    public byte getComponentType() {
        return componentType;
    }

    /**
     * 根据 componentType 获取枚举实例.
     *
     * @param componentType 组件类型值
     * @return 对应的枚举实例，如果未找到返回 null
     */
    public static MenuSubTypeEnum getByComponentType(byte componentType) {
        for (MenuSubTypeEnum enumValue : values()) {
            if (enumValue.componentType == componentType) {
                return enumValue;
            }
        }
        return null;
    }
}
