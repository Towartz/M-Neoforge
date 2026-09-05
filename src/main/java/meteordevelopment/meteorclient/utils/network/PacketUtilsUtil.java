package meteordevelopment.meteorclient.utils.network;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

public class PacketUtilsUtil {
   private static final String packetRegistryClass = "    private static class PacketRegistry extends SimpleRegistry<Class<? extends Packet<?>>> {\n        public PacketRegistry() {\n            super(RegistryKey.ofRegistry(MeteorClient.identifier(\"packets\")), Lifecycle.stable());\n        }\n\n        @Override\n        public int size() {\n            return S2C_PACKETS.keySet().size() + C2S_PACKETS.keySet().size();\n        }\n\n        @Override\n        public Identifier getId(Class<? extends Packet<?>> entry) {\n            return null;\n        }\n\n        @Override\n        public Optional<RegistryKey<Class<? extends Packet<?>>>> getKey(Class<? extends Packet<?>> entry) {\n            return Optional.empty();\n        }\n\n        @Override\n        public int getRawId(Class<? extends Packet<?>> entry) {\n            return 0;\n        }\n\n        @Override\n        public Class<? extends Packet<?>> get(RegistryKey<Class<? extends Packet<?>>> key) {\n            return null;\n        }\n\n        @Override\n        public Class<? extends Packet<?>> get(Identifier id) {\n            return null;\n        }\n\n        @Override\n        public Lifecycle getLifecycle() {\n            return null;\n        }\n\n        @Override\n        public Set<Identifier> getIds() {\n            return Collections.emptySet();\n        }\n\n        @Override\n        public boolean containsId(Identifier id) {\n            return false;\n        }\n\n        @Override\n        public Class<? extends Packet<?>> get(int index) {\n            return null;\n        }\n\n        @NotNull\n        @Override\n        public Iterator<Class<? extends Packet<?>>> iterator() {\n            return Stream.concat(S2C_PACKETS.keySet().stream(), C2S_PACKETS.keySet().stream()).iterator();\n        }\n\n        @Override\n        public boolean contains(RegistryKey<Class<? extends Packet<?>>> key) {\n            return false;\n        }\n\n        @Override\n        public Set<Map.Entry<RegistryKey<Class<? extends Packet<?>>>, Class<? extends Packet<?>>>> getEntrySet() {\n            return Collections.emptySet();\n        }\n\n        @Override\n        public Optional<RegistryEntry.Reference<Class<? extends Packet<?>>>> getRandom(Random random) {\n            return Optional.empty();\n        }\n\n        @Override\n        public Registry<Class<? extends Packet<?>>> freeze() {\n            return null;\n        }\n\n        @Override\n        public RegistryEntry.Reference<Class<? extends Packet<?>>> createEntry(Class<? extends Packet<?>> value) {\n            return null;\n        }\n\n        @Override\n        public Optional<RegistryEntry.Reference<Class<? extends Packet<?>>>> getEntry(int rawId) {\n            return Optional.empty();\n        }\n\n        @Override\n        public Optional<RegistryEntry.Reference<Class<? extends Packet<?>>>> getEntry(RegistryKey<Class<? extends Packet<?>>> key) {\n            return Optional.empty();\n        }\n\n        @Override\n        public Stream<RegistryEntry.Reference<Class<? extends Packet<?>>>> streamEntries() {\n            return null;\n        }\n\n        @Override\n        public Optional<RegistryEntryList.Named<Class<? extends Packet<?>>>> getEntryList(TagKey<Class<? extends Packet<?>>> tag) {\n            return Optional.empty();\n        }\n\n        @Override\n        public RegistryEntryList.Named<Class<? extends Packet<?>>> getOrCreateEntryList(TagKey<Class<? extends Packet<?>>> tag) {\n            return null;\n        }\n\n        @Override\n        public Stream<Pair<TagKey<Class<? extends Packet<?>>>, RegistryEntryList.Named<Class<? extends Packet<?>>>>> streamTagsAndEntries() {\n            return null;\n        }\n\n        @Override\n        public Stream<TagKey<Class<? extends Packet<?>>>> streamTags() {\n            return null;\n        }\n\n        @Override\n        public void clearTags() {}\n\n        @Override\n        public void populateTags(Map<TagKey<Class<? extends Packet<?>>>, List<RegistryEntry<Class<? extends Packet<?>>>>> tagEntries) {}\n\n        @Override\n        public Set<RegistryKey<Class<? extends Packet<?>>>> getKeys() {\n            return Collections.emptySet();\n        }\n    }\n";

   private PacketUtilsUtil() {
   }

   public static void main(String[] args) {
      try {
         init();
      } catch (IOException var2) {
         throw new RuntimeException(var2);
      }
   }

   public static void init() throws IOException {
      File file = new File("src/main/java/%s/PacketUtils.java".formatted(PacketUtilsUtil.class.getPackageName().replace('.', '/')));
      if (!file.exists()) {
         file.getParentFile().mkdirs();
         file.createNewFile();
      }

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
         writer.write("/*\n");
         writer.write(" * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).\n");
         writer.write(" * Copyright (c) Meteor Development.\n");
         writer.write(" */\n\n");
         writer.write("package meteordevelopment.meteorclient.utils.network;\n\n");
         writer.write("import com.mojang.datafixers.util.Pair;\n");
         writer.write("import com.mojang.serialization.Lifecycle;\n");
         writer.write("import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;\n");
         writer.write("import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;\n");
         writer.write("import meteordevelopment.meteorclient.MeteorClient;\n");
         writer.write("import net.minecraft.network.packet.Packet;\n");
         writer.write("import net.minecraft.registry.Registry;\n");
         writer.write("import net.minecraft.registry.RegistryKey;\n");
         writer.write("import net.minecraft.registry.SimpleRegistry;\n");
         writer.write("import net.minecraft.registry.entry.RegistryEntry;\n");
         writer.write("import net.minecraft.registry.entry.RegistryEntryList;\n");
         writer.write("import net.minecraft.registry.tag.TagKey;\n");
         writer.write("import net.minecraft.util.Identifier;\n");
         writer.write("import net.minecraft.util.math.random.Random;\n");
         writer.write("import org.jetbrains.annotations.NotNull;\n");
         writer.write("import java.util.*;\n");
         writer.write("import java.util.stream.Stream;\n");
         writer.write("\npublic class PacketUtils {\n");
         writer.write("    public static final Registry<Class<? extends Packet<?>>> REGISTRY = new PacketRegistry();\n\n");
         writer.write("    private static final Map<Class<? extends Packet<?>>, String> S2C_PACKETS = new Reference2ObjectOpenHashMap<>();\n");
         writer.write("    private static final Map<Class<? extends Packet<?>>, String> C2S_PACKETS = new Reference2ObjectOpenHashMap<>();\n\n");
         writer.write("    private static final Map<String, Class<? extends Packet<?>>> S2C_PACKETS_R = new Object2ReferenceOpenHashMap<>();\n");
         writer.write("    private static final Map<String, Class<? extends Packet<?>>> C2S_PACKETS_R = new Object2ReferenceOpenHashMap<>();\n\n");
         writer.write("    static {\n");
         Reflections c2s = new Reflections("net.minecraft.network.packet.c2s", Scanners.SubTypes);

         for (Class<? extends Packet> c2sPacket : c2s.getSubTypesOf(Packet.class)) {
            String name = c2sPacket.getName();
            String className = name.substring(name.lastIndexOf(46) + 1).replace('$', '.');
            String fullName = name.replace('$', '.');
            writer.write("        C2S_PACKETS.put(%s.class, \"%s\");%n".formatted(fullName, className));
            writer.write("        C2S_PACKETS_R.put(\"%s\", %s.class);%n".formatted(className, fullName));
         }

         writer.newLine();
         Reflections s2c = new Reflections("net.minecraft.network.packet.s2c", Scanners.SubTypes);

         for (Class<? extends Packet> s2cPacket : s2c.getSubTypesOf(Packet.class)) {
            if (s2cPacket != BundlePacket.class && s2cPacket != BundleDelimiterPacket.class) {
               String name = s2cPacket.getName();
               String className = name.substring(name.lastIndexOf(46) + 1).replace('$', '.');
               String fullName = name.replace('$', '.');
               writer.write("        S2C_PACKETS.put(%s.class, \"%s\");%n".formatted(fullName, className));
               writer.write("        S2C_PACKETS_R.put(\"%s\", %s.class);%n".formatted(className, fullName));
            }
         }

         writer.write("    }\n\n");
         writer.write("    private PacketUtils() {\n");
         writer.write("    }\n\n");
         writer.write("    public static String getName(Class<? extends Packet<?>> packetClass) {\n");
         writer.write("        String name = S2C_PACKETS.get(packetClass);\n");
         writer.write("        if (name != null) return name;\n");
         writer.write("        return C2S_PACKETS.get(packetClass);\n");
         writer.write("    }\n\n");
         writer.write("    public static Class<? extends Packet<?>> getPacket(String name) {\n");
         writer.write("        Class<? extends Packet<?>> packet = S2C_PACKETS_R.get(name);\n");
         writer.write("        if (packet != null) return packet;\n");
         writer.write("        return C2S_PACKETS_R.get(name);\n");
         writer.write("    }\n\n");
         writer.write("    public static Set<Class<? extends Packet<?>>> getS2CPackets() {\n");
         writer.write("        return S2C_PACKETS.keySet();\n");
         writer.write("    }\n\n");
         writer.write("    public static Set<Class<? extends Packet<?>>> getC2SPackets() {\n");
         writer.write("        return C2S_PACKETS.keySet();\n");
         writer.write("    }\n\n");
         writer.write(
            "    private static class PacketRegistry extends SimpleRegistry<Class<? extends Packet<?>>> {\n        public PacketRegistry() {\n            super(RegistryKey.ofRegistry(MeteorClient.identifier(\"packets\")), Lifecycle.stable());\n        }\n\n        @Override\n        public int size() {\n            return S2C_PACKETS.keySet().size() + C2S_PACKETS.keySet().size();\n        }\n\n        @Override\n        public Identifier getId(Class<? extends Packet<?>> entry) {\n            return null;\n        }\n\n        @Override\n        public Optional<RegistryKey<Class<? extends Packet<?>>>> getKey(Class<? extends Packet<?>> entry) {\n            return Optional.empty();\n        }\n\n        @Override\n        public int getRawId(Class<? extends Packet<?>> entry) {\n            return 0;\n        }\n\n        @Override\n        public Class<? extends Packet<?>> get(RegistryKey<Class<? extends Packet<?>>> key) {\n            return null;\n        }\n\n        @Override\n        public Class<? extends Packet<?>> get(Identifier id) {\n            return null;\n        }\n\n        @Override\n        public Lifecycle getLifecycle() {\n            return null;\n        }\n\n        @Override\n        public Set<Identifier> getIds() {\n            return Collections.emptySet();\n        }\n\n        @Override\n        public boolean containsId(Identifier id) {\n            return false;\n        }\n\n        @Override\n        public Class<? extends Packet<?>> get(int index) {\n            return null;\n        }\n\n        @NotNull\n        @Override\n        public Iterator<Class<? extends Packet<?>>> iterator() {\n            return Stream.concat(S2C_PACKETS.keySet().stream(), C2S_PACKETS.keySet().stream()).iterator();\n        }\n\n        @Override\n        public boolean contains(RegistryKey<Class<? extends Packet<?>>> key) {\n            return false;\n        }\n\n        @Override\n        public Set<Map.Entry<RegistryKey<Class<? extends Packet<?>>>, Class<? extends Packet<?>>>> getEntrySet() {\n            return Collections.emptySet();\n        }\n\n        @Override\n        public Optional<RegistryEntry.Reference<Class<? extends Packet<?>>>> getRandom(Random random) {\n            return Optional.empty();\n        }\n\n        @Override\n        public Registry<Class<? extends Packet<?>>> freeze() {\n            return null;\n        }\n\n        @Override\n        public RegistryEntry.Reference<Class<? extends Packet<?>>> createEntry(Class<? extends Packet<?>> value) {\n            return null;\n        }\n\n        @Override\n        public Optional<RegistryEntry.Reference<Class<? extends Packet<?>>>> getEntry(int rawId) {\n            return Optional.empty();\n        }\n\n        @Override\n        public Optional<RegistryEntry.Reference<Class<? extends Packet<?>>>> getEntry(RegistryKey<Class<? extends Packet<?>>> key) {\n            return Optional.empty();\n        }\n\n        @Override\n        public Stream<RegistryEntry.Reference<Class<? extends Packet<?>>>> streamEntries() {\n            return null;\n        }\n\n        @Override\n        public Optional<RegistryEntryList.Named<Class<? extends Packet<?>>>> getEntryList(TagKey<Class<? extends Packet<?>>> tag) {\n            return Optional.empty();\n        }\n\n        @Override\n        public RegistryEntryList.Named<Class<? extends Packet<?>>> getOrCreateEntryList(TagKey<Class<? extends Packet<?>>> tag) {\n            return null;\n        }\n\n        @Override\n        public Stream<Pair<TagKey<Class<? extends Packet<?>>>, RegistryEntryList.Named<Class<? extends Packet<?>>>>> streamTagsAndEntries() {\n            return null;\n        }\n\n        @Override\n        public Stream<TagKey<Class<? extends Packet<?>>>> streamTags() {\n            return null;\n        }\n\n        @Override\n        public void clearTags() {}\n\n        @Override\n        public void populateTags(Map<TagKey<Class<? extends Packet<?>>>, List<RegistryEntry<Class<? extends Packet<?>>>>> tagEntries) {}\n\n        @Override\n        public Set<RegistryKey<Class<? extends Packet<?>>>> getKeys() {\n            return Collections.emptySet();\n        }\n    }\n"
         );
         writer.write("}\n");
      }
   }
}
