package meteordevelopment.meteorclient.systems.modules.movement;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.events.entity.player.CanWalkOnFluidEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.LivingEntityAccessor;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Jesus extends Module {
   private final SettingGroup sgGeneral = this.settings.createGroup("General");
   private final SettingGroup sgWater = this.settings.createGroup("Water");
   private final SettingGroup sgLava = this.settings.createGroup("Lava");
   private final Setting<Boolean> powderSnow = this.sgGeneral
      .add(new BoolSetting.Builder().name("powder-snow").description("Walk on powder snow.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Jesus.Mode> waterMode = this.sgWater
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode")).description("How to treat the water."))
               .defaultValue(Jesus.Mode.Solid))
            .build()
      );
   private final Setting<Boolean> dipIfBurning = this.sgWater
      .add(
         new BoolSetting.Builder()
            .name("dip-if-burning")
            .description("Lets you go into the water when you are burning.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.waterMode.get() == Jesus.Mode.Solid)
            .build()
      );
   private final Setting<Boolean> dipOnSneakWater = this.sgWater
      .add(
         new BoolSetting.Builder()
            .name("dip-on-sneak")
            .description("Lets you go into the water when your sneak key is held.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.waterMode.get() == Jesus.Mode.Solid)
            .build()
      );
   private final Setting<Boolean> dipOnFallWater = this.sgWater
      .add(
         new BoolSetting.Builder()
            .name("dip-on-fall")
            .description("Lets you go into the water when you fall over a certain height.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.waterMode.get() == Jesus.Mode.Solid)
            .build()
      );
   private final Setting<Integer> dipFallHeightWater = this.sgWater
      .add(
         new IntSetting.Builder()
            .name("dip-fall-height")
            .description("The fall height at which you will go into the water.")
            .defaultValue(Integer.valueOf(4))
            .range(1, 255)
            .sliderRange(3, 20)
            .visible(() -> this.waterMode.get() == Jesus.Mode.Solid && this.dipOnFallWater.get())
            .build()
      );
   private final Setting<Jesus.Mode> lavaMode = this.sgLava
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode")).description("How to treat the lava."))
               .defaultValue(Jesus.Mode.Solid))
            .build()
      );
   private final Setting<Boolean> dipIfFireResistant = this.sgLava
      .add(
         new BoolSetting.Builder()
            .name("dip-if-resistant")
            .description("Lets you go into the lava if you have Fire Resistance effect.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.lavaMode.get() == Jesus.Mode.Solid)
            .build()
      );
   private final Setting<Boolean> dipOnSneakLava = this.sgLava
      .add(
         new BoolSetting.Builder()
            .name("dip-on-sneak")
            .description("Lets you go into the lava when your sneak key is held.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.lavaMode.get() == Jesus.Mode.Solid)
            .build()
      );
   private final Setting<Boolean> dipOnFallLava = this.sgLava
      .add(
         new BoolSetting.Builder()
            .name("dip-on-fall")
            .description("Lets you go into the lava when you fall over a certain height.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.lavaMode.get() == Jesus.Mode.Solid)
            .build()
      );
   private final Setting<Integer> dipFallHeightLava = this.sgLava
      .add(
         new IntSetting.Builder()
            .name("dip-fall-height")
            .description("The fall height at which you will go into the lava.")
            .defaultValue(Integer.valueOf(4))
            .range(1, 255)
            .sliderRange(3, 20)
            .visible(() -> this.lavaMode.get() == Jesus.Mode.Solid && this.dipOnFallLava.get())
            .build()
      );
   private final MutableBlockPos blockPos = new MutableBlockPos();
   private int tickTimer = 10;
   private int packetTimer = 0;
   private boolean prePathManagerWalkOnWater;
   private boolean prePathManagerWalkOnLava;
   public boolean isInBubbleColumn = false;

   public Jesus() {
      super(Categories.Movement, "jesus", "Walk on liquids and powder snow like Jesus.");
   }

   @Override
   public void onActivate() {
      this.prePathManagerWalkOnWater = PathManagers.get().getSettings().getWalkOnWater().get();
      this.prePathManagerWalkOnLava = PathManagers.get().getSettings().getWalkOnLava().get();
      PathManagers.get().getSettings().getWalkOnWater().set(this.waterMode.get() == Jesus.Mode.Solid);
      PathManagers.get().getSettings().getWalkOnLava().set(this.lavaMode.get() == Jesus.Mode.Solid);
   }

   @Override
   public void onDeactivate() {
      PathManagers.get().getSettings().getWalkOnWater().set(this.prePathManagerWalkOnWater);
      PathManagers.get().getSettings().getWalkOnLava().set(this.prePathManagerWalkOnLava);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      boolean bubbleColumn = this.isInBubbleColumn;
      this.isInBubbleColumn = false;
      if (this.waterMode.get() == Jesus.Mode.Bob && this.mc.player.isInWater() || this.lavaMode.get() == Jesus.Mode.Bob && this.mc.player.isInLava()) {
         double fluidHeight;
         if (this.mc.player.isInLava()) {
            fluidHeight = this.mc.player.getFluidHeight(FluidTags.LAVA);
         } else {
            fluidHeight = this.mc.player.getFluidHeight(FluidTags.WATER);
         }

         double swimHeight = this.mc.player.getFluidJumpThreshold();
         if (this.mc.player.isInWater() && fluidHeight > swimHeight) {
            ((LivingEntityAccessor)this.mc.player).swimUpwards(FluidTags.WATER);
         } else if (this.mc.player.onGround() && fluidHeight <= swimHeight && ((LivingEntityAccessor)this.mc.player).getJumpCooldown() == 0) {
            this.mc.player.jumpFromGround();
            ((LivingEntityAccessor)this.mc.player).setJumpCooldown(10);
         } else {
            ((LivingEntityAccessor)this.mc.player).swimUpwards(FluidTags.LAVA);
         }
      }

      if (!this.mc.player.isInWater() || this.waterShouldBeSolid()) {
         if (!this.mc.player.isVisuallySwimming()) {
            if (!this.mc.player.isInLava() || this.lavaShouldBeSolid()) {
               if (bubbleColumn) {
                  if (this.mc.options.keyJump.isDown() && this.mc.player.getDeltaMovement().y() < 0.11) {
                     ((IVec3d)this.mc.player.getDeltaMovement()).setY(0.11);
                  }
               } else if (!this.mc.player.isInWater() && !this.mc.player.isInLava()) {
                  BlockState blockBelowState = this.mc.level.getBlockState(this.mc.player.blockPosition().below());
                  boolean waterLogger = false;

                  try {
                     waterLogger = (Boolean)blockBelowState.getValue(BlockStateProperties.WATERLOGGED);
                  } catch (Exception var7) {
                  }

                  if (this.tickTimer == 0) {
                     ((IVec3d)this.mc.player.getDeltaMovement()).setY(0.3);
                  } else if (this.tickTimer == 1
                     && (blockBelowState == Blocks.WATER.defaultBlockState() || blockBelowState == Blocks.LAVA.defaultBlockState() || waterLogger)) {
                     ((IVec3d)this.mc.player.getDeltaMovement()).setY(0.0);
                  }

                  this.tickTimer++;
               } else {
                  ((IVec3d)this.mc.player.getDeltaMovement()).setY(0.11);
                  this.tickTimer = 0;
               }
            }
         }
      }
   }

   @EventHandler
   private void onCanWalkOnFluid(CanWalkOnFluidEvent event) {
      if (this.mc.player == null || !this.mc.player.isVisuallySwimming()) {
         if ((event.fluidState.getType() == Fluids.WATER || event.fluidState.getType() == Fluids.FLOWING_WATER) && this.waterShouldBeSolid()) {
            event.walkOnFluid = true;
         } else if ((event.fluidState.getType() == Fluids.LAVA || event.fluidState.getType() == Fluids.FLOWING_LAVA) && this.lavaShouldBeSolid()) {
            event.walkOnFluid = true;
         }
      }
   }

   @EventHandler
   private void onFluidCollisionShape(CollisionShapeEvent event) {
      if (!event.state.getFluidState().isEmpty()) {
         if (event.state.getBlock() == Blocks.WATER | event.state.getFluidState().getType() == Fluids.WATER
            && !this.mc.player.isInWater()
            && this.waterShouldBeSolid()
            && (double)event.pos.getY() <= this.mc.player.getY() - 1.0) {
            event.shape = Shapes.block();
         } else if (event.state.getBlock() == Blocks.LAVA
            && !this.mc.player.isInLava()
            && this.lavaShouldBeSolid()
            && (!this.lavaIsSafe() || (double)event.pos.getY() <= this.mc.player.getY() - 1.0)) {
            event.shape = Shapes.block();
         }
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (event.packet instanceof ServerboundMovePlayerPacket packet) {
         if (!this.mc.player.isInWater() || this.waterShouldBeSolid()) {
            if (!this.mc.player.isInLava() || this.lavaShouldBeSolid()) {
               if (packet instanceof Pos || packet instanceof PosRot) {
                  if (!this.mc.player.isInWater() && !this.mc.player.isInLava() && !(this.mc.player.fallDistance > 3.0F) && this.isOverLiquid()) {
                     if (this.mc.player.input.forwardImpulse == 0.0F && this.mc.player.input.leftImpulse == 0.0F) {
                        event.cancel();
                     } else if (this.packetTimer++ >= 4) {
                        this.packetTimer = 0;
                        event.cancel();
                        double x = packet.getX(0.0);
                        double y = packet.getY(0.0) + 0.05;
                        double z = packet.getZ(0.0);
                        Packet<?> newPacket;
                        if (packet instanceof Pos) {
                           newPacket = new Pos(x, y, z, true);
                        } else {
                           newPacket = new PosRot(x, y, z, packet.getYRot(0.0F), packet.getXRot(0.0F), true);
                        }

                        this.mc.getConnection().getConnection().send(newPacket);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean waterShouldBeSolid() {
      if (EntityUtils.getGameMode(this.mc.player) != GameType.SPECTATOR && !this.mc.player.getAbilities().flying) {
         if (this.mc.player.getVehicle() != null) {
            EntityType<?> vehicle = this.mc.player.getVehicle().getType();
            if (vehicle == EntityType.BOAT || vehicle == EntityType.CHEST_BOAT) {
               return false;
            }
         }

         if (Modules.get().get(Flight.class).isActive()) {
            return false;
         } else if (this.dipIfBurning.get() && this.mc.player.isOnFire()) {
            return false;
         } else if (this.dipOnSneakWater.get() && this.mc.options.keyShift.isDown()) {
            return false;
         } else {
            return this.dipOnFallWater.get() && this.mc.player.fallDistance > (float)this.dipFallHeightWater.get().intValue()
               ? false
               : this.waterMode.get() == Jesus.Mode.Solid;
         }
      } else {
         return false;
      }
   }

   private boolean lavaShouldBeSolid() {
      if (EntityUtils.getGameMode(this.mc.player) != GameType.SPECTATOR && !this.mc.player.getAbilities().flying) {
         if (!this.lavaIsSafe() && this.lavaMode.get() == Jesus.Mode.Solid) {
            return true;
         } else if (this.dipOnSneakLava.get() && this.mc.options.keyShift.isDown()) {
            return false;
         } else {
            return this.dipOnFallLava.get() && this.mc.player.fallDistance > (float)this.dipFallHeightLava.get().intValue()
               ? false
               : this.lavaMode.get() == Jesus.Mode.Solid;
         }
      } else {
         return false;
      }
   }

   private boolean lavaIsSafe() {
      return !this.dipIfFireResistant.get()
         ? false
         : this.mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)
            && (double)this.mc.player.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() > 300.0 * this.mc.player.getAttributeValue(Attributes.BURNING_TIME);
   }

   private boolean isOverLiquid() {
      boolean foundLiquid = false;
      boolean foundSolid = false;

      for (AABB bb : (List<AABB>)Streams.stream(this.mc.level.getBlockCollisions(this.mc.player, this.mc.player.getBoundingBox().move(0.0, -0.5, 0.0)))
         .map(VoxelShape::bounds)
         .collect(Collectors.toCollection(ArrayList::new))) {
         this.blockPos.set(Mth.lerp(0.5, bb.minX, bb.maxX), Mth.lerp(0.5, bb.minY, bb.maxY), Mth.lerp(0.5, bb.minZ, bb.maxZ));
         BlockState blockState = this.mc.level.getBlockState(this.blockPos);
         if (blockState.getBlock() == Blocks.WATER | blockState.getFluidState().getType() == Fluids.WATER || blockState.getBlock() == Blocks.LAVA) {
            foundLiquid = true;
         } else if (!blockState.isAir()) {
            foundSolid = true;
         }
      }

      return foundLiquid && !foundSolid;
   }

   public boolean canWalkOnPowderSnow() {
      return this.isActive() && this.powderSnow.get();
   }

   public static enum Mode {
      Solid,
      Bob,
      Ignore;
   }
}
