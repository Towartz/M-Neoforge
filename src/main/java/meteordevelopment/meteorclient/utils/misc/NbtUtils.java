package meteordevelopment.meteorclient.utils.misc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.utils.render.prompts.OkPrompt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.apache.commons.io.output.ByteArrayOutputStream;

public class NbtUtils {
   private NbtUtils() {
   }

   public static <T extends ISerializable<?>> ListTag listToTag(Iterable<T> list) {
      ListTag tag = new ListTag();

      for (T item : list) {
         tag.add(item.toTag());
      }

      return tag;
   }

   public static <T> List<T> listFromTag(ListTag tag, NbtUtils.ToValue<T> toItem) {
      List<T> list = new ArrayList<>(tag.size());

      for (Tag itemTag : tag) {
         T value = toItem.toValue(itemTag);
         if (value != null) {
            list.add(value);
         }
      }

      return list;
   }

   public static <K, V extends ISerializable<?>> CompoundTag mapToTag(Map<K, V> map) {
      CompoundTag tag = new CompoundTag();

      for (K key : map.keySet()) {
         tag.put(key.toString(), map.get(key).toTag());
      }

      return tag;
   }

   public static <K, V> Map<K, V> mapFromTag(CompoundTag tag, NbtUtils.ToKey<K> toKey, NbtUtils.ToValue<V> toValue) {
      Map<K, V> map = new HashMap<>(tag.size());

      for (String key : tag.getAllKeys()) {
         map.put(toKey.toKey(key), toValue.toValue(tag.get(key)));
      }

      return map;
   }

   public static boolean toClipboard(System<?> system) {
      return toClipboard(system.getName(), system.toTag());
   }

   public static boolean toClipboard(String name, CompoundTag nbtCompound) {
      String preClipboard = MeteorClient.mc.keyboardHandler.getClipboard();

      try {
         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         NbtIo.writeCompressed(nbtCompound, byteArrayOutputStream);
         MeteorClient.mc.keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray()));
         return true;
      } catch (Exception var4) {
         MeteorClient.LOG.error(String.format("Error copying %s NBT to clipboard!", name));
         OkPrompt.create()
            .title(String.format("Error copying %s NBT to clipboard!", name))
            .message("This shouldn't happen, please report it.")
            .id("nbt-copying")
            .show();
         MeteorClient.mc.keyboardHandler.setClipboard(preClipboard);
         return false;
      }
   }

   public static boolean fromClipboard(System<?> system) {
      CompoundTag clipboard = fromClipboard(system.toTag());
      if (clipboard != null) {
         system.fromTag(clipboard);
         return true;
      } else {
         return false;
      }
   }

   public static CompoundTag fromClipboard(CompoundTag schema) {
      try {
         byte[] data = Base64.getDecoder().decode(MeteorClient.mc.keyboardHandler.getClipboard().trim());
         ByteArrayInputStream bis = new ByteArrayInputStream(data);
         CompoundTag pasted = NbtIo.readCompressed(new DataInputStream(bis), NbtAccounter.unlimitedHeap());

         for (String key : schema.getAllKeys()) {
            if (!pasted.getAllKeys().contains(key)) {
               return null;
            }
         }

         return !pasted.getString("name").equals(schema.getString("name")) ? null : pasted;
      } catch (Exception var6) {
         MeteorClient.LOG.error("Invalid NBT data pasted!");
         OkPrompt.create().title("Error pasting NBT data!").message("Please check that the data you pasted is valid.").id("nbt-pasting").show();
         return null;
      }
   }

   public interface ToKey<T> {
      T toKey(String var1);
   }

   public interface ToValue<T> {
      T toValue(Tag var1);
   }
}
