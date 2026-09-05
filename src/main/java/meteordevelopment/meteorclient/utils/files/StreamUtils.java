package meteordevelopment.meteorclient.utils.files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import meteordevelopment.meteorclient.MeteorClient;

public class StreamUtils {
   private StreamUtils() {
   }

   public static void copy(File from, File to) {
      try {
         Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var10) {
         MeteorClient.LOG.error("Error copying from file '%s' to file '%s'.".formatted(from.getName(), to.getName()), var10);
      }
   }

   public static void copy(InputStream in, File to) {
      try (InputStream input = in) {
         Files.copy(input, to.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var12) {
         MeteorClient.LOG.error("Error writing to file '%s'.".formatted(to.getName()), var12);
      }
   }
}
