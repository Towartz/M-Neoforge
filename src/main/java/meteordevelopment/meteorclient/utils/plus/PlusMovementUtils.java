package meteordevelopment.meteorclient.utils.plus;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.world.phys.Vec3;

public class PlusMovementUtils {
    public static double getSpeed() {
        if (MeteorClient.mc.player == null) return 0.0;
        Vec3 vel = MeteorClient.mc.player.getDeltaMovement();
        return Math.sqrt(vel.x * vel.x + vel.z * vel.z);
    }

    public static void strafe(float speed) {
        strafe((double) speed);
    }

    public static void strafe(double speed) {
        if (MeteorClient.mc.player == null) return;
        double yaw = direction();
        double sin = -Math.sin(yaw) * speed;
        double cos = Math.cos(yaw) * speed;
        MeteorClient.mc.player.setDeltaMovement(cos, MeteorClient.mc.player.getDeltaMovement().y, sin);
    }

    public static double direction() {
        if (MeteorClient.mc.player == null) return 0.0;
        float yaw = MeteorClient.mc.player.getYRot();
        float forward = MeteorClient.mc.player.input.forwardImpulse;
        float strafe = MeteorClient.mc.player.input.leftImpulse;

        if (forward < 0.0F) {
            yaw += 180.0F;
        }

        float f = 1.0F;
        if (forward < 0.0F) {
            f = -0.5F;
        } else if (forward > 0.0F) {
            f = 0.5F;
        }

        if (strafe > 0.0F) {
            yaw -= 90.0F * f;
        }
        if (strafe < 0.0F) {
            yaw += 90.0F * f;
        }

        return Math.toRadians((double) yaw);
    }
}
