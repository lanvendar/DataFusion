package com.datafusion.common.threadpool;

import java.io.Serializable;

/**
 * 建造者模式接口定义.
 *
 * @param <T> 建造对象类型
 * @author lanvendar
 * @version 3.0.0, 2022/05/16
 * @since 2022/05/16
 */
public interface Builder<T> extends Serializable {
    
    /**
     * 构建.
     *
     * @return 被构建的对象
     */
    T build();
}