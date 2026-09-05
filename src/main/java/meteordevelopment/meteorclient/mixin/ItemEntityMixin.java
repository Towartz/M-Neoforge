package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.IItemEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({ItemEntity.class})
public abstract class ItemEntityMixin implements IItemEntity {
   @Unique
   private Vec3 rotation = new Vec3(0.0, 0.0, 0.0);

   @Override
   public Vec3 getRotation() {
      return this.rotation;
   }

   @Override
   public void setRotation(Vec3 rotation) {
      this.rotation = rotation;
   }
}
