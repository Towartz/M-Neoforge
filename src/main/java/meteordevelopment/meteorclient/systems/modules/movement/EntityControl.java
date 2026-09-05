package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayerEntityAccessor;
import meteordevelopment.meteorclient.mixininterface.IHorseBaseEntity;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

public class EntityControl extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> maxJump = this.sgGeneral
      .add(new BoolSetting.Builder().name("max-jump").description("Sets jump power to maximum.").defaultValue(Boolean.valueOf(true)).build());

   public EntityControl() {
      super(Categories.Movement, "entity-control", "Lets you control rideable entities without a saddle.");
   }

   @Override
   public void onDeactivate() {
      if (Utils.canUpdate() && this.mc.level.entitiesForRendering() != null) {
         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof AbstractHorse) {
               ((IHorseBaseEntity)entity).setSaddled(false);
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (entity instanceof AbstractHorse) {
            ((IHorseBaseEntity)entity).setSaddled(true);
         }
      }

      if (this.maxJump.get()) {
         ((ClientPlayerEntityAccessor)this.mc.player).setMountJumpStrength(1.0F);
      }
   }
}
