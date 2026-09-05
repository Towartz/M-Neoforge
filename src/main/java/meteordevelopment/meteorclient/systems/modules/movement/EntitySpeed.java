package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.LivingEntityMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class EntitySpeed extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> speed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("speed").description("Horizontal speed in blocks per second.").defaultValue(10.0).min(0.0).sliderMax(50.0).build());
   private final Setting<Boolean> onlyOnGround = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("only-on-ground").description("Use speed only when standing on a block.").defaultValue(Boolean.valueOf(false)).build()
      );
   private final Setting<Boolean> inWater = this.sgGeneral
      .add(new BoolSetting.Builder().name("in-water").description("Use speed when in water.").defaultValue(Boolean.valueOf(false)).build());

   public EntitySpeed() {
      super(Categories.Movement, "entity-speed", "Makes you go faster when riding entities.");
   }

   @EventHandler
   private void onLivingEntityMove(LivingEntityMoveEvent event) {
      if (event.entity.getControllingPassenger() == this.mc.player) {
         LivingEntity entity = event.entity;
         if (!this.onlyOnGround.get() || entity.onGround()) {
            if (this.inWater.get() || !entity.isInWater()) {
               Vec3 vel = PlayerUtils.getHorizontalVelocity(this.speed.get());
               ((IVec3d)event.movement).setXZ(vel.x, vel.z);
            }
         }
      }
   }
}
