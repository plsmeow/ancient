package tech.onetap.util.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.discordipc.RichPresence;

import java.lang.reflect.Method;

public class ExtendedRichPresence extends RichPresence {
    private JsonArray buttons;

    public void addButton(String label, String url) {
        if (buttons == null) {
            buttons = new JsonArray();
        }
        
        if (buttons.size() < 2) {
            JsonObject button = new JsonObject();
            button.addProperty("label", label);
            button.addProperty("url", url);
            buttons.add(button);
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = super.toJson();
        
        if (buttons != null && buttons.size() > 0) {
            json.add("buttons", buttons);
        }
        
        return json;
    }
}
