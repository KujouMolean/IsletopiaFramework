package com.molean.isletopia.framework.annotations;


import java.lang.annotation.Annotation;

public interface IAnnotationHandler<T extends Annotation> {
    void handle(Class<?> clazz, T annotation);

}
