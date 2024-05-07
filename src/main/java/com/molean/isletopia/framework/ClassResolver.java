package com.molean.isletopia.framework;

import com.molean.isletopia.framework.annotations.*;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.logging.Logger;

public enum ClassResolver {
    INSTANCE,
    ;

    public Set<Class<?>> getClassSet() {
        return classSet;
    }

    private final Set<Class<?>> classSet = new HashSet<>();

    private final Set<Method> invokedMethods = new HashSet<>();

    //    @Getter
    private final Set<Object> objects = new HashSet<>();


    /**
     * 获取一个类及其父类所包含的所有注解, except中的除外
     *
     * @param clazz  目标类
     * @param except 排除
     * @return 注解集合
     */
    public Set<Class<?>> getAnnotations(Class<?> clazz, Set<Class<?>> except) {
        List<? extends Class<? extends Annotation>> annotations
                = Arrays.stream(clazz.getAnnotations())
                .map(Annotation::annotationType)
                .filter(aClass -> !except.contains(aClass))
                .toList();

        HashSet<Class<?>> finalAnnotations = new HashSet<>(annotations);
        except.addAll(annotations);
        for (Class<?> annotation : annotations) {
            Set<Class<?>> annotationAnnotations = getAnnotations(annotation, except);
            finalAnnotations.addAll(annotationAnnotations);
        }
        return finalAnnotations;
    }

    /**
     * 是否需要扫描并处理
     *
     * @param clazz 目标类
     * @return 布尔值
     */
    public boolean shouldScan(Class<?> clazz) {

        if (clazz.isAnnotation()) {
            return false;
        }
        Set<Class<?>> annotations = getAnnotations(clazz, new HashSet<>());

        return annotations.contains(Bean.class);
    }


    /**
     * 从容器获取对象
     *
     * @param parameter 根据参数
     * @return 目标对象, 不存在则返回null
     */
    public Object getObject(Parameter parameter) {
        Class<?> type = parameter.getType();
        return getObject(type);
    }

    /**
     * 从容器获取对象
     *
     * @param field 根据字段
     * @return 目标对象, 不存在则返回null
     */
    public Object getObject(Field field) {
        Class<?> type = field.getType();
        return getObject(type);
    }

    /**
     * 从容器获取对象
     *
     * @param type 根据类
     * @return 目标对象, 不存在则返回null
     */
    public <T> T getObject(Class<T> type) {
        for (Object value : objects) {
            if (type.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }


    /**
     * 为类中的空字段自动注入容器中的对象
     *
     * @throws Exception 异常
     */
    public void resolveFieldsInject() throws Exception {
        for (Object object : objects) {
            try {
                resolveFieldInject(object);
            } catch (Throwable e) {
                Logger.getLogger("IsletopiaFramework").severe("Error while resolve inject for " + object.getClass());
                e.printStackTrace();
            }
        }
    }

    public int resolveFieldInject(Object object) throws Exception {
        int cnt = 0;
        Field[] declaredFields = object.getClass().getDeclaredFields();
        for (Field declaredField : declaredFields) {
            if (declaredField.isAnnotationPresent(AutoInject.class)) {
                declaredField.setAccessible(true);
                Object tobeInject = getObject(declaredField);
                if (tobeInject != null) {
                    declaredField.set(object, tobeInject);
                    cnt++;
                }
            }
        }
        return cnt;

    }

    public void addBean(Object object) {
        objects.add(object);
    }


    @Nullable
    public Object tryCreateClassByConstructor(Class<?> clazz) {
        Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();
        constructor:
        for (Constructor<?> declaredConstructor : declaredConstructors) {
            ArrayList<Object> parameters = new ArrayList<>();
            for (Parameter parameter : declaredConstructor.getParameters()) {
                Object object = getObject(parameter);
                if (object != null) {
                    parameters.add(object);
                } else {
                    break constructor;
                }
            }
            try {
                return declaredConstructor.newInstance(parameters.toArray());
            } catch (Throwable e) {
                Logger logger = Logger.getLogger("IsletopiaFramework");
                logger.severe("Error while create bean " + clazz.getName());
                e.printStackTrace();
            }
            break;
        }
        return null;
    }


    public Set<Class<?>> getClassesShouldScan() {
        Set<Class<?>> targetClass = new HashSet<>();

        for (Class<?> clazz : classSet) {
            try {
                if (shouldScan(clazz)) {
                    targetClass.add(clazz);
                }
            } catch (Throwable e) {
                Logger.getLogger("IsletopiaFramework").warning("Error while scanning " + clazz);
                classSet.remove(clazz);
                e.printStackTrace();
            }
        }
        return targetClass;

    }

    public void createBeans(Collection<Class<?>> targetClass) {

        long nano = System.nanoTime();
        boolean lastEmpty = false;
        while (true) {

            int resolved = 0;
            for (Class<?> clazz : new HashSet<>(targetClass)) {
                try {
                    Object classByConstructor = tryCreateClassByConstructor(clazz);
                    if (classByConstructor != null) {
                        objects.add(classByConstructor);
                        resolved++;
                        targetClass.remove(clazz);
                    }
                } catch (Throwable e) {
                    Logger.getLogger("IsletopiaFramework").severe("Error while resolving " + clazz);
                    targetClass.remove(clazz);
                    e.printStackTrace();
                }
            }
            for (Object value : new HashSet<>(objects)) {
                try {
                    Method[] declaredMethods = value.getClass().getDeclaredMethods();
                    method:
                    for (Method declaredMethod : declaredMethods) {
                        if (invokedMethods.contains(declaredMethod)) {
                            continue;
                        }
                        if (declaredMethod.isAnnotationPresent(Bean.class)) {
                            ArrayList<Object> parameters = new ArrayList<>();
                            for (Parameter parameter : declaredMethod.getParameters()) {
                                Object object = getObject(parameter);
                                if (object != null) {
                                    parameters.add(object);
                                } else {
                                    break method;
                                }
                            }
                            Object object = declaredMethod.invoke(value, parameters.toArray());
                            invokedMethods.add(declaredMethod);
                            resolved++;
                            objects.add(object);
                        }
                    }
                } catch (Throwable e) {
                    Logger.getLogger("IsletopiaFramework").severe("Error while resolving " + value);
                    objects.remove(value);
                    e.printStackTrace();

                }
            }


            if (resolved == 0 || targetClass.isEmpty()) {
                if (lastEmpty) {
                    break;
                } else {
                    lastEmpty = true;
                }
            } else {
                lastEmpty = false;
            }
        }

        int size = targetClass.size();
        if (size > 0) {
            Logger.getLogger("IsletopiaFramework").severe("There are %d classes, which cannot be construct!".formatted(size));
            for (Class<?> aClass : targetClass) {
                Logger.getLogger("IsletopiaFramework").severe(aClass.getName());
            }
        }
        Logger.getLogger("IsletopiaFramework").info("%d classes was constructed successfully!".formatted(objects.size()));
        Logger.getLogger("IsletopiaFramework").info("%d ms was used.".formatted((System.nanoTime() - nano) / 1000000));

    }


    public Set<IBeanHandler> getBeanHandlers() {
        Set<IBeanHandler> beanHandlers = new HashSet<>();
        for (Object object : objects) {
            if (object instanceof IBeanHandler IBeanHandler) {
                beanHandlers.add(IBeanHandler);
            }
        }

        ArrayList<IBeanHandler> handlerArrayList = new ArrayList<>(beanHandlers);
        Logger.getLogger("IsletopiaFramework").info("%d bean handlers was constructed successfully!".formatted(handlerArrayList.size()));
        return beanHandlers;
    }

    public void applyAnnotationHandler() {
        for (Object object : objects) {
            if (object instanceof IAnnotationHandler) {
                IAnnotationHandler<Annotation> iAnnotationHandler = (IAnnotationHandler<Annotation>) object;
                if (object.getClass().isAnnotationPresent(AnnotationHandler.class)) {
                    Class<? extends Annotation> value = object.getClass().getAnnotation(AnnotationHandler.class).value();
                    for (Class<?> aClass : classSet) {
                        if (aClass.isAnnotationPresent(value)) {
                            Annotation annotation = aClass.getAnnotation(value);
                            iAnnotationHandler.handle(aClass, annotation);
                        }
                    }
                }
            }
        }
    }

    public void applyBeanHandler(Set<IBeanHandler> beanHandlers) {
        ArrayList<IBeanHandler> handlerArrayList = new ArrayList<>(beanHandlers);
        handlerArrayList.sort((o1, o2) -> {
            int p1 = 0;
            int p2 = 0;

            if (o1.getClass().isAnnotationPresent(BeanHandler.class)) {
                p1 = o1.getClass().getAnnotation(BeanHandler.class).value();
            }
            if (o2.getClass().isAnnotationPresent(BeanHandler.class)) {
                p2 = o2.getClass().getAnnotation(BeanHandler.class).value();
            }
            return p2 - p1;
        });


        for (IBeanHandler beanHandler : handlerArrayList) {
            for (Object value : objects) {
                try {
                    beanHandler.handle(value);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * 为@Bean注解的类创建对象并使用BeanHandler处理所有对象
     *
     * @throws Exception 异常
     */
    public void resolveBean() throws Exception {
        Logger.getLogger("IsletopiaFramework").info("Starting create beans...");
        createBeans(getClassesShouldScan());
        Logger.getLogger("IsletopiaFramework").info("Resolving field injects...");
        resolveFieldsInject();
        Logger.getLogger("IsletopiaFramework").info("Applying annotation handlers...");
        applyAnnotationHandler();
        Set<IBeanHandler> beanHandlers = getBeanHandlers();
        Logger.getLogger("IsletopiaFramework").info("Apply bean handlers...");
        applyBeanHandler(beanHandlers);
    }


    /**
     * 加载此 package 下的所有类
     */
    public Set<Class<?>> loadClass(ClassScanner classScanner, String packageName) throws Exception {
        Enumeration<JarEntry> entries = classScanner.getJarFile().entries();
        Set<Class<?>> set = new HashSet<Class<?>>();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String name = jarEntry.getName();
            if (name.contains("mixin")) {
                continue;
            }
            if (name.endsWith(".class")) {
                name = name.substring(0, name.length() - 6);
                name = name.replaceAll("/", ".");
                if (name.startsWith(packageName)) {
                    try {
                        Class<?> aClass = classScanner.loadClass(name);
                        if (aClass != getClass()) {
                            set.add(aClass);
                            classSet.add(aClass);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return set;
    }

}
