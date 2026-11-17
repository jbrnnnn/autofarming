package com.example.autofarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FarmConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("autofarm.json");
    private static FarmConfig instance;

    public boolean enabled = false;                 // selalu mati saat game start
    public boolean replant = true;                  // tanam ulang untuk mode HANCURKAN
    public boolean keepRunningInBackground = true;  // jalan terus saat ESC/inventory/pindah window
    public boolean swing = true;                    // animasi ayun tangan
    public int radius = 4;                          // 1..6
    public int verticalRange = 2;                   // 0..4
    public int actionsPerTick = 8;                  // 1..32
    public int cooldownTicks = 4;                   // 1..20 (jeda retry per blok)
    public Map<String, FarmMode> crops = new LinkedHashMap<>();

    public static FarmConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try (Reader r = Files.newBufferedReader(FILE)) {
            instance = GSON.fromJson(r, FarmConfig.class);
        } catch (Exception e) {
            instance = null;
        }
        if (instance == null) {
            instance = new FarmConfig();
            instance.defaults();
            save();
        }
        if (instance.crops == null) instance.crops = new LinkedHashMap<>();
        instance.enabled = false;
        instance.radius = clamp(instance.radius, 1, 6);
        instance.verticalRange = clamp(instance.verticalRange, 0, 4);
        instance.actionsPerTick = clamp(instance.actionsPerTick, 1, 32);
        instance.cooldownTicks = clamp(instance.cooldownTicks, 1, 20);
    }

    public static void save() {
        if (instance == null) return;
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            AutoFarmClient.LOGGER.error("Gagal menyimpan config", e);
        }
    }

    private void defaults() {
        crops.put("minecraft:wheat", FarmMode.BREAK);
        crops.put("minecraft:carrots", FarmMode.BREAK);
        crops.put("minecraft:potatoes", FarmMode.BREAK);
        crops.put("minecraft:beetroots", FarmMode.BREAK);
        crops.put("minecraft:nether_wart", FarmMode.BREAK);
        crops.put("minecraft:sweet_berry_bush", FarmMode.RIGHT_CLICK);
    }

    public FarmMode modeOf(String id) {
        return crops.getOrDefault(id, FarmMode.OFF);
    }

    public void setMode(String id, FarmMode mode) {
        if (mode == FarmMode.OFF) crops.remove(id);
        else crops.put(id, mode);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
