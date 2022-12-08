package tabby.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ClassLoaderUtils {

    public static ClassLoader getClassLoader(List<String> jarPaths) throws IOException {
        final List<URL> classPathUrls = new ArrayList<>();
        for (String jarPath : jarPaths) {
            Path jarRealPath = Paths.get(jarPath).toAbsolutePath();
            if (!Files.exists(jarRealPath)) {
                throw new IllegalArgumentException("Path \"" + jarPath + "\" is not a path to a file.");
            }

            classPathUrls.add(jarRealPath.toUri().toURL());
        }

        return new URLClassLoader(classPathUrls.toArray(new URL[0]));
    }

    public static List<String> getAllClasses(List<String> targets){
        List<String> runtimeClasses = null;
        try {
            ClassResourceEnumerator classResourceEnumerator =
                    new ClassResourceEnumerator(ClassLoaderUtils.getClassLoader(targets));
            runtimeClasses = (List<String>) classResourceEnumerator.getTargetClassLoaderClasses();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return runtimeClasses;
    }

}
