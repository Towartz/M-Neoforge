package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.ints.IntDoubleImmutablePair;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResolutionChangedEvent;
import meteordevelopment.meteorclient.events.render.RenderAfterWorldEvent;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.renderer.Framebuffer;
import meteordevelopment.meteorclient.renderer.GL;
import meteordevelopment.meteorclient.renderer.PostProcessRenderer;
import meteordevelopment.meteorclient.renderer.Shader;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.listeners.ConsumerListener;
import meteordevelopment.orbit.listeners.IListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class Blur extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgScreens = this.settings.createGroup("Screens");
   private final IntDoubleImmutablePair[] strengths = new IntDoubleImmutablePair[]{
      IntDoubleImmutablePair.of(1, 1.25),
      IntDoubleImmutablePair.of(1, 2.25),
      IntDoubleImmutablePair.of(2, 2.0),
      IntDoubleImmutablePair.of(2, 3.0),
      IntDoubleImmutablePair.of(2, 4.25),
      IntDoubleImmutablePair.of(3, 2.5),
      IntDoubleImmutablePair.of(3, 3.25),
      IntDoubleImmutablePair.of(3, 4.25),
      IntDoubleImmutablePair.of(3, 5.5),
      IntDoubleImmutablePair.of(4, 3.25),
      IntDoubleImmutablePair.of(4, 4.0),
      IntDoubleImmutablePair.of(4, 5.0),
      IntDoubleImmutablePair.of(4, 6.0),
      IntDoubleImmutablePair.of(4, 7.25),
      IntDoubleImmutablePair.of(4, 8.25),
      IntDoubleImmutablePair.of(5, 4.5),
      IntDoubleImmutablePair.of(5, 5.25),
      IntDoubleImmutablePair.of(5, 6.25),
      IntDoubleImmutablePair.of(5, 7.25),
      IntDoubleImmutablePair.of(5, 8.5)
   };
   private final Setting<Integer> strength = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("strength")
            .description("How strong the blur should be.")
            .defaultValue(Integer.valueOf(5))
            .min(1)
            .max(20)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Integer> fadeTime = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("fade-time")
            .description("How long the fade will last in milliseconds.")
            .defaultValue(Integer.valueOf(100))
            .min(0)
            .sliderMax(500)
            .build()
      );
   private final Setting<Boolean> meteor = this.sgScreens
      .add(new BoolSetting.Builder().name("meteor").description("Applies blur to Meteor screens.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> inventories = this.sgScreens
      .add(new BoolSetting.Builder().name("inventories").description("Applies blur to inventory screens.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> chat = this.sgScreens
      .add(new BoolSetting.Builder().name("chat").description("Applies blur when in chat.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> other = this.sgScreens
      .add(new BoolSetting.Builder().name("other").description("Applies blur to all other screen types.").defaultValue(Boolean.valueOf(true)).build());
   private Shader shaderDown;
   private Shader shaderUp;
   private Shader shaderPassthrough;
   private final Framebuffer[] fbos = new Framebuffer[6];
   private boolean enabled;
   private long fadeEndAt;

   public Blur() {
      super(Categories.Render, "blur", "Blurs background when in GUI screens.");
      MeteorClient.EVENT_BUS.subscribe((IListener)(new ConsumerListener<>(ResolutionChangedEvent.class, event -> {
         for (int i = 0; i < this.fbos.length; i++) {
            if (this.fbos[i] != null) {
               this.fbos[i].resize();
            } else {
               this.fbos[i] = new Framebuffer(1.0 / (1 << i));
            }
         }
      })));
      MeteorClient.EVENT_BUS.subscribe((IListener)(new ConsumerListener<>(RenderAfterWorldEvent.class, event -> this.onRenderAfterWorld())));
   }

   private void onRenderAfterWorld() {
      boolean shouldRender = this.shouldRender();
      long time = System.currentTimeMillis();
      if (this.enabled) {
         if (!shouldRender) {
            if (this.fadeEndAt == -1L) {
               this.fadeEndAt = System.currentTimeMillis() + (long)this.fadeTime.get().intValue();
            }

            if (time >= this.fadeEndAt) {
               this.enabled = false;
               this.fadeEndAt = -1L;
            }
         }
      } else if (shouldRender) {
         this.enabled = true;
         this.fadeEndAt = System.currentTimeMillis() + (long)this.fadeTime.get().intValue();
      }

      if (this.enabled) {
         if (this.shaderDown == null) {
            this.shaderDown = new Shader("blur.vert", "blur_down.frag");
            this.shaderUp = new Shader("blur.vert", "blur_up.frag");
            this.shaderPassthrough = new Shader("passthrough.vert", "passthrough.frag");

            for (int i = 0; i < this.fbos.length; i++) {
               if (this.fbos[i] == null) {
                  this.fbos[i] = new Framebuffer(1.0 / (1 << i));
               }
            }
         }

         double progress = 1.0;
         if (time < this.fadeEndAt) {
            if (shouldRender) {
               progress = 1.0 - (double)(this.fadeEndAt - time) / this.fadeTime.get().doubleValue();
            } else {
               progress = (double)(this.fadeEndAt - time) / this.fadeTime.get().doubleValue();
            }
         } else {
            this.fadeEndAt = -1L;
         }

         IntDoubleImmutablePair strength = this.strengths[(int)((double)(this.strength.get() - 1) * progress)];
         int iterations = strength.leftInt();
         double offset = strength.rightDouble();
         PostProcessRenderer.beginRender();
         this.renderToFbo(this.fbos[0], Minecraft.getInstance().getMainRenderTarget().getColorTextureId(), this.shaderDown, offset);

         for (int ix = 0; ix < iterations; ix++) {
            this.renderToFbo(this.fbos[ix + 1], this.fbos[ix].texture, this.shaderDown, offset);
         }

         for (int ix = iterations; ix >= 1; ix--) {
            this.renderToFbo(this.fbos[ix - 1], this.fbos[ix].texture, this.shaderUp, offset);
         }

         Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
         this.shaderPassthrough.bind();
         GL.bindTexture(this.fbos[0].texture);
         this.shaderPassthrough.set("uTexture", 0);
         PostProcessRenderer.render();
         PostProcessRenderer.endRender();
      }
   }

   private void renderToFbo(Framebuffer targetFbo, int sourceText, Shader shader, double offset) {
      targetFbo.bind();
      targetFbo.setViewport();
      shader.bind();
      GL.bindTexture(sourceText);
      shader.set("uTexture", 0);
      shader.set("uHalfTexelSize", 0.5 / (double)targetFbo.width, 0.5 / (double)targetFbo.height);
      shader.set("uOffset", offset);
      PostProcessRenderer.render();
   }

   private boolean shouldRender() {
      if (!this.isActive()) {
         return false;
      } else {
         Screen screen = this.mc.screen;
         if (screen instanceof WidgetScreen) {
            return this.meteor.get();
         } else if (screen instanceof AbstractContainerScreen) {
            return this.inventories.get();
         } else if (screen instanceof ChatScreen) {
            return this.chat.get();
         } else {
            return screen != null ? this.other.get() : false;
         }
      }
   }
}
