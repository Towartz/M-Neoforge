package meteordevelopment.meteorclient.systems.modules.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.world.SurfaceEscapeEngine;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;

public class GotoSurface extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSafety = this.settings.createGroup("Safety & Durability");

   public final Setting<Boolean> preferNaturalCaves = this.sgGeneral
      .add(new BoolSetting.Builder().name("prefer-natural-caves").description("Favors open cave tunnels, ravines, and water columns to maximize pickaxe durability.").defaultValue(Boolean.valueOf(true)).build());

   public final Setting<Double> breakPenalty = this.sgGeneral
      .add(new DoubleSetting.Builder().name("break-penalty").description("Path cost penalty for breaking blocks. Higher values force longer cave walking over digging.").defaultValue(60.0).range(5.0, 200.0).sliderRange(10.0, 100.0).visible(this.preferNaturalCaves::get).build());

   public final Setting<Boolean> allowPlace = this.sgGeneral
      .add(new BoolSetting.Builder().name("allow-place").description("Allows placing throwaway blocks (cobble, dirt, deepslate) to bridge, climb, or pillar.").defaultValue(Boolean.valueOf(true)).build());

   public final Setting<Integer> minSurfaceY = this.sgGeneral
      .add(new IntSetting.Builder().name("min-surface-y").description("Minimum Y level to consider as reaching the surface.").defaultValue(Integer.valueOf(62)).min(0).max(320).sliderRange(50, 100).build());

   public final Setting<Boolean> protectDurability = this.sgSafety
      .add(new BoolSetting.Builder().name("protect-low-durability").description("Warns if your held pickaxe has <= 15 durability remaining.").defaultValue(Boolean.valueOf(true)).build());

   public final Setting<Boolean> chatFeedback = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat-feedback").description("Shows escape progress and durability reports in chat.").defaultValue(Boolean.valueOf(true)).build());

   private int idleTicks = 0;
   private boolean escalatedToExcavation = false;

   public GotoSurface() {
      super(Categories.World, "goto-surface", "Intelligently navigates through natural caves, ravines, and openings to escape to the surface while preserving pickaxe durability.");
   }

   @Override
   public void onActivate() {
      if (this.mc.player == null || this.mc.level == null) {
         this.toggle();
         return;
      }

      if (BaritoneAPI.getProvider() == null) {
         if (this.chatFeedback.get()) this.error("Baritone is not available.");
         this.toggle();
         return;
      }

      this.idleTicks = 0;
      this.escalatedToExcavation = false;

      int minY = this.minSurfaceY.get();
      if (SurfaceEscapeEngine.isAlreadyOnSurface(minY)) {
         if (this.chatFeedback.get()) this.info("You are already on or near the surface!");
         this.toggle();
         return;
      }

      // Check pickaxe durability
      if (this.protectDurability.get()) {
         ItemStack mainHand = this.mc.player.getMainHandItem();
         if (mainHand.getItem() instanceof PickaxeItem) {
            int remaining = mainHand.getMaxDamage() - mainHand.getDamageValue();
            if (remaining <= 15) {
               this.warning("Your pickaxe only has %d durability remaining! Mining will be strongly avoided.", remaining);
            }
         }
      }

      double penalty = this.preferNaturalCaves.get() ? this.breakPenalty.get() : 5.0;
      boolean started = SurfaceEscapeEngine.startEscape(penalty, this.allowPlace.get(), minY);
      if (started) {
         int dist = SurfaceEscapeEngine.getDistanceToSurface(minY);
         int airShaft = SurfaceEscapeEngine.detectVerticalAirShaft(this.mc.player.blockPosition());
         int blocksInInv = SurfaceEscapeEngine.countThrowawayBlocksInInventory();

         if (airShaft >= 4 && blocksInInv >= 4 && this.chatFeedback.get()) {
            this.info("Vertical shaft detected (%d blocks air). Pillaring straight up to surface...", airShaft);
         } else if (this.chatFeedback.get()) {
            this.info("Escaping to surface (approx %d blocks above). Following natural openings...", dist);
         }
      } else {
         this.toggle();
      }
   }

   @Override
   public void onDeactivate() {
      SurfaceEscapeEngine.stopEscape();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player == null || this.mc.level == null) return;
      if (!SurfaceEscapeEngine.isEscaping()) return;

      int minY = this.minSurfaceY.get();
      if (SurfaceEscapeEngine.isAlreadyOnSurface(minY)) {
         BlockPos pos = this.mc.player.blockPosition();
         int consumed = SurfaceEscapeEngine.getDurabilityConsumed();

         if (this.chatFeedback.get()) {
            this.info("Successfully escaped to surface at [%d, %d, %d]! Pickaxe durability consumed: (highlight)%d(default).",
               pos.getX(), pos.getY(), pos.getZ(), consumed);
         }

         this.mc.level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F, false);
         this.toggle();
         return;
      }

      // Supervisor: detect if Baritone stopped or failed to find an open path
      if (BaritoneAPI.getProvider() != null) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         boolean isPathing = baritone.getPathingBehavior().isPathing();
         boolean isCalculating = baritone.getPathingBehavior().getInProgress().isPresent();

         if (!isPathing && !isCalculating) {
            this.idleTicks++;
            if (this.idleTicks > 25) {
               this.idleTicks = 0;
               if (!this.escalatedToExcavation) {
                  this.escalatedToExcavation = true;
                  if (this.chatFeedback.get()) {
                     this.warning("No open cave route found. Escalating to staircase excavation mode with pickaxe...");
                  }
                  SurfaceEscapeEngine.applyEscalationTier(true, minY);
               } else {
                  SurfaceEscapeEngine.applyEscalationTier(true, minY);
               }
            }
         } else {
            this.idleTicks = 0;
         }
      }
   }

   @Override
   public String getInfoString() {
      if (this.mc.player == null || !this.isActive()) return null;
      int dist = SurfaceEscapeEngine.getDistanceToSurface(this.minSurfaceY.get());
      return dist > 0 ? dist + "m" : "Surface";
   }
}
