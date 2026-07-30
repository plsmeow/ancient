package tech.onetap.ui.mainmenu;

import com.google.gson.*;
import net.minecraft.client.session.Session;
import tech.onetap.Onetap;
import tech.onetap.mixin.IMinecraftClientAccessor;
import tech.onetap.util.IMinecraft;

import java.io.*;
import java.util.Optional;
import java.util.UUID;

public class AltConfig implements IMinecraft {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(mc.runDirectory, "ancient/files/alts.cfg");

    public void init() throws Exception {
        File parent = FILE.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!FILE.exists()) {
            FILE.createNewFile();
        } else {
            readAlts();
        }
    }

    public static void updateFile() {
        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty("last", mc.getSession().getUsername());

        JsonArray altsArray = new JsonArray();
        for (Alt alt : Onetap.getInstance().getAltWidget().alts) {
            altsArray.add(alt.name);
        }

        jsonObject.add("alts", altsArray);

        try (PrintWriter printWriter = new PrintWriter(FILE)) {
            printWriter.println(GSON.toJson(jsonObject));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readAlts() throws FileNotFoundException {
        JsonElement jsonElement = new JsonParser().parse(new BufferedReader(new FileReader(FILE)));

        if (jsonElement.isJsonNull()) return;

        JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (jsonObject.has("last")) {
            ((IMinecraftClientAccessor) mc).setSession(new Session(jsonObject.get("last").getAsString(), UUID.randomUUID(), "", Optional.empty(), Optional.empty(), Session.AccountType.MOJANG));
        }

        if (jsonObject.has("alts")) {
            for (JsonElement element : jsonObject.get("alts").getAsJsonArray()) {
                String name = element.getAsString();
                Onetap.getInstance().getAltWidget().alts.add(new Alt(name));
            }
        }
    }
}
