package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.Safety;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class AnchorAura extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPlace = this.settings.createGroup("Place");
   private final SettingGroup sgBreak = this.settings.createGroup("Break");
   private final SettingGroup sgPause = this.settings.createGroup("Pause");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> targetRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("The radius in which players get targeted.")
            .defaultValue(4.0)
            .min(0.0)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<SortPriority> targetPriority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target-priority"))
                  .description("How to select the player to target."))
               .defaultValue(SortPriority.LowestHealth))
            .build()
      );
   private final Setting<AnchorAura.RotationMode> rotationMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("rotation-mode"))
                  .description("The mode to rotate you server-side."))
               .defaultValue(AnchorAura.RotationMode.Both))
            .build()
      );
   private final Setting<Double> maxDamage = this.sgGeneral
      .add(new DoubleSetting.Builder().name("max-self-damage").description("The maximum self-damage allowed.").defaultValue(8.0).build());
   private final Setting<Double> minHealth = this.sgGeneral
      .add(new DoubleSetting.Builder().name("min-health").description("The minimum health you have to be for Anchor Aura to work.").defaultValue(15.0).build());
   private final Setting<Boolean> place = this.sgPlace
      .add(new BoolSetting.Builder().name("place").description("Allows Anchor Aura to place anchors.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Integer> placeDelay = this.sgPlace
      .add(
         new IntSetting.Builder()
            .name("place-delay")
            .description("The tick delay between placing anchors.")
            .defaultValue(Integer.valueOf(2))
            .range(0, 10)
            .visible(this.place::get)
            .build()
      );
   private final Setting<Safety> placeMode = this.sgPlace
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("place-mode"))
                     .description("The way anchors are allowed to be placed near you."))
                  .defaultValue(Safety.Safe))
               .visible(this.place::get))
            .build()
      );
   private final Setting<Double> placeRange = this.sgPlace
      .add(
         new DoubleSetting.Builder()
            .name("place-range")
            .description("The radius in which anchors are placed in.")
            .defaultValue(5.0)
            .min(0.0)
            .sliderMax(5.0)
            .visible(this.place::get)
            .build()
      );
   private final Setting<AnchorAura.PlaceMode> placePositions = this.sgPlace
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("placement-positions"))
                     .description("Where the Anchors will be placed on the entity."))
                  .defaultValue(AnchorAura.PlaceMode.AboveAndBelow))
               .visible(this.place::get))
            .build()
      );
   private final Setting<Integer> breakDelay = this.sgBreak
      .add(
         new IntSetting.Builder()
            .name("break-delay")
            .description("The tick delay between breaking anchors.")
            .defaultValue(Integer.valueOf(10))
            .range(0, 10)
            .build()
      );
   private final Setting<Safety> breakMode = this.sgBreak
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("break-mode"))
                  .description("The way anchors are allowed to be broken near you."))
               .defaultValue(Safety.Safe))
            .build()
      );
   private final Setting<Double> breakRange = this.sgBreak
      .add(
         new DoubleSetting.Builder()
            .name("break-range")
            .description("The radius in which anchors are broken in.")
            .defaultValue(5.0)
            .min(0.0)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<Boolean> pauseOnEat = this.sgPause
      .add(new BoolSetting.Builder().name("pause-on-eat").description("Pauses while eating.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> pauseOnDrink = this.sgPause
      .add(new BoolSetting.Builder().name("pause-on-drink").description("Pauses while drinking potions.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> pauseOnMine = this.sgPause
      .add(new BoolSetting.Builder().name("pause-on-mine").description("Pauses while mining blocks.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Boolean> renderPlace = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("render-place")
            .description("Renders the block where it is placing an anchor.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<SettingColor> placeSideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("place-side-color")
            .description("The side color for positions to be placed.")
            .defaultValue(new SettingColor(255, 0, 0, 75))
            .visible(this.renderPlace::get)
            .build()
      );
   private final Setting<SettingColor> placeLineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("place-line-color")
            .description("The line color for positions to be placed.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(this.renderPlace::get)
            .build()
      );
   private final Setting<Boolean> renderBreak = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("render-break")
            .description("Renders the block where it is breaking an anchor.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<SettingColor> breakSideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("break-side-color")
            .description("The side color for anchors to be broken.")
            .defaultValue(new SettingColor(255, 0, 0, 75))
            .visible(this.renderBreak::get)
            .build()
      );
   private final Setting<SettingColor> breakLineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("break-line-color")
            .description("The line color for anchors to be broken.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(this.renderBreak::get)
            .build()
      );
   private int placeDelayLeft;
   private int breakDelayLeft;
   private Player target;
   private final MutableBlockPos mutable = new MutableBlockPos();

   public AnchorAura() {
      super(Categories.Combat, "anchor-aura", "Automatically places and breaks Respawn Anchors to harm entities.");
   }

   @Override
   public void onActivate() {
      this.placeDelayLeft = 0;
      this.breakDelayLeft = 0;
      this.target = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level.dimensionType().respawnAnchorWorks()) {
         this.error("You are in the Nether... disabling.", new Object[0]);
         this.toggle();
      } else if (!PlayerUtils.shouldPause(this.pauseOnMine.get(), this.pauseOnEat.get(), this.pauseOnDrink.get())) {
         if (!((double)EntityUtils.getTotalHealth(this.mc.player) <= this.minHealth.get())) {
            if (TargetUtils.isBadTarget(this.target, this.targetRange.get())) {
               this.target = TargetUtils.getPlayerTarget(this.targetRange.get(), this.targetPriority.get());
               if (TargetUtils.isBadTarget(this.target, this.targetRange.get())) {
                  return;
               }
            }

            FindItemResult anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
            FindItemResult glowStone = InvUtils.findInHotbar(Items.GLOWSTONE);
            if (anchor.found() && glowStone.found()) {
               if (this.breakDelayLeft >= this.breakDelay.get()) {
                  BlockPos breakPos = this.findBreakPos(this.target.blockPosition());
                  if (breakPos != null) {
                     this.breakDelayLeft = 0;
                     if (this.rotationMode.get() != AnchorAura.RotationMode.Both && this.rotationMode.get() != AnchorAura.RotationMode.Break) {
                        this.breakAnchor(breakPos, anchor, glowStone);
                     } else {
                        BlockPos immutableBreakPos = breakPos.immutable();
                        Rotations.rotate(
                           Rotations.getYaw(breakPos), Rotations.getPitch(breakPos), 50, () -> this.breakAnchor(immutableBreakPos, anchor, glowStone)
                        );
                     }
                  }
               }

               if (this.placeDelayLeft >= this.placeDelay.get() && this.place.get()) {
                  BlockPos placePos = this.findPlacePos(this.target.blockPosition());
                  if (placePos != null) {
                     this.placeDelayLeft = 0;
                     BlockUtils.place(
                        placePos.immutable(),
                        anchor,
                        this.rotationMode.get() == AnchorAura.RotationMode.Place || this.rotationMode.get() == AnchorAura.RotationMode.Both,
                        50
                     );
                  }
               }

               this.placeDelayLeft++;
               this.breakDelayLeft++;
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.target != null) {
         if (this.renderPlace.get()) {
            BlockPos placePos = this.findPlacePos(this.target.blockPosition());
            if (placePos == null) {
               return;
            }

            event.renderer.box(placePos, this.placeSideColor.get(), this.placeLineColor.get(), this.shapeMode.get(), 0);
         }

         if (this.renderBreak.get()) {
            BlockPos breakPos = this.findBreakPos(this.target.blockPosition());
            if (breakPos == null) {
               return;
            }

            event.renderer.box(breakPos, this.breakSideColor.get(), this.breakLineColor.get(), this.shapeMode.get(), 0);
         }
      }
   }

   @Nullable
   private BlockPos findPlacePos(BlockPos targetPlacePos) {
      switch ((AnchorAura.PlaceMode)this.placePositions.get()) {
         case Above:
            if (this.isValidPlace(targetPlacePos, 0, 2, 0)) {
               return this.mutable;
            }
            break;
         case Around:
            if (this.isValidPlace(targetPlacePos, 0, 0, -1)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 1, 0, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, -1, 0, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 0, 0, 1)) {
               return this.mutable;
            }
            break;
         case AboveAndBelow:
            if (this.isValidPlace(targetPlacePos, 0, -1, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 0, 2, 0)) {
               return this.mutable;
            }
            break;
         case All:
            if (this.isValidPlace(targetPlacePos, 0, -1, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 0, 2, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 1, 0, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, -1, 0, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 0, 0, 1)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 0, 0, -1)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 1, 1, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, -1, -1, 0)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 0, 1, 1)) {
               return this.mutable;
            }

            if (this.isValidPlace(targetPlacePos, 0, 0, -1)) {
               return this.mutable;
            }
      }

      return null;
   }

   @Nullable
   private BlockPos findBreakPos(BlockPos targetPos) {
      if (this.isValidBreak(targetPos, 0, -1, 0)) {
         return this.mutable;
      } else if (this.isValidBreak(targetPos, 0, 2, 0)) {
         return this.mutable;
      } else if (this.isValidBreak(targetPos, 1, 0, 0)) {
         return this.mutable;
      } else if (this.isValidBreak(targetPos, -1, 0, 0)) {
         return this.mutable;
      } else if (this.isValidBreak(targetPos, 0, 0, 1)) {
         return this.mutable;
      } else if (this.isValidBreak(targetPos, 0, 0, -1)) {
         return this.mutable;
      } else if (this.isValidBreak(targetPos, 1, 1, 0)) {
         return this.mutable;
      } else if (this.isValidBreak(targetPos, -1, -1, 0)) {
         return this.mutable;
      } else if (this.isValidBreak(targetPos, 0, 1, 1)) {
         return this.mutable;
      } else {
         return this.isValidBreak(targetPos, 0, 0, -1) ? this.mutable : null;
      }
   }

   private boolean getDamagePlace(BlockPos pos) {
      return this.placeMode.get() == Safety.Suicide || (double)DamageUtils.bedDamage(this.mc.player, pos.getCenter()) <= this.maxDamage.get();
   }

   private boolean getDamageBreak(BlockPos pos) {
      return this.breakMode.get() == Safety.Suicide || (double)DamageUtils.anchorDamage(this.mc.player, pos.getCenter()) <= this.maxDamage.get();
   }

   private boolean isValidPlace(BlockPos origin, int xOffset, int yOffset, int zOffset) {
      BlockUtils.mutateAround(this.mutable, origin, xOffset, yOffset, zOffset);
      double pRange = this.placeRange.get();
      return (double)this.mc.player.blockPosition().distSqr(this.mutable) <= pRange * pRange
         && this.getDamagePlace(this.mutable)
         && BlockUtils.canPlace(this.mutable);
   }

   private boolean isValidBreak(BlockPos origin, int xOffset, int yOffset, int zOffset) {
      BlockUtils.mutateAround(this.mutable, origin, xOffset, yOffset, zOffset);
      double bRange = this.breakRange.get();
      return this.mc.level.getBlockState(this.mutable).getBlock() == Blocks.RESPAWN_ANCHOR
         && (double)this.mc.player.blockPosition().distSqr(this.mutable) <= bRange * bRange
         && this.getDamageBreak(this.mutable);
   }

   private void breakAnchor(BlockPos pos, FindItemResult anchor, FindItemResult glowStone) {
      if (pos != null && this.mc.level.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR) {
         this.mc.player.setShiftKeyDown(false);
         if (glowStone.isOffhand()) {
            this.mc
               .gameMode
               .useItemOn(
                  this.mc.player,
                  InteractionHand.OFF_HAND,
                  new BlockHitResult(new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5), Direction.UP, pos, true)
               );
         } else {
            InvUtils.swap(glowStone.slot(), true);
            this.mc
               .gameMode
               .useItemOn(
                  this.mc.player,
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5), Direction.UP, pos, true)
               );
         }

         if (anchor.isOffhand()) {
            this.mc
               .gameMode
               .useItemOn(
                  this.mc.player,
                  InteractionHand.OFF_HAND,
                  new BlockHitResult(new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5), Direction.UP, pos, true)
               );
         } else {
            InvUtils.swap(anchor.slot(), true);
            this.mc
               .gameMode
               .useItemOn(
                  this.mc.player,
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5), Direction.UP, pos, true)
               );
         }

         InvUtils.swapBack();
      }
   }

   @Override
   public String getInfoString() {
      return EntityUtils.getName(this.target);
   }

   public static enum PlaceMode {
      Above,
      Around,
      AboveAndBelow,
      All;
   }

   public static enum RotationMode {
      Place,
      Break,
      Both,
      None;
   }
}
