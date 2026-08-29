package tech.onetap.util.neuro.rotation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Подготовка Python-окружения тренера: интерпретатор и библиотеки
 * скачиваются сами, руками ставить ничего не нужно.
 *
 * Поиск интерпретатора по приоритету:
 *   1. уже собранное окружение: venv (.options/ai/venv) или управляемый
 *      runtime (.options/ai/python). Живость проверяем пробным запуском
 *      --version; битое (исходный Python удалили) — вычищаем и собираем заново.
 *   2. системный python / py / python3 из PATH → создаём из него venv,
 *      системную установку не трогаем.
 *   3. На Windows при полном отсутствии Python — скачиваем с python.org
 *      embeddable-сборку (~11 МБ), включаем в ней site-packages (правка ._pth)
 *      и bootstrap'им pip через get-pip.py.
 *
 * На Linux/macOS Python почти всегда есть из коробки, поэтому скачивание
 * делаем только для Windows; на не-Windows без Python останутся ручные
 * инструкции от TrainingLauncher.
 *
 * Библиотеки из requirements.txt ставим в своё окружение. torch тянем отдельно
 * из CPU-индекса pytorch (~200 МБ вместо ~2.5 ГБ CUDA-сборки с PyPI — inference
 * и так на CPU через ONNX Runtime). Если в окружении уже вручную поставили
 * полную CUDA-сборку, pip её не перебьёт (запрос без версии).
 *
 * Хеш requirements.txt храним в .req-stamp окружения: пока он совпадает,
 * pip не запускаем повторно.
 */
public final class TrainerEnvironment {

    private static final Path AI_DIR = AIRotationManager.getAiDir();
    private static final Path RUNTIME_DIR = AI_DIR.resolve("python");
    private static final Path VENV_DIR = AI_DIR.resolve("venv");

    private static final String PYTHON_EMBED_VERSION = "3.11.9";
    private static final String PYTHON_EMBED_BASE =
            "https://www.python.org/ftp/python/" + PYTHON_EMBED_VERSION + "/";
    private static final String GET_PIP_URL = "https://bootstrap.pypa.io/get-pip.py";
    private static final String TORCH_CPU_INDEX = "https://download.pytorch.org/whl/cpu";

    private static volatile Process activeProcess = null;

    private TrainerEnvironment() {
    }

    /**
     * Готовит окружение: интерпретатор + библиотеки из requirements.txt.
     * Блокирующий вызов, выполнять только в фоновом потоке. Прогресс и ошибки
     * репортим через consumer (в игре это чат).
     */
    public static Path ensure(Path requirements, Consumer<String> progress) {
        Path python = existing();
        if (python == null) {
            python = create(progress);
        }
        if (python == null) {
            progress.accept("§cPython не найден и не скачался (нет сети?)");
            return null;
        }
        if (!ensureLibraries(python, requirements, progress)) {
            return null;
        }
        return python;
    }

    /**
     * Строит ProcessBuilder с общими env-настройками вызовов Python.
     * TrainingLauncher добавляет к этому рабочую папку и PYTHONPATH.
     */
    public static ProcessBuilder newProcess(List<String> command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUNBUFFERED", "1");
        return pb;
    }

    public static boolean isProvisioning() {
        Process p = activeProcess;
        return p != null && p.isAlive();
    }

    public static boolean cancelProvisioning() {
        Process p = activeProcess;
        if (p != null && p.isAlive()) {
            p.destroy();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Интерпретатор
    // ------------------------------------------------------------------

    private static Path existing() {
        Path venv = venvPython();
        if (isHealthy(venv)) return venv;
        if (Files.exists(venv)) deleteDir(VENV_DIR);

        Path runtime = RUNTIME_DIR.resolve("python.exe");
        if (isHealthy(runtime)) return runtime;
        if (Files.exists(runtime)) deleteDir(RUNTIME_DIR);

        return null;
    }

    private static Path create(Consumer<String> progress) {
        String system = findSystemPython();
        if (system != null) {
            progress.accept("§7Системный Python найден (§f" + system + "§7), собираю venv");
            return createVenv(system, progress);
        }
        if (isWindows()) {
            return provisionRuntime(progress);
        }
        return null;
    }

    private static Path createVenv(String systemPython, Consumer<String> progress) {
        deleteDir(VENV_DIR);
        // Вывод venv пропускаем в чат целиком: на Debian/Ubuntu без python3-venv
        // именно там лежит внятная подсказка про apt.
        int exit = runCommand(List.of(systemPython, "-m", "venv", VENV_DIR.toString()),
                line -> true, progress);
        if (exit != 0) {
            progress.accept("§cvenv не собрался (код " + exit + ")");
            return null;
        }
        Path python = venvPython();
        if (!isHealthy(python)) {
            deleteDir(VENV_DIR);
            progress.accept("§cvenv собрался, но интерпретатор не отвечает");
            return null;
        }
        progress.accept("§avenv готов: §f" + VENV_DIR.toAbsolutePath());
        return python;
    }

    private static Path provisionRuntime(Consumer<String> progress) {
        String arch = detectEmbedArch();
        String zipName = "python-" + PYTHON_EMBED_VERSION + "-embed-" + arch + ".zip";
        Path staging = AI_DIR.resolve(".python-staging");

        try {
            progress.accept("§7Python в PATH нет — скачиваю embeddable §f"
                    + PYTHON_EMBED_VERSION + "§7 (~11 МБ)...");
            deleteDir(staging);
            Files.createDirectories(staging);

            Path zip = staging.resolve(zipName);
            download(URI.create(PYTHON_EMBED_BASE + zipName).toURL(), zip, progress);
            extractZip(zip, staging);
            Files.delete(zip);

            enableSitePackages(staging);

            deleteDir(RUNTIME_DIR);
            Files.move(staging, RUNTIME_DIR);
            Path python = RUNTIME_DIR.resolve("python.exe");
            if (!isHealthy(python)) {
                throw new IOException("скачанный python.exe не запускается");
            }
            progress.accept("§aPython §f" + PYTHON_EMBED_VERSION + "§a готов: §f"
                    + RUNTIME_DIR.toAbsolutePath());

            bootstrapPip(python, progress);
            return python;
        } catch (IOException e) {
            deleteDir(staging);
            progress.accept("§cPython не скачался: §f" + e.getMessage());
            return null;
        }
    }

    /**
     * Embeddable-сборка отключает site-packages через ._pth — pip без этого
     * ставит пакеты в никуда. Раскомментируем "import site".
     */
    private static void enableSitePackages(Path runtimeDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(runtimeDir, "*._pth")) {
            for (Path pth : stream) {
                List<String> lines = Files.readAllLines(pth);
                boolean patched = false;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).trim().equals("#import site")) {
                        lines.set(i, "import site");
                        patched = true;
                    }
                }
                if (!patched && lines.stream().noneMatch(l -> l.trim().equals("import site"))) {
                    lines.add("import site");
                }
                Files.write(pth, lines);
                return;
            }
        }
    }

    private static void bootstrapPip(Path python, Consumer<String> progress) throws IOException {
        if (pipAvailable(python)) return;

        progress.accept("§7Ставлю pip...");
        Path getPip = AI_DIR.resolve("get-pip.py");
        try {
            download(URI.create(GET_PIP_URL).toURL(), getPip, progress);
            int exit = runCommand(
                    List.of(python.toString(), getPip.toString(), "--no-warn-script-location"),
                    line -> true, progress);
            if (exit != 0 || !pipAvailable(python)) {
                throw new IOException("pip не встал (код " + exit + ")");
            }
        } finally {
            Files.deleteIfExists(getPip);
        }
    }

    private static Path venvPython() {
        return isWindows()
                ? VENV_DIR.resolve("Scripts").resolve("python.exe")
                : VENV_DIR.resolve("bin").resolve("python");
    }

    private static String detectEmbedArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        if (arch.contains("64")) return "amd64";
        return "win32";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ------------------------------------------------------------------
    // Библиотеки
    // ------------------------------------------------------------------

    private static boolean ensureLibraries(Path python, Path requirements, Consumer<String> progress) {
        String hash;
        Path stamp = stampFor(python);
        try {
            hash = sha256(requirements);
        } catch (IOException | NoSuchAlgorithmException e) {
            progress.accept("§cНе читается requirements.txt: §f" + e.getMessage());
            return false;
        }

        try {
            if (Files.exists(stamp) && Files.readString(stamp).trim().equals(hash)
                    && pipAvailable(python)) {
                return true;
            }
        } catch (IOException ignored) {
        }

        progress.accept("§7Ставлю библиотеки из requirements.txt...");
        progress.accept("§7torch — CPU-сборка (~200 МБ вместо ~2.5 ГБ CUDA)");
        progress.accept("§7Крупная загрузка без прогресс-бара, может занять несколько минут");

        // pip не в трее обрезан до вех: Collecting/Downloading/Successfully/error
        // — иначе спам в чат сотней строк.
        Predicate<String> milestones = line -> {
            String t = line.trim();
            return t.startsWith("Collecting") || t.startsWith("Downloading")
                    || t.startsWith("Using cached") || t.startsWith("Installing collected")
                    || t.startsWith("Successfully installed")
                    || t.toLowerCase().contains("error");
        };

        int exit = runCommand(pip(python, "torch", "--index-url", TORCH_CPU_INDEX), milestones, progress);
        if (exit != 0) {
            progress.accept("§cpip install torch упал (код " + exit + ")");
            return false;
        }

        exit = runCommand(pip(python, "-r", requirements.toString()), milestones, progress);
        if (exit != 0) {
            progress.accept("§cpip install -r requirements.txt упал (код " + exit + ")");
            return false;
        }

        try {
            Files.writeString(stamp, hash);
        } catch (IOException ignored) {
        }

        progress.accept("§aБиблиотеки готовы");
        return true;
    }

    private static Path stampFor(Path python) {
        Path dir = python.getParent();
        String leaf = dir.getFileName().toString();
        if (leaf.equalsIgnoreCase("Scripts") || leaf.equalsIgnoreCase("bin")) {
            dir = dir.getParent();
        }
        return dir.resolve(".req-stamp");
    }

    private static List<String> pip(Path python, String... args) {
        List<String> command = new ArrayList<>();
        command.add(python.toString());
        command.add("-m");
        command.add("pip");
        command.add("--disable-pip-version-check");
        command.add("--no-input");
        command.add("install");
        command.add("--no-warn-script-location");
        command.addAll(List.of(args));
        return command;
    }

    // ------------------------------------------------------------------
    // Запуск процессов / пробы
    // ------------------------------------------------------------------

    /**
     * Запускает команду и стримит подходящие по фильтру строки вывода.
     * Процесс публикуем в activeProcess, чтобы .ai cancel мог его убить.
     */
    private static int runCommand(List<String> command, Predicate<String> filter, Consumer<String> progress) {
        try {
            Process process = newProcess(command).start();
            activeProcess = process;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (filter.test(line)) progress.accept(line);
                }
            }
            return process.waitFor();
        } catch (IOException e) {
            progress.accept("§cНе запустился процесс: §f" + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } finally {
            activeProcess = null;
        }
    }

    private static boolean isHealthy(Path python) {
        String out = probeOutput(python.toString(), "--version");
        return out != null && out.contains("Python 3.");
    }

    private static boolean pipAvailable(Path python) {
        return probeOutput(python.toString(), "-m", "pip", "--version") != null;
    }

    private static String findSystemPython() {
        for (String candidate : new String[]{"python", "py", "python3"}) {
            String out = probeOutput(candidate, "--version");
            if (out != null && out.contains("Python 3.")) return candidate;
        }
        return null;
    }

    /**
     * Запускает команду и возвращает вывод (stdout+stderr) при exit 0,
     * null иначе. Вывод всегда вычитываем — иначе на Windows процесс
     * способен повиснуть на заполненном пайпе.
     */
    private static String probeOutput(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String out;
            try (InputStream in = process.getInputStream()) {
                out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            return process.waitFor() == 0 ? out : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Скачивание / распаковка
    // ------------------------------------------------------------------

    private static void download(URL url, Path target, Consumer<String> progress) throws IOException {
        HttpURLConnection connection = open(url);
        long total = connection.getContentLengthLong();
        Path temp = target.resolveSibling(target.getFileName() + ".part");

        try (InputStream in = connection.getInputStream();
                OutputStream out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            long read = 0;
            long reported = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                read += n;
                if (read - reported >= 4 * 1024 * 1024) {
                    reported = read;
                    String size = total > 0 ? mb(read) + "/" + mb(total) : mb(read);
                    progress.accept("§7Загружаю: §f" + size + " МБ");
                }
            }
        } finally {
            connection.disconnect();
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Открывает соединение, вручную проходя редиректы: встроенный follow
     * HttpURLConnection не умеет cross-protocol (http→https).
     */
    private static HttpURLConnection open(URL url) throws IOException {
        URL current = url;
        for (int i = 0; i < 5; i++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "OneTapClient");
            int code = connection.getResponseCode();

            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("редирект без Location");
                try {
                    current = current.toURI().resolve(location).toURL();
                } catch (Exception e) {
                    throw new IOException("битый Location: " + location);
                }
                continue;
            }
            if (code != 200) throw new IOException("HTTP " + code);
            return connection;
        }
        throw new IOException("слишком много редиректов");
    }

    private static void extractZip(Path zip, Path dir) throws IOException {
        Path dirPath = dir.toAbsolutePath().normalize();
        dir = dirPath;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path out = dir.resolve(entry.getName()).normalize();
                if (!out.startsWith(dirPath)) {
                    throw new IOException("zip-slip: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        int n;
                        while ((n = in.read(buffer)) != -1) os.write(buffer, 0, n);
                    }
                }
                in.closeEntry();
            }
        }
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void deleteDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    private static String mb(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024.0));
    }
}
