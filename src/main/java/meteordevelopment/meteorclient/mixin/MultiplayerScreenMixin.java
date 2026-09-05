package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import meteordevelopment.meteorclient.systems.proxies.Proxies;
import meteordevelopment.meteorclient.systems.proxies.Proxy;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({JoinMultiplayerScreen.class})
public abstract class MultiplayerScreenMixin extends Screen {
   @Unique
   private int textColor1;
   @Unique
   private int textColor2;
   @Unique
   private String loggedInAs;
   @Unique
   private int loggedInAsLength;

   public MultiplayerScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      this.textColor1 = Color.fromRGBA(255, 255, 255, 255);
      this.textColor2 = Color.fromRGBA(175, 175, 175, 255);
      this.loggedInAs = "Logged in as ";
      this.loggedInAsLength = this.font.width(this.loggedInAs);
      this.addRenderableWidget(
         new Builder(Component.literal("Accounts"), button -> this.minecraft.setScreen(GuiThemes.get().accountsScreen()))
            .pos(this.width - 75 - 3, 3)
            .size(75, 20)
            .build()
      );
      this.addRenderableWidget(
         new Builder(Component.literal("Proxies"), button -> this.minecraft.setScreen(GuiThemes.get().proxiesScreen()))
            .pos(this.width - 75 - 3 - 75 - 2, 3)
            .size(75, 20)
            .build()
      );
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      int x = 3;
      int y = 3;
      context.drawString(MeteorClient.mc.font, this.loggedInAs, x, y, this.textColor1);
      context.drawString(
         MeteorClient.mc.font, Modules.get().get(NameProtect.class).getName(this.minecraft.getUser().getName()), x + this.loggedInAsLength, y, this.textColor2
      );
      y += 9 + 2;
      Proxy proxy = Proxies.get().getEnabled();
      String left = proxy != null ? "Using proxy " : "Not using a proxy";
      String right = proxy != null
         ? (proxy.name.get() != null && !proxy.name.get().isEmpty() ? "(" + proxy.name.get() + ") " : "") + proxy.address.get() + ":" + proxy.port.get()
         : null;
      context.drawString(MeteorClient.mc.font, left, x, y, this.textColor1);
      if (right != null) {
         context.drawString(MeteorClient.mc.font, right, x + this.font.width(left), y, this.textColor2);
      }
   }
}
