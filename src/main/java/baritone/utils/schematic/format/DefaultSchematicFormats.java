package baritone.utils.schematic.format;

import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.utils.schematic.format.defaults.LitematicaSchematic;
import baritone.utils.schematic.format.defaults.MCEditSchematic;
import baritone.utils.schematic.format.defaults.SpongeSchematic;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.apache.commons.io.FilenameUtils;

public enum DefaultSchematicFormats implements ISchematicFormat {
   MCEDIT("schematic") {
      @Override
      public IStaticSchematic parse(InputStream input) throws IOException {
         return new MCEditSchematic(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
      }
   },
   SPONGE("schem") {
      @Override
      public IStaticSchematic parse(InputStream input) throws IOException {
         CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
         int version = nbt.getInt("Version");
         switch (version) {
            case 1:
            case 2:
               return new SpongeSchematic(nbt);
            default:
               throw new UnsupportedOperationException("Unsupported Version of a Sponge Schematic");
         }
      }
   },
   LITEMATICA("litematic") {
      @Override
      public IStaticSchematic parse(InputStream input) throws IOException {
         CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
         int version = nbt.getInt("Version");
         switch (version) {
            case 4:
            case 5:
               throw new UnsupportedOperationException("This litematic Version is too old.");
            case 6:
               throw new UnsupportedOperationException("This litematic Version is too old.");
            case 7:
               return new LitematicaSchematic(nbt);
            default:
               throw new UnsupportedOperationException("Unsuported Version of a Litematica Schematic");
         }
      }
   };

   private final String extension;

   private DefaultSchematicFormats(String extension) {
      this.extension = extension;
   }

   @Override
   public boolean isFileType(File file) {
      return this.extension.equalsIgnoreCase(FilenameUtils.getExtension(file.getAbsolutePath()));
   }

   @Override
   public List<String> getFileExtensions() {
      return Collections.singletonList(this.extension);
   }
}
