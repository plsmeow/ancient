package meow.ancient.util.neuro.rotation;

import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Управление Python-окружением и запуском обучения моделей Neuro.
 */
public final class NeuroTrainer {
    private static final String RESOURCE_TRAINER_DIR = "assets/ancient/neuro/trainer/";
    private static final String COMMON_SCRIPT = "common.py";
    private static final String TRAIN_SCRIPT = "train_aura.py";

    private static volatile boolean setupRunning;
    private static volatile boolean installing;
    private static volatile String lastError;

    private NeuroTrainer() {
    }

    public static File findPython() {
        // Проверяем явный путь из свойств
        String prop = System.getProperty("python.path");
        if (prop != null) {
            File f = new File(prop);
            if (f.isFile() && f.canExecute()) return f;
        }

        // Проверяем стандартные системные пути Linux/Mac/Win
        String[] possiblePaths = new String[]{
                "/usr/bin/python3",
                "/usr/local/bin/python3",
                "/usr/bin/python",
                "python3",
                "python"
        };

        for (String p : possiblePaths) {
            try {
                Process proc = new ProcessBuilder(p, "--version").start();
                if (proc.waitFor() == 0) {
                    if (p.startsWith("/")) {
                        return new File(p);
                    } else {
                        // Поиск в PATH
                        String pathEnv = System.getenv("PATH");
                        if (pathEnv != null) {
                            for (String dir : pathEnv.split(File.pathSeparator)) {
                                File file = new File(dir, p);
                                if (file.isFile() && file.canExecute()) {
                                    return file;
                                }
                            }
                        }
                        return new File(p);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean isEnvironmentReady() {
        return hasModule("numpy") && hasModule("torch");
    }

    public static boolean isInstalling() {
        return installing;
    }

    public static String getLastError() {
        return lastError;
    }

    public static boolean hasModule(String module) {
        File py = findPython();
        if (py == null) return false;
        try {
            Process p = new ProcessBuilder(py.getAbsolutePath(), "-c", "import " + module).start();
            return p.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Process startTraining(File python, Path dataDir, Path outPath, String epochs) throws Exception {
        // Проверяем наличие tools/neuro/train_neuro.py на диске
        Path toolScript = Path.of("tools/neuro/train_neuro.py");
        if (Files.isRegularFile(toolScript)) {
            List<String> cmd = new ArrayList<>(List.of(
                    python.getAbsolutePath(),
                    toolScript.toAbsolutePath().toString(),
                    "--data", dataDir.toString(),
                    "--out", outPath.toString()
            ));
            if (epochs != null) {
                cmd.add("--epochs");
                cmd.add(epochs);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");
            return pb.start();
        }

        // Иначе запускаем встроенный скрипт через stdin
        String script = buildInlinePythonScript();
        List<String> cmd = new ArrayList<>(List.of(
                python.getAbsolutePath(),
                "-u",
                "-",
                "--data", dataDir.toString(),
                "--out", outPath.toString()
        ));
        if (epochs != null) {
            cmd.add("--epochs");
            cmd.add(epochs);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");
        Process proc = pb.start();
        try (OutputStream os = proc.getOutputStream()) {
            os.write(script.getBytes(StandardCharsets.UTF_8));
        }
        return proc;
    }

    private static String buildInlinePythonScript() {
        String commonSrc = readTrainerScript(COMMON_SCRIPT);
        String trainSrc = readTrainerScript(TRAIN_SCRIPT);
        String commonB64 = Base64.getEncoder().encodeToString(commonSrc.getBytes(StandardCharsets.UTF_8));
        String trainB64 = Base64.getEncoder().encodeToString(trainSrc.getBytes(StandardCharsets.UTF_8));
        String homeDir = NeuroModel.getNeuroDir().resolve("trainer").toString().replace("\\", "\\\\");

        return "import base64, sys, types\n"
                + "__rs_home = \"" + homeDir + "\"\n"
                + "__rs_common = types.ModuleType(\"common\")\n"
                + "__rs_common.__file__ = __rs_home + \"/common.py\"\n"
                + "exec(compile(base64.b64decode(\"" + commonB64 + "\").decode(\"utf-8\"), \"common.py\", \"exec\"), __rs_common.__dict__)\n"
                + "sys.modules[\"common\"] = __rs_common\n"
                + "__rs_globals = {\"__name__\": \"__main__\", \"__file__\": __rs_home + \"/train_aura.py\"}\n"
                + "exec(compile(base64.b64decode(\"" + trainB64 + "\").decode(\"utf-8\"), \"train_aura.py\", \"exec\"), __rs_globals)\n";
    }

    private static String readTrainerScript(String name) {
        Path path = Path.of("tools/neuro/" + name);
        try {
            if (Files.isRegularFile(path, new LinkOption[0])) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        InputStream stream = NeuroTrainer.class.getClassLoader().getResourceAsStream(RESOURCE_TRAINER_DIR + name);
        if (stream != null) {
            try (InputStream is = stream) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return "";
    }
}
