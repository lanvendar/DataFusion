package com.datafusion.manager.utils;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/29
 * @since 2025/10/29
 */
public class HologresScalarFunction {
    
    public static Object IF(Boolean isTrue, Object a, Object b) {
        return isTrue == true ? a : b;
    }
}
