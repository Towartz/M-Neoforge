package baritone.behavior;

import baritone.Baritone;
import baritone.api.cache.IWaypoint;
import baritone.api.cache.Waypoint;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.event.events.BlockInteractEvent;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.utils.BlockStateInterface;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public class WaypointBehavior extends Behavior {
   public WaypointBehavior(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void onBlockInteract(BlockInteractEvent event) {
      if (Baritone.settings().doBedWaypoints.value) {
         if (event.getType() == BlockInteractEvent.Type.USE) {
            BetterBlockPos pos = BetterBlockPos.from(event.getPos());
            BlockState state = BlockStateInterface.get(this.ctx, pos);
            if (state.getBlock() instanceof BedBlock) {
               if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
                  pos = pos.relative((Direction)state.getValue(BedBlock.FACING));
               }

               Set<IWaypoint> waypoints = this.baritone.getWorldProvider().getCurrentWorld().getWaypoints().getByTag(IWaypoint.Tag.BED);
               boolean exists = waypoints.stream().map(IWaypoint::getLocation).filter(pos::equals).findFirst().isPresent();
               if (!exists) {
                  this.baritone.getWorldProvider().getCurrentWorld().getWaypoints().addWaypoint(new Waypoint("bed", IWaypoint.Tag.BED, pos));
               }
            }
         }
      }
   }

   @Override
   public void onPlayerDeath() {
      if (Baritone.settings().doDeathWaypoints.value) {
         Waypoint deathWaypoint = new Waypoint("death", IWaypoint.Tag.DEATH, this.ctx.playerFeet());
         this.baritone.getWorldProvider().getCurrentWorld().getWaypoints().addWaypoint(deathWaypoint);
         MutableComponent component = Component.literal("Death position saved.");
         component.setStyle(
            component.getStyle()
               .withColor(ChatFormatting.WHITE)
               .withHoverEvent(new HoverEvent(Action.SHOW_TEXT, Component.literal("Click to goto death")))
               .withClickEvent(
                  new ClickEvent(
                     net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                     String.format(
                        "%s%s goto %s @ %d",
                        IBaritoneChatControl.FORCE_COMMAND_PREFIX,
                        "wp",
                        deathWaypoint.getTag().getName(),
                        deathWaypoint.getCreationTimestamp()
                     )
                  )
               )
         );
         Helper.HELPER.logDirect(component);
      }
   }
}
