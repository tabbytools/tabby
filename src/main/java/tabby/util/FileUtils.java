package tabby.util;

import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import tabby.config.GlobalConfiguration;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

@Slf4j
public class FileUtils {

    /**
     *（target/org/xxx/xxx）
     * @param target
     * @return
     * @throws IOException
     */
    public static Map<String, String> getTargetDirectoryJarFiles(String target, boolean checkFatJar) throws IOException {
        Map<String, String> paths = new HashMap<>();
        Path path = Paths.get(target).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid jar path: " + path);
        }

        if(path.toFile().isFile()){
            String filename = path.toFile().getName();
            String fileMd5 = FileUtils.getFileMD5(filename);
            if(filename.endsWith(".class")){
                paths.put(fileMd5, path.getParent().toString());
            }else if(filename.endsWith(".war")){
                paths.putAll(unpackWarOrJarFiles(path, filename));
            }else if(filename.endsWith(".jar")){
                if(checkFatJar && isFatJar(path)){
                    paths.putAll(unpackWarOrJarFiles(path, filename));
                }else{
                    paths.put(fileMd5, path.toAbsolutePath().toString());
                }
            }
        }else{
            Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    String filename = path.getFileName().toString();
                    String fileMd5 = FileUtils.getFileMD5(filename);
                    if(filename.endsWith(".jar")){
                        if(checkFatJar && isFatJar(path)){
                            paths.putAll(unpackWarOrJarFiles(path, filename));
                        }else{
                            paths.put(fileMd5, path.toAbsolutePath().toString());
                        }
                    }else if(filename.endsWith(".class")){
                        paths.put(fileMd5, target);
                    } else if(filename.endsWith(".war")){
                        paths.putAll(unpackWarOrJarFiles(path, filename));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return paths;
    }

    /**
     * @param path
     * @return
     */
    public static boolean isFatJar(Path path){
        String file = path.toAbsolutePath().toString();
        try {
            JarFile jarFile = new JarFile(path.toFile());
            if(jarFile.getEntry("WEB-INF") != null
                    || jarFile.getEntry("BOOT-INF") != null){
                return true;
            }else{
                return false;
            }
        }
        catch (Exception e) {
            log.error("Something error with func.dealFatJar <{}>, just add this jar", file);
            return false;
        }
    }

    public static Map<String, String> unpackWarOrJarFiles(Path path, String filename) throws IOException {
        Map<String, String> paths = new HashMap<>();
        Path tmpDir = registerTempDirectory(filename);
        String bootInfClassesDir = String.join(File.separator, tmpDir.toString(), "BOOT-INF/classes");
        String webInfClassesDir = String.join(File.separator, tmpDir.toString(), "WEB-INF/classes");

        extract(path, tmpDir);
        paths.putAll(findLibTargets(tmpDir, "BOOT-INF/lib"));
        paths.putAll(findLibTargets(tmpDir, "WEB-INF/lib"));

        // get all classes from classes
        if(new File(bootInfClassesDir).exists()){
            paths.put(filename+"_bootinf_classes_"+ RandomStringUtils.randomAlphanumeric(5),
                    bootInfClassesDir);
        }
        if(new File(webInfClassesDir).exists()){
            paths.put(filename+"_webinf_classes_"+ RandomStringUtils.randomAlphanumeric(5),
                    webInfClassesDir);
        }

        return paths;
    }

    public static Map<String, String> findLibTargets(Path baseDir, String targetDir) throws IOException {
        Map<String, String> paths = new HashMap<>();
        if(baseDir.resolve(targetDir).toFile().exists()){
            Files.walkFileTree(baseDir.resolve(targetDir), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filepath = file.toAbsolutePath().toString();
                    String fileMd5 = FileUtils.getFileMD5(filepath);
                    if(filepath.endsWith(".jar")){
                        paths.put(fileMd5, filepath);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return paths;
    }

    public static void extract(Path jarPath, Path tmpDir) throws IOException {
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(jarPath))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                Path fullPath = tmpDir.resolve(jarEntry.getName());
                if (!jarEntry.isDirectory()
                        && (jarEntry.getName().endsWith(".class")
                        || jarEntry.getName().endsWith(".jar"))) {
                    Path dirName = fullPath.getParent();
                    if (dirName == null) {
                        throw new IllegalStateException("Parent of item is outside temp directory.");
                    }
                    if (!Files.exists(dirName)) {
                        Files.createDirectories(dirName);
                    }
                    try (OutputStream outputStream = Files.newOutputStream(fullPath)) {
                        copy(jarInputStream, outputStream);
                    }
                }
            }
        }
    }

    /**
     * Copy inputStream to outputStream. Neither stream is closed by this method.
     */
    public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[4096];
        int n;
        while ((n = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, n);
        }
    }

    public static boolean fileExists(String path){
        File file = new File(path);
        return file.exists();
    }


    public static Object getJsonContent(String path, Class<?> type){
        File file = new File(path);
        if(!file.exists()) return null;
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            return GlobalConfiguration.GSON.fromJson(reader, type);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void putJsonContent(String path, Object data){
        File file = new File(path);
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(GlobalConfiguration.GSON.toJson(data));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param directory temp directory
     * @return temp directory path
     * @throws IOException
     */
    public static Path registerTempDirectory(String directory) throws IOException {

        final Path tmpDir = Files.createTempDirectory(directory);
        // Delete the temp directory at shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteDirectory(tmpDir);
            } catch (IOException e) {
                log.error("Error cleaning up temp directory " + tmpDir.toString(), e);
            }
        }));

        return tmpDir;
    }

    /**
     * Recursively delete the directory root and all its contents
     * @param root Root directory to be deleted
     */
    public static void deleteDirectory(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void createDirectory(String path){
        File file = new File(path);
        if(file.mkdirs()){
            log.info("Create directory {} success!", path);
        }
    }

    public static String getWinPath(String path){
        if(JavaVersion.isWin()){
            path = "/"+path.replace("\\", "/");
        }
        return path;
    }

    public static String getFileMD5(String filepath){
        return getFileMD5(new File(filepath));
    }

    public static String getFileMD5(File file){
        try {
            return com.google.common.io.Files.hash(file, Hashing.md5()).toString();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

}
