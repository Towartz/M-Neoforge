package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class EndermanLook extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<EndermanLook.Mode> lookMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("look-mode")).description("How this module behaves."))
               .defaultValue(EndermanLook.Mode.Away))
            .build()
      );
   private final Setting<Boolean> stun = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("stun-hostiles")
            .description("Automatically stares at hostile endermen to stun them in place.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.lookMode.get() == EndermanLook.Mode.Away)
            .build()
      );

   public EndermanLook() {
      super(Categories.World, "enderman-look", "Either looks at all Endermen or prevents you from looking at Endermen.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (!((ItemStack)this.mc.player.getInventory().armor.get(3)).is(Blocks.CARVED_PUMPKIN.asItem()) && !this.mc.player.getAbilities().instabuild) {
         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof EnderMan) {
               EnderMan enderman = (EnderMan)entity;
               if (enderman.isAlive() && this.mc.player.hasLineOfSight(enderman)) {
                  switch ((EndermanLook.Mode)this.lookMode.get()) {
                     case At:
                        if (!enderman.isCreepy()) {
                           Rotations.rotate(Rotations.getYaw(enderman), Rotations.getPitch(enderman, Target.Head), -75, null);
                        }
                        break;
                     case Away:
                        if (enderman.isCreepy() && this.stun.get()) {
                           Rotations.rotate(Rotations.getYaw(enderman), Rotations.getPitch(enderman, Target.Head), -75, null);
                        } else if (this.angleCheck(enderman)) {
                           Rotations.rotate((double)this.mc.player.getYRot(), 90.0, -75, null);
                        }
                  }
               }
            }
         }
      }
   }

   private boolean angleCheck(EnderMan entity) {
      Vec3 vec3d = this.mc.player.getViewVector(1.0F).normalize();
      Vec3 vec3d2 = new Vec3(entity.getX() - this.mc.player.getX(), entity.getEyeY() - this.mc.player.getEyeY(), entity.getZ() - this.mc.player.getZ());
      double d = vec3d2.length();
      vec3d2 = vec3d2.normalize();
      double e = vec3d.dot(vec3d2);
      return e > 1.0 - 0.025 / d;
   }

   public static enum Mode {
      At,
      Away;
   }
}
