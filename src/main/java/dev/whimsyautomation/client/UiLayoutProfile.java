package dev.whimsyautomation.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent, screen-local layout profile used by the in-game layout editor.
 *
 * Coordinates are GUI logical coordinates relative to the container's
 * top-left corner. The profile deliberately stores generic elements rather
 * than buttons only, so slots, labels, panels and previews can share the
 * same format.
 */
public final class UiLayoutProfile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FMLPaths.CONFIGDIR.get()
            .resolve("whimsy_automation").resolve("layouts");

    private final String screen;
    private int layoutVersion = 1;
    private final Map<String, Element> elements = new LinkedHashMap<>();

    private UiLayoutProfile(String screen) {
        this.screen = screen;
    }

    public static UiLayoutProfile load(String screen) {
        UiLayoutProfile profile = new UiLayoutProfile(screen);
        Path file = profile.file();
        if (!Files.isRegularFile(file)) return profile;
        try {
            UiLayoutProfile loaded = GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), UiLayoutProfile.class);
            if (loaded != null && screen.equals(loaded.screen) && loaded.elements != null) {
                profile.layoutVersion = Math.max(1, loaded.layoutVersion);
                profile.elements.putAll(loaded.elements);
            }
        } catch (IOException | JsonParseException ignored) {
            // A damaged profile must never prevent a machine screen from opening.
        }
        return profile;
    }

    public Element element(String id, String type, int x, int y, int width, int height) {
        Element value = elements.get(id);
        if (value == null) {
            value = new Element(type, x, y, width, height);
            elements.put(id, value);
        } else {
            value.type = type;
        }
        return value;
    }

    public Map<String, Element> elements() {
        return elements;
    }

    public String screen() {
        return screen;
    }

    public void save() {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存界面布局：" + screen, exception);
        }
    }

    /** Writes a separately named, portable JSON copy for sharing with developers. */
    public Path export() throws IOException {
        Files.createDirectories(ROOT);
        Path target = ROOT.resolve(screen + "-export.json");
        Files.writeString(target, GSON.toJson(this), StandardCharsets.UTF_8);
        return target;
    }

    public void reset() {
        elements.clear();
        save();
    }

    private Path file() {
        return ROOT.resolve(screen + ".json");
    }

    public static final class Element {
        public String type;
        public int x;
        public int y;
        public int width;
        public int height;
        public float textScale = 1.0F;

        public Element(String type, int x, int y, int width, int height) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
