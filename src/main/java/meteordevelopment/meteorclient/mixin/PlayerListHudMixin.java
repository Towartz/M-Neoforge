package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PlayerTabOverlay.class})
public abstract class PlayerListHudMixin {
   @Shadow
   protected abstract List<PlayerInfo> getPlayerInfos();

   @ModifyConstant(
      constant = {@Constant(
         longValue = 80L
      )},
      method = {"collectPlayerEntries"}
   )
   private long modifyCount(long count) {
      BetterTab module = Modules.get().get(BetterTab.class);
      return module.isActive() ? (long)module.tabSize.get().intValue() : count;
   }

   @Inject(
      method = {"getPlayerName"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void getPlayerName(PlayerInfo playerListEntry, CallbackInfoReturnable<Component> info) {
      BetterTab betterTab = Modules.get().get(BetterTab.class);
      if (betterTab.isActive()) {
         info.setReturnValue(betterTab.getPlayerName(playerListEntry));
      }
   }

   @ModifyArg(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/lang/Math;min(II)I"
      ),
      index = 0
   )
   private int modifyWidth(int width) {
      BetterTab module = Modules.get().get(BetterTab.class);
      return module.isActive() && module.accurateLatency.get() ? width + 30 : width;
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/lang/Math;min(II)I",
         shift = Shift.BEFORE
      )}
   )
   private void modifyHeight(CallbackInfo ci, @Local(ordinal = 5) LocalIntRef o, @Local(ordinal = 6) LocalIntRef p) {
      BetterTab module = Modules.get().get(BetterTab.class);
      if (module.isActive()) {
         int newP = 1;

         int newO;
         for (int totalPlayers = newO = this.getPlayerInfos().size(); newO > module.tabHeight.get(); newO = (totalPlayers + newP - 1) / newP) {
            newP++;
         }

         o.set(newO);
         p.set(newP);
      }
   }

   @Inject(
      method = {"renderLatencyIcon"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderLatencyIcon(GuiGraphics context, int width, int x, int y, PlayerInfo entry, CallbackInfo ci) {
      BetterTab betterTab = Modules.get().get(BetterTab.class);
      if (betterTab.isActive() && betterTab.accurateLatency.get()) {
         Minecraft mc = Minecraft.getInstance();
         Font textRenderer = mc.font;
         int latency = Mth.clamp(entry.getLatency(), 0, 9999);
         int color = latency < 150 ? '\ue970' : (latency < 300 ? 15192096 : 14107192);
         String text = latency + "ms";
         context.drawString(textRenderer, text, x + width - textRenderer.width(text), y, color);
         ci.cancel();
      }
   }
}
