package meteordevelopment.meteorclient.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MeteorToast implements Toast {
   public static final int TITLE_COLOR = Color.fromRGBA(145, 61, 226, 255);
   public static final int TEXT_COLOR = Color.fromRGBA(220, 220, 220, 255);
   private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/gui/sprites/toast/advancement.png");
   private ItemStack icon;
   private Component title;
   private Component text;
   private boolean justUpdated = true;
   private boolean playedSound;
   private long start;
   private long duration;

   public MeteorToast(@Nullable Item item, @NotNull String title, @Nullable String text, long duration) {
      this.icon = item != null ? item.getDefaultInstance() : null;
      this.title = Component.literal(title).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TITLE_COLOR)));
      this.text = text != null ? Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TEXT_COLOR))) : null;
      this.duration = duration;
   }

   public MeteorToast(@Nullable Item item, @NotNull String title, @Nullable String text) {
      this.icon = item != null ? item.getDefaultInstance() : null;
      this.title = Component.literal(title).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TITLE_COLOR)));
      this.text = text != null ? Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TEXT_COLOR))) : null;
      this.duration = 6000L;
   }

   public Visibility render(GuiGraphics context, ToastComponent toastManager, long currentTime) {
      if (this.justUpdated) {
         this.start = currentTime;
         this.justUpdated = false;
      }

      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      context.blit(TEXTURE, 0, 0, 0, 0, this.width(), this.height());
      int x = this.icon != null ? 28 : 12;
      int titleY = 12;
      if (this.text != null) {
         context.drawString(MeteorClient.mc.font, this.title, x, 18, TITLE_COLOR, false);
         titleY = 7;
      }

      context.drawString(MeteorClient.mc.font, this.title, x, titleY, TITLE_COLOR, false);
      if (this.icon != null) {
         context.renderItem(this.icon, 8, 8);
      }

      if (!this.playedSound) {
         MeteorClient.mc.getSoundManager().play(this.getSound());
         this.playedSound = true;
      }

      return currentTime - this.start >= this.duration ? Visibility.HIDE : Visibility.SHOW;
   }

   public void setIcon(@Nullable Item item) {
      this.icon = item != null ? item.getDefaultInstance() : null;
      this.justUpdated = true;
   }

   public void setTitle(@NotNull String title) {
      this.title = Component.literal(title).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TITLE_COLOR)));
      this.justUpdated = true;
   }

   public void setText(@Nullable String text) {
      this.text = text != null ? Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TEXT_COLOR))) : null;
      this.justUpdated = true;
   }

   public void setDuration(long duration) {
      this.duration = duration;
      this.justUpdated = true;
   }

   public SoundInstance getSound() {
      return SimpleSoundInstance.forUI((SoundEvent)SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 1.0F);
   }
}
