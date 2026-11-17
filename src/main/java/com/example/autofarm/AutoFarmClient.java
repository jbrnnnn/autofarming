package com.example.autofarm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoFarmClient implements ClientModInitializer {
    public static final String MOD_ID = "autofarm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openGuiKey;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        FarmConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openGuiKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autofarm.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.autofarm.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(AutoFarmClient::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> FarmConfig.save());
    }

    private static void onTick(MinecraftClient mc) {
        while (openGuiKey.wasPressed()) {
            mc.setScreen(new FarmScreen(mc.currentScreen));
        }
        while (toggleKey.wasPressed()) {
            setEnabled(!FarmConfig.get().enabled);
        }
        // Supaya window tidak auto-pause saat kamu pindah ke aplikasi lain
        if (keepRunning()) {
            mc.options.pauseOnLostFocus = false;
        }
        FarmEngine.tick(mc);
    }

    /** true = dunia (singleplayer) tidak boleh ke-pause saat ada screen/menu terbuka. */
    public static boolean keepRunning() {
        FarmConfig c = FarmConfig.get();
        return c.enabled && c.keepRunningInBackground;
    }

    public static void setEnabled(boolean on) {
        FarmConfig.get().enabled = on;
        FarmEngine.reset();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("Auto Farm: " + (on ? "§aON" : "§cOFF")), true);
        }
    }
}
