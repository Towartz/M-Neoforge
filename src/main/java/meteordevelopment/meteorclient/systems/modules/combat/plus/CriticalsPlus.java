package meteordevelopment.meteorclient.systems.modules.combat.plus;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.Criticals;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class CriticalsPlus extends Module {
    public CriticalsPlus() {
        super(Categories.PlusCombat, "Criticals+", "Better criticals helper module.");
    }

    public static boolean canCrit() {
        if (MeteorClient.mc.player == null) return false;
        return !MeteorClient.mc.player.onGround() && MeteorClient.mc.player.fallDistance > 0.0F;
    }

    public static boolean skipCrit() {
        if (MeteorClient.mc.player == null) return true;
        return !MeteorClient.mc.player.onGround() || MeteorClient.mc.player.isInWater() || MeteorClient.mc.player.isInLava() || MeteorClient.mc.player.onClimbable();
    }

    public static boolean allowCrit() {
        if (canCrit()) return true;
        Criticals crits = Modules.get().get(Criticals.class);
        return crits != null && crits.isActive() && !skipCrit();
    }

    public static boolean needCrit(Entity entity) {
        if (MeteorClient.mc.player == null) return false;
        return entity instanceof LivingEntity living ? living.getHealth() >= DamageUtils.getAttackDamage(MeteorClient.mc.player, living) : false;
    }
}
