package com.zerionis.log.spring.sql;

import com.zerionis.log.core.format.ZerionisLogFormatter;
import com.zerionis.log.spring.config.ZerionisProperties;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

/**
 * BeanPostProcessor that wraps DataSource beans with the Zerionis SQL monitoring proxy.
 *
 * <p>Only activates when {@code zerionis.log.sql-enabled=true}.</p>
 */
public class ZerionisDataSourcePostProcessor implements BeanPostProcessor {

    private final ZerionisLogFormatter formatter;
    private final ZerionisProperties properties;

    public ZerionisDataSourcePostProcessor(ZerionisLogFormatter formatter,
                                            ZerionisProperties properties) {
        this.formatter = formatter;
        this.properties = properties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource && !(bean instanceof ZerionisDataSourceProxy)) {
            return new ZerionisDataSourceProxy((DataSource) bean, formatter, properties);
        }
        return bean;
    }
}
