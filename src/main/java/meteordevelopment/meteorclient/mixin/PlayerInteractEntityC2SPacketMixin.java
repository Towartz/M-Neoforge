package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.meteorclient.systems.modules.movement.Sneak;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.Action;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({ServerboundInteractPacket.class})
public abstract class PlayerInteractEntityC2SPacketMixin implements IPlayerInteractEntityC2SPacket {
   @Shadow
   @Final
   private Action action;
   @Shadow
   @Final
   private int entityId;

   @Override
   public ActionType getType() {
      return this.action.getType();
   }

   @Override
   public Entity getEntity() {
      return MeteorClient.mc.level.getEntity(this.entityId);
   }

   @ModifyVariable(
      method = {"<init>(IZLnet/minecraft/network/protocol/game/ServerboundInteractPacket$Action;)V", "<init>"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private static boolean setSneaking(boolean sneaking) {
      return Modules.get().get(Sneak.class).doPacket() || Modules.get().get(NoSlow.class).airStrict() || sneaking;
   }
}
