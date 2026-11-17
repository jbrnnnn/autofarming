package com.example.autofarm;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Mesin auto farm. Dioptimasi:
 *  - offset scan sudah di-precompute & diurutkan dari yang terdekat
 *  - lookup blok -> meta lewat HashMap (tanpa parsing string per tick)
 *  - tanpa alokasi objek di loop scan (BlockPos.Mutable)
 *  - cooldown per-blok (fastutil) supaya tidak spam paket
 */
public final class FarmEngine {
    private record Meta(FarmMode mode, IntProperty age, int max, boolean tall, boolean replantable) {}

    private static final Map<Block, Meta> META = new HashMap<>();
    private static final Long2LongOpenHashMap COOLDOWN = new Long2LongOpenHashMap();
    private static boolean dirty = true;
    private static int[] offsets = new int[0];
    private static int builtR = -1, builtV = -1;
    private static long now;

    private static BlockPos slowPos;
    private static BlockState slowState;
    private static Meta slowMeta;

    private FarmEngine() {}

    public static void invalidate() { dirty = true; }

    public static void reset() {
        slowPos = null;
        COOLDOWN.clear();
        dirty = true;
    }

    public static void tick(MinecraftClient mc) {
        FarmConfig cfg = FarmConfig.get();
        if (!cfg.enabled) return;

        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        ClientPlayerInteractionManager im = mc.interactionManager;
        if (player == null || world == null || im == null || !player.isAlive() || player.isSpectator()) return;

        if (dirty) rebuildMeta(cfg);
        if (cfg.radius != builtR || cfg.verticalRange != builtV) buildOffsets(cfg.radius, cfg.verticalRange);
        if (META.isEmpty()) return;

        now++;
        if ((now & 127) == 0) COOLDOWN.long2LongEntrySet().removeIf(e -> e.getLongValue() <= now);

        boolean acted = false;

        // Selesaikan target "hancurkan lambat" (mis. cocoa) lalu tanam ulang
        if (slowPos != null) {
            BlockState cur = world.getBlockState(slowPos);
            if (cur.isAir()) {
                if (cfg.replant && slowMeta.replantable()) replant(mc, player, slowPos, slowState);
                slowPos = null;
                acted = true;
            } else if (cur.getBlock() != slowState.getBlock()) {
                slowPos = null;
            }
        }

        double reach = Math.min(cfg.radius, player.getBlockInteractionRange() - 0.5);
        double reachSq = reach * reach;
        Vec3d eye = player.getEyePos();
        BlockPos base = player.getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();
        boolean creative = player.isCreative();
        boolean slowUsed = false;
        int budget = cfg.actionsPerTick;

        for (int i = 0; i < offsets.length && budget > 0; i += 3) {
            m.set(base.getX() + offsets[i], base.getY() + offsets[i + 1], base.getZ() + offsets[i + 2]);
            BlockState st = world.getBlockState(m);
            if (st.isAir()) continue;
            Meta meta = META.get(st.getBlock());
            if (meta == null) continue;

            double dx = m.getX() + 0.5 - eye.x, dy = m.getY() + 0.5 - eye.y, dz = m.getZ() + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > reachSq) continue;
            if (!isReady(world, m, st, meta)) continue;

            long key = m.asLong();
            if (COOLDOWN.get(key) > now) continue;

            BlockPos pos = m.toImmutable();

            if (meta.mode() == FarmMode.RIGHT_CLICK) {
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                im.interactBlock(player, Hand.MAIN_HAND, hit);
                COOLDOWN.put(key, now + cfg.cooldownTicks);
                budget--;
                acted = true;
            } else if (meta.mode() == FarmMode.BREAK) {
                boolean instant = creative || st.getHardness(world, pos) == 0f;
                if (instant) {
                    im.attackBlock(pos, Direction.UP);
                    COOLDOWN.put(key, now + cfg.cooldownTicks);
                    if (cfg.replant && meta.replantable() && world.getBlockState(pos).isAir()) {
                        replant(mc, player, pos, st);
                    }
                    budget--;
                    acted = true;
                } else {
                    if (slowUsed || (slowPos != null && !slowPos.equals(pos))) continue;
                    im.updateBlockBreakingProgress(pos, Direction.UP);
                    slowPos = pos;
                    slowState = st;
                    slowMeta = meta;
                    slowUsed = true;
                    budget--;
                    acted = true;
                }
            }
        }

        if (acted && cfg.swing) player.swingHand(Hand.MAIN_HAND);
    }

    private static boolean isReady(ClientWorld world, BlockPos pos, BlockState st, Meta meta) {
        if (meta.tall()) {
            // tebu / kaktus / bambu: hancurkan semua kecuali blok paling bawah
            return world.getBlockState(pos.down()).isOf(st.getBlock());
        }
        if (st.contains(Properties.BERRIES)) return st.get(Properties.BERRIES); // glow berries
        if (meta.age() != null) return st.get(meta.age()) >= meta.max();
        return true; // labu / semangka
    }

    /** Tanam ulang memakai bibit di HOTBAR (slot 1-9). */
    private static void replant(MinecraftClient mc, ClientPlayerEntity player, BlockPos pos, BlockState old) {
        Block block = old.getBlock();
        PlayerInventory inv = player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock() == block) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;

        BlockPos target;
        Direction side;
        if (old.contains(Properties.HORIZONTAL_FACING)) { // cocoa: menempel ke batang jungle
            Direction f = old.get(Properties.HORIZONTAL_FACING);
            target = pos.offset(f);
            side = f.getOpposite();
        } else {
            target = pos.down();
            side = Direction.UP;
        }
        Vec3d hitVec = Vec3d.ofCenter(target)
                .add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);

        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(hitVec, side, target, false));
        inv.setSelectedSlot(prev);
    }

    private static void rebuildMeta(FarmConfig cfg) {
        META.clear();
        for (Map.Entry<String, FarmMode> e : cfg.crops.entrySet()) {
            if (e.getValue() == null || e.getValue() == FarmMode.OFF) continue;
            Identifier id = Identifier.tryParse(e.getKey());
            if (id == null || !Registries.BLOCK.containsId(id)) continue;
            Block b = Registries.BLOCK.get(id);
            if (b == Blocks.AIR) continue;

            boolean tall = b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.BAMBOO;
            BlockState d = b.getDefaultState();
            IntProperty age = null;
            int max = 0;
            for (Property<?> p : d.getProperties()) {
                if (p instanceof IntProperty ip && p.getName().equals("age")) {
                    age = ip;
                    max = Collections.max(ip.getValues());
                    break;
                }
            }
            boolean replantable = !tall && age != null && !d.contains(Properties.BERRIES);
            META.put(b, new Meta(e.getValue(), age, max, tall, replantable));
        }
        dirty = false;
    }

    private static void buildOffsets(int r, int v) {
        List<int[]> list = new ArrayList<>();
        int lim = (r + 1) * (r + 1);
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -v; dy <= v; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d <= lim) list.add(new int[]{dx, dy, dz, d});
                }
        list.sort(Comparator.comparingInt(a -> a[3]));
        offsets = new int[list.size() * 3];
        for (int i = 0; i < list.size(); i++) {
            int[] o = list.get(i);
            offsets[i * 3] = o[0];
            offsets[i * 3 + 1] = o[1];
            offsets[i * 3 + 2] = o[2];
        }
        builtR = r;
        builtV = v;
    }
}
