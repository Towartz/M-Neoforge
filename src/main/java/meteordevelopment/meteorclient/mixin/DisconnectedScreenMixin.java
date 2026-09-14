package meteordevelopment.meteorclient.mixin;

import it.unimi.dsi.fastutil.Pair;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({DisconnectedScreen.class})
public abstract class DisconnectedScreenMixin extends Screen {
   @Shadow
   @Final
   private LinearLayout layout;
   @Unique
   private Button reconnectBtn;
   @Unique
   private double time = Modules.get() != null && Modules.get().get(AutoReconnect.class) != null
      ? Modules.get().get(AutoReconnect.class).time.get() * 20.0
      : 70.0;

   protected DisconnectedScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void addButtons(CallbackInfo ci) {
      AutoReconnect autoReconnect = Modules.get() != null ? Modules.get().get(AutoReconnect.class) : null;
      if (autoReconnect != null && autoReconnect.lastServerConnection != null) {
         this.reconnectBtn = new Builder(Component.literal(this.getText()), button -> this.tryConnecting()).build();
         this.layout.addChild(this.reconnectBtn);
         this.layout.addChild(new Builder(Component.literal("Toggle Auto Reconnect"), button -> {
            autoReconnect.toggle();
            if (this.reconnectBtn != null) {
               this.reconnectBtn.setMessage(Component.literal(this.getText()));
            }
            this.time = autoReconnect.time.get() * 20.0;
         }).build());
         this.layout.arrangeElements();
         FrameLayout.centerInRectangle(this.layout, this.getRectangle());
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void onTick(CallbackInfo ci) {
      AutoReconnect autoReconnect = Modules.get() != null ? Modules.get().get(AutoReconnect.class) : null;
      if (autoReconnect != null && autoReconnect.isActive() && autoReconnect.lastServerConnection != null) {
         if (this.time <= 0.0) {
            this.time = autoReconnect.time.get() * 20.0;
            this.tryConnecting();
         } else {
            this.time--;
            if (this.reconnectBtn != null) {
               this.reconnectBtn.setMessage(Component.literal(this.getText()));
            }
         }
      }
   }

   @Unique
   private String getText() {
      String reconnectText = "Reconnect";
      if (Modules.get() != null && Modules.get().isActive(AutoReconnect.class)) {
         reconnectText = reconnectText + " " + String.format("(%.1f)", this.time / 20.0);
      }

      return reconnectText;
   }

   @Unique
   private void tryConnecting() {
      AutoReconnect autoReconnect = Modules.get() != null ? Modules.get().get(AutoReconnect.class) : null;
      if (autoReconnect != null) {
         this.time = autoReconnect.time.get() * 20.0;
      }
      if (autoReconnect != null && autoReconnect.lastServerConnection != null) {
         Pair<ServerAddress, ServerData> lastServer = autoReconnect.lastServerConnection;
         ConnectScreen.startConnecting(new TitleScreen(), MeteorClient.mc, (ServerAddress)lastServer.left(), (ServerData)lastServer.right(), false, null);
      }
   }
}
