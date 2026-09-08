package baritone.pathing.movement;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.ActionCosts;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.precompute.Ternary;
import baritone.utils.BlockStateInterface;
import baritone.utils.ToolSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Step;
import meteordevelopment.meteorclient.systems.modules.movement.HighJump;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import meteordevelopment.meteorclient.systems.modules.movement.Sprint;
import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public interface MovementHelper extends ActionCosts, Helper {
   static boolean avoidBreaking(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      if (!bsi.worldBorder.canPlaceAt(x, z)) {
         return true;
      } else {
         Block b = state.getBlock();
         return Baritone.settings().blocksToDisallowBreaking.value.contains(b)
            || b == Blocks.ICE
            || b instanceof InfestedBlock
            || avoidAdjacentBreaking(bsi, x, y + 1, z, true)
            || avoidAdjacentBreaking(bsi, x + 1, y, z, false)
            || avoidAdjacentBreaking(bsi, x - 1, y, z, false)
            || avoidAdjacentBreaking(bsi, x, y, z + 1, false)
            || avoidAdjacentBreaking(bsi, x, y, z - 1, false);
      }
   }

   static boolean avoidBreaking(IPlayerContext ctx, BlockPos pos) {
      return avoidBreaking(new BlockStateInterface(ctx), pos.getX(), pos.getY(), pos.getZ(), BlockStateInterface.get(ctx, pos));
   }

   static boolean avoidAdjacentBreaking(BlockStateInterface bsi, int x, int y, int z, boolean directlyAbove) {
      BlockState state = bsi.get0(x, y, z);
      Block block = state.getBlock();
      if (block instanceof FallingBlock) {
         if (directlyAbove) {
            return true;
         } else if (Baritone.settings().avoidUpdatingFallingBlocks.value && FallingBlock.isFree(bsi.get0(x, y - 1, z))) {
            return true;
         }
      } else if (block instanceof LiquidBlock) {
         if (!directlyAbove && !Baritone.settings().strictLiquidCheck.value) {
            int level = (Integer)state.getValue(LiquidBlock.LEVEL);
            return level == 0 ? true : !(bsi.get0(x, y - 1, z).getBlock() instanceof LiquidBlock);
         } else {
            return true;
         }
      } else {
         return !state.getFluidState().isEmpty();
      }
      return false;
   }

   static boolean canWalkThrough(IPlayerContext ctx, BetterBlockPos pos) {
      return canWalkThrough(new BlockStateInterface(ctx), pos.x, pos.y, pos.z);
   }

   static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z) {
      return canWalkThrough(bsi, x, y, z, bsi.get0(x, y, z));
   }

   static boolean canWalkThrough(CalculationContext context, int x, int y, int z, BlockState state) {
      return context.precomputedData.canWalkThrough(context.bsi, x, y, z, state);
   }

   static boolean canWalkThrough(CalculationContext context, int x, int y, int z) {
      return context.precomputedData.canWalkThrough(context.bsi, x, y, z, context.get(x, y, z));
   }

   static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      Ternary canWalkThrough = canWalkThroughBlockState(state);
      if (canWalkThrough == Ternary.YES) {
         return true;
      } else {
         return canWalkThrough == Ternary.NO ? false : canWalkThroughPosition(bsi, x, y, z, state);
      }
   }

   static Ternary canWalkThroughBlockState(BlockState state) {
      Block block = state.getBlock();
      if (block instanceof AirBlock) {
         return Ternary.YES;
      } else if (block instanceof BaseFireBlock
         || block == Blocks.COBWEB
         || block == Blocks.END_PORTAL
         || block == Blocks.COCOA
         || block instanceof AbstractSkullBlock
         || block == Blocks.BUBBLE_COLUMN
         || block instanceof ShulkerBoxBlock
         || block instanceof SlabBlock
         || block instanceof TrapDoorBlock
         || block == Blocks.HONEY_BLOCK
         || block == Blocks.END_ROD
         || block == Blocks.SWEET_BERRY_BUSH
         || block == Blocks.POINTED_DRIPSTONE
         || block instanceof AmethystClusterBlock
         || block instanceof AzaleaBlock) {
         return Ternary.NO;
      } else if (block == Blocks.BIG_DRIPLEAF) {
         return Ternary.NO;
      } else if (block == Blocks.POWDER_SNOW) {
         return Ternary.NO;
      } else if (Baritone.settings().blocksToAvoid.value.contains(block)) {
         return Ternary.NO;
      } else if (block instanceof DoorBlock) {
         return DoorBlock.isWoodenDoor(state) ? Ternary.YES : Ternary.NO;
      } else if (block instanceof FenceGateBlock) {
         return Ternary.YES;
      } else if (block instanceof CarpetBlock) {
         return Ternary.MAYBE;
      } else if (block instanceof SnowLayerBlock) {
         return Ternary.MAYBE;
      } else {
         FluidState fluidState = state.getFluidState();
         if (!fluidState.isEmpty()) {
            return fluidState.getType().getAmount(fluidState) != 8 ? Ternary.NO : Ternary.MAYBE;
         } else if (block instanceof CauldronBlock) {
            return Ternary.NO;
         } else {
            return state.isPathfindable(PathComputationType.LAND) ? Ternary.YES : Ternary.NO;
         }
      }
   }

   static boolean canWalkThroughPosition(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      Block block = state.getBlock();
      if (block instanceof CarpetBlock) {
         return canWalkOn(bsi, x, y - 1, z);
      } else if (block instanceof SnowLayerBlock) {
         if (!bsi.worldContainsLoadedChunk(x, z)) {
            return true;
         } else {
            return state.getValue(SnowLayerBlock.LAYERS) >= 3 ? false : canWalkOn(bsi, x, y - 1, z);
         }
      } else {
         FluidState fluidState = state.getFluidState();
         if (!fluidState.isEmpty()) {
            if (isFlowing(x, y, z, state, bsi)) {
               return false;
            } else if (Baritone.settings().assumeWalkOnWater.value) {
               return false;
            } else {
               BlockState up = bsi.get0(x, y + 1, z);
               return up.getFluidState().isEmpty() && !(up.getBlock() instanceof WaterlilyBlock) ? fluidState.getType() instanceof WaterFluid : false;
            }
         } else {
            return state.isPathfindable(PathComputationType.LAND);
         }
      }
   }

   static Ternary fullyPassableBlockState(BlockState state) {
      Block block = state.getBlock();
      if (block instanceof AirBlock) {
         return Ternary.YES;
      } else if (block instanceof BaseFireBlock
         || block == Blocks.TRIPWIRE
         || block == Blocks.COBWEB
         || block == Blocks.VINE
         || block == Blocks.LADDER
         || block == Blocks.COCOA
         || block instanceof AzaleaBlock
         || block instanceof DoorBlock
         || block instanceof FenceGateBlock
         || block instanceof SnowLayerBlock
         || !state.getFluidState().isEmpty()
         || block instanceof TrapDoorBlock
         || block instanceof EndPortalBlock
         || block instanceof SkullBlock
         || block instanceof ShulkerBoxBlock) {
         return Ternary.NO;
      } else {
         return state.isPathfindable(PathComputationType.LAND) ? Ternary.YES : Ternary.NO;
      }
   }

   static boolean fullyPassable(CalculationContext context, int x, int y, int z) {
      return fullyPassable(context, x, y, z, context.get(x, y, z));
   }

   static boolean fullyPassable(CalculationContext context, int x, int y, int z, BlockState state) {
      return context.precomputedData.fullyPassable(context.bsi, x, y, z, state);
   }

   static boolean fullyPassable(IPlayerContext ctx, BlockPos pos) {
      BlockState state = ctx.world().getBlockState(pos);
      Ternary fullyPassable = fullyPassableBlockState(state);
      if (fullyPassable == Ternary.YES) {
         return true;
      } else {
         return fullyPassable == Ternary.NO ? false : state.isPathfindable(PathComputationType.LAND);
      }
   }

   static boolean fullyPassablePosition(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      return state.isPathfindable(PathComputationType.LAND);
   }

   static boolean isReplaceable(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
      Block block = state.getBlock();
      if (block instanceof AirBlock) {
         return true;
      } else if (block instanceof SnowLayerBlock) {
         return !bsi.worldContainsLoadedChunk(x, z) ? true : (Integer)state.getValue(SnowLayerBlock.LAYERS) == 1;
      } else {
         return block != Blocks.LARGE_FERN && block != Blocks.TALL_GRASS ? state.canBeReplaced() : true;
      }
   }

   static boolean isDoorPassable(BlockState state, Direction side) {
      boolean open = (Boolean)state.getValue(DoorBlock.OPEN);
      Direction closedFacing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
      Direction openFacing = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT ? closedFacing.getClockWise() : closedFacing.getCounterClockWise();
      return side != (open ? openFacing : closedFacing);
   }

   static boolean isGatePassable(IPlayerContext ctx, BlockPos gatePos, BlockPos playerPos) {
      if (playerPos.equals(gatePos)) {
         return false;
      } else {
         BlockState state = BlockStateInterface.get(ctx, gatePos);
         return !(state.getBlock() instanceof FenceGateBlock) ? true : (Boolean)state.getValue(FenceGateBlock.OPEN);
      }
   }

   static boolean avoidWalkingInto(BlockState state) {
      Block block = state.getBlock();
      return !state.getFluidState().isEmpty()
         || block == Blocks.MAGMA_BLOCK && !Baritone.settings().allowWalkOnMagmaBlocks.value
         || block == Blocks.CACTUS
         || block == Blocks.SWEET_BERRY_BUSH
         || block instanceof BaseFireBlock
         || block == Blocks.END_PORTAL
         || block == Blocks.COBWEB
         || block == Blocks.BUBBLE_COLUMN;
   }

   static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      Ternary canWalkOn = canWalkOnBlockState(state);
      if (canWalkOn == Ternary.YES) {
         return true;
      } else {
         return canWalkOn == Ternary.NO ? false : canWalkOnPosition(bsi, x, y, z, state);
      }
   }

   static Ternary canWalkOnBlockState(BlockState state) {
      Block block = state.getBlock();
      if (isBlockNormalCube(state)
         && (block != Blocks.MAGMA_BLOCK || Baritone.settings().allowWalkOnMagmaBlocks.value)
         && block != Blocks.BUBBLE_COLUMN
         && block != Blocks.HONEY_BLOCK) {
         return Ternary.YES;
      } else if (block instanceof AzaleaBlock) {
         return Ternary.YES;
      } else if (block != Blocks.LADDER && (!isClimbable(block) || !Baritone.settings().allowVines.value)) {
         if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH || block == Blocks.SOUL_SAND) {
            return Ternary.YES;
         } else if (block == Blocks.ENDER_CHEST || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
            return Ternary.YES;
         } else if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
            return Ternary.YES;
         } else if (block instanceof StairBlock) {
            return Ternary.YES;
         } else if (isWater(state)) {
            return Ternary.MAYBE;
         } else if (isLava(state) && Baritone.settings().assumeWalkOnLava.value) {
            return Ternary.MAYBE;
         } else if (!(block instanceof SlabBlock)) {
            return Ternary.NO;
         } else if (Baritone.settings().allowWalkOnBottomSlab.value) {
            return Ternary.YES;
         } else {
            return state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM ? Ternary.YES : Ternary.NO;
         }
      } else {
         return Ternary.YES;
      }
   }

   static boolean canWalkOnPosition(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      Block block = state.getBlock();
      if (!isWater(state)) {
         return isLava(state) && !isFlowing(x, y, z, state, bsi) && Baritone.settings().assumeWalkOnLava.value;
      } else {
         BlockState upState = bsi.get0(x, y + 1, z);
         Block up = upState.getBlock();
         if (up != Blocks.LILY_PAD && !(up instanceof CarpetBlock)) {
            return !isFlowing(x, y, z, state, bsi) && upState.getFluidState().getType() != Fluids.FLOWING_WATER
               ? isWater(upState) ^ Baritone.settings().assumeWalkOnWater.value
               : isWater(upState) && !Baritone.settings().assumeWalkOnWater.value;
         } else {
            return true;
         }
      }
   }

   static boolean canWalkOn(CalculationContext context, int x, int y, int z, BlockState state) {
      return context.precomputedData.canWalkOn(context.bsi, x, y, z, state);
   }

   static boolean canWalkOn(CalculationContext context, int x, int y, int z) {
      return canWalkOn(context, x, y, z, context.get(x, y, z));
   }

   static boolean canWalkOn(IPlayerContext ctx, BetterBlockPos pos, BlockState state) {
      return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, state);
   }

   static boolean canWalkOn(IPlayerContext ctx, BlockPos pos) {
      return canWalkOn(new BlockStateInterface(ctx), pos.getX(), pos.getY(), pos.getZ());
   }

   static boolean canWalkOn(IPlayerContext ctx, BetterBlockPos pos) {
      return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z);
   }

   static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z) {
      return canWalkOn(bsi, x, y, z, bsi.get0(x, y, z));
   }

   static boolean canUseFrostWalker(CalculationContext context, BlockState state) {
      return context.frostWalker != 0 && state == FrostedIceBlock.meltsInto() && (Integer)state.getValue(LiquidBlock.LEVEL) == 0;
   }

   static boolean canUseFrostWalker(IPlayerContext ctx, BlockPos pos) {
      boolean hasFrostWalker = false;

      label32:
      for (EquipmentSlot slot : EquipmentSlot.values()) {
         ItemEnchantments itemEnchantments = ctx.player().getItemBySlot(slot).getEnchantments();

         for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
            if (enchant.is(Enchantments.FROST_WALKER)) {
               hasFrostWalker = true;
               break label32;
            }
         }
      }

      BlockState state = BlockStateInterface.get(ctx, pos);
      return hasFrostWalker && state == FrostedIceBlock.meltsInto() && (Integer)state.getValue(LiquidBlock.LEVEL) == 0;
   }

   static boolean mustBeSolidToWalkOn(CalculationContext context, int x, int y, int z, BlockState state) {
      Block block = state.getBlock();
      if (isClimbable(block)) {
         return false;
      } else {
         if (!state.getFluidState().isEmpty()) {
            if (block instanceof SlabBlock) {
               if (state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
                  return true;
               }
            } else if (block instanceof StairBlock) {
               if (state.getValue(StairBlock.HALF) == Half.TOP) {
                  return true;
               }

               StairsShape shape = (StairsShape)state.getValue(StairBlock.SHAPE);
               if (shape == StairsShape.INNER_LEFT || shape == StairsShape.INNER_RIGHT) {
                  return true;
               }
            } else if (block instanceof TrapDoorBlock) {
               if (!(Boolean)state.getValue(TrapDoorBlock.OPEN) && state.getValue(TrapDoorBlock.HALF) == Half.TOP) {
                  return true;
               }
            } else {
               if (block == Blocks.SCAFFOLDING) {
                  return true;
               }

               if (block instanceof LeavesBlock) {
                  return true;
               }
            }

            if (context.assumeWalkOnWater) {
               return false;
            }

            Block blockAbove = context.getBlock(x, y + 1, z);
            if (blockAbove instanceof LiquidBlock) {
               return false;
            }
         }

         return true;
      }
   }

   static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z) {
      return canPlaceAgainst(bsi, x, y, z, bsi.get0(x, y, z));
   }

   static boolean canPlaceAgainst(BlockStateInterface bsi, BlockPos pos) {
      return canPlaceAgainst(bsi, pos.getX(), pos.getY(), pos.getZ());
   }

   static boolean canPlaceAgainst(IPlayerContext ctx, BlockPos pos) {
      return canPlaceAgainst(new BlockStateInterface(ctx), pos);
   }

   static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      return !bsi.worldBorder.canPlaceAt(x, z)
         ? false
         : isBlockNormalCube(state) || state.getBlock() == Blocks.GLASS || state.getBlock() instanceof StainedGlassBlock;
   }

   static boolean isClimbable(Block block) {
      return block == Blocks.LADDER
         || block == Blocks.VINE
         || block == Blocks.WEEPING_VINES
         || block == Blocks.WEEPING_VINES_PLANT
         || block == Blocks.TWISTING_VINES
         || block == Blocks.TWISTING_VINES_PLANT;
   }

   static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, boolean includeFalling) {
      return getMiningDurationTicks(context, x, y, z, context.get(x, y, z), includeFalling);
   }

   static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, BlockState state, boolean includeFalling) {
      Block block = state.getBlock();
      if (!canWalkThrough(context, x, y, z, state)) {
         if (!state.getFluidState().isEmpty()) {
            return 1000000.0;
         } else {
            double mult = context.breakCostMultiplierAt(x, y, z, state);
            if (mult >= 1000000.0) {
               return 1000000.0;
            } else if (avoidBreaking(context.bsi, x, y, z, state)) {
               return 1000000.0;
            } else {
               double strVsBlock = context.toolSet.getStrVsBlock(state);
               if (strVsBlock <= 0.0) {
                  return 1000000.0;
               } else {
                  double result = 1.0 / strVsBlock;
                  result += context.breakBlockAdditionalCost;
                  result *= mult;
                  if (includeFalling) {
                     BlockState above = context.get(x, y + 1, z);
                     if (above.getBlock() instanceof FallingBlock) {
                        result += getMiningDurationTicks(context, x, y + 1, z, above, true);
                     }
                  }

                  return result;
               }
            }
         }
      } else {
         return 0.0;
      }
   }

   static boolean isBottomSlab(BlockState state) {
      return state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
   }

   static void switchToBestToolFor(IPlayerContext ctx, BlockState b) {
      switchToBestToolFor(ctx, b, new ToolSet(ctx.player()), BaritoneAPI.getSettings().preferSilkTouch.value);
   }

   static void switchToBestToolFor(IPlayerContext ctx, BlockState b, ToolSet ts, boolean preferSilkTouch) {
      if (Baritone.settings().autoTool.value && !Baritone.settings().assumeExternalAutoTool.value) {
         ctx.player().getInventory().selected = ts.getBestSlot(b.getBlock(), preferSilkTouch);
      }
   }

   static void moveTowards(IPlayerContext ctx, MovementState state, BlockPos pos) {
      state.setTarget(
            new MovementState.MovementTarget(
               RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.getBlockPosCenter(pos), ctx.playerRotations())
                  .withPitch(ctx.playerRotations().getPitch()),
               false
            )
         )
         .setInput(Input.MOVE_FORWARD, true);
   }

   static void moveTowardsWithoutRotation(IPlayerContext ctx, MovementState state, float idealYaw) {
      MovementOption.getOptions(
            Mth.sin(ctx.playerRotations().getYaw() * (float) (Math.PI / 180.0)),
            Mth.cos(ctx.playerRotations().getYaw() * (float) (Math.PI / 180.0)),
            Baritone.settings().allowSprint.value
         )
         .min(Comparator.comparing(option -> option.distanceToSq(Mth.sin(idealYaw * (float) (Math.PI / 180.0)), Mth.cos(idealYaw * (float) (Math.PI / 180.0)))))
         .ifPresent(selection -> selection.setInputs(state));
   }

   static void moveTowardsWithoutRotation(IPlayerContext ctx, MovementState state, BlockPos dest) {
      float idealYaw = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.getBlockPosCenter(dest), ctx.playerRotations()).getYaw();
      moveTowardsWithoutRotation(ctx, state, idealYaw);
   }

   static void moveTowardsWithSlightRotation(IPlayerContext ctx, MovementState state, BlockPos dest) {
      float idealYaw = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.getBlockPosCenter(dest), ctx.playerRotations()).getYaw();
      float distance = Rotation.yawDistanceFromOffset(ctx.playerRotations().getYaw(), idealYaw) % 45.0F;
      float newYaw = distance > 0.0F ? (distance > 22.5F ? distance - 45.0F : distance) : (distance < -22.5F ? distance + 45.0F : distance);
      state.setTarget(new MovementState.MovementTarget(new Rotation(ctx.playerRotations().getYaw() - newYaw, ctx.playerRotations().getPitch()), true));
      moveTowardsWithoutRotation(ctx, state, idealYaw);
   }

   static boolean isWater(BlockState state) {
      Fluid f = state.getFluidState().getType();
      return f == Fluids.WATER || f == Fluids.FLOWING_WATER;
   }

   static boolean isWater(IPlayerContext ctx, BlockPos bp) {
      return isWater(BlockStateInterface.get(ctx, bp));
   }

   static boolean isLava(BlockState state) {
      Fluid f = state.getFluidState().getType();
      return f == Fluids.LAVA || f == Fluids.FLOWING_LAVA;
   }

   static boolean isLava(IPlayerContext ctx, BlockPos bp) {
      return isLava(BlockStateInterface.get(ctx, bp));
   }

   static boolean isLiquid(IPlayerContext ctx, BlockPos p) {
      return isLiquid(BlockStateInterface.get(ctx, p));
   }

   static boolean isLiquid(BlockState blockState) {
      return !blockState.getFluidState().isEmpty();
   }

   static boolean possiblyFlowing(BlockState state) {
      FluidState fluidState = state.getFluidState();
      return fluidState.getType() instanceof FlowingFluid && fluidState.getType().getAmount(fluidState) != 8;
   }

   static boolean isFlowing(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
      FluidState fluidState = state.getFluidState();
      if (!(fluidState.getType() instanceof FlowingFluid)) {
         return false;
      } else {
         return fluidState.getType().getAmount(fluidState) != 8
            ? true
            : possiblyFlowing(bsi.get0(x + 1, y, z))
               || possiblyFlowing(bsi.get0(x - 1, y, z))
               || possiblyFlowing(bsi.get0(x, y, z + 1))
               || possiblyFlowing(bsi.get0(x, y, z - 1));
      }
   }

   static boolean isBlockNormalCube(BlockState state) {
      Block block = state.getBlock();
      if (!(block instanceof BambooStalkBlock)
         && !(block instanceof MovingPistonBlock)
         && !(block instanceof ScaffoldingBlock)
         && !(block instanceof ShulkerBoxBlock)
         && !(block instanceof PointedDripstoneBlock)
         && !(block instanceof AmethystClusterBlock)) {
         try {
            return Block.isShapeFullBlock(state.getCollisionShape(null, null));
         } catch (Exception var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   static boolean openDoors(IPlayerContext ctx, MovementState state, BetterBlockPos from, BetterBlockPos to) {
      Direction direction = Stream.of(Direction.values()).filter(d -> from.relative(d).equals(to)).findFirst().get();

      for (BetterBlockPos pos : new BetterBlockPos[]{from, to, from.above(), to.above()}) {
         Direction side = !pos.equals(to) && !pos.equals(to.above()) ? direction.getOpposite() : direction;
         BlockState door = BlockStateInterface.get(ctx, pos);
         if (DoorBlock.isWoodenDoor(door) && !isDoorPassable(door, side)) {
            state.setTarget(
                  new MovementState.MovementTarget(
                     RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.calculateBlockCenter(ctx.world(), pos), ctx.playerRotations()), true
                  )
               )
               .setInput(Input.CLICK_RIGHT, true);
            return false;
         }
      }

      return true;
   }

   static MovementHelper.PlaceResult attemptToPlaceABlock(MovementState state, IBaritone baritone, BlockPos placeAt, boolean preferDown, boolean wouldSneak) {
      IPlayerContext ctx = baritone.getPlayerContext();
      Optional<Rotation> direct = RotationUtils.reachable(ctx, placeAt, wouldSneak);
      boolean found = false;
      if (direct.isPresent()) {
         state.setTarget(new MovementState.MovementTarget(direct.get(), true));
         found = true;
      }

      int i = 0;

      while (true) {
         label81: {
            if (i < 5) {
               BlockPos against1 = placeAt.relative(Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i]);
               if (!canPlaceAgainst(ctx, against1)) {
                  break label81;
               }

               if (!((Baritone)baritone).getInventoryBehavior().selectThrowawayForLocation(false, placeAt.getX(), placeAt.getY(), placeAt.getZ())) {
                  Helper.HELPER.logDebug("bb pls get me some blocks. dirt, netherrack, cobble");
                  state.setStatus(MovementStatus.UNREACHABLE);
                  return MovementHelper.PlaceResult.NO_OPTION;
               }

               double faceX = ((double)(placeAt.getX() + against1.getX()) + 1.0) * 0.5;
               double faceY = ((double)(placeAt.getY() + against1.getY()) + 0.5) * 0.5;
               double faceZ = ((double)(placeAt.getZ() + against1.getZ()) + 1.0) * 0.5;
               Rotation place = RotationUtils.calcRotationFromVec3d(
                  wouldSneak ? RayTraceUtils.inferSneakingEyePosition(ctx.player()) : ctx.playerHead(), new Vec3(faceX, faceY, faceZ), ctx.playerRotations()
               );
               Rotation actual = baritone.getLookBehavior().getAimProcessor().peekRotation(place);
               HitResult res = RayTraceUtils.rayTraceTowards(ctx.player(), actual, ctx.playerController().getBlockReachDistance(), wouldSneak);
               if (res == null
                  || res.getType() != Type.BLOCK
                  || !((BlockHitResult)res).getBlockPos().equals(against1)
                  || !((BlockHitResult)res).getBlockPos().relative(((BlockHitResult)res).getDirection()).equals(placeAt)) {
                  break label81;
               }

               state.setTarget(new MovementState.MovementTarget(place, true));
               found = true;
               if (preferDown) {
                  break label81;
               }
            }

            if (ctx.getSelectedBlock().isPresent()) {
               BlockPos selectedBlock = ctx.getSelectedBlock().get();
               Direction side = ((BlockHitResult)ctx.objectMouseOver()).getDirection();
               if (selectedBlock.equals(placeAt) || canPlaceAgainst(ctx, selectedBlock) && selectedBlock.relative(side).equals(placeAt)) {
                  if (wouldSneak) {
                     state.setInput(Input.SNEAK, true);
                  }

                  ((Baritone)baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
                  return MovementHelper.PlaceResult.READY_TO_PLACE;
               }
            }

            if (found) {
               if (wouldSneak) {
                  state.setInput(Input.SNEAK, true);
               }

               ((Baritone)baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
               return MovementHelper.PlaceResult.ATTEMPTING;
            }

            return MovementHelper.PlaceResult.NO_OPTION;
         }

         i++;
      }
   }

   static boolean isTransparent(Block b) {
      return b instanceof AirBlock || b == Blocks.LAVA || b == Blocks.WATER;
   }

   static List<BetterBlockPos> steppingOnBlocks(IPlayerContext ctx) {
      List<BetterBlockPos> blocks = new ArrayList<>();

      for (byte x = -1; x <= 1; x++) {
         for (byte z = -1; z <= 1; z++) {
            if (ctx.player()
               .getBoundingBox()
               .intersects(
                  Vec3.atLowerCornerOf(ctx.player().blockPosition()).add((double)x, 0.0, (double)z),
                  Vec3.atLowerCornerOf(ctx.player().blockPosition()).add((double)(x + 1), 1.0, (double)(z + 1))
               )) {
               blocks.add(new BetterBlockPos(ctx.player().getBlockX() + x, ctx.player().getBlockY() - 1, ctx.player().getBlockZ() + z));
            }
         }
      }

      return blocks;
   }

   static boolean canAutoStep(IPlayerContext ctx, double targetHeight) {
      if (Baritone.settings().assumeStep.value) {
         return true;
      }
      try {
         if (ctx.player() != null) {
            float step = ctx.player().maxUpStep();
            if ((double) step >= targetHeight - 0.05) {
               return true;
            }
            double attr = ctx.player().getAttributeValue(Attributes.STEP_HEIGHT);
            if (attr >= targetHeight - 0.05) {
               return true;
            }
         }
      } catch (Exception ignored) {}
      try {
         Step stepMod = Modules.get().get(Step.class);
         if (stepMod != null && stepMod.isActive() && stepMod.height.get() >= targetHeight - 0.05) {
            return true;
         }
      } catch (Exception ignored) {}
      return false;
   }

   static double getJumpMultiplier(IPlayerContext ctx) {
      double mult = 1.0;
      try {
         if (ctx.player() != null && ctx.player().hasEffect(MobEffects.JUMP)) {
            int amp = ctx.player().getEffect(MobEffects.JUMP).getAmplifier();
            mult += (amp + 1) * 0.25;
         }
      } catch (Exception ignored) {}
      try {
         HighJump highJump = Modules.get().get(HighJump.class);
         if (highJump != null && highJump.isActive()) {
            mult *= highJump.getMultiplier();
         }
      } catch (Exception ignored) {}
      return Math.max(0.1, mult);
   }

   static double getEffectiveJumpHeight(IPlayerContext ctx) {
      double mult = getJumpMultiplier(ctx);
      return 1.25 * mult * mult;
   }

   static boolean hasArrivedHorizontally(IPlayerContext ctx, BlockPos dest, double extraMargin) {
      if (ctx.player() == null) {
         return false;
      }
      double hSpeed = ctx.player().getDeltaMovement().horizontalDistance();
      double tolerance = Math.max(0.25, hSpeed * 0.7) + extraMargin;
      double dx = Math.abs(ctx.player().position().x - ((double) dest.getX() + 0.5));
      double dz = Math.abs(ctx.player().position().z - ((double) dest.getZ() + 0.5));
      return Math.max(dx, dz) <= tolerance;
   }

   static boolean isCrossingDestination(IPlayerContext ctx, BlockPos dest) {
      if (ctx.player() == null) {
         return false;
      }
      Vec3 pos = ctx.player().position();
      Vec3 vel = ctx.player().getDeltaMovement();
      double destCenterX = (double) dest.getX() + 0.5;
      double destCenterZ = (double) dest.getZ() + 0.5;
      double currDistSq = (pos.x - destCenterX) * (pos.x - destCenterX) + (pos.z - destCenterZ) * (pos.z - destCenterZ);
      double nextX = pos.x + vel.x;
      double nextZ = pos.z + vel.z;
      double nextDistSq = (nextX - destCenterX) * (nextX - destCenterX) + (nextZ - destCenterZ) * (nextZ - destCenterZ);
      return currDistSq < 0.64 && nextDistSq > currDistSq;
   }

   static boolean isSpeedActive() {
      try {
         return Modules.get().isActive(Speed.class);
      } catch (Exception ignored) {
         return false;
      }
   }

   static boolean isNoFallActive() {
      try {
         return Modules.get().isActive(NoFall.class);
      } catch (Exception ignored) {
         return false;
      }
   }

   static boolean isSprintActive() {
      try {
         return Modules.get().isActive(Sprint.class);
      } catch (Exception ignored) {
         return false;
      }
   }

   public static enum PlaceResult {
      READY_TO_PLACE,
      ATTEMPTING,
      NO_OPTION;
   }
}
