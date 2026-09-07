package meteordevelopment.meteorclient.systems.modules.movement.plus.fly.modes;

import meteordevelopment.meteorclient.events.entity.DamageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fly.FlyMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.fly.FlyModes;
import net.minecraft.world.phys.Vec3;

public class Damage extends FlyMode {
    public static int workingTicks = 15;
    public static int workingUpTicks = 0;
    public static double speed = 0.0;
    public static double speedUp = 0.0;
    private int ticks = 0;
    private int ticks_up = 0;
    private boolean damaged = false;

    public Damage() {
        super(FlyModes.Damage);
    }

    @Override
    public void onActivate() {
        this.damaged = false;
        this.ticks = 0;
        this.ticks_up = 0;
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (this.damaged && this.ticks != workingTicks) {
            float yaw = mc.player.getYRot();
            Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
            double velX = 0.0;
            double velZ = 0.0;
            double s = speed;

            if (mc.options.keyUp.isDown()) {
                velX += forward.x * s;
                velZ += forward.z * s;
            }
            if (mc.options.keyDown.isDown()) {
                velX -= forward.x * s;
                velZ -= forward.z * s;
            }

            if (this.ticks_up < workingUpTicks) {
                mc.player.setDeltaMovement(velX, speedUp, velZ);
            } else {
                mc.player.setDeltaMovement(velX, 0.0, velZ);
            }

            this.ticks++;
            this.ticks_up++;
        } else if (this.damaged) {
            this.damaged = false;
            this.ticks = 0;
            this.ticks_up = 0;
        }
    }

    @Override
    public void onDamage(DamageEvent event) {
        if (event.entity == mc.player) {
            this.damaged = true;
            this.ticks = 0;
            this.ticks_up = 0;
        }
    }
}
