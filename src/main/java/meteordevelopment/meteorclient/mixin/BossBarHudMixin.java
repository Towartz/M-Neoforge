package meteordevelopment.meteorclient.mixin;

import java.util.Collection;
import java.util.Iterator;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.RenderBossBarEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BossHealthOverlay.class})
public abstract class BossBarHudMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRender(CallbackInfo info) {
      if (Modules.get().get(NoRender.class).noBossBar()) {
         info.cancel();
      }
   }

   @Redirect(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;"
      )
   )
   public Iterator<LerpingBossEvent> onRender(Collection<LerpingBossEvent> collection) {
      RenderBossBarEvent.BossIterator event = MeteorClient.EVENT_BUS.post(RenderBossBarEvent.BossIterator.get(collection.iterator()));
      return event.iterator;
   }

   @Redirect(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/ClientBossBar;getName()Lnet/minecraft/text/Text;"
      )
   )
   public Component onAsFormattedString(LerpingBossEvent clientBossBar) {
      RenderBossBarEvent.BossText event = MeteorClient.EVENT_BUS.post(RenderBossBarEvent.BossText.get(clientBossBar, clientBossBar.getName()));
      return event.name;
   }

   @ModifyConstant(
      method = {"render"},
      constant = {@Constant(
         intValue = 9,
         ordinal = 1
      )}
   )
   public int modifySpacingConstant(int j) {
      RenderBossBarEvent.BossSpacing event = MeteorClient.EVENT_BUS.post(RenderBossBarEvent.BossSpacing.get(j));
      return event.spacing;
   }
}
