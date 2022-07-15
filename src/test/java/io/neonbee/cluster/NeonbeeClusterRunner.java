package io.neonbee.cluster;

import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static io.neonbee.test.helper.FileSystemHelper.deleteRecursiveBlocking;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.io.FileUtils;

import com.sap.cds.impl.util.Pair;

import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public class NeonbeeClusterRunner {
    private final Map<Process, StringBuilder> processes = new HashMap<>();

    private static Path workDirPath;

    private static String workDir;

    static String[] args;

    private static final Logger LOGGER = LoggerFactory.getLogger(NeonbeeClusterRunner.class);

    static final int STARTING_PORT = 8081;

    private static final int CLUSTER_STARTING_PORT = 10000;

    static void init() throws IOException {
        workDirPath = createTempDirectory();
        workDir = workDirPath.toAbsolutePath().toString();
        args = new String[] { "-no-cp", "-cl", "-cc", "hazelcast-localtcp.xml" };
    }

    static void cleanUp() {
        deleteRecursiveBlocking(workDirPath);
    }

    void clean() {
        processes.forEach((k, v) -> LOGGER.info("output from process " + k.pid() + " " + v.toString()));
        processes.keySet().forEach(Process::destroy);
    }

    Pair<Process, StringBuilder> startProcess(int instanceNumber) throws IOException {
        String classPath = System.getProperty("java.class.path");
        var sb = new StringBuilder();
        String[] params = { "java", "-cp", classPath, "io.neonbee.Launcher" };
        var paramsList = new ArrayList<>(List.of(params));
        var tmpFolder = workDir + '/' + instanceNumber;
        var moduleDir = tmpFolder + '/' + WorkingDirectoryBuilder.MODULES_DIR;
        FileUtils.forceMkdir(new File(moduleDir));

        paramsList.addAll(List.of("-port", Integer.toString(STARTING_PORT + instanceNumber), "-clp",
                Integer.toString(CLUSTER_STARTING_PORT + instanceNumber), "-cwd", tmpFolder));
        paramsList.addAll(List.of(args));
        var processBuilder = new ProcessBuilder(paramsList);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        LOGGER.info("started process " + process.pid());
        new Thread(() -> {
            try {
                try (BufferedReader output =
                        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {

                    processes.put(process, sb);
                    while (process.isAlive()) {
                        sb.append((char) output.read());
                    }
                    sb.append("exit code ").append(process.exitValue());
                }
            } catch (IOException e) {
                LOGGER.error(e);
            }
        }).start();

        return Pair.of(process, sb);
    }

    public static Path getWorkDirPath(int instanceNumber) {
        return Path.of(workDir + '/' + instanceNumber);
    }
}
