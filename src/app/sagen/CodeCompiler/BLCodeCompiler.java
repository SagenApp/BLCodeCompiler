package app.sagen.CodeCompiler;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>This class is responsible for dynamically compiling and loading java classes. Can be used for custom behaviour in game
 * and for pretty much all code that should be "hotswappable" at runtime.</p>
 *
 * <p>Note:<br>
 * The classes generated should in theory be unloaded if all references to that class is removed however it is not
 * guaranteed. Excessive use of this can lead to a memory leak and memory overhead.</p>
 *
 * <br>
 * Example:<br>
 * <code>
 *       MyClass obj = (MyClass) BLCodeCompiler.get().compile(sourcePath).getConstructor().newInstance();<br>
 *       obj.doStuff();
 * </code>
 *
 * @author SagenKoder
 */
public class BLCodeCompiler {

    private static BLCodeCompiler instance;

    public static BLCodeCompiler get() {
        if(instance == null) instance = new BLCodeCompiler();
        return instance;
    }

    /**
     * <p>Compiles the given source file and returns the generated Class.
     * The given source should be named "<code>ClassName</code>" as this name will be replaced with a unique random name.</p>
     *
     * <p>Note:<br>
     * The classes generated should in theory be unloaded if all references to that class is removed however it is not
     * guaranteed. Excessive use of this can lead to a memory leak and memory overhead.</p>
     *
     * @param classBodyPath
     *      The path to the file containing the source code. The name of the source class should be "<code>ClassName</code>"!
     * @return
     *      The compiled and loaded Class file. This can be used with reflection to create a new instance of the class.
     * @throws BLCodeCompilerException
     *      Every exception thrown by this method will be wrapped in this exception.
     */
    public Class compile(Path classBodyPath, boolean printSourceInError) throws BLCodeCompilerException {
        String sourceCode = loadSourceFile(classBodyPath);
        String generatedClassname = generateClassName();
        sourceCode = sourceCode
                .replace("ClassName", generatedClassname)
                .replace("\t", "    ");
        return compile(sourceCode, generatedClassname, printSourceInError);
    }

    /**
     * <p>Compiles the given source code String and returns the generated Class.
     * The given source should be named "<code>ClassName</code>" as this name will be replaced with a unique random name.</p>
     *
     * <p>Note:<br>
     * The classes generated should in theory be unloaded if all references to that class is removed however it is not
     * guaranteed. Excessive use of this can lead to a memory leak and memory overhead.</p>
     *
     * @param sourceCode
     *      The String containing the source code. The name of the source class should be "<code>ClassName</code>"!
     * @return
     *      The compiled and loaded Class file. This can be used with reflection to create a new instance of the class.
     * @throws BLCodeCompilerException
     *      Every exception thrown by this method will be wrapped in this exception.
     */
    public Class compile(String sourceCode, boolean printSourceInError) throws BLCodeCompilerException {
        String generatedClassname = generateClassName();
        sourceCode = sourceCode
                .replace("ClassName", generatedClassname)
                .replace("\t", "    ");
        return compile(sourceCode, generatedClassname, printSourceInError);
    }

    private Class compile(String sourceCode, String className, boolean printSourceInError) throws BLCodeCompilerException {
        try {
            // write the class to tmp
            Path sourcePath = Paths.get(System.getProperty("java.io.tmpdir"), className + ".java");
            Files.write(sourcePath, sourceCode.getBytes(StandardCharsets.UTF_8));

            // setup classpath
            List<File> fileList = new ArrayList<>();
            fileList.add(new File(System.getProperty("java.class.path")));
            // add every file in plugins/ folder to classpath
            for (Path path : Files.newDirectoryStream(Paths.get("plugins/"))) {
                if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                    fileList.add(path.toFile());
                }
            }

            // compiler helper classes
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager standardJavaFileManager = compiler.getStandardFileManager(null, null, null);

            // setup file manager
            try {
                standardJavaFileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(sourcePath.getParent().toFile()));
                standardJavaFileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.singletonList(sourcePath.toFile()));
                standardJavaFileManager.setLocation(StandardLocation.CLASS_PATH, fileList);
            } catch (IOException e) {
                throw new BLCodeCompilerException("Invalid .class directory!", e);
            }

            // the compilation unit
            Iterable<? extends JavaFileObject> compilationUnit = standardJavaFileManager.getJavaFileObjectsFromFiles(Collections.singletonList(sourcePath.toFile()));

            // create compilation task
            JavaCompiler.CompilationTask compilationTask = compiler.getTask(null, standardJavaFileManager, diagnostics, null, null, compilationUnit);

            // run the compiler, if not successful -> print error and return
            if (!compilationTask.call()) {
                StringBuilder exceptionBody = new StringBuilder("Exception while compiling java code!\n\nError on the following lines: ");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    exceptionBody.append(String.format("%d:%d", diagnostic.getLineNumber(), diagnostic.getColumnNumber()));
                }
                if(printSourceInError)
                    exceptionBody.append("\nSource code that failed:\n").append(addLinenumbersToSource(sourceCode));
                throw new BLCodeCompilerException(exceptionBody.toString());
            }

            // loadClassBinary class
            return loadClassBinary(sourcePath, className);

        } catch (BLCodeCompilerException e) {
            throw e;
        } catch (Exception e) {
            throw new BLCodeCompilerException("Exception while compiling and loading code!", e);
        } finally {
            try {
                // delete tmp files
                Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir"), className + ".java"));
                Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir"), className + ".class"));
            } catch (Exception ignored) {}
        }
    }

    private String addLinenumbersToSource(String sourceCode) {
        StringBuilder sb = new StringBuilder("Line: 1    ");
        int line = 2;
        for(char c : sourceCode.toCharArray()) {
            if(c == '\n') {
                sb.append("\nLine: ").append(line++).append("    ");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String generateClassName() {
        return "BLClass_" + Long.toHexString(System.currentTimeMillis());
    }

    private String loadSourceFile(Path path) throws BLCodeCompilerException {
        try {
            return String.join("\n", Files.readAllLines(path));
        } catch (IOException e) {
            throw new BLCodeCompilerException("Could not load source from " + path.toString() + "!", e);
        }
    }

    private Class loadClassBinary(Path binaryPath, String className) throws BLCodeCompilerException {
        URLClassLoader classLoader = null;
        try {
            URL classUrl = binaryPath.getParent().toFile().toURI().toURL();
            classLoader = new URLClassLoader(new URL[]{classUrl}, this.getClass().getClassLoader());
            return Class.forName(className, true, classLoader);
        } catch (Exception e) {
            throw new BLCodeCompilerException("Exception while loading binary class file!", e);
        } finally {
            try {
                if (classLoader != null) classLoader.close();
            } catch (Exception ignored) {}
        }
    }

}
