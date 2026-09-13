package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoFarm extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgCrops = this.settings.createGroup("Crops");

    private final Setting<Double> range = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Farming reach in blocks.")
            .defaultValue(4.5)
            .min(1.0)
            .max(6.0)
            .sliderMax(6.0)
            .build()
    );

    private final Setting<Integer> delay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("Tick delay between farming actions.")
            .defaultValue(2)
            .min(0)
            .max(20)
            .build()
    );

    private final Setting<Boolean> replant = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("replant")
            .description("Automatically replants seeds from hotbar after harvesting.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> bonemeal = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("bonemeal")
            .description("Applies bone meal to growing crops if available in hotbar.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> wheat = this.sgCrops.add(new BoolSetting.Builder().name("wheat").defaultValue(true).build());
    private final Setting<Boolean> carrots = this.sgCrops.add(new BoolSetting.Builder().name("carrots").defaultValue(true).build());
    private final Setting<Boolean> potatoes = this.sgCrops.add(new BoolSetting.Builder().name("potatoes").defaultValue(true).build());
    private final Setting<Boolean> beetroots = this.sgCrops.add(new BoolSetting.Builder().name("beetroots").defaultValue(true).build());
    private final Setting<Boolean> netherWart = this.sgCrops.add(new BoolSetting.Builder().name("nether-wart").defaultValue(true).build());
    private final Setting<Boolean> cocoa = this.sgCrops.add(new BoolSetting.Builder().name("cocoa").defaultValue(true).build());
    private final Setting<Boolean> sweetBerries = this.sgCrops.add(new BoolSetting.Builder().name("sweet-berries").defaultValue(true).build());

    private int timer = 0;

    public AutoFarm() {
        super(Categories.World, "auto-farm", "Harvests fully mature crops and automatically replants them.");
    }

    @Override
    public void onDeactivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        int r = (int) Math.ceil(range.get());
        double rangeSq = range.get() * range.get();

        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    if (mc.player.distanceToSqr(Vec3.atCenterOf(pos)) <= rangeSq) {
                        candidates.add(pos);
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(Vec3.atCenterOf(p))));

        for (BlockPos pos : candidates) {
            BlockState state = mc.level.getBlockState(pos);
            Block block = state.getBlock();

            // 1. Sweet Berry Bush (Harvest via right-click without breaking)
            if (sweetBerries.get() && block instanceof SweetBerryBushBlock) {
                int age = state.getValue(SweetBerryBushBlock.AGE);
                if (age >= 2) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    timer = delay.get();
                    return;
                }
            }

            // 2. Standard Crops
            Item seedItem = null;
            boolean isMature = false;

            if (block instanceof CropBlock crop) {
                if (crop.isMaxAge(state)) {
                    if (state.is(Blocks.WHEAT) && wheat.get()) {
                        isMature = true;
                        seedItem = Items.WHEAT_SEEDS;
                    } else if (state.is(Blocks.CARROTS) && carrots.get()) {
                        isMature = true;
                        seedItem = Items.CARROT;
                    } else if (state.is(Blocks.POTATOES) && potatoes.get()) {
                        isMature = true;
                        seedItem = Items.POTATO;
                    } else if (state.is(Blocks.BEETROOTS) && beetroots.get()) {
                        isMature = true;
                        seedItem = Items.BEETROOT_SEEDS;
                    }
                }
            } else if (block instanceof NetherWartBlock && netherWart.get()) {
                if (state.getValue(NetherWartBlock.AGE) >= 3) {
                    isMature = true;
                    seedItem = Items.NETHER_WART;
                }
            } else if (block instanceof CocoaBlock && cocoa.get()) {
                if (state.getValue(CocoaBlock.AGE) >= 2) {
                    isMature = true;
                    seedItem = Items.COCOA_BEANS;
                }
            }

            if (isMature) {
                mc.gameMode.destroyBlock(pos);
                mc.player.swing(InteractionHand.MAIN_HAND);

                if (replant.get() && seedItem != null) {
                    FindItemResult seedResult = InvUtils.findInHotbar(seedItem);
                    if (seedResult.found()) {
                        InvUtils.swap(seedResult.slot(), true);
                        if (block instanceof CocoaBlock) {
                            Direction facing = state.getValue(CocoaBlock.FACING);
                            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), facing, pos.relative(facing), false));
                        } else {
                            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos.below(), false));
                        }
                        InvUtils.swapBack();
                    }
                }

                timer = delay.get();
                return;
            }

            // 3. Bone Meal support for immature crops
            if (bonemeal.get() && block instanceof BonemealableBlock bonemealable) {
                if (bonemealable.isValidBonemealTarget(mc.level, pos, state)) {
                    FindItemResult bm = InvUtils.findInHotbar(Items.BONE_MEAL);
                    if (bm.found()) {
                        InvUtils.swap(bm.slot(), true);
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        InvUtils.swapBack();
                        timer = delay.get();
                        return;
                    }
                }
            }
        }
    }
}
