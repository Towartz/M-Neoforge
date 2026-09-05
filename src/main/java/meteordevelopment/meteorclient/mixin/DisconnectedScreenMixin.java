package meteordevelopment.meteorclient.mixin;

import it.unimi.dsi.fastutil.Pair;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.Builder;
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
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin({DisconnectedScreen.class})
public abstract class DisconnectedScreenMixin extends Screen {
   @Shadow
   @Final
   private LinearLayout layout;
   @Unique
   private Button reconnectBtn;
   @Unique
   private double time = Modules.get().get(AutoReconnect.class).time.get() * 20.0;

   protected DisconnectedScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/widget/DirectionalLayoutWidget;refreshPositions()V",
         shift = Shift.BEFORE
      )},
      locals = LocalCapture.CAPTURE_FAILSOFT,
      require = 0
   )
   private void addButtons(CallbackInfo ci, Button buttonWidget) {
      AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
      if (autoReconnect.lastServerConnection != null) {
         this.reconnectBtn = new Builder(Component.literal(this.getText()), button -> this.tryConnecting()).build();
         this.layout.addChild(this.reconnectBtn);
         this.layout.addChild(new Builder(Component.literal("Toggle Auto Reconnect"), button -> {
            autoReconnect.toggle();
            this.reconnectBtn.setMessage(Component.literal(this.getText()));
            this.time = autoReconnect.time.get() * 20.0;
         }).build());
      }
   }

   public void tick() {
      AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
      if (autoReconnect.isActive() && autoReconnect.lastServerConnection != null) {
         if (this.time <= 0.0) {
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
      if (Modules.get().isActive(AutoReconnect.class)) {
         reconnectText = reconnectText + " " + String.format("(%.1f)", this.time / 20.0);
      }

      return reconnectText;
   }

   @Unique
   private void tryConnecting() {
      Pair<ServerAddress, ServerData> lastServer = Modules.get().get(AutoReconnect.class).lastServerConnection;
      ConnectScreen.startConnecting(new TitleScreen(), MeteorClient.mc, (ServerAddress)lastServer.left(), (ServerData)lastServer.right(), false, null);
   }
}
