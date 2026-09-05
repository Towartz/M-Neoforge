package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ClickTP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> maxDistance = this.sgGeneral
      .add(new DoubleSetting.Builder().name("max-distance").description("The maximum distance you can teleport.").defaultValue(5.0).build());

   public ClickTP() {
      super(Categories.Movement, "click-tp", "Teleports you to the block you click on.");
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (!this.mc.player.isUsingItem()) {
         if (this.mc.options.keyUse.isDown()) {
            HitResult hitResult = this.mc.player.pick(this.maxDistance.get(), 0.05F, false);
            if (hitResult.getType() == Type.ENTITY
               && this.mc.player.interactOn(((EntityHitResult)hitResult).getEntity(), InteractionHand.MAIN_HAND) != InteractionResult.PASS) {
               return;
            }

            if (hitResult.getType() == Type.BLOCK) {
               BlockPos pos = ((BlockHitResult)hitResult).getBlockPos();
               Direction side = ((BlockHitResult)hitResult).getDirection();
               if (this.mc.level.getBlockState(pos).useWithoutItem(this.mc.level, this.mc.player, (BlockHitResult)hitResult) != InteractionResult.PASS) {
                  return;
               }

               BlockState state = this.mc.level.getBlockState(pos);
               VoxelShape shape = state.getCollisionShape(this.mc.level, pos);
               if (shape.isEmpty()) {
                  shape = state.getShape(this.mc.level, pos);
               }

               double height = shape.isEmpty() ? 1.0 : shape.max(Axis.Y);
               this.mc
                  .player
                  .setPos((double)pos.getX() + 0.5 + (double)side.getStepX(), (double)pos.getY() + height, (double)pos.getZ() + 0.5 + (double)side.getStepZ());
            }
         }
      }
   }
}
