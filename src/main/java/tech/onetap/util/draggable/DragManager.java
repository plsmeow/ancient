package tech.onetap.util.draggable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import lombok.Setter;
import tech.onetap.module.Module;
import tech.onetap.util.IMinecraft;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

@Setter
public class DragManager implements IMinecraft {
    @Expose
    public static LinkedHashMap<String, Draggable> draggableElements = new LinkedHashMap<>();

    private static final File dataFile = new File(mc.getInstance().runDirectory, ".options/draggable.json");
    private static final Gson jsonParser = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();

    private static Draggable activeDragging = null;

    public static Draggable installDrag(Module module, String name, float x, float y) {
        draggableElements.put(name, new Draggable(module, name, x, y));
        return draggableElements.get(name);
    }

    public void load() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            return;
        }

        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<LinkedHashMap<String, Draggable>>() {}.getType();
            LinkedHashMap<String, Draggable> loaded = jsonParser.fromJson(reader, type);
            if (loaded != null) {
                for (Map.Entry<String, Draggable> entry : loaded.entrySet()) {
                    Draggable loadedDraggable = entry.getValue();
                    Draggable existing = draggableElements.get(entry.getKey());
                    if (existing != null) {
                        existing.setX(loadedDraggable.getX());
                        existing.setY(loadedDraggable.getY());
                    } else {
                        draggableElements.put(entry.getKey(), loadedDraggable);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveDraggables() {
        try {
            dataFile.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(dataFile)) {
                jsonParser.toJson(draggableElements, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void onClickAll(int button) {
        if (button != 0) return;

        List<Draggable> elements = new ArrayList<>(draggableElements.values());
        Collections.reverse(elements);
        for (Draggable draggable : elements) {
            if (draggable.isHovering() && draggable.getModule() != null && draggable.getModule().isEnabled()) {
                activeDragging = draggable;
                draggable.onClick(button);
                break;
            }
        }
    }

    public static void onDrawAll() {
        for (Draggable draggable : draggableElements.values()) {
            if (draggable.getModule() != null && draggable.getModule().isEnabled()) {
                draggable.onDraw();
            }
        }
    }

    public static void onReleaseAll(int button) {
        if (activeDragging != null) {
            activeDragging.onRelease(button);
            activeDragging = null;
        }
    }
}