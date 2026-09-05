package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.IExplosionS2CPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({ClientboundExplodePacket.class})
public abstract class ExplosionS2CPacketMixin implements IExplosionS2CPacket {
   @Shadow
   @Final
   @Mutable
   private float knockbackX;
   @Shadow
   @Final
   @Mutable
   private float knockbackY;
   @Shadow
   @Final
   @Mutable
   private float knockbackZ;

   @Override
   public void setVelocityX(float velocity) {
      this.knockbackX = velocity;
   }

   @Override
   public void setVelocityY(float velocity) {
      this.knockbackY = velocity;
   }

   @Override
   public void setVelocityZ(float velocity) {
      this.knockbackZ = velocity;
   }
}
