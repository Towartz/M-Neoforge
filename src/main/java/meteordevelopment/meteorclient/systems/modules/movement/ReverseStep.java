package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.BedBlock;

public class ReverseStep extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> fallSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("fall-speed").description("How fast to fall in blocks per second.").defaultValue(3.0).min(0.0).build());
   private final Setting<Double> fallDistance = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fall-distance")
            .description("The maximum fall distance this setting will activate at.")
            .defaultValue(3.0)
            .min(0.0)
            .build()
      );

   public ReverseStep() {
      super(Categories.Movement, "reverse-step", "Allows you to fall down blocks at a greater speed.");
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player.onGround()
         && !this.mc.player.isSuppressingSlidingDownLadder()
         && !this.mc.player.isUnderWater()
         && !this.mc.player.isInLava()
         && !this.mc.options.keyJump.isDown()
         && !this.mc.player.noPhysics
         && (this.mc.player.zza != 0.0F || this.mc.player.xxa != 0.0F)) {
         if (!this.isOnBed()
            && !this.mc.level.noCollision(this.mc.player.getBoundingBox().move(0.0, (double)((float)(-(this.fallDistance.get() + 0.01))), 0.0))) {
            ((IVec3d)this.mc.player.getDeltaMovement()).setY(-this.fallSpeed.get());
         }
      }
   }

   private boolean isOnBed() {
      MutableBlockPos blockPos = this.mc.player.blockPosition().mutable();
      if (this.check(blockPos, 0, 0)) {
         return true;
      } else {
         double xa = this.mc.player.getX() - (double)blockPos.getX();
         double za = this.mc.player.getZ() - (double)blockPos.getZ();
         if (xa >= 0.0 && xa <= 0.3 && this.check(blockPos, -1, 0)) {
            return true;
         } else if (xa >= 0.7 && this.check(blockPos, 1, 0)) {
            return true;
         } else if (za >= 0.0 && za <= 0.3 && this.check(blockPos, 0, -1)) {
            return true;
         } else if (za >= 0.7 && this.check(blockPos, 0, 1)) {
            return true;
         } else if (xa >= 0.0 && xa <= 0.3 && za >= 0.0 && za <= 0.3 && this.check(blockPos, -1, -1)) {
            return true;
         } else if (xa >= 0.0 && xa <= 0.3 && za >= 0.7 && this.check(blockPos, -1, 1)) {
            return true;
         } else {
            return xa >= 0.7 && za >= 0.0 && za <= 0.3 && this.check(blockPos, 1, -1) ? true : xa >= 0.7 && za >= 0.7 && this.check(blockPos, 1, 1);
         }
      }
   }

   private boolean check(MutableBlockPos blockPos, int x, int z) {
      blockPos.move(x, 0, z);
      boolean is = this.mc.level.getBlockState(blockPos).getBlock() instanceof BedBlock;
      blockPos.move(-x, 0, -z);
      return is;
   }
}
