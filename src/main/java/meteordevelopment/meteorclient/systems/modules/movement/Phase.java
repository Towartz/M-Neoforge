package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.shapes.Shapes;

public class Phase extends Module {
    public enum Mode {
        Collision,
        Clip,
        Sand,
        All
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("How phasing is performed.")
            .defaultValue(Mode.All)
            .build()
    );

    private final Setting<Boolean> doors = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("doors")
            .description("Phases through doors, trapdoors, and fence gates.")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Collision || mode.get() == Mode.All)
            .build()
    );

    private final Setting<Double> clipDistance = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("clip-distance")
            .description("Distance pushed forward per tick when colliding with a wall.")
            .defaultValue(0.2)
            .min(0.05)
            .max(1.0)
            .visible(() -> mode.get() == Mode.Clip || mode.get() == Mode.All)
            .build()
    );

    private final Setting<Boolean> autoNoFall = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("no-fall")
            .description("Resets fall distance while phasing.")
            .defaultValue(true)
            .build()
    );

    public Phase() {
        super(Categories.Movement, "phase", "Phase and clip through walls, doors, fences, and falling blocks.");
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (!isActive() || mc.player == null) return;

        Mode currentMode = mode.get();

        // Door/Trapdoor/FenceGate phasing
        if ((currentMode == Mode.Collision || currentMode == Mode.All) && doors.get()) {
            if (event.state.getBlock() instanceof DoorBlock || event.state.getBlock() instanceof TrapDoorBlock || event.state.getBlock() instanceof FenceGateBlock) {
                event.shape = Shapes.empty();
                return;
            }
        }

        // Falling block phasing (sand, gravel, concrete powder)
        if (currentMode == Mode.Sand || currentMode == Mode.All) {
            if (event.state.getBlock() instanceof FallingBlock) {
                event.shape = Shapes.empty();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null || mc.getConnection() == null) return;

        Mode currentMode = mode.get();
        if (currentMode != Mode.Clip && currentMode != Mode.All) return;

        if (mc.player.horizontalCollision) {
            float yaw = mc.player.getYRot();
            double rad = Math.toRadians(yaw);
            double dist = clipDistance.get();

            double dx = -Math.sin(rad) * dist;
            double dz = Math.cos(rad) * dist;

            double newX = mc.player.getX() + dx;
            double newY = mc.player.getY();
            double newZ = mc.player.getZ() + dz;

            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(newX, newY, newZ, true));
            mc.player.setPos(newX, newY, newZ);

            if (autoNoFall.get()) {
                mc.player.fallDistance = 0.0F;
            }
        }
    }
}
