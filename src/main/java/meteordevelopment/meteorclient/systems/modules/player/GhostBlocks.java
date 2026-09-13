package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GhostBlocks extends Module {
    public enum Mode {
        Creative,
        Survival
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Creative fakes your client-side gamemode so ghost blocks can be freely placed and broken.")
            .defaultValue(Mode.Creative)
            .onChanged(m -> syncGamemode())
            .build()
    );

    private final Setting<Boolean> breakGhosts = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("break-ghosts")
            .description("Left-clicking a ghost block removes it client-side.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> sneakBypass = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("sneak-bypass")
            .description("Sneaking allows placing real blocks sent to the server.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> clearOnDisable = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("clear-on-disable")
            .description("Restores all ghost blocks back to server state on module disable.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Keybind> clearKey = this.sgGeneral.add(
        new KeybindSetting.Builder()
            .name("clear-key")
            .description("Keybind to clear and restore all ghost blocks.")
            .action(this::restoreAllWithFeedback)
            .build()
    );

    private final Setting<Boolean> render = this.sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Renders overlay highlight on active ghost blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = this.sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the ghost block shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = this.sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of ghost blocks.")
            .defaultValue(new SettingColor(100, 200, 255, 45))
            .build()
    );

    private final Setting<SettingColor> lineColor = this.sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of ghost blocks.")
            .defaultValue(new SettingColor(100, 200, 255, 200))
            .build()
    );

    private static final int MAX_GHOSTS = 4096;
    private final Map<BlockPos, BlockState> ghosts = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private ClientLevel lastLevel;
    private GameType previousGameType;
    private boolean fakeCreativeApplied;

    public GhostBlocks() {
        super(Categories.Player, "ghost-blocks", "Allows placing and breaking client-side ghost blocks without sending packets.");
    }

    @Override
    public void onActivate() {
        syncGamemode();
    }

    @Override
    public void onDeactivate() {
        if (clearOnDisable.get()) {
            restoreAll();
        }
        revertGamemode();
    }

    private void syncGamemode() {
        if (!isActive() || mc.player == null || mc.gameMode == null) return;

        if (mode.get() == Mode.Creative) {
            if (!fakeCreativeApplied) {
                previousGameType = mc.gameMode.getPlayerMode();
                if (previousGameType != GameType.CREATIVE) {
                    mc.gameMode.setLocalMode(GameType.CREATIVE);
                    fakeCreativeApplied = true;
                }
            } else if (mc.gameMode.getPlayerMode() != GameType.CREATIVE) {
                mc.gameMode.setLocalMode(GameType.CREATIVE);
            }
        } else {
            revertGamemode();
        }
    }

    private void revertGamemode() {
        if (fakeCreativeApplied && mc.gameMode != null) {
            if (previousGameType != null) {
                mc.gameMode.setLocalMode(previousGameType);
            }
            fakeCreativeApplied = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (mc.level != lastLevel) {
            clearGhosts();
            lastLevel = mc.level;
        }

        sweepGhosts();

        if (mode.get() == Mode.Creative && fakeCreativeApplied && mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.CREATIVE) {
            mc.gameMode.setLocalMode(GameType.CREATIVE);
        }
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (event.result == null || event.result.getType() != HitResult.Type.BLOCK) return;

        boolean sneaking = mc.player.isShiftKeyDown();
        if (sneaking && sneakBypass.get()) return;

        BlockPos hitPos = event.result.getBlockPos();
        BlockState hitState = mc.level.getBlockState(hitPos);
        if (!sneaking && hasUseAction(hitState, hitPos)) return;

        InteractionHand hand = event.hand != null ? event.hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem)) {
            hand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            stack = mc.player.getItemInHand(hand);
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        BlockPlaceContext context = new BlockPlaceContext(mc.player, hand, stack, event.result);
        if (!context.canPlace()) return;

        BlockPos placePos = context.getClickedPos();
        if (mc.level.isOutsideBuildHeight(placePos)) return;

        Map<BlockPos, BlockState> before = snapshotAround(placePos);
        ItemStack held = stack.copy();

        boolean placed = blockItem.place(context).consumesAction();
        mc.player.setItemInHand(hand, held);

        if (placed) {
            for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
                BlockState current = mc.level.getBlockState(entry.getKey());
                if (current != entry.getValue()) {
                    rememberOriginal(entry.getKey(), entry.getValue());
                    markGhost(entry.getKey(), current);
                }
            }
            mc.player.swing(hand);
        }

        event.cancel();
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (mc.player == null || mc.level == null) return;
        BlockPos pos = event.blockPos;

        if (isGhost(pos)) {
            if (breakGhosts.get()) {
                restoreGhost(pos, true);
            }
            event.cancel();
            return;
        }

        if (mode.get() == Mode.Creative) {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir() && !mc.level.isOutsideBuildHeight(pos)) {
                rememberOriginal(pos, state);
                mc.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                markGhost(pos, Blocks.AIR.defaultBlockState());
                mc.level.levelEvent(null, 2001, pos, Block.getId(state));
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mode.get() != Mode.Creative) return;

        if (event.packet instanceof ServerboundSetCreativeModeSlotPacket) {
            event.cancel();
            return;
        }

        if (event.packet instanceof ServerboundPlayerActionPacket action) {
            ServerboundPlayerActionPacket.Action kind = action.getAction();
            if (kind == ServerboundPlayerActionPacket.Action.DROP_ITEM || kind == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;
        if (event.packet instanceof ClientboundBlockUpdatePacket update) {
            synchronized (ghosts) {
                if (ghosts.containsKey(update.getPos())) {
                    originals.put(update.getPos(), update.getBlockState());
                    event.cancel();
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || ghosts.isEmpty() || mc.level == null) return;

        synchronized (ghosts) {
            for (BlockPos pos : ghosts.keySet()) {
                if (!mc.level.getBlockState(pos).isAir()) {
                    event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                }
            }
        }
    }

    private void pickBlockLocally(BlockPos pos) {
        if (mc.level == null || mc.player == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (!state.isAir()) {
            ItemStack picked = new ItemStack(state.getBlock().asItem());
            if (!picked.isEmpty()) {
                Inventory inventory = mc.player.getInventory();
                int existing = inventory.findSlotMatchingItem(picked);
                if (Inventory.isHotbarSlot(existing)) {
                    inventory.selected = existing;
                } else {
                    inventory.setItem(inventory.selected, picked);
                }
            }
        }
    }

    private Map<BlockPos, BlockState> snapshotAround(BlockPos pos) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        states.put(pos.immutable(), mc.level.getBlockState(pos));
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction).immutable();
            states.put(neighbour, mc.level.getBlockState(neighbour));
        }
        return states;
    }

    private void sweepGhosts() {
        List<Map.Entry<BlockPos, BlockState>> snapshot;
        synchronized (ghosts) {
            if (ghosts.isEmpty()) return;
            snapshot = new ArrayList<>(ghosts.entrySet());
        }

        for (Map.Entry<BlockPos, BlockState> entry : snapshot) {
            BlockPos pos = entry.getKey();
            if (hasChunk(pos) && mc.level.getBlockState(pos) != entry.getValue()) {
                mc.level.setBlock(pos, entry.getValue(), 3);
            }
        }
    }

    private boolean hasChunk(BlockPos pos) {
        return mc.level != null && mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private void rememberOriginal(BlockPos pos, BlockState original) {
        synchronized (ghosts) {
            originals.putIfAbsent(pos.immutable(), original);
        }
    }

    private void markGhost(BlockPos pos, BlockState state) {
        synchronized (ghosts) {
            ghosts.put(pos.immutable(), state);
            while (ghosts.size() > MAX_GHOSTS) {
                BlockPos eldest = ghosts.keySet().iterator().next();
                ghosts.remove(eldest);
                BlockState original = originals.remove(eldest);
                if (original != null && hasChunk(eldest)) {
                    mc.level.setBlock(eldest, original, 3);
                }
            }
        }
    }

    private void clearGhosts() {
        synchronized (ghosts) {
            ghosts.clear();
            originals.clear();
        }
    }

    private void restoreGhost(BlockPos pos, boolean effects) {
        BlockState ghost;
        BlockState original;
        synchronized (ghosts) {
            ghost = ghosts.remove(pos);
            original = originals.remove(pos);
        }

        if (ghost != null && mc.level != null && hasChunk(pos)) {
            if (original != null) {
                mc.level.setBlock(pos, original, 3);
            }
            if (effects) {
                mc.level.levelEvent(null, 2001, pos, Block.getId(ghost));
            }
        }
    }

    public int restoreAll() {
        List<BlockPos> positions;
        synchronized (ghosts) {
            positions = new ArrayList<>(ghosts.keySet());
        }

        for (BlockPos pos : positions) {
            restoreGhost(pos, false);
        }

        return positions.size();
    }

    private void restoreAllWithFeedback() {
        int restored = restoreAll();
        if (restored > 0) {
            info("Cleared " + restored + " ghost block" + (restored == 1 ? "." : "s."));
        }
    }

    public boolean isGhost(BlockPos pos) {
        synchronized (ghosts) {
            return ghosts.containsKey(pos);
        }
    }

    public int ghostCount() {
        synchronized (ghosts) {
            return ghosts.size();
        }
    }

    private boolean hasUseAction(BlockState state, BlockPos pos) {
        if (mc.level == null) return false;
        if (state.getMenuProvider(mc.level, pos) != null) return true;

        Block block = state.getBlock();
        return block instanceof DoorBlock
            || block instanceof TrapDoorBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock
            || block instanceof NoteBlock
            || block instanceof BedBlock
            || block instanceof BellBlock
            || block instanceof CakeBlock
            || block instanceof RespawnAnchorBlock
            || block instanceof DiodeBlock
            || block instanceof DaylightDetectorBlock
            || block instanceof DragonEggBlock
            || block instanceof RedStoneWireBlock
            || block instanceof JukeboxBlock
            || block instanceof FlowerPotBlock
            || block instanceof BeehiveBlock
            || block instanceof ChiseledBookShelfBlock
            || block instanceof CandleCakeBlock;
    }

    @Override
    public String getInfoString() {
        int count = ghostCount();
        return count > 0 ? String.valueOf(count) : null;
    }
}
