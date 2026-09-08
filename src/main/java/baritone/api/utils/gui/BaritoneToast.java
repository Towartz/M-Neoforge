package baritone.api.utils.gui;

import baritone.api.BaritoneAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class BaritoneToast implements Toast {
   private String title;
   private String subtitle;
   private long firstDrawTime;
   private boolean newDisplay;
   private long totalShowTime;

   public BaritoneToast(Component titleComponent, Component subtitleComponent, long totalShowTime) {
      this.title = titleComponent.getString();
      this.subtitle = subtitleComponent == null ? null : subtitleComponent.getString();
      this.totalShowTime = totalShowTime;
   }

   public Visibility render(GuiGraphics gui, ToastComponent toastGui, long delta) {
      if (this.newDisplay) {
         this.firstDrawTime = delta;
         this.newDisplay = false;
      }

      gui.blit(ResourceLocation.parse("textures/gui/toasts.png"), 0, 0, 0, 32, 160, 32);
      if (this.subtitle == null) {
         gui.drawString(toastGui.getMinecraft().font, this.title, 18, 12, -11534256);
      } else {
         gui.drawString(toastGui.getMinecraft().font, this.title, 18, 7, -11534256);
         gui.drawString(toastGui.getMinecraft().font, this.subtitle, 18, 18, -16777216);
      }

      return delta - this.firstDrawTime < this.totalShowTime ? Visibility.SHOW : Visibility.HIDE;
   }

   public void setDisplayedText(Component titleComponent, Component subtitleComponent) {
      this.title = titleComponent.getString();
      this.subtitle = subtitleComponent == null ? null : subtitleComponent.getString();
      this.newDisplay = true;
   }

   public static void addOrUpdate(ToastComponent toast, Component title, Component subtitle, long totalShowTime) {
      BaritoneToast baritonetoast = (BaritoneToast)toast.getToast(BaritoneToast.class, new Object());
      if (baritonetoast == null) {
         toast.addToast(new BaritoneToast(title, subtitle, totalShowTime));
      } else {
         baritonetoast.setDisplayedText(title, subtitle);
      }
   }

   public static void addOrUpdate(Component title, Component subtitle) {
      addOrUpdate(Minecraft.getInstance().getToasts(), title, subtitle, BaritoneAPI.getSettings().toastTimer.value);
   }
}
