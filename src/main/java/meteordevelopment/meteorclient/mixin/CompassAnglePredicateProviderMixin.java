package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({CompassItemPropertyFunction.class})
public abstract class CompassAnglePredicateProviderMixin {
   @ModifyExpressionValue(
      method = {"getBodyYaw"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;getBodyYaw()F"
      )}
   )
   private float callLivingEntityGetYaw(float original) {
      return Modules.get().isActive(Freecam.class) ? MeteorClient.mc.gameRenderer.getMainCamera().getYRot() : original;
   }

   @ModifyReturnValue(
      method = {"getAngleTo(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/BlockPos;)D"},
      at = {@At("RETURN")}
   )
   private double modifyGetAngleTo(double original, Entity entity, BlockPos pos) {
      if (Modules.get().isActive(Freecam.class)) {
         Vec3 vec3d = Vec3.atCenterOf(pos);
         Camera camera = MeteorClient.mc.gameRenderer.getMainCamera();
         return Math.atan2(vec3d.z() - camera.getPosition().z, vec3d.x() - camera.getPosition().x) / (float) (Math.PI * 2);
      } else {
         return original;
      }
   }
}
