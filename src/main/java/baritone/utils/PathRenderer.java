package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.event.events.RenderEvent;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalInverted;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.behavior.PathingBehavior;
import baritone.pathing.path.PathExecutor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class PathRenderer implements IRenderer {
   private static final ResourceLocation TEXTURE_BEACON_BEAM = ResourceLocation.parse("textures/entity/beacon_beam.png");

   private PathRenderer() {
   }

   public static double posX() {
      return renderManager.renderPosX();
   }

   public static double posY() {
      return renderManager.renderPosY();
   }

   public static double posZ() {
      return renderManager.renderPosZ();
   }

   public static void render(RenderEvent event, PathingBehavior behavior) {
      IPlayerContext ctx = behavior.ctx;
      if (ctx.world() != null) {
         if (ctx.minecraft().screen instanceof GuiClick) {
            ((GuiClick)ctx.minecraft().screen).onRender(event.getModelViewStack(), event.getProjectionMatrix());
         }

         float partialTicks = event.getPartialTicks();
         Goal goal = behavior.getGoal();
         DimensionType thisPlayerDimension = ctx.world().dimensionType();
         DimensionType currentRenderViewDimension = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().world().dimensionType();
         if (thisPlayerDimension == currentRenderViewDimension) {
            if (goal != null && settings.renderGoal.value) {
               drawGoal(event.getModelViewStack(), ctx, goal, partialTicks, settings.colorGoalBox.value);
            }

            if (settings.renderPath.value) {
               PathExecutor current = behavior.getCurrent();
               PathExecutor next = behavior.getNext();
               if (current != null && settings.renderSelectionBoxes.value) {
                  drawManySelectionBoxes(event.getModelViewStack(), ctx.player(), current.toBreak(), settings.colorBlocksToBreak.value);
                  drawManySelectionBoxes(event.getModelViewStack(), ctx.player(), current.toPlace(), settings.colorBlocksToPlace.value);
                  drawManySelectionBoxes(event.getModelViewStack(), ctx.player(), current.toWalkInto(), settings.colorBlocksToWalkInto.value);
               }

               if (current != null && current.getPath() != null) {
                  int renderBegin = Math.max(current.getPosition() - 3, 0);
                  drawPath(
                     event.getModelViewStack(), current.getPath().positions(), renderBegin, settings.colorCurrentPath.value, settings.fadePath.value, 10, 20
                  );
                  if (settings.renderPathWaypoints.value) {
                     drawPathWaypoints(event.getModelViewStack(), current.getPath().positions(), renderBegin, settings.colorCurrentPath.value);
                  }
               }

               if (next != null && next.getPath() != null) {
                  drawPath(event.getModelViewStack(), next.getPath().positions(), 0, settings.colorNextPath.value, settings.fadePath.value, 10, 20);
                  if (settings.renderPathWaypoints.value) {
                     drawPathWaypoints(event.getModelViewStack(), next.getPath().positions(), 0, settings.colorNextPath.value);
                  }
               }

               if (settings.renderScannedNodes.value) {
                  baritone.pathing.calc.ScannedNodeTracker tracker = baritone.pathing.calc.ScannedNodeTracker.getInstance();
                  long fadeDuration = settings.scannedNodesFadeTimeMS.value.longValue();
                  if (tracker.isVisible(fadeDuration)) {
                     float alpha = tracker.getAlpha(fadeDuration);
                     List<BetterBlockPos> scannedNodes = tracker.getSnapshot(settings.scannedNodesLimit.value);
                     drawScannedNodes(event.getModelViewStack(), scannedNodes, settings.colorScannedNodes.value, alpha);
                  }
               }

               behavior.getInProgress()
                  .ifPresent(
                     currentlyRunning -> {
                        currentlyRunning.bestPathSoFar()
                           .ifPresent(
                              p -> drawPath(event.getModelViewStack(), p.positions(), 0, settings.colorBestPathSoFar.value, settings.fadePath.value, 10, 20)
                           );
                        currentlyRunning.pathToMostRecentNodeConsidered()
                           .ifPresent(
                              mr -> {
                                 if (settings.renderMostRecentConsidered.value) {
                                    drawPath(
                                       event.getModelViewStack(), mr.positions(), 0, settings.colorMostRecentConsidered.value, settings.fadePath.value, 10, 20
                                    );
                                    drawManySelectionBoxes(
                                       event.getModelViewStack(), ctx.player(), Collections.singletonList(mr.getDest()), settings.colorMostRecentConsidered.value
                                    );
                                 }
                              }
                           );
                     }
                  );
            }
         }
      }
   }

   public static void drawPath(PoseStack stack, List<BetterBlockPos> positions, int startIndex, Color color, boolean fadeOut, int fadeStart0, int fadeEnd0) {
      drawPath(stack, positions, startIndex, color, fadeOut, fadeStart0, fadeEnd0, 0.5);
   }

   public static void drawPath(
      PoseStack stack, List<BetterBlockPos> positions, int startIndex, Color color, boolean fadeOut, int fadeStart0, int fadeEnd0, double offset
   ) {
      BufferBuilder bufferBuilder = IRenderer.startLines(color, settings.pathRenderLineWidthPixels.value, settings.renderPathIgnoreDepth.value);
      int fadeStart = fadeStart0 + startIndex;
      int fadeEnd = fadeEnd0 + startIndex;
      int i = startIndex;

      while (i < positions.size() - 1) {
         BetterBlockPos start = positions.get(i);
         int next;
         BetterBlockPos end = positions.get(next = i + 1);
         int dirX = end.x - start.x;
         int dirY = end.y - start.y;
         int dirZ = end.z - start.z;

         while (
            next + 1 < positions.size()
               && (!fadeOut || next + 1 < fadeStart)
               && dirX == positions.get(next + 1).x - end.x
               && dirY == positions.get(next + 1).y - end.y
               && dirZ == positions.get(next + 1).z - end.z
         ) {
            end = positions.get(++next);
         }

         if (fadeOut) {
            float alpha;
            if (i <= fadeStart) {
               alpha = 0.4F;
            } else {
               if (i > fadeEnd) {
                  break;
               }

               alpha = 0.4F * (1.0F - (float)(i - fadeStart) / (float)(fadeEnd - fadeStart));
            }

            IRenderer.glColor(color, alpha);
         }

         emitPathLine(bufferBuilder, stack, (double)start.x, (double)start.y, (double)start.z, (double)end.x, (double)end.y, (double)end.z, offset);
         i = next;
      }

      IRenderer.endLines(bufferBuilder, settings.renderPathIgnoreDepth.value);
   }

   private static void emitPathLine(
      BufferBuilder bufferBuilder, PoseStack stack, double x1, double y1, double z1, double x2, double y2, double z2, double offset
   ) {
      double extraOffset = offset + 0.03;
      double vpX = posX();
      double vpY = posY();
      double vpZ = posZ();
      boolean renderPathAsFrickinThingy = !settings.renderPathAsLine.value;
      IRenderer.emitLine(bufferBuilder, stack, x1 + offset - vpX, y1 + offset - vpY, z1 + offset - vpZ, x2 + offset - vpX, y2 + offset - vpY, z2 + offset - vpZ);
      if (renderPathAsFrickinThingy) {
         IRenderer.emitLine(
            bufferBuilder, stack, x2 + offset - vpX, y2 + offset - vpY, z2 + offset - vpZ, x2 + offset - vpX, y2 + extraOffset - vpY, z2 + offset - vpZ
         );
         IRenderer.emitLine(
            bufferBuilder, stack, x2 + offset - vpX, y2 + extraOffset - vpY, z2 + offset - vpZ, x1 + offset - vpX, y1 + extraOffset - vpY, z1 + offset - vpZ
         );
         IRenderer.emitLine(
            bufferBuilder, stack, x1 + offset - vpX, y1 + extraOffset - vpY, z1 + offset - vpZ, x1 + offset - vpX, y1 + offset - vpY, z1 + offset - vpZ
         );
      }
   }

   public static void drawManySelectionBoxes(PoseStack stack, Entity player, Collection<BlockPos> positions, Color color) {
      BufferBuilder bufferBuilder = IRenderer.startLines(color, settings.pathRenderLineWidthPixels.value, settings.renderSelectionBoxesIgnoreDepth.value);
      BlockStateInterface bsi = new BlockStateInterface(BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext());
      positions.forEach(pos -> {
         BlockState state = bsi.get0(pos);
         VoxelShape shape = state.getShape(player.level(), pos);
         AABB toDraw = shape.isEmpty() ? Shapes.block().bounds() : shape.bounds();
         toDraw = toDraw.move(pos);
         IRenderer.emitAABB(bufferBuilder, stack, toDraw, 0.002);
      });
      IRenderer.endLines(bufferBuilder, settings.renderSelectionBoxesIgnoreDepth.value);
   }

   public static void drawScannedNodes(PoseStack stack, List<BetterBlockPos> nodes, Color color, float alpha) {
      if (nodes == null || nodes.isEmpty() || alpha <= 0.01F) return;
      BufferBuilder bufferBuilder = IRenderer.startLines(color, alpha * 0.7F, 1.5F, settings.renderPathIgnoreDepth.value);
      for (BetterBlockPos pos : nodes) {
         AABB aabb = new AABB(pos.x + 0.35, pos.y + 0.1, pos.z + 0.35, pos.x + 0.65, pos.y + 0.4, pos.z + 0.65);
         IRenderer.emitAABB(bufferBuilder, stack, aabb);
      }
      IRenderer.endLines(bufferBuilder, settings.renderPathIgnoreDepth.value);
   }

   public static void drawPathWaypoints(PoseStack stack, List<BetterBlockPos> positions, int startIndex, Color color) {
      if (positions == null || positions.isEmpty()) return;
      BufferBuilder bufferBuilder = IRenderer.startLines(color, 0.85F, 2.0F, settings.renderPathIgnoreDepth.value);
      for (int i = startIndex; i < positions.size(); i++) {
         BetterBlockPos pos = positions.get(i);
         AABB aabb = new AABB(pos.x + 0.38, pos.y + 0.38, pos.z + 0.38, pos.x + 0.62, pos.y + 0.62, pos.z + 0.62);
         IRenderer.emitAABB(bufferBuilder, stack, aabb);
      }
      IRenderer.endLines(bufferBuilder, settings.renderPathIgnoreDepth.value);
   }

   public static void drawGoal(PoseStack stack, IPlayerContext ctx, Goal goal, float partialTicks, Color color) {
      drawGoal(null, stack, ctx, goal, partialTicks, color, true);
   }

   private static void drawGoal(
      @Nullable BufferBuilder bufferBuilder, PoseStack stack, IPlayerContext ctx, Goal goal, float partialTicks, Color color, boolean setupRender
   ) {
      if (!setupRender && bufferBuilder == null) {
         throw new RuntimeException("BufferBuilder must not be null if setupRender is false");
      } else {
         double renderPosX = posX();
         double renderPosY = posY();
         double renderPosZ = posZ();
         double y;
         if (!settings.renderGoalAnimated.value) {
            y = 0.999F;
         } else {
            y = (double)Mth.cos((float)((double)((float)(System.nanoTime() / 100000L % 20000L) / 20000.0F) * Math.PI * 2.0));
         }

         if (goal instanceof IGoalRenderPos) {
            BlockPos goalPos = ((IGoalRenderPos)goal).getGoalPos();
            double minX = (double)goalPos.getX() + 0.002 - renderPosX;
            double maxX = (double)(goalPos.getX() + 1) - 0.002 - renderPosX;
            double minZ = (double)goalPos.getZ() + 0.002 - renderPosZ;
            double maxZ = (double)(goalPos.getZ() + 1) - 0.002 - renderPosZ;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
               y /= 2.0;
            }

            double y1 = 1.0 + y + (double)goalPos.getY() - renderPosY;
            double y2 = 1.0 - y + (double)goalPos.getY() - renderPosY;
            double minY = (double)goalPos.getY() - renderPosY;
            double maxY = minY + 2.0;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
               y1 -= 0.5;
               y2 -= 0.5;
               maxY--;
            }

            drawDankLitGoalBox(bufferBuilder, stack, color, minX, maxX, minZ, maxZ, minY, maxY, y1, y2, setupRender);
         } else if (goal instanceof GoalXZ goalPosx) {
            double minY = (double)ctx.world().getMinBuildHeight();
            double maxY = (double)ctx.world().getMaxBuildHeight();
            if (settings.renderGoalXZBeacon.value) {
               textureManager.bindForSetup(TEXTURE_BEACON_BEAM);
               if (settings.renderGoalIgnoreDepth.value) {
                  RenderSystem.disableDepthTest();
               }

               stack.pushPose();
               stack.translate((double)goalPosx.getX() - renderPosX, -renderPosY, (double)goalPosx.getZ() - renderPosZ);
               BeaconRenderer.renderBeaconBeam(
                  stack,
                  ctx.minecraft().renderBuffers().bufferSource(),
                  TEXTURE_BEACON_BEAM,
                  settings.renderGoalAnimated.value ? partialTicks : 0.0F,
                  1.0F,
                  settings.renderGoalAnimated.value ? ctx.world().getGameTime() : 0L,
                  (int)minY,
                  (int)maxY,
                  color.getRGB(),
                  0.2F,
                  0.25F
               );
               stack.popPose();
               if (settings.renderGoalIgnoreDepth.value) {
                  RenderSystem.enableDepthTest();
               }

               return;
            }

            double minXx = (double)goalPosx.getX() + 0.002 - renderPosX;
            double maxXx = (double)(goalPosx.getX() + 1) - 0.002 - renderPosX;
            double minZx = (double)goalPosx.getZ() + 0.002 - renderPosZ;
            double maxZx = (double)(goalPosx.getZ() + 1) - 0.002 - renderPosZ;
            double y1 = 0.0;
            double y2 = 0.0;
            minY -= renderPosY;
            maxY -= renderPosY;
            drawDankLitGoalBox(bufferBuilder, stack, color, minXx, maxXx, minZx, maxZx, minY, maxY, y1, y2, setupRender);
         } else if (goal instanceof GoalComposite) {
            boolean batch = Arrays.stream(((GoalComposite)goal).goals()).allMatch(IGoalRenderPos.class::isInstance);
            BufferBuilder buf = bufferBuilder;
            if (batch) {
               buf = IRenderer.startLines(color, settings.goalRenderLineWidthPixels.value, settings.renderGoalIgnoreDepth.value);
            }

            for (Goal g : ((GoalComposite)goal).goals()) {
               drawGoal(buf, stack, ctx, g, partialTicks, color, !batch);
            }

            if (batch) {
               IRenderer.endLines(buf, settings.renderGoalIgnoreDepth.value);
            }
         } else if (goal instanceof GoalInverted) {
            drawGoal(stack, ctx, ((GoalInverted)goal).origin, partialTicks, settings.colorInvertedGoalBox.value);
         } else if (goal instanceof GoalYLevel goalpos) {
            double minXx = ctx.player().position().x - settings.yLevelBoxSize.value - renderPosX;
            double minZx = ctx.player().position().z - settings.yLevelBoxSize.value - renderPosZ;
            double maxXx = ctx.player().position().x + settings.yLevelBoxSize.value - renderPosX;
            double maxZx = ctx.player().position().z + settings.yLevelBoxSize.value - renderPosZ;
            double minY = (double)((GoalYLevel)goal).level - renderPosY;
            double maxY = minY + 2.0;
            double y1 = 1.0 + y + (double)goalpos.level - renderPosY;
            double y2 = 1.0 - y + (double)goalpos.level - renderPosY;
            drawDankLitGoalBox(bufferBuilder, stack, color, minXx, maxXx, minZx, maxZx, minY, maxY, y1, y2, setupRender);
         }
      }
   }

   private static void drawDankLitGoalBox(
      BufferBuilder bufferBuilder,
      PoseStack stack,
      Color colorIn,
      double minX,
      double maxX,
      double minZ,
      double maxZ,
      double minY,
      double maxY,
      double y1,
      double y2,
      boolean setupRender
   ) {
      if (setupRender) {
         bufferBuilder = IRenderer.startLines(colorIn, settings.goalRenderLineWidthPixels.value, settings.renderGoalIgnoreDepth.value);
      }

      renderHorizontalQuad(bufferBuilder, stack, minX, maxX, minZ, maxZ, y1);
      renderHorizontalQuad(bufferBuilder, stack, minX, maxX, minZ, maxZ, y2);

      for (double y = minY; y < maxY; y += 16.0) {
         double max = Math.min(maxY, y + 16.0);
         IRenderer.emitLine(bufferBuilder, stack, minX, y, minZ, minX, max, minZ, 0.0, 1.0, 0.0);
         IRenderer.emitLine(bufferBuilder, stack, maxX, y, minZ, maxX, max, minZ, 0.0, 1.0, 0.0);
         IRenderer.emitLine(bufferBuilder, stack, maxX, y, maxZ, maxX, max, maxZ, 0.0, 1.0, 0.0);
         IRenderer.emitLine(bufferBuilder, stack, minX, y, maxZ, minX, max, maxZ, 0.0, 1.0, 0.0);
      }

      if (setupRender) {
         IRenderer.endLines(bufferBuilder, settings.renderGoalIgnoreDepth.value);
      }
   }

   private static void renderHorizontalQuad(BufferBuilder bufferBuilder, PoseStack stack, double minX, double maxX, double minZ, double maxZ, double y) {
      if (y != 0.0) {
         IRenderer.emitLine(bufferBuilder, stack, minX, y, minZ, maxX, y, minZ, 1.0, 0.0, 0.0);
         IRenderer.emitLine(bufferBuilder, stack, maxX, y, minZ, maxX, y, maxZ, 0.0, 0.0, 1.0);
         IRenderer.emitLine(bufferBuilder, stack, maxX, y, maxZ, minX, y, maxZ, -1.0, 0.0, 0.0);
         IRenderer.emitLine(bufferBuilder, stack, minX, y, maxZ, minX, y, minZ, 0.0, 0.0, -1.0);
      }
   }
}
