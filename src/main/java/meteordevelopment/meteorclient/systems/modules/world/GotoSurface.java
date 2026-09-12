package meteordevelopment.meteorclient.systems.modules.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
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
import net.minecraft.world.level.Level;

public class GotoSurface extends Module {
   public enum Strategy {
      SmartAuto("Smart Auto"),
      NaturalCavesOnly("Natural Caves Only"),
      PillarPriority("Pillar Priority"),
      Excavation("Excavation");

      private final String title;

      Strategy(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSafety = this.settings.createGroup("Safety & Durability");

   public final Setting<Strategy> escapeStrategy = this.sgGeneral.add(
      new EnumSetting.Builder<Strategy>()
         .name("strategy")
         .description("Strategy used to escape to the surface.")
         .defaultValue(Strategy.SmartAuto)
         .build()
   );

   public final Setting<Boolean> skyLightGradient = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("sky-light-gradient")
         .description("Guides Baritone along illuminated cave mouths and natural openings using daylight attraction.")
         .defaultValue(true)
         .build()
   );

   public final Setting<Double> breakPenalty = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("break-penalty")
         .description("Path cost penalty for breaking blocks in Smart Auto mode.")
         .defaultValue(60.0)
         .range(5.0, 200.0)
         .sliderRange(10.0, 100.0)
         .visible(() -> this.escapeStrategy.get() == Strategy.SmartAuto)
         .build()
   );

   public final Setting<Boolean> allowPlace = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("allow-place")
         .description("Allows placing throwaway blocks (cobble, dirt, deepslate) to bridge, climb, or pillar.")
         .defaultValue(true)
         .build()
   );

   public final Setting<Integer> minSurfaceY = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("min-surface-y")
         .description("Minimum Y level to consider as reaching the surface.")
         .defaultValue(62)
         .min(0)
         .max(320)
         .sliderRange(50, 100)
         .build()
   );

   public final Setting<Integer> scanRadius = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("scan-radius")
         .description("Chunk radius to scan for natural surface exits and valleys.")
         .defaultValue(4)
         .min(1)
         .max(8)
         .sliderRange(1, 8)
         .build()
   );

   public final Setting<Integer> netherSafeElevation = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("nether-elevation")
         .description("Target safe cavern elevation in the Nether.")
         .defaultValue(65)
         .min(40)
         .max(95)
         .sliderRange(40, 95)
         .build()
   );

   public final Setting<Boolean> protectDurability = this.sgSafety.add(
      new BoolSetting.Builder()
         .name("protect-low-durability")
         .description("Warns if your held pickaxe has <= 15 durability remaining.")
         .defaultValue(true)
         .build()
   );

   public final Setting<Boolean> chatFeedback = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("chat-feedback")
         .description("Shows escape progress and durability reports in chat.")
         .defaultValue(true)
         .build()
   );

   private int idleTicks = 0;
   private boolean escalatedToExcavation = false;
   private Integer targetX = null;
   private Integer targetZ = null;

   public GotoSurface() {
      super(Categories.World, "goto-surface", "Intelligently navigates through natural openings or terrain to reach the surface with dynamic block elevation.");
   }

   public void setTarget(int x, int z) {
      this.targetX = x;
      this.targetZ = z;
   }

   public void clearTarget() {
      this.targetX = null;
      this.targetZ = null;
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

      int minY = this.mc.level.dimension() == Level.NETHER ? this.netherSafeElevation.get() : this.minSurfaceY.get();
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
            if (remaining <= 5) {
               this.error("CRITICAL: Pickaxe has only %d durability remaining! Switching to non-destructive escape.", remaining);
            } else if (remaining <= 15) {
               this.warning("Your pickaxe only has %d durability remaining! Mining will be strongly avoided.", remaining);
            }
         }
      }

      double penalty;
      boolean place = this.allowPlace.get();
      boolean useGradient = this.skyLightGradient.get();

      switch (this.escapeStrategy.get()) {
         case NaturalCavesOnly -> penalty = 150.0;
         case PillarPriority -> penalty = 20.0;
         case Excavation -> penalty = 2.0;
         case SmartAuto -> penalty = this.breakPenalty.get();
         default -> penalty = 60.0;
      }

      boolean started;
      if (this.targetX != null && this.targetZ != null) {
         started = SurfaceEscapeEngine.startNavigation(this.targetX, this.targetZ, penalty, place, minY, useGradient);
         if (started && this.chatFeedback.get()) {
            this.info("Navigating to surface at [%d, %d] with dynamic block column heightmap...", this.targetX, this.targetZ);
         }
      } else {
         if (this.mc.level.dimension() == Level.NETHER) {
            started = SurfaceEscapeEngine.startEscape(penalty, place, minY, useGradient);
            if (started && this.chatFeedback.get()) {
               this.info("Escaping Nether tunnels to safe open cavern chamber at Y=%d...", minY);
            }
         } else {
            // Check vicinity shaft / water column
            SurfaceEscapeEngine.ShaftOpening shaft = SurfaceEscapeEngine.findBestVicinityShaft(this.mc.player.blockPosition(), 3);
            int blocksInInv = SurfaceEscapeEngine.countThrowawayBlocksInInventory();

            if (this.escapeStrategy.get() == Strategy.PillarPriority || (shaft != null && (shaft.isWaterColumn || (place && blocksInInv >= 4)))) {
               started = SurfaceEscapeEngine.startEscape(penalty, place, minY, useGradient);
               if (started && this.chatFeedback.get()) {
                  if (shaft != null && shaft.isWaterColumn) {
                     this.info("Ascending via natural water column at [%d, %d] (clearance: %d blocks)...", shaft.pos.getX(), shaft.pos.getZ(), shaft.clearance);
                  } else if (shaft != null) {
                     this.info("Vertical shaft detected at [%d, %d] (clearance: %d blocks). Pillaring to surface...", shaft.pos.getX(), shaft.pos.getZ(), shaft.clearance);
                  } else {
                     this.info("Pillaring priority mode active. Ascending upwards...");
                  }
               }
            } else {
               started = SurfaceEscapeEngine.startEscape(penalty, place, minY, useGradient);
               if (started && this.chatFeedback.get()) {
                  int dist = SurfaceEscapeEngine.getDistanceToSurface(minY);
                  if (useGradient) {
                     this.info("Following daylight gradient and natural openings to surface (approx %d blocks)...", dist);
                  } else {
                     this.info("Escaping to surface (approx %d blocks above). Finding best route...", dist);
                  }
               }
            }
         }
      }

      if (!started) {
         this.toggle();
      }
   }

   @Override
   public void onDeactivate() {
      SurfaceEscapeEngine.stopEscape();
      this.targetX = null;
      this.targetZ = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player == null || this.mc.level == null) return;
      if (!SurfaceEscapeEngine.isEscaping()) return;

      int minY = this.mc.level.dimension() == Level.NETHER ? this.netherSafeElevation.get() : this.minSurfaceY.get();
      if (SurfaceEscapeEngine.isAlreadyOnSurface(minY)) {
         BlockPos pos = this.mc.player.blockPosition();
         int consumed = SurfaceEscapeEngine.getDurabilityConsumed();

         if (this.chatFeedback.get()) {
            if (SurfaceEscapeEngine.isTargetedNavigation()) {
               this.info("Successfully reached target surface at [%d, %d, %d]! Pickaxe durability consumed: (highlight)%d(default).",
                  pos.getX(), pos.getY(), pos.getZ(), consumed);
            } else {
               this.info("Successfully escaped to surface at [%d, %d, %d]! Pickaxe durability consumed: (highlight)%d(default).",
                  pos.getX(), pos.getY(), pos.getZ(), consumed);
            }
         }

         this.mc.level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F, false);
         this.toggle();
         return;
      }

      // Check pickaxe durability during excavation
      if (this.protectDurability.get()) {
         ItemStack mainHand = this.mc.player.getMainHandItem();
         if (mainHand.getItem() instanceof PickaxeItem) {
            int remaining = mainHand.getMaxDamage() - mainHand.getDamageValue();
            if (remaining <= 3 && !this.escalatedToExcavation) {
               this.error("Pickaxe critically low (%d)! Halting destructive excavation.", remaining);
               SurfaceEscapeEngine.applyEscalationTier(false, minY, this.skyLightGradient.get());
            }
         }
      }

      // Supervisor: detect if Baritone stopped or failed to find an open path
      if (BaritoneAPI.getProvider() != null) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         boolean isPathing = baritone.getPathingBehavior().isPathing();
         boolean isCalculating = baritone.getPathingBehavior().getInProgress().isPresent();

         if (!isPathing && !isCalculating) {
            this.idleTicks++;
            if (this.idleTicks > 80) {
               this.idleTicks = 0;
               if (!this.escalatedToExcavation && this.escapeStrategy.get() != Strategy.NaturalCavesOnly) {
                  this.escalatedToExcavation = true;
                  if (this.chatFeedback.get()) {
                     this.warning("No open cave route found. Escalating to staircase excavation mode with pickaxe...");
                  }
                  SurfaceEscapeEngine.applyEscalationTier(true, minY, this.skyLightGradient.get());
               } else {
                  SurfaceEscapeEngine.applyEscalationTier(this.escapeStrategy.get() == Strategy.Excavation, minY, this.skyLightGradient.get());
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
      int minY = (this.mc.level != null && this.mc.level.dimension() == Level.NETHER)
         ? this.netherSafeElevation.get()
         : this.minSurfaceY.get();
      int dist = SurfaceEscapeEngine.getDistanceToSurface(minY);
      return dist > 0 ? dist + "m" : "Surface";
   }
}
