package meteordevelopment.meteorclient.utils.world;

import java.util.Arrays;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.Mth;

public class TickRate {
   public static TickRate INSTANCE = new TickRate();
   private final float[] tickRates = new float[20];
   private int nextIndex = 0;
   private long timeLastTimeUpdate = -1L;
   private long lastServerPacketTime = -1L;
   private long timeGameJoined;

   private TickRate() {
      MeteorClient.EVENT_BUS.subscribe(this);
   }

   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      long now = System.currentTimeMillis();
      this.lastServerPacketTime = now;

      if (event.packet instanceof ClientboundSetTimePacket) {
         if (this.timeLastTimeUpdate > 0L) {
            float timeElapsed = (float)(now - this.timeLastTimeUpdate) / 1000.0F;
            if (timeElapsed > 0.0F) {
               this.tickRates[this.nextIndex] = Mth.clamp(20.0F / timeElapsed, 0.0F, 20.0F);
               this.nextIndex = (this.nextIndex + 1) % this.tickRates.length;
            }
         }
         this.timeLastTimeUpdate = now;
      }
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      Arrays.fill(this.tickRates, 0.0F);
      this.nextIndex = 0;
      this.timeGameJoined = this.timeLastTimeUpdate = this.lastServerPacketTime = System.currentTimeMillis();
   }

   public float getTickRate() {
      if (!Utils.canUpdate()) {
         return 0.0F;
      } else if (System.currentTimeMillis() - this.timeGameJoined < 4000L) {
         return 20.0F;
      } else {
         int numTicks = 0;
         float sumTickRates = 0.0F;

         for (float tickRate : this.tickRates) {
            if (tickRate > 0.0F) {
               sumTickRates += tickRate;
               numTicks++;
            }
         }

         return numTicks == 0 ? 20.0F : sumTickRates / (float)numTicks;
      }
   }

   public float getTimeSinceLastTick() {
      if (this.timeLastTimeUpdate <= 0L && this.lastServerPacketTime <= 0L) {
         return 0.0F;
      }
      long now = System.currentTimeMillis();
      if (now - this.timeGameJoined < 4000L) {
         return 0.0F;
      }
      long ref = this.lastServerPacketTime > 0L ? this.lastServerPacketTime : this.timeLastTimeUpdate;
      return (float)(now - ref) / 1000.0F;
   }
}
