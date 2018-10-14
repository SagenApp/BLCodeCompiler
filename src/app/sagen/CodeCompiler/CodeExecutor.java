package app.sagen.CodeCompiler;


import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CodeExecutor {

    private static CodeExecutor codeExecutor;

    public static CodeExecutor get() {
        if(codeExecutor == null) codeExecutor = new CodeExecutor();
        return codeExecutor;
    }

    private final String CLASS_BODY = "public class CodeWrapperClass {public static void methodBody(){\n%code%\n}}";

    private LinkedBlockingQueue<String> codeToExecute = new LinkedBlockingQueue<>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;

    public void startExecutor() {
        if(running) return; // only one instance running...
        synchronized (CodeExecutor.class) {
            running = true;
        }
        executorService.execute(() -> {
            while(isRunning()) {
                try {
                    String code = codeToExecute.poll(1, TimeUnit.SECONDS);
                    if(code != null) compileAndRunCode(code);
                } catch (InterruptedException ignored) {
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public boolean isRunning() {
        synchronized (CodeExecutor.class) {
            return running;
        }
    }

    public void stopExecutor() {
        running = false;
        executorService.shutdown();
    }

    public void addCodeToRun(String code) {
        try {
            codeToExecute.put(code);
        } catch (InterruptedException ignored) {}
    }

    private void compileAndRunCode(String methodBody) throws Exception {
        Path sourcePath = Paths.get(System.getProperty("java.io.tmpdir"), "CodeWrapperClass.java");
        Files.write(sourcePath, CLASS_BODY.replace("%code%", methodBody).getBytes(StandardCharsets.UTF_8));
        ToolProvider.getSystemJavaCompiler().run(null, null, null, sourcePath.toFile().getAbsolutePath());
        URL classUrl = sourcePath.getParent().toFile().toURI().toURL();
        Class<?> clazz = Class.forName("CodeWrapperClass", true, URLClassLoader.newInstance(new URL[]{classUrl}));
        clazz.getMethod("methodBody").invoke(null);
    }

    public static void main(String... args) throws Exception {

        CodeExecutor codeExecutor = CodeExecutor.get();
        codeExecutor.startExecutor();

        codeExecutor.addCodeToRun("" +
                "java.util.ArrayList<String> list = new java.util.ArrayList<String>();" +
                "list.add(\"Hello\");" +
                "list.add(\", \");" +
                "list.add(\"World\");" +
                "System.out.println(String.join(\"\", list));");

        codeExecutor.addCodeToRun("" +
                "java.util.ArrayList<String> list = new java.util.ArrayList<String>();" +
                "list.add(\"Banan \");" +
                "list.add(\"Eple \");" +
                "list.add(\"Mango\");" +
                "System.out.println(String.join(\"\", list));");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {}

        codeExecutor.stopExecutor();
    }
}