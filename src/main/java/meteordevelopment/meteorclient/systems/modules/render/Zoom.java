package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;

public class Zoom extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> zoom = this.sgGeneral
      .add(new DoubleSetting.Builder().name("zoom").description("How much to zoom.").defaultValue(6.0).min(1.0).build());
   private final Setting<Double> scrollSensitivity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scroll-sensitivity")
            .description("Allows you to change zoom value using scroll wheel. 0 to disable.")
            .defaultValue(1.0)
            .min(0.0)
            .build()
      );
   private final Setting<Boolean> smooth = this.sgGeneral
      .add(new BoolSetting.Builder().name("smooth").description("Smooth transition.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> cinematic = this.sgGeneral
      .add(new BoolSetting.Builder().name("cinematic").description("Enables cinematic camera.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> renderHands = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-hands").description("Whether or not to render your hands.").defaultValue(Boolean.valueOf(false)).build());
   private boolean enabled;
   private boolean preCinematic;
   private double preMouseSensitivity;
   private double value;
   private double lastFov;
   private double time;

   public Zoom() {
      super(Categories.Render, "zoom", "Zooms your view.");
      this.autoSubscribe = false;
   }

   @Override
   public void onActivate() {
      if (!this.enabled) {
         this.preCinematic = this.mc.options.smoothCamera;
         this.preMouseSensitivity = (Double)this.mc.options.sensitivity().get();
         this.value = this.zoom.get();
         this.lastFov = (double)((Integer)this.mc.options.fov().get()).intValue();
         this.time = 0.001;
         MeteorClient.EVENT_BUS.subscribe(this);
         this.enabled = true;
      }
   }

   public void onStop() {
      this.mc.options.smoothCamera = this.preCinematic;
      this.mc.options.sensitivity().set(this.preMouseSensitivity);
      this.mc.levelRenderer.needsUpdate();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      this.mc.options.smoothCamera = this.cinematic.get();
      if (!this.cinematic.get()) {
         this.mc.options.sensitivity().set(this.preMouseSensitivity / Math.max(this.getScaling() * 0.5, 1.0));
      }

      if (this.time == 0.0) {
         MeteorClient.EVENT_BUS.unsubscribe(this);
         this.enabled = false;
         this.onStop();
      }
   }

   @EventHandler
   private void onMouseScroll(MouseScrollEvent event) {
      if (this.scrollSensitivity.get() > 0.0 && this.isActive()) {
         this.value = this.value + event.value * 0.25 * this.scrollSensitivity.get() * this.value;
         if (this.value < 1.0) {
            this.value = 1.0;
         }

         event.cancel();
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.smooth.get()) {
         this.time = this.isActive() ? 1.0 : 0.0;
      } else {
         if (this.isActive()) {
            this.time = this.time + event.frameTime * 5.0;
         } else {
            this.time = this.time - event.frameTime * 5.0;
         }

         this.time = Mth.clamp(this.time, 0.0, 1.0);
      }
   }

   @EventHandler
   private void onGetFov(GetFovEvent event) {
      event.fov = event.fov / this.getScaling();
      if (this.lastFov != event.fov) {
         this.mc.levelRenderer.needsUpdate();
      }

      this.lastFov = event.fov;
   }

   public double getScaling() {
      double v = -2.0 * this.time + 2.0;
      double delta = this.time < 0.5 ? 4.0 * this.time * this.time * this.time : 1.0 - (v * v * v) / 2.0;
      return Mth.lerp(delta, 1.0, this.value);
   }

   public boolean renderHands() {
      return !this.isActive() || this.renderHands.get();
   }
}
