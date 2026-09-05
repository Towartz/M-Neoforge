package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

public class AutoFish extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSplashRangeDetection = this.settings.createGroup("Splash Detection");
   private final Setting<Boolean> autoCast = this.sgGeneral
      .add(new BoolSetting.Builder().name("auto-cast").description("Automatically casts when not fishing.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Integer> ticksAutoCast = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("ticks-auto-cast")
            .description("The amount of ticks to wait before recasting automatically.")
            .defaultValue(Integer.valueOf(10))
            .min(0)
            .sliderMax(60)
            .build()
      );
   private final Setting<Integer> ticksCatch = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("catch-delay")
            .description("The amount of ticks to wait before catching the fish.")
            .defaultValue(Integer.valueOf(6))
            .min(0)
            .sliderMax(60)
            .build()
      );
   private final Setting<Integer> ticksThrow = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("throw-delay")
            .description("The amount of ticks to wait before throwing the bobber.")
            .defaultValue(Integer.valueOf(14))
            .min(0)
            .sliderMax(60)
            .build()
      );
   private final Setting<Boolean> antiBreak = this.sgGeneral
      .add(new BoolSetting.Builder().name("anti-break").description("Prevents fishing rod from being broken.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> splashDetectionRangeEnabled = this.sgSplashRangeDetection
      .add(
         new BoolSetting.Builder()
            .name("splash-detection-range-enabled")
            .description("Allows you to use multiple accounts next to each other.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Double> splashDetectionRange = this.sgSplashRangeDetection
      .add(
         new DoubleSetting.Builder()
            .name("splash-detection-range")
            .description("The detection range of a splash. Lower values will not work when the TPS is low.")
            .defaultValue(10.0)
            .min(0.0)
            .build()
      );
   private boolean ticksEnabled;
   private int ticksToRightClick;
   private int ticksData;
   private int autoCastTimer;
   private boolean autoCastEnabled;
   private int autoCastCheckTimer;

   public AutoFish() {
      super(Categories.Player, "auto-fish", "Automatically fishes for you.");
   }

   @Override
   public void onActivate() {
      this.ticksEnabled = false;
      this.autoCastEnabled = false;
      this.autoCastCheckTimer = 0;
   }

   @EventHandler
   private void onPlaySound(PlaySoundEvent event) {
      SoundInstance p = event.sound;
      FishingHook b = this.mc.player.fishing;
      if (p.getLocation().getPath().equals("entity.fishing_bobber.splash")
         && (
            !this.splashDetectionRangeEnabled.get()
               || Utils.distance(b.getX(), b.getY(), b.getZ(), p.getX(), p.getY(), p.getZ()) <= this.splashDetectionRange.get()
         )) {
         this.ticksEnabled = true;
         this.ticksToRightClick = this.ticksCatch.get();
         this.ticksData = 0;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.autoCastCheckTimer <= 0) {
         this.autoCastCheckTimer = 30;
         if (this.autoCast.get() && !this.ticksEnabled && !this.autoCastEnabled && this.mc.player.fishing == null && this.hasFishingRod()) {
            this.autoCastTimer = 0;
            this.autoCastEnabled = true;
         }
      } else {
         this.autoCastCheckTimer--;
      }

      if (this.autoCastEnabled) {
         this.autoCastTimer++;
         if (this.autoCastTimer > this.ticksAutoCast.get()) {
            this.autoCastEnabled = false;
            Utils.rightClick();
         }
      }

      if (this.ticksEnabled && this.ticksToRightClick <= 0) {
         if (this.ticksData == 0) {
            Utils.rightClick();
            this.ticksToRightClick = this.ticksThrow.get();
            this.ticksData = 1;
         } else if (this.ticksData == 1) {
            Utils.rightClick();
            this.ticksEnabled = false;
         }
      }

      this.ticksToRightClick--;
   }

   @EventHandler
   private void onKey(KeyEvent event) {
      if (this.mc.options.keyUse.isDown()) {
         this.ticksEnabled = false;
      }
   }

   private boolean hasFishingRod() {
      return InvUtils.swap(
         InvUtils.findInHotbar(
               itemStack -> itemStack.getItem() == Items.FISHING_ROD && (!this.antiBreak.get() || itemStack.getDamageValue() < itemStack.getMaxDamage() - 1)
            )
            .slot(),
         false
      );
   }
}
