package meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.modes;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowMode;
import meteordevelopment.meteorclient.systems.modules.movement.plus.noslow.NoSlowModes;
import net.minecraft.world.phys.Vec3;

public class Matrix extends NoSlowMode {
    private int ticks = 0;

    public Matrix() {
        super(NoSlowModes.Matrix);
    }

    @Override
    public void onActivate() {
        this.ticks = 0;
    }

    @Override
    public void onTickEventPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.player.isUsingItem() && mc.player.onGround() && this.ticks % 2 == 0) {
            float speed = 0.4F;
            Vec3 vel = mc.player.getDeltaMovement();
            double x = vel.x * (double) speed;
            double z = vel.z * (double) speed;
            mc.player.setDeltaMovement(x, vel.y, z);
        }
        this.ticks++;
    }
}
