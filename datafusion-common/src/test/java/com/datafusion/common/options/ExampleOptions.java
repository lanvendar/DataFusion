package com.datafusion.common.options;

import static com.datafusion.common.options.ConfigOptions.key;

/**
 * 测试参数.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/4/10
 * @since 2025/4/10
 */
public class ExampleOptions {
    public static final ConfigOption<Integer> CLIENT_POOL_SIZE = key("client.pool.size")
            .intType()
            .defaultValue(2)
            .withDescription("Configure the size of the connection pool.");
}
