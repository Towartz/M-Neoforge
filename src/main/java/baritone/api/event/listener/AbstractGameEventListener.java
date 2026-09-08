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

public interface AbstractGameEventListener extends IGameEventListener {
   @Override
   default void onTick(TickEvent event) {
   }

   @Override
   default void onPostTick(TickEvent event) {
   }

   @Override
   default void onPlayerUpdate(PlayerUpdateEvent event) {
   }

   @Override
   default void onSendChatMessage(ChatEvent event) {
   }

   @Override
   default void onPreTabComplete(TabCompleteEvent event) {
   }

   @Override
   default void onChunkEvent(ChunkEvent event) {
   }

   @Override
   default void onBlockChange(BlockChangeEvent event) {
   }

   @Override
   default void onRenderPass(RenderEvent event) {
   }

   @Override
   default void onWorldEvent(WorldEvent event) {
   }

   @Override
   default void onSendPacket(PacketEvent event) {
   }

   @Override
   default void onReceivePacket(PacketEvent event) {
   }

   @Override
   default void onPlayerRotationMove(RotationMoveEvent event) {
   }

   @Override
   default void onPlayerSprintState(SprintStateEvent event) {
   }

   @Override
   default void onBlockInteract(BlockInteractEvent event) {
   }

   @Override
   default void onPlayerDeath() {
   }

   @Override
   default void onPathEvent(PathEvent event) {
   }
}
