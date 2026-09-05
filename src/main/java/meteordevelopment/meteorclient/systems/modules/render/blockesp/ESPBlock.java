package meteordevelopment.meteorclient.systems.modules.render.blockesp;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.Dir;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ESPBlock {
   private static final MutableBlockPos blockPos = new MutableBlockPos();
   private static final BlockESP blockEsp = Modules.get().get(BlockESP.class);
   private static final Color cTop = new Color();
   private static final Color cBottom = new Color();
   private static final Color cNorth = new Color();
   private static final Color cSouth = new Color();
   private static final Color cEast = new Color();
   private static final Color cWest = new Color();
   private static final Color cLine = new Color();

   private static void setShadedColor(Color target, Color base, float multiplier, int alpha) {
      int r = Math.min(255, (int)((float)base.r * multiplier));
      int g = Math.min(255, (int)((float)base.g * multiplier));
      int b = Math.min(255, (int)((float)base.b * multiplier));
      target.set(r, g, b, alpha);
   }
   public static final int FO = 2;
   public static final int FO_RI = 4;
   public static final int RI = 8;
   public static final int BA_RI = 16;
   public static final int BA = 32;
   public static final int BA_LE = 64;
   public static final int LE = 128;
   public static final int FO_LE = 256;
   public static final int TO = 512;
   public static final int TO_FO = 1024;
   public static final int TO_BA = 2048;
   public static final int TO_RI = 4096;
   public static final int TO_LE = 8192;
   public static final int BO = 16384;
   public static final int BO_FO = 32768;
   public static final int BO_BA = 65536;
   public static final int BO_RI = 131072;
   public static final int BO_LE = 262144;
   public static final int[] SIDES = new int[]{2, 32, 128, 8, 512, 16384};
   public final int x;
   public final int y;
   public final int z;
   private BlockState state;
   public int neighbours;
   public ESPGroup group;
   public boolean loaded = true;

   public ESPBlock(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public ESPBlock getSideBlock(int side) {
      return switch (side) {
         case 2 -> blockEsp.getBlock(this.x, this.y, this.z + 1);
         case 8 -> blockEsp.getBlock(this.x + 1, this.y, this.z);
         case 32 -> blockEsp.getBlock(this.x, this.y, this.z - 1);
         case 128 -> blockEsp.getBlock(this.x - 1, this.y, this.z);
         case 512 -> blockEsp.getBlock(this.x, this.y + 1, this.z);
         case 16384 -> blockEsp.getBlock(this.x, this.y - 1, this.z);
         default -> null;
      };
   }

   private void assignGroup() {
      ESPGroup firstGroup = null;

      for (int side : SIDES) {
         if ((this.neighbours & side) == side) {
            ESPBlock neighbour = this.getSideBlock(side);
            if (neighbour != null && neighbour.group != null) {
               if (firstGroup == null) {
                  firstGroup = neighbour.group;
               } else if (firstGroup != neighbour.group) {
                  firstGroup.merge(neighbour.group);
               }
            }
         }
      }

      if (firstGroup == null) {
         firstGroup = blockEsp.newGroup(this.state.getBlock());
      }

      firstGroup.add(this);
   }

   public void update() {
      this.state = MeteorClient.mc.level.getBlockState(blockPos.set(this.x, this.y, this.z));
      this.neighbours = 0;
      if (this.isNeighbour(Direction.SOUTH)) {
         this.neighbours |= 2;
      }

      if (this.isNeighbourDiagonal(1.0, 0.0, 1.0)) {
         this.neighbours |= 4;
      }

      if (this.isNeighbour(Direction.EAST)) {
         this.neighbours |= 8;
      }

      if (this.isNeighbourDiagonal(1.0, 0.0, -1.0)) {
         this.neighbours |= 16;
      }

      if (this.isNeighbour(Direction.NORTH)) {
         this.neighbours |= 32;
      }

      if (this.isNeighbourDiagonal(-1.0, 0.0, -1.0)) {
         this.neighbours |= 64;
      }

      if (this.isNeighbour(Direction.WEST)) {
         this.neighbours |= 128;
      }

      if (this.isNeighbourDiagonal(-1.0, 0.0, 1.0)) {
         this.neighbours |= 256;
      }

      if (this.isNeighbour(Direction.UP)) {
         this.neighbours |= 512;
      }

      if (this.isNeighbourDiagonal(0.0, 1.0, 1.0)) {
         this.neighbours |= 1024;
      }

      if (this.isNeighbourDiagonal(0.0, 1.0, -1.0)) {
         this.neighbours |= 2048;
      }

      if (this.isNeighbourDiagonal(1.0, 1.0, 0.0)) {
         this.neighbours |= 4096;
      }

      if (this.isNeighbourDiagonal(-1.0, 1.0, 0.0)) {
         this.neighbours |= 8192;
      }

      if (this.isNeighbour(Direction.DOWN)) {
         this.neighbours |= 16384;
      }

      if (this.isNeighbourDiagonal(0.0, -1.0, 1.0)) {
         this.neighbours |= 32768;
      }

      if (this.isNeighbourDiagonal(0.0, -1.0, -1.0)) {
         this.neighbours |= 65536;
      }

      if (this.isNeighbourDiagonal(1.0, -1.0, 0.0)) {
         this.neighbours |= 131072;
      }

      if (this.isNeighbourDiagonal(-1.0, -1.0, 0.0)) {
         this.neighbours |= 262144;
      }

      if (this.group == null) {
         this.assignGroup();
      }
   }

   private boolean isNeighbour(Direction dir) {
      blockPos.set(this.x + dir.getStepX(), this.y + dir.getStepY(), this.z + dir.getStepZ());
      BlockState neighbourState = MeteorClient.mc.level.getBlockState(blockPos);
      if (neighbourState.getBlock() != this.state.getBlock()) {
         return false;
      } else {
         VoxelShape cube = Shapes.block();
         VoxelShape shape = this.state.getShape(MeteorClient.mc.level, blockPos);
         VoxelShape neighbourShape = neighbourState.getShape(MeteorClient.mc.level, blockPos);
         if (shape.isEmpty()) {
            shape = cube;
         }

         if (neighbourShape.isEmpty()) {
            neighbourShape = cube;
         }

         switch (dir) {
            case SOUTH:
               if (shape.max(Axis.Z) == 1.0 && neighbourShape.min(Axis.Z) == 0.0) {
                  return true;
               }
               break;
            case NORTH:
               if (shape.min(Axis.Z) == 0.0 && neighbourShape.max(Axis.Z) == 1.0) {
                  return true;
               }
               break;
            case EAST:
               if (shape.max(Axis.X) == 1.0 && neighbourShape.min(Axis.X) == 0.0) {
                  return true;
               }
               break;
            case WEST:
               if (shape.min(Axis.X) == 0.0 && neighbourShape.max(Axis.X) == 1.0) {
                  return true;
               }
               break;
            case UP:
               if (shape.max(Axis.Y) == 1.0 && neighbourShape.min(Axis.Y) == 0.0) {
                  return true;
               }
               break;
            case DOWN:
               if (shape.min(Axis.Y) == 0.0 && neighbourShape.max(Axis.Y) == 1.0) {
                  return true;
               }
         }

         return false;
      }
   }

   private boolean isNeighbourDiagonal(double x, double y, double z) {
      blockPos.set((double)this.x + x, (double)this.y + y, (double)this.z + z);
      return this.state.getBlock() == MeteorClient.mc.level.getBlockState(blockPos).getBlock();
   }

   public void render(Render3DEvent event) {
      double x1 = (double)this.x;
      double y1 = (double)this.y;
      double z1 = (double)this.z;
      double x2 = (double)(this.x + 1);
      double y2 = (double)(this.y + 1);
      double z2 = (double)(this.z + 1);
      VoxelShape shape = this.state.getShape(MeteorClient.mc.level, blockPos);
      if (!shape.isEmpty()) {
         x1 = (double)this.x + shape.min(Axis.X);
         y1 = (double)this.y + shape.min(Axis.Y);
         z1 = (double)this.z + shape.min(Axis.Z);
         x2 = (double)this.x + shape.max(Axis.X);
         y2 = (double)this.y + shape.max(Axis.Y);
         z2 = (double)this.z + shape.max(Axis.Z);
      }

      ESPBlockData blockData = blockEsp.getBlockData(this.state.getBlock());
      ShapeMode shapeMode = blockData.shapeMode;
      Color lineColor = blockData.lineColor;
      Color sideColor = blockData.sideColor;

      // Distance-based depth fade
      double fadeFactor = 1.0;
      if (blockEsp.distanceFade.get()) {
         double cx = (x1 + x2) * 0.5;
         double cy = (y1 + y2) * 0.5;
         double cz = (z1 + z2) * 0.5;
         double dx = cx - event.offsetX;
         double dy = cy - event.offsetY;
         double dz = cz - event.offsetZ;
         double distSq = dx * dx + dy * dy + dz * dz;
         double fadeDist = blockEsp.fadeDistance.get();
         double fadeDistSq = fadeDist * fadeDist;
         if (distSq > fadeDistSq) {
            double dist = Math.sqrt(distSq);
            double maxDist = fadeDist * 2.0;
            if (dist >= maxDist) {
               fadeFactor = 0.25;
            } else {
               fadeFactor = 1.0 - 0.75 * ((dist - fadeDist) / (maxDist - fadeDist));
            }
         }
      }

      // Shading fill alpha
      int sideAlpha = sideColor.a;
      int globalFill = blockEsp.fillOpacity.get();
      if (globalFill > 0) {
         sideAlpha = globalFill;
      }
      if (blockEsp.distanceFade.get()) {
         sideAlpha = Math.max(14, (int) Math.round(sideAlpha * fadeFactor));
      }

      // Line color
      int lineAlpha = lineColor.a;
      if (blockEsp.distanceFade.get()) {
         lineAlpha = Math.max(30, (int) Math.round(lineAlpha * fadeFactor));
      }
      cLine.set(lineColor.r, lineColor.g, lineColor.b, lineAlpha);

      // Directional face lighting
      boolean directional = blockEsp.directionalShading.get();
      float topMult = directional ? 1.00f : 1.0f;
      float southMult = directional ? 0.85f : 1.0f;
      float northMult = directional ? 0.75f : 1.0f;
      float eastMult = directional ? 0.65f : 1.0f;
      float westMult = directional ? 0.60f : 1.0f;
      float bottomMult = directional ? 0.50f : 1.0f;

      setShadedColor(cTop, sideColor, topMult, sideAlpha);
      setShadedColor(cSouth, sideColor, southMult, sideAlpha);
      setShadedColor(cNorth, sideColor, northMult, sideAlpha);
      setShadedColor(cEast, sideColor, eastMult, sideAlpha);
      setShadedColor(cWest, sideColor, westMult, sideAlpha);
      setShadedColor(cBottom, sideColor, bottomMult, sideAlpha);

      // Render wireframe lines
      if (shapeMode.lines() && lineAlpha > 0) {
         if (this.neighbours == 0) {
            event.renderer.boxLines(x1, y1, z1, x2, y2, z2, cLine, 0);
         } else {
            if ((this.neighbours & 128) != 128 && (this.neighbours & 32) != 32
               || (this.neighbours & 128) == 128 && (this.neighbours & 32) == 32 && (this.neighbours & 64) != 64) {
               event.renderer.line(x1, y1, z1, x1, y2, z1, cLine);
            }

            if ((this.neighbours & 128) != 128 && (this.neighbours & 2) != 2
               || (this.neighbours & 128) == 128 && (this.neighbours & 2) == 2 && (this.neighbours & 256) != 256) {
               event.renderer.line(x1, y1, z2, x1, y2, z2, cLine);
            }

            if ((this.neighbours & 8) != 8 && (this.neighbours & 32) != 32
               || (this.neighbours & 8) == 8 && (this.neighbours & 32) == 32 && (this.neighbours & 16) != 16) {
               event.renderer.line(x2, y1, z1, x2, y2, z1, cLine);
            }

            if ((this.neighbours & 8) != 8 && (this.neighbours & 2) != 2
               || (this.neighbours & 8) == 8 && (this.neighbours & 2) == 2 && (this.neighbours & 4) != 4) {
               event.renderer.line(x2, y1, z2, x2, y2, z2, cLine);
            }

            if ((this.neighbours & 32) != 32 && (this.neighbours & 16384) != 16384 || (this.neighbours & 32) != 32 && (this.neighbours & 65536) == 65536) {
               event.renderer.line(x1, y1, z1, x2, y1, z1, cLine);
            }

            if ((this.neighbours & 2) != 2 && (this.neighbours & 16384) != 16384 || (this.neighbours & 2) != 2 && (this.neighbours & 32768) == 32768) {
               event.renderer.line(x1, y1, z2, x2, y1, z2, cLine);
            }

            if ((this.neighbours & 32) != 32 && (this.neighbours & 512) != 512 || (this.neighbours & 32) != 32 && (this.neighbours & 2048) == 2048) {
               event.renderer.line(x1, y2, z1, x2, y2, z1, cLine);
            }

            if ((this.neighbours & 2) != 2 && (this.neighbours & 512) != 512 || (this.neighbours & 2) != 2 && (this.neighbours & 1024) == 1024) {
               event.renderer.line(x1, y2, z2, x2, y2, z2, cLine);
            }

            if ((this.neighbours & 128) != 128 && (this.neighbours & 16384) != 16384 || (this.neighbours & 128) != 128 && (this.neighbours & 262144) == 262144) {
               event.renderer.line(x1, y1, z1, x1, y1, z2, cLine);
            }

            if ((this.neighbours & 8) != 8 && (this.neighbours & 16384) != 16384 || (this.neighbours & 8) != 8 && (this.neighbours & 131072) == 131072) {
               event.renderer.line(x2, y1, z1, x2, y1, z2, cLine);
            }

            if ((this.neighbours & 128) != 128 && (this.neighbours & 512) != 512 || (this.neighbours & 128) != 128 && (this.neighbours & 8192) == 8192) {
               event.renderer.line(x1, y2, z1, x1, y2, z2, cLine);
            }

            if ((this.neighbours & 8) != 8 && (this.neighbours & 512) != 512 || (this.neighbours & 8) != 8 && (this.neighbours & 4096) == 4096) {
               event.renderer.line(x2, y2, z1, x2, y2, z2, cLine);
            }
         }
      }

      // Render shaded sides
      if (shapeMode.sides() && sideAlpha > 0) {
         int excludeDir = 0;
         if ((this.neighbours & 512) == 512) excludeDir |= Dir.UP;
         if ((this.neighbours & 16384) == 16384) excludeDir |= Dir.DOWN;
         if ((this.neighbours & 32) == 32) excludeDir |= Dir.NORTH;
         if ((this.neighbours & 2) == 2) excludeDir |= Dir.SOUTH;
         if ((this.neighbours & 128) == 128) excludeDir |= Dir.WEST;
         if ((this.neighbours & 8) == 8) excludeDir |= Dir.EAST;

         if (blockEsp.cullBackfaces.get()) {
            double camX = event.offsetX;
            double camY = event.offsetY;
            double camZ = event.offsetZ;
            boolean inside = camX >= x1 && camX <= x2 && camY >= y1 && camY <= y2 && camZ >= z1 && camZ <= z2;
            if (!inside) {
               if (camY < y2) excludeDir |= Dir.UP;
               if (camY > y1) excludeDir |= Dir.DOWN;
               if (camZ > z1) excludeDir |= Dir.NORTH;
               if (camZ < z2) excludeDir |= Dir.SOUTH;
               if (camX > x1) excludeDir |= Dir.WEST;
               if (camX < x2) excludeDir |= Dir.EAST;
            }
         }

         event.renderer.boxSides(x1, y1, z1, x2, y2, z2, cTop, cBottom, cNorth, cSouth, cEast, cWest, excludeDir);
      }
   }

   public static long getKey(int x, int y, int z) {
      return (long)y << 16 | (long)(z & 15) << 8 | (long)(x & 15);
   }

   public static long getKey(BlockPos blockPos) {
      return getKey(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }
}
