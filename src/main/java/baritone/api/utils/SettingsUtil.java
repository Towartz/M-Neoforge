package baritone.api.utils;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;

public class SettingsUtil {
   public static final String SETTINGS_DEFAULT_NAME = "settings.txt";
   private static final Pattern SETTING_PATTERN = Pattern.compile("^(?<setting>[^ ]+) +(?<value>.+)");

   private static boolean isComment(String line) {
      return line.startsWith("#") || line.startsWith("//");
   }

   private static void forEachLine(Path file, Consumer<String> consumer) throws IOException {
      String line;
      try (BufferedReader scan = Files.newBufferedReader(file)) {
         while ((line = scan.readLine()) != null) {
            if (!line.isEmpty() && !isComment(line)) {
               consumer.accept(line);
            }
         }
      }
   }

   public static void readAndApply(Settings settings, String settingsName) {
      try {
         forEachLine(settingsByName(settingsName), line -> {
            Matcher matcher = SETTING_PATTERN.matcher(line);
            if (!matcher.matches()) {
               Helper.HELPER.logDirect("Invalid syntax in setting file: " + line);
            } else {
               String settingName = matcher.group("setting").toLowerCase();
               String settingValue = matcher.group("value");
               if ("allowjumpat256".equals(settingName)) {
                  settingName = "allowjumpatbuildlimit";
               }

               try {
                  parseAndApply(settings, settingName, settingValue);
               } catch (Exception var6) {
                  Helper.HELPER.logDirect("Unable to parse line " + line);
                  var6.printStackTrace();
               }
            }
         });
      } catch (NoSuchFileException var3) {
         Helper.HELPER.logDirect("Baritone settings file not found, resetting.");
      } catch (Exception var4) {
         Helper.HELPER.logDirect("Exception while reading Baritone settings, some settings may be reset to default values!");
         var4.printStackTrace();
      }
   }

   public static synchronized void save(Settings settings) {
      try (BufferedWriter out = Files.newBufferedWriter(settingsByName("settings.txt"))) {
         for (Settings.Setting setting : modifiedSettings(settings)) {
            out.write(settingToString(setting) + "\n");
         }
      } catch (Exception var6) {
         Helper.HELPER.logDirect("Exception thrown while saving Baritone settings!");
         var6.printStackTrace();
      }
   }

   private static Path settingsByName(String name) {
      return Minecraft.getInstance().gameDirectory.toPath().resolve("baritone").resolve(name);
   }

   public static List<Settings.Setting> modifiedSettings(Settings settings) {
      List<Settings.Setting> modified = new ArrayList<>();

      for (Settings.Setting setting : settings.allSettings) {
         if (setting.value == null) {
            System.out.println("NULL SETTING?" + setting.getName());
         } else if (!setting.isJavaOnly() && setting.value != setting.defaultValue) {
            modified.add(setting);
         }
      }

      return modified;
   }

   public static String settingTypeToString(Settings.Setting setting) {
      return setting.getType().getTypeName().replaceAll("(?:\\w+\\.)+(\\w+)", "$1");
   }

   public static <T> String settingValueToString(Settings.Setting<T> setting, T value) throws IllegalArgumentException {
      SettingsUtil.Parser io = SettingsUtil.Parser.getParser(setting.getType());
      if (io == null) {
         throw new IllegalStateException("Missing " + setting.getValueClass() + " " + setting.getName());
      } else {
         return io.toString(setting.getType(), value);
      }
   }

   public static String settingValueToString(Settings.Setting setting) throws IllegalArgumentException {
      return settingValueToString(setting, setting.value);
   }

   public static String settingDefaultToString(Settings.Setting setting) throws IllegalArgumentException {
      return settingValueToString(setting, setting.defaultValue);
   }

   public static String maybeCensor(int coord) {
      return BaritoneAPI.getSettings().censorCoordinates.value ? "<censored>" : Integer.toString(coord);
   }

   public static String settingToString(Settings.Setting setting) throws IllegalStateException {
      return setting.isJavaOnly() ? setting.getName() : setting.getName() + " " + settingValueToString(setting);
   }

   @Deprecated
   public static boolean javaOnlySetting(Settings.Setting setting) {
      return setting.isJavaOnly();
   }

   public static void parseAndApply(Settings settings, String settingName, String settingValue) throws IllegalStateException, NumberFormatException {
      Settings.Setting setting = settings.byLowerName.get(settingName);
      if (setting == null) {
         throw new IllegalStateException("No setting by that name");
      } else {
         Class intendedType = setting.getValueClass();
         SettingsUtil.ISettingParser ioMethod = SettingsUtil.Parser.getParser(setting.getType());
         Object parsed = ioMethod.parse(setting.getType(), settingValue);
         if (!intendedType.isInstance(parsed)) {
            throw new IllegalStateException(
               ioMethod + " parser returned incorrect type, expected " + intendedType + " got " + parsed + " which is " + parsed.getClass()
            );
         } else {
            setting.value = parsed;
         }
      }
   }

   private interface ISettingParser<T> {
      T parse(Type var1, String var2);

      String toString(Type var1, T var2);

      boolean accepts(Type var1);
   }

   private static enum Parser implements SettingsUtil.ISettingParser<Object> {
      DOUBLE(Double.class, Double::parseDouble),
      BOOLEAN(Boolean.class, Boolean::parseBoolean),
      INTEGER(Integer.class, Integer::parseInt),
      FLOAT(Float.class, Float::parseFloat),
      LONG(Long.class, Long::parseLong),
      STRING(String.class, String::new),
      MIRROR(Mirror.class, Mirror::valueOf, Enum::name),
      ROTATION(net.minecraft.world.level.block.Rotation.class, net.minecraft.world.level.block.Rotation::valueOf, Enum::name),
      COLOR(
         Color.class,
         str -> new Color(Integer.parseInt(str.split(",")[0]), Integer.parseInt(str.split(",")[1]), Integer.parseInt(str.split(",")[2])),
         color -> color.getRed() + "," + color.getGreen() + "," + color.getBlue()
      ),
      VEC3I(
         Vec3i.class,
         str -> new Vec3i(Integer.parseInt(str.split(",")[0]), Integer.parseInt(str.split(",")[1]), Integer.parseInt(str.split(",")[2])),
         vec -> vec.getX() + "," + vec.getY() + "," + vec.getZ()
      ),
      BLOCK(Block.class, str -> BlockUtils.stringToBlockRequired(str.trim()), BlockUtils::blockToString),
      ITEM(Item.class, str -> (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(str.trim())), item -> BuiltInRegistries.ITEM.getKey(item).toString()),
      LIST {
         @Override
         public Object parse(Type type, String raw) {
            Type elementType = ((ParameterizedType)type).getActualTypeArguments()[0];
            SettingsUtil.Parser parser = SettingsUtil.Parser.getParser(elementType);
            return Stream.of(raw.split(",")).map(s -> parser.parse(elementType, s)).collect(Collectors.toList());
         }

         @Override
         public String toString(Type type, Object value) {
            Type elementType = ((ParameterizedType)type).getActualTypeArguments()[0];
            SettingsUtil.Parser parser = SettingsUtil.Parser.getParser(elementType);
            return ((List<?>)value).stream().map(o -> (CharSequence)parser.toString(elementType, o)).collect(Collectors.joining(","));
         }

         @Override
         public boolean accepts(Type type) {
            return List.class.isAssignableFrom(TypeUtils.resolveBaseClass(type));
         }
      },
      MAPPING {
         @Override
         public Object parse(Type type, String raw) {
            Type keyType = ((ParameterizedType)type).getActualTypeArguments()[0];
            Type valueType = ((ParameterizedType)type).getActualTypeArguments()[1];
            SettingsUtil.Parser keyParser = SettingsUtil.Parser.getParser(keyType);
            SettingsUtil.Parser valueParser = SettingsUtil.Parser.getParser(valueType);
            return Stream.of(raw.split(",(?=[^,]*->)"))
               .map(s -> s.split("->"))
               .collect(Collectors.toMap(s -> keyParser.parse(keyType, s[0]), s -> valueParser.parse(valueType, s[1])));
         }

         @Override
         public String toString(Type type, Object value) {
            Type keyType = ((ParameterizedType)type).getActualTypeArguments()[0];
            Type valueType = ((ParameterizedType)type).getActualTypeArguments()[1];
            SettingsUtil.Parser keyParser = SettingsUtil.Parser.getParser(keyType);
            SettingsUtil.Parser valueParser = SettingsUtil.Parser.getParser(valueType);
            return ((Map<?, ?>)value)
               .entrySet()
               .stream()
               .map(entry -> (CharSequence)(keyParser.toString(keyType, entry.getKey()) + "->" + valueParser.toString(valueType, entry.getValue())))
               .collect(Collectors.joining(","));
         }

         @Override
         public boolean accepts(Type type) {
            return Map.class.isAssignableFrom(TypeUtils.resolveBaseClass(type));
         }
      };

      private final Class<?> cla$$;
      private final Function<String, Object> parser;
      private final Function<Object, String> toString;

      private Parser() {
         this.cla$$ = null;
         this.parser = null;
         this.toString = null;
      }

      private <T> Parser(Class<T> cla$$, Function<String, T> parser) {
         this(cla$$, parser, Object::toString);
      }

      private <T> Parser(Class<T> cla$$, Function<String, T> parser, Function<T, String> toString) {
         this.cla$$ = cla$$;
         this.parser = parser::apply;
         this.toString = x -> toString.apply((T)x);
      }

      @Override
      public Object parse(Type type, String raw) {
         Object parsed = this.parser.apply(raw);
         Objects.requireNonNull(parsed);
         return parsed;
      }

      @Override
      public String toString(Type type, Object value) {
         return this.toString.apply(value);
      }

      @Override
      public boolean accepts(Type type) {
         return type instanceof Class && this.cla$$.isAssignableFrom((Class<?>)type);
      }

      public static SettingsUtil.Parser getParser(Type type) {
         return Stream.of(values()).filter(parser -> parser.accepts(type)).findFirst().orElse(null);
      }
   }
}
