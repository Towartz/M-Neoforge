package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.FinishUsingItemEvent;
import meteordevelopment.meteorclient.events.entity.player.StoppedUsingItemEvent;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ItemStack.class})
public abstract class ItemStackMixin {
   @ModifyReturnValue(
      method = {"getTooltip"},
      at = {@At("RETURN")}
   )
   private List<Component> onGetTooltip(List<Component> original) {
      if (Utils.canUpdate()) {
         ItemStackTooltipEvent event = MeteorClient.EVENT_BUS.post(new ItemStackTooltipEvent((ItemStack)(Object)this, original));
         return event.list();
      } else {
         return original;
      }
   }

   @ModifyExpressionValue(
      method = {"getTooltip"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/item/BlockPredicatesChecker;showInTooltip()Z",
         ordinal = 0
      )}
   )
   private boolean modifyCanBreakText(boolean original) {
      BetterTooltips bt = Modules.get().get(BetterTooltips.class);
      return bt.isActive() && bt.canDestroy.get() || original;
   }

   @ModifyExpressionValue(
      method = {"getTooltip"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/item/BlockPredicatesChecker;showInTooltip()Z",
         ordinal = 1
      )}
   )
   private boolean modifyCanPlaceText(boolean original) {
      BetterTooltips bt = Modules.get().get(BetterTooltips.class);
      return bt.isActive() && bt.canPlaceOn.get() || original;
   }

   @ModifyExpressionValue(
      method = {"getTooltip"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/item/ItemStack;contains(Lnet/minecraft/component/ComponentType;)Z",
         ordinal = 0
      )}
   )
   private boolean modifyContainsTooltip(boolean original) {
      BetterTooltips bt = Modules.get().get(BetterTooltips.class);
      return (!bt.isActive() || !bt.tooltip.get()) && original;
   }

   @ModifyExpressionValue(
      method = {"getTooltip"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/item/ItemStack;contains(Lnet/minecraft/component/ComponentType;)Z",
         ordinal = 3
      )}
   )
   private boolean modifyContainsAdditional(boolean original) {
      BetterTooltips bt = Modules.get().get(BetterTooltips.class);
      return (!bt.isActive() || !bt.additional.get()) && original;
   }

   @Inject(
      method = {"finishUsing"},
      at = {@At("HEAD")}
   )
   private void onFinishUsing(Level world, LivingEntity user, CallbackInfoReturnable<ItemStack> info) {
      if (user == MeteorClient.mc.player) {
         MeteorClient.EVENT_BUS.post(FinishUsingItemEvent.get((ItemStack)(Object)this));
      }
   }

   @Inject(
      method = {"onStoppedUsing"},
      at = {@At("HEAD")}
   )
   private void onStoppedUsing(Level world, LivingEntity user, int remainingUseTicks, CallbackInfo info) {
      if (user == MeteorClient.mc.player) {
         MeteorClient.EVENT_BUS.post(StoppedUsingItemEvent.get((ItemStack)(Object)this));
      }
   }

   @ModifyExpressionValue(
      method = {"appendAttributeModifiersTooltip"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/component/type/AttributeModifiersComponent;showInTooltip()Z"
      )}
   )
   private boolean modifyShowInTooltip(boolean original) {
      BetterTooltips bt = Modules.get().get(BetterTooltips.class);
      return bt.isActive() && bt.modifiers.get() || original;
   }
}
