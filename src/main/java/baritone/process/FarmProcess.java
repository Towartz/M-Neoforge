package baritone.process;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IFarmProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class FarmProcess extends BaritoneProcessHelper implements IFarmProcess {
   private boolean active;
   private List<BlockPos> locations;
   private int tickCount;
   private int range;
   private BlockPos center;
   private static final List<Item> FARMLAND_PLANTABLE = Arrays.asList(
      Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.WHEAT_SEEDS, Items.PUMPKIN_SEEDS, Items.POTATO, Items.CARROT
   );
   private static final List<Item> PICKUP_DROPPED = Arrays.asList(
      Items.BEETROOT_SEEDS,
      Items.BEETROOT,
      Items.MELON_SEEDS,
      Items.MELON_SLICE,
      Blocks.MELON.asItem(),
      Items.WHEAT_SEEDS,
      Items.WHEAT,
      Items.PUMPKIN_SEEDS,
      Blocks.PUMPKIN.asItem(),
      Items.POTATO,
      Items.CARROT,
      Items.NETHER_WART,
      Items.COCOA_BEANS,
      Blocks.SUGAR_CANE.asItem(),
      Blocks.BAMBOO.asItem(),
      Blocks.CACTUS.asItem()
   );

   public FarmProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public boolean isActive() {
      return this.active;
   }

   @Override
   public void farm(int range, BlockPos pos) {
      if (pos == null) {
         this.center = this.baritone.getPlayerContext().playerFeet();
      } else {
         this.center = pos;
      }

      this.range = range;
      this.active = true;
      this.locations = null;
   }

   private boolean readyForHarvest(Level world, BlockPos pos, BlockState state) {
      for (FarmProcess.Harvest harvest : FarmProcess.Harvest.values()) {
         if (harvest.block == state.getBlock()) {
            return harvest.readyToHarvest(world, pos, state);
         }
      }

      return false;
   }

   private boolean isPlantable(ItemStack stack) {
      return FARMLAND_PLANTABLE.contains(stack.getItem());
   }

   private boolean isBoneMeal(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem().equals(Items.BONE_MEAL);
   }

   private boolean isNetherWart(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem().equals(Items.NETHER_WART);
   }

   private boolean isCocoa(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem().equals(Items.COCOA_BEANS);
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      if (Baritone.settings().mineGoalUpdateInterval.value != 0 && this.tickCount++ % Baritone.settings().mineGoalUpdateInterval.value == 0) {
         ArrayList<Block> scan = new ArrayList<>();

         for (FarmProcess.Harvest harvest : FarmProcess.Harvest.values()) {
            scan.add(harvest.block);
         }

         if (Baritone.settings().replantCrops.value) {
            scan.add(Blocks.FARMLAND);
            scan.add(Blocks.JUNGLE_LOG);
            if (Baritone.settings().replantNetherWart.value) {
               scan.add(Blocks.SOUL_SAND);
            }
         }

         Baritone.getExecutor()
            .execute(
               () -> this.locations = BaritoneAPI.getProvider()
                     .getWorldScanner()
                     .scanChunkRadius(this.ctx, scan, Baritone.settings().farmMaxScanSize.value, 10, 10)
            );
      }

      if (this.locations == null) {
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      } else {
         List<BlockPos> toBreak = new ArrayList<>();
         List<BlockPos> openFarmland = new ArrayList<>();
         List<BlockPos> bonemealable = new ArrayList<>();
         List<BlockPos> openSoulsand = new ArrayList<>();
         List<BlockPos> openLog = new ArrayList<>();
         boolean hasImmatureCrop = false;

         for (BlockPos pos : this.locations) {
            if (this.range == 0 || !(pos.distSqr(this.center) > (double)(this.range * this.range))) {
               BlockState state = this.ctx.world().getBlockState(pos);
               boolean airAbove = this.ctx.world().getBlockState(pos.above()).getBlock() instanceof AirBlock;
               if (state.getBlock() == Blocks.FARMLAND) {
                  if (airAbove) {
                     openFarmland.add(pos);
                  }
               } else if (state.getBlock() == Blocks.SOUL_SAND) {
                  if (airAbove) {
                     openSoulsand.add(pos);
                  }
               } else if (state.getBlock() == Blocks.JUNGLE_LOG) {
                  for (Direction direction : Plane.HORIZONTAL) {
                     if (this.ctx.world().getBlockState(pos.relative(direction)).getBlock() instanceof AirBlock) {
                        openLog.add(pos);
                        break;
                     }
                  }
               } else if (this.readyForHarvest(this.ctx.world(), pos, state)) {
                  toBreak.add(pos);
               } else {
                  FarmProcess.Harvest[] goalz = FarmProcess.Harvest.values();
                  int posx = goalz.length;
                  int entity = 0;

                  while (true) {
                     if (entity < posx) {
                        FarmProcess.Harvest harvest = goalz[entity];
                        if (harvest.block != state.getBlock()) {
                           entity++;
                           continue;
                        }

                        hasImmatureCrop = true;
                     }

                     if (state.getBlock() instanceof BonemealableBlock) {
                        BonemealableBlock ig = (BonemealableBlock)state.getBlock();
                        if (ig.isValidBonemealTarget(this.ctx.world(), pos, state)
                           && ig.isBonemealSuccess(this.ctx.world(), this.ctx.world().random, pos, state)) {
                           bonemealable.add(pos);
                        }
                     }
                     break;
                  }
               }
            }
         }

         this.baritone.getInputOverrideHandler().clearAllKeys();
         BetterBlockPos playerPos = this.ctx.playerFeet();
         double blockReachDistance = this.ctx.playerController().getBlockReachDistance();

         for (BlockPos posx : toBreak) {
            if (!(playerPos.distSqr(posx) > blockReachDistance * blockReachDistance)) {
               Optional<Rotation> rot = RotationUtils.reachable(this.ctx, posx);
               if (rot.isPresent() && isSafeToCancel) {
                  this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                  MovementHelper.switchToBestToolFor(this.ctx, this.ctx.world().getBlockState(posx));
                  if (this.ctx.isLookingAt(posx)) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                  }

                  return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               }
            }
         }

         ArrayList<BlockPos> both = new ArrayList<>(openFarmland);
         both.addAll(openSoulsand);

         for (BlockPos posxx : both) {
            if (!(playerPos.distSqr(posxx) > blockReachDistance * blockReachDistance)) {
               boolean soulsand = openSoulsand.contains(posxx);
               Optional<Rotation> rot = RotationUtils.reachableOffset(
                  this.ctx, posxx, new Vec3((double)posxx.getX() + 0.5, (double)(posxx.getY() + 1), (double)posxx.getZ() + 0.5), blockReachDistance, false
               );
               if (rot.isPresent() && isSafeToCancel && this.baritone.getInventoryBehavior().throwaway(true, soulsand ? this::isNetherWart : this::isPlantable)
                  )
                {
                  HitResult result = RayTraceUtils.rayTraceTowards(this.ctx.player(), rot.get(), blockReachDistance);
                  if (result instanceof BlockHitResult && ((BlockHitResult)result).getDirection() == Direction.UP) {
                     this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                     if (this.ctx.isLookingAt(posxx)) {
                        this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                     }

                     return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                  }
               }
            }
         }

         for (BlockPos posxxx : openLog) {
            if (!(playerPos.distSqr(posxxx) > blockReachDistance * blockReachDistance)) {
               for (Direction dir : Plane.HORIZONTAL) {
                  if (this.ctx.world().getBlockState(posxxx.relative(dir)).getBlock() instanceof AirBlock) {
                     Vec3 faceCenter = Vec3.atCenterOf(posxxx).add(Vec3.atLowerCornerOf(dir.getNormal()).scale(0.5));
                     Optional<Rotation> rot = RotationUtils.reachableOffset(this.ctx, posxxx, faceCenter, blockReachDistance, false);
                     if (rot.isPresent() && isSafeToCancel && this.baritone.getInventoryBehavior().throwaway(true, this::isCocoa)) {
                        HitResult result = RayTraceUtils.rayTraceTowards(this.ctx.player(), rot.get(), blockReachDistance);
                        if (result instanceof BlockHitResult && ((BlockHitResult)result).getDirection() == dir) {
                           this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                           if (this.ctx.isLookingAt(posxxx)) {
                              this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                           }

                           return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                        }
                     }
                  }
               }
            }
         }

         for (BlockPos posxxxx : bonemealable) {
            if (!(playerPos.distSqr(posxxxx) > blockReachDistance * blockReachDistance)) {
               Optional<Rotation> rot = RotationUtils.reachable(this.ctx, posxxxx);
               if (rot.isPresent() && isSafeToCancel && this.baritone.getInventoryBehavior().throwaway(true, this::isBoneMeal)) {
                  this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                  if (this.ctx.isLookingAt(posxxxx)) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                  }

                  return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               }
            }
         }

         if (calcFailed) {
            this.logDirect("Farm failed");
            if (Baritone.settings().notificationOnFarmFail.value) {
               this.logNotification("Farm failed", true);
            }

            this.onLostControl();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
         } else {
            List<Goal> goalz = new ArrayList<>();

            for (BlockPos posxxxxx : toBreak) {
               goalz.add(new BuilderProcess.GoalBreak(posxxxxx));
            }

            if (this.baritone.getInventoryBehavior().throwaway(false, this::isPlantable)) {
               for (BlockPos posxxxxx : openFarmland) {
                  goalz.add(new GoalBlock(posxxxxx.above()));
               }
            }

            if (this.baritone.getInventoryBehavior().throwaway(false, this::isNetherWart)) {
               for (BlockPos posxxxxx : openSoulsand) {
                  goalz.add(new GoalBlock(posxxxxx.above()));
               }
            }

            if (this.baritone.getInventoryBehavior().throwaway(false, this::isCocoa)) {
               for (BlockPos posxxxxx : openLog) {
                  for (Direction directionx : Plane.HORIZONTAL) {
                     if (this.ctx.world().getBlockState(posxxxxx.relative(directionx)).getBlock() instanceof AirBlock) {
                        goalz.add(new GoalGetToBlock(posxxxxx.relative(directionx)));
                     }
                  }
               }
            }

            if (this.baritone.getInventoryBehavior().throwaway(false, this::isBoneMeal)) {
               for (BlockPos posxxxxx : bonemealable) {
                  goalz.add(new GoalBlock(posxxxxx));
               }
            }

            for (Entity entity : this.ctx.entities()) {
               if (entity instanceof ItemEntity && entity.onGround()) {
                  ItemEntity ei = (ItemEntity)entity;
                  if (PICKUP_DROPPED.contains(ei.getItem().getItem())) {
                     goalz.add(new GoalBlock(new BetterBlockPos(entity.position().x, entity.position().y + 0.1, entity.position().z)));
                  }
               }
            }

            if (!goalz.isEmpty()) {
               return new PathingCommand(new GoalComposite(goalz.toArray(new Goal[0])), PathingCommandType.SET_GOAL_AND_PATH);
            } else if (hasImmatureCrop && Baritone.settings().farmWaitForGrowth.value) {
               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            } else {
               this.logDirect("Farm failed");
               if (Baritone.settings().notificationOnFarmFail.value) {
                  this.logNotification("Farm failed", true);
               }

               this.onLostControl();
               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
         }
      }
   }

   @Override
   public void onLostControl() {
      this.active = false;
   }

   @Override
   public String displayName0() {
      return "Farming";
   }

   private static enum Harvest {
      WHEAT((CropBlock)Blocks.WHEAT),
      CARROTS((CropBlock)Blocks.CARROTS),
      POTATOES((CropBlock)Blocks.POTATOES),
      BEETROOT((CropBlock)Blocks.BEETROOTS),
      PUMPKIN(Blocks.PUMPKIN, state -> true),
      MELON(Blocks.MELON, state -> true),
      NETHERWART(Blocks.NETHER_WART, state -> (Integer)state.getValue(NetherWartBlock.AGE) >= 3),
      COCOA(Blocks.COCOA, state -> (Integer)state.getValue(CocoaBlock.AGE) >= 2),
      SUGARCANE(Blocks.SUGAR_CANE, null) {
         @Override
         public boolean readyToHarvest(Level world, BlockPos pos, BlockState state) {
            return Baritone.settings().replantCrops.value ? world.getBlockState(pos.below()).getBlock() instanceof SugarCaneBlock : true;
         }
      },
      BAMBOO(Blocks.BAMBOO, null) {
         @Override
         public boolean readyToHarvest(Level world, BlockPos pos, BlockState state) {
            return Baritone.settings().replantCrops.value ? world.getBlockState(pos.below()).getBlock() instanceof BambooStalkBlock : true;
         }
      },
      CACTUS(Blocks.CACTUS, null) {
         @Override
         public boolean readyToHarvest(Level world, BlockPos pos, BlockState state) {
            return Baritone.settings().replantCrops.value ? world.getBlockState(pos.below()).getBlock() instanceof CactusBlock : true;
         }
      };

      public final Block block;
      public final Predicate<BlockState> readyToHarvest;

      private Harvest(CropBlock blockCrops) {
         this(blockCrops, blockCrops::isMaxAge);
      }

      private Harvest(Block block, Predicate<BlockState> readyToHarvest) {
         this.block = block;
         this.readyToHarvest = readyToHarvest;
      }

      public boolean readyToHarvest(Level world, BlockPos pos, BlockState state) {
         return this.readyToHarvest.test(state);
      }
   }
}
