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
      UpwardStage("Upward Staged (~100)"),
      DirectAscent("Direct Surface"),
      SmartAuto("Smart Auto"),
      Excavation("Excavation"),
      NaturalCavesOnly("Natural Caves Only");

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
         .defaultValue(Strategy.UpwardStage)
         .build()
   );

   public final Setting<Integer> stageStep = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("stage-step")
         .description("Vertical height per stage (like goto ~ ~100 ~). Keeps pathfinding focused.")
         .defaultValue(100)
         .min(20)
         .max(250)
         .sliderRange(40, 150)
         .visible(() -> this.escapeStrategy.get() == Strategy.UpwardStage || this.escapeStrategy.get() == Strategy.SmartAuto)
         .build()
   );

   public final Setting<Double> breakPenalty = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("break-penalty")
         .description("Path cost penalty for breaking blocks. Default 2.0 allows natural staircasing.")
         .defaultValue(2.0)
         .range(0.5, 50.0)
         .sliderRange(1.0, 15.0)
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
   private int consecutiveIdleRecoveries = 0;
   private Integer targetX = null;
   private Integer targetY = null;
   private Integer targetZ = null;

   public GotoSurface() {
      super(Categories.World, "goto-surface", "Intelligently navigates through natural openings or terrain to reach the surface with progressive upward staging.");
   }

   public void setTarget(int x, int z) {
      this.targetX = x;
      this.targetY = null;
      this.targetZ = z;
   }

   public void setTarget(int x, int y, int z) {
      this.targetX = x;
      this.targetY = y;
      this.targetZ = z;
   }

   public void setTargetY(int y) {
      this.targetX = null;
      this.targetY = y;
      this.targetZ = null;
   }

   public void clearTarget() {
      this.targetX = null;
      this.targetY = null;
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
      this.consecutiveIdleRecoveries = 0;

      int minY = this.mc.level.dimension() == Level.NETHER ? this.netherSafeElevation.get() : this.minSurfaceY.get();
      if (this.targetY == null && SurfaceEscapeEngine.isAlreadyOnSurface(minY)) {
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
               this.error("CRITICAL: Pickaxe has only %d durability remaining! Mining will be strongly avoided.", remaining);
            } else if (remaining <= 15) {
               this.warning("Your pickaxe only has %d durability remaining! Mining will be strongly avoided.", remaining);
            }
         }
      }

      double penalty;
      boolean place = this.allowPlace.get();

      switch (this.escapeStrategy.get()) {
         case NaturalCavesOnly -> penalty = 80.0;
         case Excavation -> penalty = 1.0;
         default -> penalty = this.breakPenalty.get();
      }

      int step = (this.escapeStrategy.get() == Strategy.DirectAscent) ? 500 : this.stageStep.get();
      boolean started;

      if (this.targetX != null && this.targetZ != null) {
         started = SurfaceEscapeEngine.startNavigation(this.targetX, this.targetZ, penalty, place, minY, step);
         if (started && this.chatFeedback.get()) {
            this.info("Navigating to surface at [%d, %d] with dynamic chunk heightmap...", this.targetX, this.targetZ);
         }
      } else if (this.targetY != null) {
         started = SurfaceEscapeEngine.startDirectY(this.targetY, penalty, place, step);
         if (started && this.chatFeedback.get()) {
            this.info("Ascending to target elevation Y=%d...", this.targetY);
         }
      } else {
         if (this.mc.level.dimension() == Level.NETHER) {
            started = SurfaceEscapeEngine.startEscape(penalty, place, minY, step, false);
            if (started && this.chatFeedback.get()) {
               this.info("Escaping Nether tunnels to safe open cavern chamber at Y=%d...", minY);
            }
         } else {
            boolean prioritizeShafts = (this.escapeStrategy.get() == Strategy.SmartAuto);
            started = SurfaceEscapeEngine.startEscape(penalty, place, minY, step, prioritizeShafts);
            if (started && this.chatFeedback.get()) {
               int targetSurf = SurfaceEscapeEngine.getTotalTargetSurfaceY();
               int stageY = SurfaceEscapeEngine.getCurrentStageTargetY();
               if (stageY < targetSurf) {
                  this.info("Ascending to surface: stage [Y=%d -> Y=%d] (Surface approx Y=%d)...",
                     this.mc.player.getBlockY(), stageY, targetSurf);
               } else {
                  this.info("Ascending directly to surface at approx Y=%d...", targetSurf);
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
      this.clearTarget();
      this.idleTicks = 0;
      this.consecutiveIdleRecoveries = 0;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player == null || this.mc.level == null) return;
      if (!SurfaceEscapeEngine.isEscaping()) return;

      int minY = this.mc.level.dimension() == Level.NETHER ? this.netherSafeElevation.get() : this.minSurfaceY.get();

      // Check arrival at surface
      if (SurfaceEscapeEngine.isAlreadyOnSurface(minY)) {
         BlockPos pos = this.mc.player.blockPosition();
         int consumed = SurfaceEscapeEngine.getDurabilityConsumed();

         if (this.chatFeedback.get()) {
            if (SurfaceEscapeEngine.isTargetedNavigation()) {
               this.info("Successfully reached target surface at [%d, %d, %d]! Pickaxe durability consumed: (highlight)%d(default).",
                  pos.getX(), pos.getY(), pos.getZ(), consumed);
            } else {
               this.info("Successfully reached the surface at [%d, %d, %d]! Pickaxe durability consumed: (highlight)%d(default).",
                  pos.getX(), pos.getY(), pos.getZ(), consumed);
            }
         }

         this.mc.level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F, false);
         this.toggle();
         return;
      }

      // Check pickaxe durability during ascent
      if (this.protectDurability.get()) {
         ItemStack mainHand = this.mc.player.getMainHandItem();
         if (mainHand.getItem() instanceof PickaxeItem) {
            int remaining = mainHand.getMaxDamage() - mainHand.getDamageValue();
            if (remaining <= 3) {
               this.error("Pickaxe critically low (%d durability)! Stopping surface ascent for tool safety.", remaining);
               this.toggle();
               return;
            }
         }
      }

      // Stage progression check
      int currentStageY = SurfaceEscapeEngine.getCurrentStageTargetY();
      int totalSurfaceY = SurfaceEscapeEngine.getTotalTargetSurfaceY();
      int step = (this.escapeStrategy.get() == Strategy.DirectAscent) ? 500 : this.stageStep.get();

      if (this.mc.player.getBlockY() >= currentStageY - 3 && currentStageY < totalSurfaceY) {
         boolean advanced = SurfaceEscapeEngine.updateStage(
            this.mc.player.getBlockX(), this.mc.player.getBlockY(), this.mc.player.getBlockZ(), minY, step
         );
         if (advanced && this.chatFeedback.get()) {
            int nextStageY = SurfaceEscapeEngine.getCurrentStageTargetY();
            this.info("Stage reached! Ascending to next stage Y=%d (Surface approx Y=%d)...", nextStageY, totalSurfaceY);
         }
      }

      // Horizontal drift check: if staircasing drifted horizontally > 14 blocks, re-anchor goal column
      if (this.targetX == null && this.targetZ == null) {
         int anchorX = SurfaceEscapeEngine.getCurrentAnchorX();
         int anchorZ = SurfaceEscapeEngine.getCurrentAnchorZ();
         double dx = this.mc.player.getX() - anchorX;
         double dz = this.mc.player.getZ() - anchorZ;
         if (dx * dx + dz * dz > 196.0) {
            SurfaceEscapeEngine.reanchorColumn(this.mc.player.getBlockX(), this.mc.player.getBlockZ());
         }
      }

      // Supervisor: detect if Baritone stopped or failed to find an open path
      if (BaritoneAPI.getProvider() != null) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         boolean isPathing = baritone.getPathingBehavior().isPathing();
         boolean isCalculating = baritone.getPathingBehavior().getInProgress().isPresent();

         if (!isPathing && !isCalculating) {
            this.idleTicks++;
            if (this.idleTicks >= 20) {
               this.idleTicks = 0;
               this.consecutiveIdleRecoveries++;

               if (SurfaceEscapeEngine.isAlreadyOnSurface(minY)) {
                  this.toggle();
                  return;
               }

               boolean boost = (this.consecutiveIdleRecoveries >= 2);
               SurfaceEscapeEngine.applyAntiGiveUpRecovery(boost, minY);

               if (this.chatFeedback.get()) {
                  if (boost) {
                     this.warning("Path obstructed. Clearing upward staircase with pickaxe towards Y=%d...",
                        SurfaceEscapeEngine.getCurrentStageTargetY());
                  } else {
                     this.info("Path interrupted. Recalculating upward route towards Y=%d...",
                        SurfaceEscapeEngine.getCurrentStageTargetY());
                  }
               }
            }
         } else {
            this.idleTicks = 0;
            this.consecutiveIdleRecoveries = 0;
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
