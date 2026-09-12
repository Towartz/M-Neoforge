package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class TillAura extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Double> range = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far to reach for tilling blocks.")
        .defaultValue(4.5)
        .min(1.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Integer> delay = this.sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between till actions in ticks.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> autoSwitch = this.sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically selects a hoe from your hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = this.sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Smoothly rotates to the block before tilling.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = this.sgRender.add(new BoolSetting.Builder().name("render").description("Renders bounding box around targeted block.").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = this.sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").description("How the shape is rendered.").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = this.sgRender.add(new ColorSetting.Builder().name("side-color").description("Side color.").defaultValue(new SettingColor(150, 75, 0, 25)).build());
    private final Setting<SettingColor> lineColor = this.sgRender.add(new ColorSetting.Builder().name("line-color").description("Line color.").defaultValue(new SettingColor(150, 75, 0, 200)).build());

    private int timer;
    private BlockPos currentTarget;

    public TillAura() {
        super(Categories.World, "till-aura", "Automatically tills nearby dirt and grass into farmland with a hoe.");
    }

    @Override
    public void onActivate() {
        this.timer = 0;
        this.currentTarget = null;
    }

    @Override
    public void onDeactivate() {
        this.currentTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) return;

        if (this.timer > 0) {
            this.timer--;
            return;
        }

        boolean holdingHoe = this.mc.player.getMainHandItem().getItem() instanceof HoeItem || this.mc.player.getOffhandItem().getItem() instanceof HoeItem;
        int hoeSlot = -1;
        if (!holdingHoe && this.autoSwitch.get()) {
            FindItemResult result = InvUtils.findInHotbar(stack -> stack.getItem() instanceof HoeItem);
            if (result.found()) {
                hoeSlot = result.slot();
            } else {
                this.currentTarget = null;
                return;
            }
        } else if (!holdingHoe) {
            this.currentTarget = null;
            return;
        }

        BlockPos target = this.findClosestTarget();
        if (target == null) {
            this.currentTarget = null;
            return;
        }

        this.currentTarget = target;

        if (hoeSlot != -1 && !holdingHoe) {
            InvUtils.swap(hoeSlot, false);
        }

        InteractionHand hand = this.mc.player.getMainHandItem().getItem() instanceof HoeItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        if (this.rotate.get()) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 50, () -> this.till(target, hand));
        } else {
            this.till(target, hand);
        }

        this.timer = this.delay.get();
    }

    private void till(BlockPos pos, InteractionHand hand) {
        if (this.mc.gameMode == null || this.mc.player == null) return;
        BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP, pos, false);
        this.mc.gameMode.useItemOn(this.mc.player, hand, hit);
        this.mc.player.swing(hand);
    }

    private BlockPos findClosestTarget() {
        if (this.mc.player == null || this.mc.level == null) return null;

        double r = this.range.get();
        double rSq = r * r;
        Vec3 playerPos = this.mc.player.position();
        int px = this.mc.player.getBlockX();
        int py = this.mc.player.getBlockY();
        int pz = this.mc.player.getBlockZ();
        int reach = (int) Math.ceil(r);

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = px - reach; x <= px + reach; x++) {
            for (int y = py - reach; y <= py + reach; y++) {
                for (int z = pz - reach; z <= pz + reach; z++) {
                    mpos.set(x, y, z);
                    double dSq = mpos.distToCenterSqr(playerPos);
                    if (dSq > rSq || dSq >= bestDistSq) continue;
                    if (this.isTillable(mpos)) {
                        bestDistSq = dSq;
                        bestPos = mpos.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean isTillable(BlockPos pos) {
        BlockState state = this.mc.level.getBlockState(pos);
        Block block = state.getBlock();

        if (block != Blocks.GRASS_BLOCK && block != Blocks.DIRT && block != Blocks.DIRT_PATH && block != Blocks.COARSE_DIRT) {
            return false;
        }

        BlockState above = this.mc.level.getBlockState(pos.above());
        return above.isAir();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get() && this.currentTarget != null) {
            event.renderer.box(this.currentTarget, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
        }
    }
}
