package com.molean.isletopia.framework.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Bean
public @interface BeanHandler {
    int value() default 0;
}
