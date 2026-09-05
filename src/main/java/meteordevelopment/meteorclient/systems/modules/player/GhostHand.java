package meteordevelopment.meteorclient.systems.modules.player;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class GhostHand extends Module {
   private final Set<BlockPos> posList = new ObjectOpenHashSet();

   public GhostHand() {
      super(Categories.Player, "ghost-hand", "Opens containers through walls.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.options.keyUse.isDown() && !this.mc.player.isShiftKeyDown()) {
         if (!this.mc
            .level
            .getBlockState(
               BlockPos.containing(
                  this.mc.player.pick(this.mc.player.blockInteractionRange(), this.mc.getTimer().getGameTimeDeltaPartialTick(true), false).getLocation()
               )
            )
            .hasBlockEntity()) {
            Vec3 direction = new Vec3(0.0, 0.0, 0.1)
               .xRot(-((float)Math.toRadians((double)this.mc.player.getXRot())))
               .yRot(-((float)Math.toRadians((double)this.mc.player.getYRot())));
            this.posList.clear();

            for (int i = 1; (double)i < this.mc.player.blockInteractionRange() * 10.0; i++) {
               BlockPos pos = BlockPos.containing(
                  this.mc.player.getEyePosition(this.mc.getTimer().getGameTimeDeltaPartialTick(true)).add(direction.scale((double)i))
               );
               if (!this.posList.contains(pos)) {
                  this.posList.add(pos);
                  if (this.mc.level.getBlockState(pos).hasBlockEntity()) {
                     this.mc
                        .gameMode
                        .useItemOn(
                           this.mc.player,
                           InteractionHand.MAIN_HAND,
                           new BlockHitResult(new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5), Direction.UP, pos, true)
                        );
                     return;
                  }
               }
            }
         }
      }
   }
}
