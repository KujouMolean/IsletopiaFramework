package com.molean.isletopia.framework;

import java.util.jar.JarFile;

public interface ClassScanner {
    Class<?> loadClass(String path) throws Exception;

    JarFile getJarFile() throws Exception;
}
