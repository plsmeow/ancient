package tech.onetap.ancientloader;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AncientLoader implements PreLaunchEntrypoint {
	private static final String RELEASE_API_URL = "https://api.github.com/repos/plsmeow/ancient/releases/latest";
	private static final String TARGET_ASSET_NAME = "onetap-1.0.0.jar";
	private static final Pattern DOWNLOAD_URL_PATTERN = Pattern.compile(
			"\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+/" + Pattern.quote(TARGET_ASSET_NAME) + ")\\\""
	);

	@Override
	public void onPreLaunch() {
		try {
			Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
			Path existing = modsDir.resolve(TARGET_ASSET_NAME);

			URI downloadUri = findLatestAssetDownloadUrl();
			Path downloaded = downloadToTemp(modsDir, downloadUri);

			if (!Files.exists(existing) || filesDiffer(existing, downloaded)) {
				Files.createDirectories(modsDir);
				Files.move(downloaded, existing, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

				JOptionPane.showMessageDialog(null,
						"Клиент обновлен.\nПерезапусти.",
						"Ancient Loader",
						JOptionPane.INFORMATION_MESSAGE);

				System.exit(0);
			} else {
				Files.deleteIfExists(downloaded);
				System.out.println("[Ancient Loader] " + TARGET_ASSET_NAME + " is up to date");
			}
		} catch (Exception exception) {
			JOptionPane.showMessageDialog(null,
					"Failed to download " + TARGET_ASSET_NAME + ":\n" + exception.getMessage(),
					"Ancient Loader - Error",
					JOptionPane.ERROR_MESSAGE);
			throw new IllegalStateException("Failed to update " + TARGET_ASSET_NAME, exception);
		}
	}

	private static Path downloadToTemp(Path modsDir, URI downloadUri) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		HttpRequest request = HttpRequest.newBuilder(downloadUri)
				.header("Accept", "application/octet-stream")
				.header("User-Agent", "ancient-loader")
				.GET()
				.build();

		Files.createDirectories(modsDir);
		Path temp = Files.createTempFile(modsDir, "ancient-loader-", ".jar.tmp");
		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() / 100 != 2) {
				throw new IOException("GitHub asset download failed with HTTP " + response.statusCode());
			}

			try (InputStream body = response.body()) {
				Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
			}

			return temp;
		} finally {
			if (!Files.exists(temp)) {
				Files.deleteIfExists(temp);
			}
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

		Matcher matcher = DOWNLOAD_URL_PATTERN.matcher(response.body());
		if (!matcher.find()) {
			throw new IOException("Latest ancient release does not contain " + TARGET_ASSET_NAME);
		}

		return URI.create(unescapeJson(matcher.group(1)));
	}

	private static boolean filesDiffer(Path a, Path b) throws IOException {
		if (Files.size(a) != Files.size(b)) {
			return true;
		}

		byte[] bytesA = Files.readAllBytes(a);
		byte[] bytesB = Files.readAllBytes(b);

		if (bytesA.length != bytesB.length) {
			return true;
		}

		for (int i = 0; i < bytesA.length; i++) {
			if (bytesA[i] != bytesB[i]) {
				return true;
			}
		}

		return false;
	}

	private static String unescapeJson(String value) {
		return value
				.replace("\\\\/", "/")
				.replace("\\\\\"", "\"")
				.replace("\\\\\\\\", "\\");
	}
}
