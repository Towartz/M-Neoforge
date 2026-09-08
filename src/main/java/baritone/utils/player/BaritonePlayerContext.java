package baritone.utils.player;

import baritone.Baritone;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.IPlayerController;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public final class BaritonePlayerContext implements IPlayerContext {
   private final Baritone baritone;
   private final Minecraft mc;
   private final IPlayerController playerController;

   public BaritonePlayerContext(Baritone baritone, Minecraft mc) {
      this.baritone = baritone;
      this.mc = mc;
      this.playerController = new BaritonePlayerController(mc);
   }

   @Override
   public Minecraft minecraft() {
      return this.mc;
   }

   @Override
   public LocalPlayer player() {
      return this.mc.player;
   }

   @Override
   public IPlayerController playerController() {
      return this.playerController;
   }

   @Override
   public Level world() {
      return this.mc.level;
   }

   @Override
   public IWorldData worldData() {
      return this.baritone.getWorldProvider().getCurrentWorld();
   }

   @Override
   public BetterBlockPos viewerPos() {
      Entity entity = this.mc.getCameraEntity();
      return entity == null ? this.playerFeet() : BetterBlockPos.from(entity.blockPosition());
   }

   @Override
   public Rotation playerRotations() {
      return this.baritone.getLookBehavior().getEffectiveRotation().orElseGet(() -> IPlayerContext.super.playerRotations());
   }

   @Override
   public HitResult objectMouseOver() {
      return RayTraceUtils.rayTraceTowards(this.player(), this.playerRotations(), this.playerController().getBlockReachDistance());
   }
}
