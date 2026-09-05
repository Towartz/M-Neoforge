package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AntiPacketKick;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({FriendlyByteBuf.class})
public abstract class PacketByteBufMixin {
   @ModifyArg(
      method = {"readNbt(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/nbt/NbtCompound;"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/PacketByteBuf;readNbt(Lio/netty/buffer/ByteBuf;Lnet/minecraft/nbt/NbtSizeTracker;)Lnet/minecraft/nbt/NbtElement;"
      )
   )
   private static NbtAccounter xlPackets(NbtAccounter sizeTracker) {
      return Modules.get().isActive(AntiPacketKick.class) ? NbtAccounter.unlimitedHeap() : sizeTracker;
   }
}
