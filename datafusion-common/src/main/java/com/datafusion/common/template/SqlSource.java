package com.datafusion.common.template;

import com.jfinal.template.source.ISource;

/**
 * 封装 sql 模板源.
 *
 * @author lanvendar
 * @version 1.0.0 ,2022/01/25
 * @since 2019/12/12
 */
public class SqlSource {
    
    /**
     * 相对路径,默认为 classPath 路径下的文件.
     */
    String file;
    
    /**
     * 任意路径下的文件,取决于 {@link com.jfinal.template.source.ISource} 接口的实现.
     * {@link com.jfinal.template.source.FileSource}
     * {@link com.jfinal.template.source.ClassPathSource}
     * {@link com.jfinal.template.source.StringSource}
     * {@link com.datafusion.common.template.source.JarFileSource}
     */
    ISource source;
    
    /**
     * 构造方法.
     *
     * @param file 模板来源为文件
     */
    SqlSource(String file) {
        this.file = file;
        source = null;
    }
    
    /**
     * 构造方法.
     *
     * @param source 模板来源为 ISource 形式接口
     */
    SqlSource(ISource source) {
        file = null;
        this.source = source;
    }
    
    boolean isFile() {
        return file != null;
    }
}



