package meteordevelopment.meteorclient.systems.modules.movement;

import com.google.common.collect.Streams;
import java.util.OptionalDouble;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

public class Step extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Double> height = this.sgGeneral
      .add(new DoubleSetting.Builder().name("height").description("Step height.").defaultValue(1.0).min(0.0).build());
   private final Setting<Step.ActiveWhen> activeWhen = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("active-when"))
                  .description("Step is active when you meet these requirements."))
               .defaultValue(Step.ActiveWhen.Always))
            .build()
      );
   private final Setting<Boolean> safeStep = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("safe-step")
            .description("Doesn't let you step out of a hole if you are low on health or there is a crystal nearby.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Integer> stepHealth = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("step-health")
            .description("The health you stop being able to step at.")
            .defaultValue(Integer.valueOf(5))
            .range(1, 36)
            .sliderRange(1, 36)
            .visible(this.safeStep::get)
            .build()
      );
   private float prevStepHeight;
   private boolean prevPathManagerStep;

   public Step() {
      super(Categories.Movement, "step", "Allows you to walk up full blocks instantly.");
   }

   @Override
   public void onActivate() {
      this.prevStepHeight = this.mc.player.maxUpStep();
      this.prevPathManagerStep = PathManagers.get().getSettings().getStep().get();
      PathManagers.get().getSettings().getStep().set(true);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      boolean work = this.activeWhen.get() == Step.ActiveWhen.Always
         || this.activeWhen.get() == Step.ActiveWhen.Sneaking && this.mc.player.isShiftKeyDown()
         || this.activeWhen.get() == Step.ActiveWhen.NotSneaking && !this.mc.player.isShiftKeyDown();
      this.mc.player.setBoundingBox(this.mc.player.getBoundingBox().move(0.0, 1.0, 0.0));
      if (!work
         || this.safeStep.get()
            && (
               !(this.getHealth() > (float)this.stepHealth.get().intValue())
                  || !((double)this.getHealth() - this.getExplosionDamage() > (double)this.stepHealth.get().intValue())
            )) {
         this.mc.player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue((double)this.prevStepHeight);
      } else {
         this.mc.player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(this.height.get());
      }

      this.mc.player.setBoundingBox(this.mc.player.getBoundingBox().move(0.0, -1.0, 0.0));
   }

   @Override
   public void onDeactivate() {
      this.mc.player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue((double)this.prevStepHeight);
      PathManagers.get().getSettings().getStep().set(this.prevPathManagerStep);
   }

   private float getHealth() {
      return this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
   }

   private double getExplosionDamage() {
      OptionalDouble crystalDamage = Streams.stream(this.mc.level.entitiesForRendering())
         .filter(entity -> entity instanceof EndCrystal)
         .filter(Entity::isAlive)
         .mapToDouble(entity -> (double)DamageUtils.crystalDamage(this.mc.player, entity.position()))
         .max();
      return crystalDamage.orElse(0.0);
   }

   public static enum ActiveWhen {
      Always,
      Sneaking,
      NotSneaking;
   }
}
