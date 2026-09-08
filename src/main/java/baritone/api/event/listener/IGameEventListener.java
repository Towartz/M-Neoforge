package baritone.api.event.listener;

import baritone.api.event.events.BlockChangeEvent;
import baritone.api.event.events.BlockInteractEvent;
import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.PathEvent;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.event.events.SprintStateEvent;
import baritone.api.event.events.TabCompleteEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;

public interface IGameEventListener {
   void onTick(TickEvent var1);

   void onPostTick(TickEvent var1);

   void onPlayerUpdate(PlayerUpdateEvent var1);

   void onSendChatMessage(ChatEvent var1);

   void onPreTabComplete(TabCompleteEvent var1);

   void onChunkEvent(ChunkEvent var1);

   void onBlockChange(BlockChangeEvent var1);

   void onRenderPass(RenderEvent var1);

   void onWorldEvent(WorldEvent var1);

   void onSendPacket(PacketEvent var1);

   void onReceivePacket(PacketEvent var1);

   void onPlayerRotationMove(RotationMoveEvent var1);

   void onPlayerSprintState(SprintStateEvent var1);

   void onBlockInteract(BlockInteractEvent var1);

   void onPlayerDeath();

   void onPathEvent(PathEvent var1);
}
