package meteordevelopment.meteorclient.systems.modules.world.plus;

import java.util.ArrayDeque;
import meteordevelopment.meteorclient.events.entity.player.BreakBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.state.BlockState;

public class GhostBlockFixer extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> delay = this.sgGeneral.add(new IntSetting.Builder()
        .name("Fixer delay")
        .description("Delay for block fixing in milliseconds.")
        .defaultValue(50)
        .range(1, 250)
        .sliderRange(1, 250)
        .build()
    );

    private final Setting<Integer> range = this.sgGeneral.add(new IntSetting.Builder()
        .name("Fixer range")
        .description("Range to query block state.")
        .defaultValue(6)
        .range(1, 80)
        .sliderRange(1, 80)
        .build()
    );

    private final ArrayDeque<BlockPos> blocks = new ArrayDeque<>();
    private long millis = 0L;

    public GhostBlockFixer() {
        super(Categories.PlusWorld, "auto-ghost-block-fixer", "Automatically fixes client-side ghost blocks by querying server block state.");
    }

    @Override
    public void onDeactivate() {
        this.blocks.clear();
        this.millis = 0L;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.blocks.clear();
        this.millis = 0L;
    }

    @EventHandler
    private void onBlockBreak(BreakBlockEvent event) {
        if (event.blockPos != null) {
            this.blocks.add(event.blockPos);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null || this.blocks.isEmpty()) return;

        if (System.currentTimeMillis() < this.millis) return;

        BlockPos pos = this.blocks.peek();
        if (pos == null) {
            this.blocks.poll();
            return;
        }

        double distSq = this.mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double maxDist = this.range.get();

        if (distSq <= maxDist * maxDist) {
            BlockState state = this.mc.level.getBlockState(pos);
            if (state.isAir()) {
                this.millis = System.currentTimeMillis() + this.delay.get();
                ServerboundPlayerActionPacket packet = new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    pos,
                    Direction.UP,
                    0
                );
                this.mc.getConnection().send(packet);
            }
        }
        this.blocks.poll();
    }
}
