package meteordevelopment.meteorclient.utils.notebot.decoder;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.meteorclient.utils.notebot.song.Song;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public class TextSongDecoder extends SongDecoder {
   @Override
   public Song parse(File file) throws Exception {
      List<String> data = Files.readAllLines(file.toPath());
      Multimap<Integer, Note> notesMap = MultimapBuilder.linkedHashKeys().arrayListValues().build();
      String name = file.getName();
      int dot = name.lastIndexOf('.');
      String title = dot > 0 ? name.substring(0, dot) : name;
      String author = "Unknown";

      for (int lineNumber = 0; lineNumber < data.size(); lineNumber++) {
         String line = data.get(lineNumber);
         if (line.startsWith("// Name: ")) {
            title = line.substring(9);
         } else if (line.startsWith("// Author: ")) {
            author = line.substring(11);
         } else if (!line.isEmpty()) {
            String[] parts = data.get(lineNumber).split(":");
            if (parts.length < 2) {
               this.notebot.warning("Malformed line %d", new Object[]{lineNumber});
            } else {
               int type = 0;

               int key;
               int val;
               try {
                  key = Integer.parseInt(parts[0]);
                  val = Integer.parseInt(parts[1]);
                  if (parts.length > 2) {
                     type = Integer.parseInt(parts[2]);
                  }
               } catch (NumberFormatException var13) {
                  this.notebot.warning("Invalid character at line %d", new Object[]{lineNumber});
                  continue;
               }

               Note note = new Note(NoteBlockInstrument.values()[type], val);
               notesMap.put(key, note);
            }
         }
      }

      return new Song(notesMap, title, author);
   }
}
