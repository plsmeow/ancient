package tech.onetap.ancientloader;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AncientLoader implements PreLaunchEntrypoint {
	private static final String RELEASE_API_URL = "https://api.github.com/repos/plsmeow/ancient/releases/latest";
	private static final String TARGET_ASSET_NAME = "onetap-1.0.0.jar";
	private static final Pattern ASSET_PATTERN = Pattern.compile(
			"\\{[^{}]*?\\\"name\\\"\\s*:\\s*\\\"" + Pattern.quote(TARGET_ASSET_NAME) + "\\\"[^{}]*?\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
			Pattern.DOTALL
	);
	private static final Pattern ENTRYPOINTS_PATTERN = Pattern.compile("\\\"entrypoints\\\"\\s*:\\s*\\{(.*?)\\}\\s*,", Pattern.DOTALL);
	private static final Pattern ENTRYPOINT_PATTERN = Pattern.compile("\\\"(main|client|server)\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
	private static final Pattern CLASS_PATTERN = Pattern.compile("\\\"([^\\\"]+)\\\"");

	@Override
	public void onPreLaunch() {
		try {
			Path jarPath = downloadLatestAncientJar();
			addToClasspath(jarPath);
			invokeEntrypoints(jarPath);
			System.out.println("[Ancient Loader] Loaded " + TARGET_ASSET_NAME + " from " + jarPath);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to load " + TARGET_ASSET_NAME + " from latest ancient release", exception);
		}
	}

	private static Path downloadLatestAncientJar() throws IOException, InterruptedException {
		Path cacheDir = FabricLoader.getInstance().getGameDir().resolve(".ancient-loader").resolve("cache");
		Files.createDirectories(cacheDir);

		Path target = cacheDir.resolve(TARGET_ASSET_NAME);
		URI downloadUri = findLatestAssetDownloadUrl();

		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		HttpRequest request = HttpRequest.newBuilder(downloadUri)
				.header("Accept", "application/octet-stream")
				.header("User-Agent", "ancient-loader")
				.GET()
				.build();

		Path temp = Files.createTempFile(cacheDir, "ancient-loader-", ".jar.tmp");
		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() / 100 != 2) {
				throw new IOException("GitHub asset download failed with HTTP " + response.statusCode());
			}

			try (InputStream body = response.body()) {
				Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
			}

			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return target;
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	private static URI findLatestAssetDownloadUrl() throws IOException, InterruptedException {
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASE_API_URL))
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", "ancient-loader")
				.GET()
				.build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() / 100 != 2) {
			throw new IOException("GitHub release lookup failed with HTTP " + response.statusCode());
		}

		Matcher matcher = ASSET_PATTERN.matcher(response.body());
		if (!matcher.find()) {
			throw new IOException("Latest ancient release does not contain " + TARGET_ASSET_NAME);
		}

		return URI.create(unescapeJson(matcher.group(1)));
	}

	private static void addToClasspath(Path jarPath) throws Exception {
		Class<?> launcherBaseClass = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
		Method getLauncher = launcherBaseClass.getDeclaredMethod("getLauncher");
		Object launcher = getLauncher.invoke(null);

		Method addToClassPath = launcher.getClass().getMethod("addToClassPath", Path.class, String[].class);
		addToClassPath.invoke(launcher, jarPath, new String[0]);
	}

	private static void invokeEntrypoints(Path jarPath) throws Exception {
		try (JarFile jarFile = new JarFile(jarPath.toFile())) {
			JarEntry modJson = jarFile.getJarEntry("fabric.mod.json");
			if (modJson == null) {
				throw new IOException(TARGET_ASSET_NAME + " does not contain fabric.mod.json");
			}

			String json;
			try (InputStream inputStream = jarFile.getInputStream(modJson)) {
				json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			}

			for (String className : findEntrypointClasses(json)) {
				invokeEntrypoint(className);
			}
		}
	}

	private static List<String> findEntrypointClasses(String fabricModJson) {
		Matcher entrypointsMatcher = ENTRYPOINTS_PATTERN.matcher(fabricModJson);
		if (!entrypointsMatcher.find()) {
			return List.of();
		}

		List<String> classNames = new ArrayList<>();
		Matcher entrypointMatcher = ENTRYPOINT_PATTERN.matcher(entrypointsMatcher.group(1));
		while (entrypointMatcher.find()) {
			String type = entrypointMatcher.group(1).toLowerCase(Locale.ROOT);
			if ("server".equals(type) && FabricLoader.getInstance().getEnvironmentType().name().equals("CLIENT")) {
				continue;
			}

			Matcher classMatcher = CLASS_PATTERN.matcher(entrypointMatcher.group(2));
			while (classMatcher.find()) {
				classNames.add(unescapeJson(classMatcher.group(1)));
			}
		}

		return classNames;
	}

	private static void invokeEntrypoint(String className) throws Exception {
		Class<?> entrypointClass = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
		Constructor<?> constructor = entrypointClass.getDeclaredConstructor();
		constructor.setAccessible(true);
		Object entrypoint = constructor.newInstance();

		if (isInstance(entrypoint, "net.fabricmc.api.ClientModInitializer")) {
			entrypointClass.getMethod("onInitializeClient").invoke(entrypoint);
		} else if (isInstance(entrypoint, "net.fabricmc.api.ModInitializer")) {
			entrypointClass.getMethod("onInitialize").invoke(entrypoint);
		} else if (isInstance(entrypoint, "net.fabricmc.api.DedicatedServerModInitializer")) {
			entrypointClass.getMethod("onInitializeServer").invoke(entrypoint);
		}
	}

	private static boolean isInstance(Object instance, String className) {
		try {
			return Class.forName(className).isInstance(instance);
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	private static String unescapeJson(String value) {
		return Objects.requireNonNull(value, "value")
				.replace("\\\\/", "/")
				.replace("\\\\\"", "\"")
				.replace("\\\\\\\\", "\\");
	}
}
