package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public class SwarmConnection extends Thread {
   public final Socket socket;
   public String messageToSend;

   public SwarmConnection(Socket socket) {
      this.socket = socket;
      this.start();
   }

   @Override
   public void run() {
      ChatUtils.infoPrefix("Swarm", "New worker connected on %s.", this.getIp(this.socket.getInetAddress().getHostAddress()));

      try {
         DataOutputStream out = new DataOutputStream(this.socket.getOutputStream());

         while (!this.isInterrupted()) {
            if (this.messageToSend != null) {
               try {
                  out.writeUTF(this.messageToSend);
                  out.flush();
               } catch (Exception var3) {
                  ChatUtils.errorPrefix("Swarm", "Encountered error when sending command.");
                  var3.printStackTrace();
               }

               this.messageToSend = null;
            }
         }

         out.close();
      } catch (IOException var4) {
         ChatUtils.infoPrefix(
            "Swarm", "Error creating a connection with %s on port %s.", this.getIp(this.socket.getInetAddress().getHostAddress()), this.socket.getPort()
         );
         var4.printStackTrace();
      }
   }

   public void disconnect() {
      try {
         this.socket.close();
      } catch (IOException var2) {
         var2.printStackTrace();
      }

      ChatUtils.infoPrefix("Swarm", "Worker disconnected on ip: %s.", this.socket.getInetAddress().getHostAddress());
      this.interrupt();
   }

   public String getConnection() {
      return this.getIp(this.socket.getInetAddress().getHostAddress()) + ":" + this.socket.getPort();
   }

   private String getIp(String ip) {
      return ip.equals("127.0.0.1") ? "localhost" : ip;
   }
}
