package meteordevelopment.meteorclient.systems.modules.world.plus;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

public class SafeMine extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<Boolean> antiMine = this.sgGeneral.add(new BoolSetting.Builder()
        .name("anti-mine")
        .description("Prevents mining blocks directly adjacent to lava or water hazards.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> solidLava = this.sgGeneral.add(new BoolSetting.Builder()
        .name("solid-lava")
        .description("Treats lava surface as solid to prevent falling in.")
        .defaultValue(true)
        .build()
    );

    public SafeMine() {
        super(Categories.PlusWorld, "Safe Mine", "Prevents breaking blocks that would cause dangerous lava or fluids to pour over the player.");
    }

    private boolean isHazard(BlockPos pos) {
        if (this.mc.level == null) return false;
        BlockState state = this.mc.level.getBlockState(pos);
        return state.is(Blocks.LAVA) || state.getFluidState().is(FluidTags.LAVA);
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!this.antiMine.get() || this.mc.level == null) return;

        BlockPos pos = event.blockPos;
        for (Direction dir : Direction.values()) {
            if (isHazard(pos.relative(dir))) {
                event.cancel();
                return;
            }
        }
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (this.solidLava.get() && this.mc.player != null && event.state.is(Blocks.LAVA) && !this.mc.player.isEyeInFluid(FluidTags.LAVA)) {
            event.shape = Shapes.block();
        }
    }
}
