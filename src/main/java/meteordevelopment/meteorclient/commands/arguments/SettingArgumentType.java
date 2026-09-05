package meteordevelopment.meteorclient.commands.arguments;

import com.google.common.collect.Streams;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class SettingArgumentType implements ArgumentType<String> {
   private static final SettingArgumentType INSTANCE = new SettingArgumentType();
   private static final DynamicCommandExceptionType NO_SUCH_SETTING = new DynamicCommandExceptionType(
      name -> Component.literal("No such setting '" + name + "'.")
   );

   public static SettingArgumentType create() {
      return INSTANCE;
   }

   public static Setting<?> get(CommandContext<?> context) throws CommandSyntaxException {
      Module module = (Module)context.getArgument("module", Module.class);
      String settingName = (String)context.getArgument("setting", String.class);
      Setting<?> setting = module.settings.get(settingName);
      if (setting == null) {
         throw NO_SUCH_SETTING.create(settingName);
      } else {
         return setting;
      }
   }

   private SettingArgumentType() {
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      Stream<String> stream = Streams.stream(((Module)context.getArgument("module", Module.class)).settings.iterator())
         .flatMap(settings -> Streams.stream(settings.iterator()))
         .map(setting -> setting.name);
      return SharedSuggestionProvider.suggest(stream, builder);
   }
}
