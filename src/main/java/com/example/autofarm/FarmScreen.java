package com.example.autofarm;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class FarmScreen extends Screen {
    private static final int ROW_H = 22;
    private static List<Block> CANDIDATES;
    private static Map<Block, Item> ICONS;

    private final Screen parent;
    private int tab = 0;
    private int page = 0;
    private String query = "";
    private TextFieldWidget search;
    private List<Block> filtered = List.of();
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private final List<Block> rowBlocks = new ArrayList<>();
    private int rowsPerPage = 5, listX, listW, listY = 62;

    public FarmScreen(Screen parent) {
        super(Text.literal("Auto Farm"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ensureCandidates();
        rowButtons.clear();
        rowBlocks.clear();
        int cx = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Tanaman"), b -> { tab = 0; clearAndInit(); })
                .dimensions(cx - 104, 10, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pengaturan"), b -> { tab = 1; clearAndInit(); })
                .dimensions(cx + 4, 10, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), b -> close())
                .dimensions(cx - 50, height - 28, 100, 20).build());
        if (tab == 0) initCrops(); else initSettings();
    }

    // ---------- TAB TANAMAN ----------
    private void initCrops() {
        listW = Math.min(width - 30, 360);
        listX = (width - listW) / 2;
        listY = 62;
        rowsPerPage = Math.max(1, (height - 48 - listY) / ROW_H);

        search = new TextFieldWidget(textRenderer, listX, 36, listW, 18, Text.literal("Cari"));
        search.setPlaceholder(Text.literal("Cari tanaman / id block..."));
        search.setText(query);
        search.setChangedListener(s -> { query = s; page = 0; applyFilter(); refreshRows(); });
        addDrawableChild(search);

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> changePage(-1))
                .dimensions(listX, height - 28, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> changePage(1))
                .dimensions(listX + listW - 40, height - 28, 40, 20).build());

        applyFilter();
        refreshRows();
    }

    private void applyFilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        FarmConfig cfg = FarmConfig.get();
        List<Block> out = new ArrayList<>();
        for (Block b : CANDIDATES) {
            if (q.isEmpty() || idOf(b).contains(q) || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(b);
            }
        }
        out.sort(Comparator.comparing(b -> cfg.modeOf(idOf(b)) == FarmMode.OFF)); // yang aktif di atas (stabil)
        filtered = out;
    }

    private int pageCount() {
        return Math.max(1, (filtered.size() + rowsPerPage - 1) / rowsPerPage);
    }

    private void changePage(int d) {
        page = Math.max(0, Math.min(pageCount() - 1, page + d));
        refreshRows();
    }

    private void refreshRows() {
        for (ButtonWidget bw : rowButtons) remove(bw);
        rowButtons.clear();
        rowBlocks.clear();
        page = Math.max(0, Math.min(page, pageCount() - 1));
        int start = page * rowsPerPage;
        for (int i = 0; i < rowsPerPage && start + i < filtered.size(); i++) {
            Block b = filtered.get(start + i);
            String id = idOf(b);
            int y = listY + i * ROW_H;
            ButtonWidget bw = ButtonWidget.builder(modeText(FarmConfig.get().modeOf(id)), btn -> {
                FarmConfig c = FarmConfig.get();
                FarmMode n = c.modeOf(id).next();
                c.setMode(id, n);
                btn.setMessage(modeText(n));
                FarmEngine.invalidate();
            }).dimensions(listX + listW - 130, y, 130, 20).build();
            addDrawableChild(bw);
            rowButtons.add(bw);
            rowBlocks.add(b);
        }
    }

    private static Text modeText(FarmMode m) {
        return switch (m) {
            case OFF -> Text.literal("Mati").formatted(Formatting.GRAY);
            case BREAK -> Text.literal("Hancurkan").formatted(Formatting.GREEN);
            case RIGHT_CLICK -> Text.literal("Klik Kanan").formatted(Formatting.AQUA);
        };
    }

    // ---------- TAB PENGATURAN ----------
    private void initSettings() {
        FarmConfig cfg = FarmConfig.get();
        int w = Math.min((width - 40) / 2, 200);
        int xl = width / 2 - w - 4, xr = width / 2 + 4;
        int y = 44;
        addToggle(xl, y, w, "Auto Farm", cfg.enabled, AutoFarmClient::setEnabled);
        addDrawableChild(new IntSlider(xr, y, w, 20, "Radius", 1, 6, cfg.radius, v -> cfg.radius = v));
        y += 26;
        addToggle(xl, y, w, "Tanam ulang", cfg.replant, v -> cfg.replant = v);
        addDrawableChild(new IntSlider(xr, y, w, 20, "Tinggi (Y)", 0, 4, cfg.verticalRange, v -> cfg.verticalRange = v));
        y += 26;
        addToggle(xl, y, w, "Jalan di background", cfg.keepRunningInBackground, v -> cfg.keepRunningInBackground = v);
        addDrawableChild(new IntSlider(xr, y, w, 20, "Aksi/tick", 1, 32, cfg.actionsPerTick, v -> cfg.actionsPerTick = v));
        y += 26;
        addToggle(xl, y, w, "Animasi tangan", cfg.swing, v -> cfg.swing = v);
        addDrawableChild(new IntSlider(xr, y, w, 20, "Cooldown (tick)", 1, 20, cfg.cooldownTicks, v -> cfg.cooldownTicks = v));
    }

    private void addToggle(int x, int y, int w, String label, boolean initial, Consumer<Boolean> setter) {
        boolean[] state = {initial};
        addDrawableChild(ButtonWidget.builder(toggleText(label, initial), b -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            b.setMessage(toggleText(label, state[0]));
        }).dimensions(x, y, w, 20).build());
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": ").append(on
                ? Text.literal("ON").formatted(Formatting.GREEN)
                : Text.literal("OFF").formatted(Formatting.RED));
    }

    private static class IntSlider extends SliderWidget {
        private final String label;
        private final int min, max;
        private final IntConsumer onChange;

        IntSlider(int x, int y, int w, int h, String label, int min, int max, int value, IntConsumer onChange) {
            super(x, y, w, h, Text.empty(), (value - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }

    // ---------- RENDER & INPUT ----------
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (tab == 0) {
            for (int i = 0; i < rowBlocks.size(); i++) {
                Block b = rowBlocks.get(i);
                int y = listY + i * ROW_H;
                Item icon = ICONS.getOrDefault(b, Items.AIR);
                if (icon != Items.AIR) ctx.drawItem(new ItemStack(icon), listX, y + 2);
                String name = textRenderer.trimToWidth(b.getName().getString(), listW - 22 - 136);
                ctx.drawTextWithShadow(textRenderer, Text.literal(name), listX + 22, y + 6, 0xFFFFFFFF);
            }
            String info = "Halaman " + (page + 1) + "/" + pageCount() + "  (scroll mouse untuk ganti halaman)";
            ctx.drawTextWithShadow(textRenderer, Text.literal(info),
                    width / 2 - textRenderer.getWidth(info) / 2, height - 44, 0xFFAAAAAA);
        } else {
            String info = "Bibit untuk tanam ulang harus ada di HOTBAR.";
            ctx.drawTextWithShadow(textRenderer, Text.literal(info),
                    width / 2 - textRenderer.getWidth(info) / 2, 160, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == 0) {
            changePage(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        FarmConfig.save();
    }

    // ---------- DATA ----------
    private static String idOf(Block b) {
        return Registries.BLOCK.getId(b).toString();
    }

    private static void ensureCandidates() {
        if (CANDIDATES != null) return;
        ICONS = new HashMap<>();
        for (Item it : Registries.ITEM) {
            if (it instanceof BlockItem bi) ICONS.putIfAbsent(bi.getBlock(), it);
        }
        List<Block> list = new ArrayList<>();
        for (Block b : Registries.BLOCK) {
            if (b == Blocks.AIR || b == Blocks.FROSTED_ICE || b instanceof AbstractFireBlock) continue;
            BlockState d = b.getDefaultState();
            boolean ok = d.contains(Properties.BERRIES)
                    || b == Blocks.PUMPKIN || b == Blocks.MELON
                    || b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.BAMBOO;
            if (!ok) {
                for (Property<?> p : d.getProperties()) {
                    if (p instanceof IntProperty && p.getName().equals("age")) { ok = true; break; }
                }
            }
            if (ok) list.add(b);
        }
        list.sort(Comparator.comparing(b -> b.getName().getString()));
        CANDIDATES = list;
    }
}
