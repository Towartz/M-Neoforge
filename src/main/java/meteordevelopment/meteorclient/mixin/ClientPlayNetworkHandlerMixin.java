package meteordevelopment.meteorclient.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.entity.EntityDestroyEvent;
import meteordevelopment.meteorclient.events.entity.player.PickItemsEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.packets.ContainerSlotUpdateEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.packets.PlaySoundPacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.mixininterface.IExplosionS2CPacket;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class ClientPlayNetworkHandlerMixin extends ClientCommonPacketListenerImpl {
   @Shadow
   private ClientLevel level;
   @Unique
   private boolean ignoreChatMessage;
   @Unique
   private boolean worldNotNull;

   @Shadow
   public abstract void sendChat(String var1);

   protected ClientPlayNetworkHandlerMixin(Minecraft client, Connection connection, CommonListenerCookie connectionState) {
      super(client, connection, connectionState);
   }

   @Inject(
      method = {"onEntitySpawn"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onEntitySpawn(ClientboundAddEntityPacket packet, CallbackInfo info) {
      if (packet != null
         && packet.getType() != null
         && Modules.get().get(NoRender.class).noEntity(packet.getType())
         && Modules.get().get(NoRender.class).getDropSpawnPacket()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"onGameJoin"},
      at = {@At("HEAD")}
   )
   private void onGameJoinHead(ClientboundLoginPacket packet, CallbackInfo info) {
      this.worldNotNull = this.level != null;
   }

   @Inject(
      method = {"handleLogin"},
      at = {@At("TAIL")}
   )
   private void onGameJoinTail(ClientboundLoginPacket packet, CallbackInfo info) {
      if (this.worldNotNull) {
         MeteorClient.EVENT_BUS.post(GameLeftEvent.get());
      }

      MeteorClient.EVENT_BUS.post(GameJoinedEvent.get());
   }

   @Inject(
      method = {"handleConfigurationStart"},
      at = {@At("HEAD")}
   )
   private void onEnterReconfiguration(ClientboundStartConfigurationPacket packet, CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(GameLeftEvent.get());
   }

   @Inject(
      method = {"handleSoundEvent"},
      at = {@At("HEAD")}
   )
   private void onPlaySound(ClientboundSoundPacket packet, CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(PlaySoundPacketEvent.get(packet));
   }

   @Inject(
      method = {"handleLevelChunkWithLight"},
      at = {@At("TAIL")}
   )
   private void onChunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo info) {
      LevelChunk chunk = this.minecraft.level.getChunk(packet.getX(), packet.getZ());
      MeteorClient.EVENT_BUS.post(new ChunkDataEvent(chunk));
   }

   @Inject(
      method = {"handleContainerSetSlot"},
      at = {@At("TAIL")}
   )
   private void onContainerSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(ContainerSlotUpdateEvent.get(packet));
   }

   @Inject(
      method = {"handleContainerContent"},
      at = {@At("TAIL")}
   )
   private void onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo info) {
      MeteorClient.EVENT_BUS.post(InventoryEvent.get(packet));
   }

   @Inject(
      method = {"handleRemoveEntities"},
      at = {@At("HEAD")}
   )
   private void onEntitiesDestroy(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
      IntListIterator var3 = packet.getEntityIds().iterator();

      while (var3.hasNext()) {
         int id = (Integer)var3.next();
         MeteorClient.EVENT_BUS.post(EntityDestroyEvent.get(this.minecraft.level.getEntity(id)));
      }
   }

   @Inject(
      method = {"handleExplosion"},
      at = {@At("HEAD")}
   )
   private void onExplosionVelocity(ClientboundExplodePacket packet, CallbackInfo ci) {
      Velocity velocity = Modules.get().get(Velocity.class);
      if (velocity.explosions.get()) {
         ((IExplosionS2CPacket)packet).setVelocityX((float)((double)packet.getKnockbackX() * velocity.getHorizontal(velocity.explosionsHorizontal)));
         ((IExplosionS2CPacket)packet).setVelocityY((float)((double)packet.getKnockbackY() * velocity.getVertical(velocity.explosionsVertical)));
         ((IExplosionS2CPacket)packet).setVelocityZ((float)((double)packet.getKnockbackZ() * velocity.getHorizontal(velocity.explosionsHorizontal)));
      }
   }

   @Inject(
      method = {"handleTakeItemEntity"},
      at = {@At("TAIL")}
   )
   private void onItemPickupAnimation(ClientboundTakeItemEntityPacket packet, CallbackInfo info) {
      Entity itemEntity = this.minecraft.level.getEntity(packet.getItemId());
      Entity entity = this.minecraft.level.getEntity(packet.getPlayerId());
      if (itemEntity instanceof ItemEntity && entity == this.minecraft.player) {
         MeteorClient.EVENT_BUS.post(PickItemsEvent.get(((ItemEntity)itemEntity).getItem(), packet.getAmount()));
      }
   }

   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendChatMessage(String message, CallbackInfo ci) {
      if (!this.ignoreChatMessage) {
         if (message.startsWith(Config.get().prefix.get()) || BaritoneUtils.IS_AVAILABLE && message.startsWith(BaritoneUtils.getPrefix())) {
            if (message.startsWith(Config.get().prefix.get())) {
               try {
                  Commands.dispatch(message.substring(Config.get().prefix.get().length()));
               } catch (CommandSyntaxException var4) {
                  ChatUtils.error(var4.getMessage());
               }

               this.minecraft.gui.getChat().addRecentChat(message);
               ci.cancel();
            }
         } else {
            SendMessageEvent event = MeteorClient.EVENT_BUS.post(SendMessageEvent.get(message));
            if (!event.isCancelled()) {
               this.ignoreChatMessage = true;
               this.sendChat(event.message);
               this.ignoreChatMessage = false;
            }

            ci.cancel();
         }
      }
   }
}
