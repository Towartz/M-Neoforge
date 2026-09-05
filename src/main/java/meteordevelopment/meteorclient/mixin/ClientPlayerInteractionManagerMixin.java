package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.DropItemsEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.entity.player.BreakBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import meteordevelopment.meteorclient.systems.modules.player.BreakDelay;
import meteordevelopment.meteorclient.systems.modules.player.SpeedMine;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MultiPlayerGameMode.class})
public abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {
   @Shadow
   private int destroyDelay;
   @Shadow
   @Final
   private ClientPacketListener connection;

   @Shadow
   protected abstract void ensureHasSentCarriedItem();

   @Shadow
   public abstract void handleInventoryMouseClick(int var1, int var2, int var3, ClickType var4, Player var5);

   @Shadow
   public abstract boolean destroyBlock(BlockPos var1);

   @Inject(
      method = {"handleInventoryMouseClick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onClickSlot(int syncId, int slotId, int button, ClickType actionType, Player player, CallbackInfo info) {
      if (actionType == ClickType.THROW && slotId >= 0 && slotId < player.containerMenu.slots.size()) {
         if (MeteorClient.EVENT_BUS.post(DropItemsEvent.get(((Slot)player.containerMenu.slots.get(slotId)).getItem())).isCancelled()) {
            info.cancel();
         }
      } else if (slotId == -999 && MeteorClient.EVENT_BUS.post(DropItemsEvent.get(player.containerMenu.getCarried())).isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"handleInventoryMouseClick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void onClickArmorSlot(int syncId, int slotId, int button, ClickType actionType, Player player, CallbackInfo ci) {
      if (Modules.get().get(InventoryTweaks.class).armorStorage()) {
         AbstractContainerMenu screenHandler = player.containerMenu;
         if (screenHandler instanceof InventoryMenu && slotId >= 5 && slotId <= 8) {
            int armorSlot = 8 - slotId + 36;
            if (actionType == ClickType.PICKUP && !screenHandler.getCarried().isEmpty()) {
               this.handleInventoryMouseClick(syncId, 17, armorSlot, ClickType.SWAP, player);
               this.handleInventoryMouseClick(syncId, 17, button, ClickType.PICKUP, player);
               this.handleInventoryMouseClick(syncId, 17, armorSlot, ClickType.SWAP, player);
               ci.cancel();
            } else if (actionType == ClickType.SWAP) {
               if (button >= 10) {
                  this.handleInventoryMouseClick(syncId, 45, armorSlot, ClickType.SWAP, player);
                  ci.cancel();
               } else {
                  this.handleInventoryMouseClick(syncId, 36 + button, armorSlot, ClickType.SWAP, player);
                  ci.cancel();
               }
            }
         }
      }
   }

   @Inject(
      method = {"startDestroyBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttackBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> info) {
      if (MeteorClient.EVENT_BUS.post(StartBreakingBlockEvent.get(blockPos, direction)).isCancelled()) {
         info.cancel();
      } else {
         SpeedMine sm = Modules.get().get(SpeedMine.class);
         BlockState state = MeteorClient.mc.level.getBlockState(blockPos);
         if (!sm.instamine() || !sm.filter(state.getBlock())) {
            return;
         }

         if (state.getDestroyProgress(MeteorClient.mc.player, MeteorClient.mc.level, blockPos) > 0.5F) {
            this.destroyBlock(blockPos);
            this.connection.send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, blockPos, direction));
            this.connection.send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, direction));
            info.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"useItemOn"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void interactBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
      if (MeteorClient.EVENT_BUS.post(InteractBlockEvent.get(player.getMainHandItem().isEmpty() ? InteractionHand.OFF_HAND : hand, hitResult)).isCancelled()) {
         cir.setReturnValue(InteractionResult.FAIL);
      }
   }

   @Inject(
      method = {"attack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttackEntity(Player player, Entity target, CallbackInfo info) {
      if (MeteorClient.EVENT_BUS.post(AttackEntityEvent.get(target)).isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"interact"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInteractEntity(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> info) {
      if (MeteorClient.EVENT_BUS.post(InteractEntityEvent.get(entity, hand)).isCancelled()) {
         info.setReturnValue(InteractionResult.FAIL);
      }
   }

   @Inject(
      method = {"handleCreativeModeItemDrop"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDropCreativeStack(ItemStack stack, CallbackInfo info) {
      if (MeteorClient.EVENT_BUS.post(DropItemsEvent.get(stack)).isCancelled()) {
         info.cancel();
      }
   }

   @Redirect(
      method = {"continueDestroyBlock"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I",
         opcode = 181,
         ordinal = 1
      )
   )
   private void creativeBreakDelayChange(MultiPlayerGameMode interactionManager, int value) {
      BlockBreakingCooldownEvent event = MeteorClient.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.destroyDelay = event.cooldown;
   }

   @Redirect(
      method = {"continueDestroyBlock"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I",
         opcode = 181,
         ordinal = 2
      )
   )
   private void survivalBreakDelayChange(MultiPlayerGameMode interactionManager, int value) {
      BlockBreakingCooldownEvent event = MeteorClient.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.destroyDelay = event.cooldown;
   }

   @Redirect(
      method = {"startDestroyBlock"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I",
         opcode = 181
      )
   )
   private void creativeBreakDelayChange2(MultiPlayerGameMode interactionManager, int value) {
      BlockBreakingCooldownEvent event = MeteorClient.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.destroyDelay = event.cooldown;
   }

   @Inject(
      method = {"destroyBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onBreakBlock(BlockPos blockPos, CallbackInfoReturnable<Boolean> info) {
      if (MeteorClient.EVENT_BUS.post(BreakBlockEvent.get(blockPos)).isCancelled()) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"useItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInteractItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> info) {
      InteractItemEvent event = MeteorClient.EVENT_BUS.post(InteractItemEvent.get(hand));
      if (event.toReturn != null) {
         info.setReturnValue(event.toReturn);
      }
   }

   @Inject(
      method = {"stopDestroyBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCancelBlockBreaking(CallbackInfo info) {
      if (BlockUtils.breaking) {
         info.cancel();
      }
   }

   @Override
   public void meteor$syncSelected() {
      this.ensureHasSentCarriedItem();
   }
}
