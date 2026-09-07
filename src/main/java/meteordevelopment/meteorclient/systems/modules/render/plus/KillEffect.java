package meteordevelopment.meteorclient.systems.modules.render.plus;

import java.util.HashSet;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;

public class KillEffect extends Module {
    public enum Effect {
        Lightning,
        Blood,
        Both
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Effect> effect = this.sgGeneral.add(new EnumSetting.Builder<Effect>()
        .name("effect")
        .description("Visual effect to trigger on entity kill.")
        .defaultValue(Effect.Both)
        .build()
    );

    private final Set<Integer> deadEntities = new HashSet<>();

    public KillEffect() {
        super(Categories.PlusRender, "kill-effect", "Renders lightning bolts and particle bursts when entities die.");
    }

    @Override
    public void onDeactivate() {
        this.deadEntities.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.level == null || this.mc.player == null) return;

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && living != this.mc.player) {
                if ((living.isDeadOrDying() || living.getHealth() <= 0.0f) && !this.deadEntities.contains(living.getId())) {
                    if (this.mc.player.distanceToSqr(living) <= 400.0) { // Within 20 blocks
                        this.spawnEffect(living);
                    }
                    this.deadEntities.add(living.getId());
                }
            }
        }
    }

    private void spawnEffect(LivingEntity living) {
        Effect eff = this.effect.get();

        if (eff == Effect.Lightning || eff == Effect.Both) {
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, this.mc.level);
            bolt.setPos(living.getX(), living.getY(), living.getZ());
            bolt.setVisualOnly(true);
            this.mc.level.addEntity(bolt);
            this.mc.level.playLocalSound(living.getX(), living.getY(), living.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 1.0f, false);
        }

        if (eff == Effect.Blood || eff == Effect.Both) {
            for (int i = 0; i < 30; i++) {
                double offsetX = (Math.random() - 0.5) * living.getBbWidth();
                double offsetY = Math.random() * living.getBbHeight();
                double offsetZ = (Math.random() - 0.5) * living.getBbWidth();
                this.mc.level.addParticle(
                    ParticleTypes.DAMAGE_INDICATOR,
                    living.getX() + offsetX,
                    living.getY() + offsetY,
                    living.getZ() + offsetZ,
                    (Math.random() - 0.5) * 0.2,
                    Math.random() * 0.2,
                    (Math.random() - 0.5) * 0.2
                );
            }
        }
    }
}
