package com.example.autofarm;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Membuat tombol "Configure" di Mod Menu yang membuka GUI Auto Farm. */
public class AutoFarmModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FarmScreen::new;
    }
}
