package meteordevelopment.meteorclient.systems.modules.movement.plus.speed.modes.vulcan;

import meteordevelopment.meteorclient.events.entity.player.JumpVelocityMultiplierEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.speed.SpeedModes;
import meteordevelopment.meteorclient.utils.plus.PlusMovementUtils;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class Vulcan_2_8_6 extends SpeedMode {
    private int ticks = 0;
    private int speedLevel = 0;
    private boolean jumped = false;

    public Vulcan_2_8_6() {
        super(SpeedModes.Vulcan_2dot8dot6);
    }

    @Override
    public void onJump(JumpVelocityMultiplierEvent event) {
        if (mc.player == null) return;
        this.ticks = 0;
        this.speedLevel = 0;
        this.jumped = true;
        if (mc.player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            this.speedLevel = mc.player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier();
        }
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (this.jumped) {
            this.ticks++;
            if (this.ticks == 1) {
                PlusMovementUtils.strafe((float) (0.3355 * (1.0 + (double) this.speedLevel * 0.3819)));
            }

            if (this.ticks == 2 && mc.player.isSprinting()) {
                PlusMovementUtils.strafe((float) (0.3284 * (1.0 + (double) this.speedLevel * 0.355)));
            }

            if (this.ticks == 4) {
                Vec3 vel = mc.player.position();
                mc.player.setPos(vel.x, vel.y - 0.376, vel.z);
            }

            if (this.ticks == 6) {
                if ((double) mc.player.moveDist > 0.298) {
                    PlusMovementUtils.strafe(0.298F);
                }
                this.jumped = false;
            }
        }
    }
}
