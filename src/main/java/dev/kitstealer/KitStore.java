package dev.kitstealer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class KitStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path kitsDirectory;
    private final List<InventoryKit> kits;

    private KitStore(Path kitsDirectory, List<InventoryKit> kits) {
        this.kitsDirectory = kitsDirectory;
        this.kits = new ArrayList<>(kits);
        sortKits();
    }

    public static KitStore load(Path gameDirectory) {
        Path kitsDirectory = gameDirectory.resolve("config").resolve("kitstealer").resolve("kits");
        List<InventoryKit> kits = new ArrayList<>();

        try {
            Files.createDirectories(kitsDirectory);
            try (Stream<Path> kitFiles = Files.list(kitsDirectory)) {
                kitFiles.filter(KitStore::isKitFile)
                        .map(KitStore::readKit)
                        .filter(kit -> kit != null)
                        .forEach(kits::add);
            }
        } catch (IOException exception) {
            kits.clear();
        }

        return new KitStore(kitsDirectory, kits);
    }

    public List<InventoryKit> getKits() {
        return List.copyOf(kits);
    }

    public InventoryKit save(InventoryKit kit) {
        InventoryKit existingKit = findKit(kit.getId());
        if (existingKit == null) {
            kits.add(kit);
        }

        writeKit(kit);
        sortKits();
        return kit;
    }

    public void delete(InventoryKit kit) {
        kits.removeIf(savedKit -> savedKit.getId().equals(kit.getId()));
        try {
            Files.deleteIfExists(kitsDirectory.resolve(kit.getId() + ".json"));
        } catch (IOException exception) {
            return;
        }
        sortKits();
    }

    public InventoryKit moveUp(InventoryKit kit) {
        return moveKit(kit, -1);
    }

    public InventoryKit moveDown(InventoryKit kit) {
        return moveKit(kit, 1);
    }

    public int nextPriority() {
        int nextPriority = 0;
        for (InventoryKit kit : kits) {
            nextPriority = Math.max(nextPriority, kit.getPriority() + 1);
        }
        return nextPriority;
    }

    private InventoryKit moveKit(InventoryKit kit, int positionChange) {
        int currentIndex = findKitIndex(kit.getId());
        int targetIndex = currentIndex + positionChange;
        if (currentIndex < 0 || targetIndex < 0 || targetIndex >= kits.size()) {
            return null;
        }

        List<InventoryKit> reorderedKits = new ArrayList<>(kits);
        Collections.swap(reorderedKits, currentIndex, targetIndex);
        kits.clear();
        for (int priority = 0; priority < reorderedKits.size(); priority++) {
            InventoryKit reorderedKit = reorderedKits.get(priority).withPriority(priority);
            kits.add(reorderedKit);
            writeKit(reorderedKit);
        }
        sortKits();
        return findKit(kit.getId());
    }

    private static boolean isKitFile(Path path) {
        return path.getFileName().toString().endsWith(".json");
    }

    private InventoryKit findKit(UUID id) {
        for (InventoryKit kit : kits) {
            if (kit.getId().equals(id)) {
                return kit;
            }
        }

        return null;
    }

    private int findKitIndex(UUID id) {
        for (int index = 0; index < kits.size(); index++) {
            if (kits.get(index).getId().equals(id)) {
                return index;
            }
        }

        return -1;
    }

    private void sortKits() {
        kits.sort(Comparator.comparingInt(InventoryKit::getPriority).thenComparing(InventoryKit::getName, String.CASE_INSENSITIVE_ORDER));
    }

    private void writeKit(InventoryKit kit) {
        try {
            Files.createDirectories(kitsDirectory);
            Path kitPath = kitsDirectory.resolve(kit.getId() + ".json");
            try (Writer writer = Files.newBufferedWriter(kitPath)) {
                GSON.toJson(toJson(kit), writer);
            }
        } catch (IOException exception) {
            return;
        }
    }

    private static InventoryKit readKit(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject kitJson = JsonParser.parseReader(reader).getAsJsonObject();
            UUID id = UUID.fromString(kitJson.get("id").getAsString());
            String name = kitJson.get("name").getAsString();
            int priority = kitJson.get("priority").getAsInt();
            List<KitSlot> slots = new ArrayList<>();

            JsonArray slotJsonArray = kitJson.getAsJsonArray("slots");
            for (JsonElement slotElement : slotJsonArray) {
                JsonObject slotJson = slotElement.getAsJsonObject();
                int menuSlot = slotJson.get("slot").getAsInt();
                JsonElement serializedStack = slotJson.get("stack");
                if (serializedStack == null) {
                    continue;
                }
                slots.add(KitSlot.fromSerialized(menuSlot, serializedStack));
            }

            return new InventoryKit(id, name, priority, slots);
        } catch (Exception exception) {
            return null;
        }
    }

    private static JsonObject toJson(InventoryKit kit) {
        JsonObject kitJson = new JsonObject();
        kitJson.addProperty("id", kit.getId().toString());
        kitJson.addProperty("name", kit.getName());
        kitJson.addProperty("priority", kit.getPriority());
        JsonArray slotJsonArray = new JsonArray();

        for (KitSlot slot : kit.getSlots()) {
            JsonObject slotJson = new JsonObject();
            slotJson.addProperty("slot", slot.getMenuSlot());
            slotJson.add("stack", slot.getSerializedStack());
            slotJsonArray.add(slotJson);
        }

        kitJson.add("slots", slotJsonArray);
        return kitJson;
    }
}
