package meteordevelopment.meteorclient.systems.modules.movement;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Scaffold extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<List<Block>> blocks = this.sgGeneral.add(new BlockListSetting.Builder().name("blocks").description("Selected blocks.").build());
   private final Setting<Scaffold.ListMode> blocksFilter = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("blocks-filter"))
                  .description("How to use the block list setting"))
               .defaultValue(Scaffold.ListMode.Blacklist))
            .build()
      );
   private final Setting<Boolean> fastTower = this.sgGeneral
      .add(new BoolSetting.Builder().name("fast-tower").description("Whether or not to scaffold upwards faster.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Double> towerSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("tower-speed")
            .description("The speed at which to tower.")
            .defaultValue(0.5)
            .min(0.0)
            .sliderMax(1.0)
            .visible(this.fastTower::get)
            .build()
      );
   private final Setting<Boolean> whileMoving = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("while-moving")
            .description("Allows you to tower while moving.")
            .defaultValue(Boolean.valueOf(false))
            .visible(this.fastTower::get)
            .build()
      );
   private final Setting<Boolean> onlyOnClick = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-on-click")
            .description("Only places blocks when holding right click.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> renderSwing = this.sgGeneral
      .add(new BoolSetting.Builder().name("swing").description("Renders your client-side swing.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> autoSwitch = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-switch")
            .description("Automatically swaps to a block before placing.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(new BoolSetting.Builder().name("rotate").description("Rotates towards the blocks being placed.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> airPlace = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("air-place")
            .description("Allow air place. This also allows you to modify scaffold radius.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Double> aheadDistance = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("ahead-distance")
            .description("How far ahead to place blocks.")
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(1.0)
            .visible(() -> !this.airPlace.get())
            .build()
      );
   private final Setting<Double> placeRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("closest-block-range")
            .description("How far can scaffold place blocks when you are in air.")
            .defaultValue(4.0)
            .min(0.0)
            .sliderMax(8.0)
            .visible(() -> !this.airPlace.get())
            .build()
      );
   private final Setting<Double> radius = this.sgGeneral
      .add(new DoubleSetting.Builder().name("radius").description("Scaffold radius.").defaultValue(0.0).min(0.0).max(6.0).visible(this.airPlace::get).build());
   private final Setting<Integer> blocksPerTick = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("How many blocks to place in one tick.")
            .defaultValue(Integer.valueOf(3))
            .min(1)
            .visible(this.airPlace::get)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Whether to render blocks that have been placed.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the shapes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232, 10))
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232))
            .visible(this.render::get)
            .build()
      );
   private final MutableBlockPos bp = new MutableBlockPos();

   public Scaffold() {
      super(Categories.Movement, "scaffold", "Automatically places blocks under you.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (!this.onlyOnClick.get() || this.mc.options.keyUse.isDown()) {
         Vec3 vec = this.mc.player.position().add(this.mc.player.getDeltaMovement()).add(0.0, -0.75, 0.0);
         if (this.airPlace.get()) {
            this.bp.set(vec.x(), vec.y(), vec.z());
         } else {
            Vec3 pos = this.mc.player.position();
            if (this.aheadDistance.get() != 0.0
               && !this.towering()
               && !this.mc
                  .level
                  .getBlockState(this.mc.player.blockPosition().below())
                  .getCollisionShape(this.mc.level, this.mc.player.blockPosition())
                  .isEmpty()) {
               Vec3 dir = Vec3.directionFromRotation(0.0F, this.mc.player.getYRot()).multiply(this.aheadDistance.get(), 0.0, this.aheadDistance.get());
               if (this.mc.options.keyUp.isDown()) {
                  pos = pos.add(dir.x, 0.0, dir.z);
               }

               if (this.mc.options.keyDown.isDown()) {
                  pos = pos.add(-dir.x, 0.0, -dir.z);
               }

               if (this.mc.options.keyLeft.isDown()) {
                  pos = pos.add(dir.z, 0.0, -dir.x);
               }

               if (this.mc.options.keyRight.isDown()) {
                  pos = pos.add(-dir.z, 0.0, dir.x);
               }
            }

            this.bp.set(pos.x, vec.y, pos.z);
         }

         if (this.mc.options.keyShift.isDown() && !this.mc.options.keyJump.isDown() && this.mc.player.getY() + vec.y > -1.0) {
            this.bp.setY(this.bp.getY() - 1);
         }

         if (this.bp.getY() >= this.mc.player.blockPosition().getY()) {
            this.bp.setY(this.mc.player.blockPosition().getY() - 1);
         }

         BlockPos targetBlock = this.bp.immutable();
         if (!this.airPlace.get() && BlockUtils.getPlaceSide(this.bp) == null) {
            Vec3 pos = this.mc.player.position();
            pos = pos.add(0.0, -0.98F, 0.0);
            pos.add(this.mc.player.getDeltaMovement());
            List<BlockPos> blockPosArray = new ArrayList<>();

            for (int x = (int)(this.mc.player.getX() - this.placeRange.get()); (double)x < this.mc.player.getX() + this.placeRange.get(); x++) {
               for (int z = (int)(this.mc.player.getZ() - this.placeRange.get()); (double)z < this.mc.player.getZ() + this.placeRange.get(); z++) {
                  for (int y = (int)Math.max((double)this.mc.level.getMinBuildHeight(), this.mc.player.getY() - this.placeRange.get());
                     (double)y < Math.min((double)this.mc.level.getMaxBuildHeight(), this.mc.player.getY() + this.placeRange.get());
                     y++
                  ) {
                     this.bp.set(x, y, z);
                     if (BlockUtils.getPlaceSide(this.bp) != null
                        && BlockUtils.canPlace(this.bp)
                        && !(this.mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(this.bp.relative(BlockUtils.getClosestPlaceSide(this.bp)))) > 36.0)) {
                        blockPosArray.add(new BlockPos(this.bp));
                     }
                  }
               }
            }

            if (blockPosArray.isEmpty()) {
               return;
            }

            blockPosArray.sort(Comparator.comparingDouble(blockPos -> blockPos.distSqr(targetBlock)));
            this.bp.set((Vec3i)blockPosArray.getFirst());
         }

         if (this.airPlace.get()) {
            List<BlockPos> blocks = new ArrayList<>();

            for (int x = (int)((double)this.bp.getX() - this.radius.get()); (double)x <= (double)this.bp.getX() + this.radius.get(); x++) {
               for (int z = (int)((double)this.bp.getZ() - this.radius.get()); (double)z <= (double)this.bp.getZ() + this.radius.get(); z++) {
                  BlockPos blockPos = BlockPos.containing((double)x, (double)this.bp.getY(), (double)z);
                  if (this.mc.player.position().distanceTo(Vec3.atCenterOf(blockPos)) <= this.radius.get() || x == this.bp.getX() && z == this.bp.getZ()) {
                     blocks.add(blockPos);
                  }
               }
            }

            if (!blocks.isEmpty()) {
               blocks.sort(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo));
               int counter = 0;

               for (BlockPos block : blocks) {
                  if (this.place(block)) {
                     counter++;
                  }

                  if (counter >= this.blocksPerTick.get()) {
                     break;
                  }
               }
            }
         } else {
            this.place(this.bp);
         }

         FindItemResult result = InvUtils.findInHotbar(itemStack -> this.validItem(itemStack, this.bp));
         if (this.fastTower.get()
            && this.mc.options.keyJump.isDown()
            && !this.mc.options.keyShift.isDown()
            && result.found()
            && (this.autoSwitch.get() || result.getHand() != null)) {
            Vec3 velocity = this.mc.player.getDeltaMovement();
            AABB playerBox = this.mc.player.getBoundingBox();
            if (Streams.stream(this.mc.level.getBlockCollisions(this.mc.player, playerBox.move(0.0, 1.0, 0.0))).toList().isEmpty()) {
               if (this.whileMoving.get() || !PlayerUtils.isMoving()) {
                  velocity = new Vec3(velocity.x, this.towerSpeed.get(), velocity.z);
               }

               this.mc.player.setDeltaMovement(velocity);
            } else {
               this.mc.player.setDeltaMovement(velocity.x, Math.ceil(this.mc.player.getY()) - this.mc.player.getY(), velocity.z);
               this.mc.player.setOnGround(true);
            }
         }
      }
   }

   public boolean scaffolding() {
      return this.isActive() && (!this.onlyOnClick.get() || this.onlyOnClick.get() && this.mc.options.keyUse.isDown());
   }

   public boolean towering() {
      FindItemResult result = InvUtils.findInHotbar(itemStack -> this.validItem(itemStack, this.bp));
      return this.scaffolding()
         && this.fastTower.get()
         && this.mc.options.keyJump.isDown()
         && !this.mc.options.keyShift.isDown()
         && (this.whileMoving.get() || !PlayerUtils.isMoving())
         && result.found()
         && (this.autoSwitch.get() || result.getHand() != null);
   }

   private boolean validItem(ItemStack itemStack, BlockPos pos) {
      if (!(itemStack.getItem() instanceof BlockItem)) {
         return false;
      } else {
         Block block = ((BlockItem)itemStack.getItem()).getBlock();
         if (this.blocksFilter.get() == Scaffold.ListMode.Blacklist && this.blocks.get().contains(block)) {
            return false;
         } else if (this.blocksFilter.get() == Scaffold.ListMode.Whitelist && !this.blocks.get().contains(block)) {
            return false;
         } else {
            return !Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(this.mc.level, pos))
               ? false
               : !(block instanceof FallingBlock) || !FallingBlock.isFree(this.mc.level.getBlockState(pos));
         }
      }
   }

   private boolean place(BlockPos bp) {
      FindItemResult item = InvUtils.findInHotbar(itemStack -> this.validItem(itemStack, bp));
      if (!item.found()) {
         return false;
      } else if (item.getHand() == null && !this.autoSwitch.get()) {
         return false;
      } else if (BlockUtils.place(bp, item, this.rotate.get(), 50, this.renderSwing.get(), true)) {
         if (this.render.get()) {
            RenderUtils.renderTickingBlock(bp.immutable(), this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0, 8, true, false);
         }

         return true;
      } else {
         return false;
      }
   }

   public static enum ListMode {
      Whitelist,
      Blacklist;
   }
}
