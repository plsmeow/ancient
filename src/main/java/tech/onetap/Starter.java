package tech.onetap;

import com.google.common.eventbus.Subscribe;
import tech.onetap.event.list.EventMinecraftInit;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class Starter {

    public Starter() {
        Onetap.getInstance().getEventBus().register(this);
    }

    boolean hwidTrued = false;

    @Subscribe
    public void onStart(EventMinecraftInit evenTick) {
        if (!hwidTrued) {
            if (!isSubscriptionActive(getHWID())) {
                saveHWIDToFile();
                System.exit(1);
            }
            hwidTrued = true;
        }
    }

    private static String getPasteContent(String pasteUrl) throws Exception {
        URL url = new URL(pasteUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                // Ищем строку с <meta name="description" content="...">
                if (line.contains("<meta name=\"description\"")) {
                    int start = line.indexOf("content=\"") + 9;
                    int end = line.indexOf("\"", start);
                    if (start > 8 && end > start) {
                        String content = line.substring(start, end);
                        return content.replace(" - 038c091a", "").trim();
                    }
                }
            }
            in.close();
        } else {
            throw new RuntimeException("Failed to get paste content: HTTP error code " + responseCode);
        }
        return "";
    }

    public static boolean isSubscriptionActive(String hwid) {
        String pasteUrl = "";
        try {
            String content = getPasteContent(pasteUrl);
            System.out.println("HWID: " + hwid);
            System.out.println("CONTENT:\n" + content);

            for (String part : content.split("\\s+")) {
                System.out.println("Comparing to: [" + part + "]");
                if (part.trim().equalsIgnoreCase(hwid)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getHWID() {
        try {
            String toEncrypt = System.getenv("COMPUTERNAME") + System.getProperty("user.name") + System.getenv("PROCESSOR_IDENTIFIER") + System.getenv("PROCESSOR_LEVEL");
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(toEncrypt.getBytes());
            StringBuffer hexString = new StringBuffer();

            byte byteData[] = md.digest();

            for (byte aByteData : byteData) {
                String hex = Integer.toHexString(0xff & aByteData);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
    }

    public static void saveHWIDToFile() {
        try {
            File file = new File(System.getProperty("user.dir"), "hwid.txt");

            FileWriter writer = new FileWriter(file);
            writer.write("Ваш хвид: " + getHWID());
            writer.close();

            Runtime.getRuntime().exec("explorer " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}