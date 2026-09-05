package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IExplosion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({Explosion.class})
public abstract class ExplosionMixin implements IExplosion {
   @Shadow
   @Final
   @Mutable
   private Level level;
   @Shadow
   @Final
   @Mutable
   @Nullable
   private Entity source;
   @Shadow
   @Final
   @Mutable
   private double x;
   @Shadow
   @Final
   @Mutable
   private double y;
   @Shadow
   @Final
   @Mutable
   private double z;
   @Shadow
   @Final
   @Mutable
   private float radius;
   @Shadow
   @Final
   @Mutable
   private boolean fire;
   @Shadow
   @Final
   @Mutable
   private BlockInteraction blockInteraction;

   @Override
   public void set(Vec3 pos, float power, boolean createFire) {
      this.level = MeteorClient.mc.level;
      this.source = null;
      this.x = pos.x;
      this.y = pos.y;
      this.z = pos.z;
      this.radius = power;
      this.fire = createFire;
      this.blockInteraction = BlockInteraction.DESTROY;
   }
}
