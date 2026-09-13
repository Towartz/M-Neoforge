package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TpClick extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Integer> reach = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("reach")
            .description("Maximum click teleport distance.")
            .defaultValue(50)
            .min(10)
            .max(250)
            .sliderMax(150)
            .build()
    );

    private final Setting<Double> stepSize = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("step-size")
            .description("Distance per packet step to bypass anticheat checks.")
            .defaultValue(8.5)
            .min(1.0)
            .max(10.0)
            .build()
    );

    private final Setting<Keybind> clickKey = this.sgGeneral.add(
        new KeybindSetting.Builder()
            .name("click-key")
            .description("Keybind that triggers teleportation to target.")
            .defaultValue(Keybind.fromButton(1))
            .action(this::tryTeleport)
            .build()
    );

    private final Setting<Boolean> noFall = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("no-fall")
            .description("Prevents fall damage upon teleportation.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> render = this.sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Renders bounding box on targeted destination.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = this.sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the preview box is rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private static final Color COLOR_VALID = new Color(0, 230, 80, 100);
    private static final Color COLOR_VALID_LINE = new Color(0, 230, 80, 220);
    private static final Color COLOR_ADJUSTED = new Color(255, 175, 0, 100);
    private static final Color COLOR_ADJUSTED_LINE = new Color(255, 175, 0, 220);
    private static final Color COLOR_INVALID = new Color(255, 50, 50, 100);
    private static final Color COLOR_INVALID_LINE = new Color(255, 50, 50, 220);

    private AABB highlightBox;
    private boolean targetValid;
    private boolean adjustedTarget;
    private Vec3 destination;

    public TpClick() {
        super(Categories.Movement, "tp-click", "Teleport directly to where you look via packet stepping.");
    }

    @Override
    public void onDeactivate() {
        resetTarget();
    }

    private void resetTarget() {
        highlightBox = null;
        targetValid = false;
        adjustedTarget = false;
        destination = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            resetTarget();
            return;
        }

        updateTarget();
    }

    private void updateTarget() {
        highlightBox = null;
        targetValid = false;
        adjustedTarget = false;
        destination = null;

        HitResult picked = mc.player.pick(reach.get(), 0.0F, false);
        if (picked instanceof BlockHitResult blockHit && picked.getType() == HitResult.Type.BLOCK) {
            BlockPos base = blockHit.getBlockPos();

            for (int climb = 1; climb <= 4; climb++) {
                Vec3 spot = standingSpot(base.above(climb));
                if (spot != null) {
                    accept(spot, false);
                    return;
                }
            }

            Vec3 nearest = nearestViable(base);
            if (nearest != null) {
                accept(nearest, true);
                return;
            }

            highlightBox = new AABB(base).inflate(0.002);
        }
    }

    private void accept(Vec3 spot, boolean adjusted) {
        destination = spot;
        highlightBox = new AABB(BlockPos.containing(spot).below()).inflate(0.002);
        targetValid = true;
        adjustedTarget = adjusted;
    }

    private Vec3 nearestViable(BlockPos base) {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dy = -2; dy <= 8; dy++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    Vec3 spot = standingSpot(base.offset(dx, dy, dz));
                    if (spot != null) {
                        double score = (dx * dx + dz * dz) + Math.abs(dy) * 4.0;
                        if (score < bestScore) {
                            bestScore = score;
                            best = spot;
                        }
                    }
                }
            }
        }

        return best;
    }

    private Vec3 standingSpot(BlockPos feet) {
        if (mc.level == null) return null;
        return isFree(feet) && isFree(feet.above()) && isGroundBelow(feet) ? Vec3.atBottomCenterOf(feet) : null;
    }

    private boolean isFree(BlockPos pos) {
        return mc.level != null && mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    private boolean isGroundBelow(BlockPos feet) {
        if (mc.level == null) return false;
        BlockPos below = feet.below();
        return !mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty();
    }

    private void tryTeleport() {
        if (!isActive() || !targetValid || destination == null || mc.player == null || mc.getConnection() == null) {
            return;
        }

        Vec3 origin = mc.player.position();
        Vec3 diff = destination.subtract(origin);
        double dist = diff.length();
        if (dist <= 0.1) return;

        double maxStep = Math.max(1.0, stepSize.get());
        int steps = (int) Math.ceil(dist / maxStep);

        for (int i = 1; i <= steps; i++) {
            double fraction = (double) i / steps;
            Vec3 stepPos = origin.add(diff.scale(fraction));
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(stepPos.x, stepPos.y, stepPos.z, true));
        }

        mc.player.setPos(destination.x, destination.y, destination.z);
        mc.player.setDeltaMovement(Vec3.ZERO);

        if (noFall.get()) {
            mc.player.fallDistance = 0.0F;
        }

        info("Teleported " + String.format("%.1f", dist) + "m.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || highlightBox == null) return;

        Color sideColor = targetValid ? (adjustedTarget ? COLOR_ADJUSTED : COLOR_VALID) : COLOR_INVALID;
        Color lineColor = targetValid ? (adjustedTarget ? COLOR_ADJUSTED_LINE : COLOR_VALID_LINE) : COLOR_INVALID_LINE;

        event.renderer.box(highlightBox, sideColor, lineColor, shapeMode.get(), 0);
    }
}
