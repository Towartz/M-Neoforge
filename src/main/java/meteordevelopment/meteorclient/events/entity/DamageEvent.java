package meteordevelopment.meteorclient.events.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public class DamageEvent {
   private static final DamageEvent INSTANCE = new DamageEvent();
   public LivingEntity entity;
   public DamageSource source;

   public static DamageEvent get(LivingEntity entity, DamageSource source) {
      INSTANCE.entity = entity;
      INSTANCE.source = source;
      return INSTANCE;
   }
}
