package meteordevelopment.meteorclient.events.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class LivingEntityMoveEvent {
   private static final LivingEntityMoveEvent INSTANCE = new LivingEntityMoveEvent();
   public LivingEntity entity;
   public Vec3 movement;

   public static LivingEntityMoveEvent get(LivingEntity entity, Vec3 movement) {
      INSTANCE.entity = entity;
      INSTANCE.movement = movement;
      return INSTANCE;
   }
}
