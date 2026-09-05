package meteordevelopment.meteorclient.systems.modules.world;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StorageBlockListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public class StashFinder extends Module {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<BlockEntityType<?>>> storageBlocks = this.sgGeneral
      .add(
         new StorageBlockListSetting.Builder()
            .name("storage-blocks")
            .description("Select the storage blocks to search for.")
            .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
            .build()
      );
   private final Setting<Integer> minimumStorageCount = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("minimum-storage-count")
            .description("The minimum amount of storage blocks in a chunk to record the chunk.")
            .defaultValue(Integer.valueOf(4))
            .min(1)
            .sliderMin(1)
            .build()
      );
   private final Setting<Integer> minimumDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("minimum-distance")
            .description("The minimum distance you must be from spawn to record a certain chunk.")
            .defaultValue(Integer.valueOf(0))
            .min(0)
            .sliderMax(10000)
            .build()
      );
   private final Setting<Boolean> sendNotifications = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("notifications")
            .description("Sends Minecraft notifications when new stashes are found.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<StashFinder.Mode> notificationMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("notification-mode"))
                     .description("The mode to use for notifications."))
                  .defaultValue(StashFinder.Mode.Both))
               .visible(this.sendNotifications::get))
            .build()
      );
   public List<StashFinder.Chunk> chunks = new ArrayList<>();

   public StashFinder() {
      super(Categories.World, "stash-finder", "Searches loaded chunks for storage blocks. Saves to <your minecraft folder>/meteor-client");
   }

   @Override
   public void onActivate() {
      this.load();
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      double chunkX = (double)(event.chunk().getPos().x * 16);
      double chunkZ = (double)(event.chunk().getPos().z * 16);
      double minDist = (double)this.minimumDistance.get().intValue();
      if ((chunkX * chunkX + chunkZ * chunkZ) >= minDist * minDist) {
         StashFinder.Chunk chunk = new StashFinder.Chunk(event.chunk().getPos());

         for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
            if (this.storageBlocks.get().contains(blockEntity.getType())) {
               if (blockEntity instanceof ChestBlockEntity) {
                  chunk.chests++;
               } else if (blockEntity instanceof BarrelBlockEntity) {
                  chunk.barrels++;
               } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
                  chunk.shulkers++;
               } else if (blockEntity instanceof EnderChestBlockEntity) {
                  chunk.enderChests++;
               } else if (blockEntity instanceof AbstractFurnaceBlockEntity) {
                  chunk.furnaces++;
               } else if (blockEntity instanceof DispenserBlockEntity) {
                  chunk.dispensersDroppers++;
               } else if (blockEntity instanceof HopperBlockEntity) {
                  chunk.hoppers++;
               }
            }
         }

         if (chunk.getTotal() >= this.minimumStorageCount.get()) {
            StashFinder.Chunk prevChunk = null;
            int i = this.chunks.indexOf(chunk);
            if (i < 0) {
               this.chunks.add(chunk);
            } else {
               prevChunk = this.chunks.set(i, chunk);
            }

            this.saveJson();
            this.saveCsv();
            if (this.sendNotifications.get() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
               switch ((StashFinder.Mode)this.notificationMode.get()) {
                  case Chat:
                     this.info("Found stash at (highlight)%s(default), (highlight)%s(default).", new Object[]{chunk.x, chunk.z});
                     break;
                  case Toast:
                     this.mc.getToasts().addToast(new MeteorToast(Items.CHEST, this.title, "Found Stash!"));
                     break;
                  case Both:
                     this.info("Found stash at (highlight)%s(default), (highlight)%s(default).", new Object[]{chunk.x, chunk.z});
                     this.mc.getToasts().addToast(new MeteorToast(Items.CHEST, this.title, "Found Stash!"));
               }
            }
         }
      }
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      this.chunks.sort(Comparator.comparingInt(value -> -value.getTotal()));
      WVerticalList list = theme.verticalList();
      WButton clear = list.add(theme.button("Clear")).widget();
      WTable table = new WTable();
      if (!this.chunks.isEmpty()) {
         list.add(table);
      }

      clear.action = () -> {
         this.chunks.clear();
         table.clear();
      };
      this.fillTable(theme, table);
      return list;
   }

   private void fillTable(GuiTheme theme, WTable table) {
      for (StashFinder.Chunk chunk : this.chunks) {
         table.add(theme.label("Pos: " + chunk.x + ", " + chunk.z));
         table.add(theme.label("Total: " + chunk.getTotal()));
         WButton open = table.add(theme.button("Open")).widget();
         open.action = () -> this.mc.setScreen(new StashFinder.ChunkScreen(theme, chunk));
         WButton gotoBtn = table.add(theme.button("Goto")).widget();
         gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(chunk.x, 0, chunk.z), true);
         WMinus delete = table.add(theme.minus()).widget();
         delete.action = () -> {
            if (this.chunks.remove(chunk)) {
               table.clear();
               this.fillTable(theme, table);
               this.saveJson();
               this.saveCsv();
            }
         };
         table.row();
      }
   }

   private void load() {
      boolean loaded = false;
      File file = this.getJsonFile();
      if (file.exists()) {
         try {
            FileReader reader = new FileReader(file);
            this.chunks = (List<StashFinder.Chunk>)GSON.fromJson(reader, (new TypeToken<List<StashFinder.Chunk>>() {
            }).getType());
            reader.close();

            for (StashFinder.Chunk chunk : this.chunks) {
               chunk.calculatePos();
            }

            loaded = true;
         } catch (Exception var8) {
            if (this.chunks == null) {
               this.chunks = new ArrayList<>();
            }
         }
      }

      file = this.getCsvFile();
      if (!loaded && file.exists()) {
         try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
               String[] values = line.split(" ");
               StashFinder.Chunk chunk = new StashFinder.Chunk(new ChunkPos(Integer.parseInt(values[0]), Integer.parseInt(values[1])));
               chunk.chests = Integer.parseInt(values[2]);
               chunk.shulkers = Integer.parseInt(values[3]);
               chunk.enderChests = Integer.parseInt(values[4]);
               chunk.furnaces = Integer.parseInt(values[5]);
               chunk.dispensersDroppers = Integer.parseInt(values[6]);
               chunk.hoppers = Integer.parseInt(values[7]);
               this.chunks.add(chunk);
            }

            reader.close();
         } catch (Exception var7) {
            if (this.chunks == null) {
               this.chunks = new ArrayList<>();
            }
         }
      }
   }

   private void saveCsv() {
      try {
         File file = this.getCsvFile();
         file.getParentFile().mkdirs();
         Writer writer = new FileWriter(file);
         writer.write("X,Z,Chests,Barrels,Shulkers,EnderChests,Furnaces,DispensersDroppers,Hoppers\n");

         for (StashFinder.Chunk chunk : this.chunks) {
            chunk.write(writer);
         }

         writer.close();
      } catch (IOException var5) {
         var5.printStackTrace();
      }
   }

   private void saveJson() {
      try {
         File file = this.getJsonFile();
         file.getParentFile().mkdirs();
         Writer writer = new FileWriter(file);
         GSON.toJson(this.chunks, writer);
         writer.close();
      } catch (IOException var3) {
         var3.printStackTrace();
      }
   }

   private File getJsonFile() {
      return new File(new File(new File(MeteorClient.FOLDER, "stashes"), Utils.getFileWorldName()), "stashes.json");
   }

   private File getCsvFile() {
      return new File(new File(new File(MeteorClient.FOLDER, "stashes"), Utils.getFileWorldName()), "stashes.csv");
   }

   @Override
   public String getInfoString() {
      return String.valueOf(this.chunks.size());
   }

   public static class Chunk {
      private static final StringBuilder sb = new StringBuilder();
      public ChunkPos chunkPos;
      public transient int x;
      public transient int z;
      public int chests;
      public int barrels;
      public int shulkers;
      public int enderChests;
      public int furnaces;
      public int dispensersDroppers;
      public int hoppers;

      public Chunk(ChunkPos chunkPos) {
         this.chunkPos = chunkPos;
         this.calculatePos();
      }

      public void calculatePos() {
         this.x = this.chunkPos.x * 16 + 8;
         this.z = this.chunkPos.z * 16 + 8;
      }

      public int getTotal() {
         return this.chests + this.barrels + this.shulkers + this.enderChests + this.furnaces + this.dispensersDroppers + this.hoppers;
      }

      public void write(Writer writer) throws IOException {
         sb.setLength(0);
         sb.append(this.x).append(',').append(this.z).append(',');
         sb.append(this.chests)
            .append(',')
            .append(this.barrels)
            .append(',')
            .append(this.shulkers)
            .append(',')
            .append(this.enderChests)
            .append(',')
            .append(this.furnaces)
            .append(',')
            .append(this.dispensersDroppers)
            .append(',')
            .append(this.hoppers)
            .append('\n');
         writer.write(sb.toString());
      }

      public boolean countsEqual(StashFinder.Chunk c) {
         return c == null
            ? false
            : this.chests != c.chests
               || this.barrels != c.barrels
               || this.shulkers != c.shulkers
               || this.enderChests != c.enderChests
               || this.furnaces != c.furnaces
               || this.dispensersDroppers != c.dispensersDroppers
               || this.hoppers != c.hoppers;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            StashFinder.Chunk chunk = (StashFinder.Chunk)o;
            return Objects.equals(this.chunkPos, chunk.chunkPos);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.chunkPos);
      }
   }

   private static class ChunkScreen extends WindowScreen {
      private final StashFinder.Chunk chunk;

      public ChunkScreen(GuiTheme theme, StashFinder.Chunk chunk) {
         super(theme, "Chunk at " + chunk.x + ", " + chunk.z);
         this.chunk = chunk;
      }

      @Override
      public void initWidgets() {
         WTable t = this.add(this.theme.table()).expandX().widget();
         t.add(this.theme.label("Total:"));
         t.add(this.theme.label(this.chunk.getTotal() + ""));
         t.row();
         t.add(this.theme.horizontalSeparator()).expandX();
         t.row();
         t.add(this.theme.label("Chests:"));
         t.add(this.theme.label(this.chunk.chests + ""));
         t.row();
         t.add(this.theme.label("Barrels:"));
         t.add(this.theme.label(this.chunk.barrels + ""));
         t.row();
         t.add(this.theme.label("Shulkers:"));
         t.add(this.theme.label(this.chunk.shulkers + ""));
         t.row();
         t.add(this.theme.label("Ender Chests:"));
         t.add(this.theme.label(this.chunk.enderChests + ""));
         t.row();
         t.add(this.theme.label("Furnaces:"));
         t.add(this.theme.label(this.chunk.furnaces + ""));
         t.row();
         t.add(this.theme.label("Dispensers and droppers:"));
         t.add(this.theme.label(this.chunk.dispensersDroppers + ""));
         t.row();
         t.add(this.theme.label("Hoppers:"));
         t.add(this.theme.label(this.chunk.hoppers + ""));
      }
   }

   public static enum Mode {
      Chat,
      Toast,
      Both;
   }
}
