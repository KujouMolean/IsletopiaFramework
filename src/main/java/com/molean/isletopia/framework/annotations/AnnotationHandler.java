package com.molean.isletopia.framework.annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Service
public @interface AnnotationHandler {
    Class<? extends Annotation> value();
}
