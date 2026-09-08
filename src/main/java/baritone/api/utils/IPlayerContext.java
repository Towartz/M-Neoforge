package baritone.api.utils;

import baritone.api.cache.IWorldData;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public interface IPlayerContext {
   Minecraft minecraft();

   LocalPlayer player();

   IPlayerController playerController();

   Level world();

   default Iterable<Entity> entities() {
      return ((ClientLevel)this.world()).entitiesForRendering();
   }

   default Stream<Entity> entitiesStream() {
      return StreamSupport.stream(this.entities().spliterator(), false);
   }

   IWorldData worldData();

   HitResult objectMouseOver();

   default BetterBlockPos playerFeet() {
      BetterBlockPos feet = new BetterBlockPos(this.player().position().x, this.player().position().y + 0.1251, this.player().position().z);

      try {
         if (this.world().getBlockState(feet).getBlock() instanceof SlabBlock) {
            return feet.above();
         }
      } catch (NullPointerException var3) {
      }

      return feet;
   }

   default Vec3 playerFeetAsVec() {
      return new Vec3(this.player().position().x, this.player().position().y, this.player().position().z);
   }

   default Vec3 playerHead() {
      return new Vec3(this.player().position().x, this.player().position().y + (double)this.player().getEyeHeight(), this.player().position().z);
   }

   default Vec3 playerMotion() {
      return this.player().getDeltaMovement();
   }

   BetterBlockPos viewerPos();

   default Rotation playerRotations() {
      return new Rotation(this.player().getYRot(), this.player().getXRot());
   }

   @Deprecated
   static double eyeHeight(boolean ifSneaking) {
      return ifSneaking ? 1.27 : 1.62;
   }

   default Optional<BlockPos> getSelectedBlock() {
      HitResult result = this.objectMouseOver();
      return result != null && result.getType() == Type.BLOCK ? Optional.of(((BlockHitResult)result).getBlockPos()) : Optional.empty();
   }

   default boolean isLookingAt(BlockPos pos) {
      return this.getSelectedBlock().equals(Optional.of(pos));
   }
}
