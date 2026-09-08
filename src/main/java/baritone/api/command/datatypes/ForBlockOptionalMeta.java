package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.utils.BlockOptionalMeta;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public enum ForBlockOptionalMeta implements IDatatypeFor<BlockOptionalMeta> {
   INSTANCE;

   private static Pattern PATTERN = Pattern.compile(
      "(?:[a-z0-9_.-]+:)?(?:[a-z0-9/_.-]+(?:\\[(?:(?:[a-z0-9_.-]+=[a-z0-9_.-]+,)*(?:[a-z0-9_.-]+(?:=(?:[a-z0-9_.-]+(?:\\])?)?)?)?|\\])?)?)?"
   );

   public BlockOptionalMeta get(IDatatypeContext ctx) throws CommandException {
      return new BlockOptionalMeta(ctx.getConsumer().getString());
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      String arg = ctx.getConsumer().peekString();
      if (!PATTERN.matcher(arg).matches()) {
         ctx.getConsumer().getString();
         return Stream.empty();
      } else if (arg.endsWith("]")) {
         ctx.getConsumer().getString();
         return Stream.empty();
      } else if (!arg.contains("[")) {
         return ctx.getConsumer().tabCompleteDatatype(BlockById.INSTANCE);
      } else {
         ctx.getConsumer().getString();
         String[] parts = splitLast(arg, '[');
         String blockId = parts[0];
         String properties = parts[1];
         Block block = (Block)BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId)).orElse(null);
         if (block == null) {
            return Stream.empty();
         } else {
            String[] partsx = splitLast(properties, ',');
            String leadingProperties = partsx[0];
            String lastProperty = partsx[1];
            if (!lastProperty.contains("=")) {
               Set<String> usedProps = Stream.of(leadingProperties.split(",")).map(pair -> pair.split("=")[0]).collect(Collectors.toSet());
               String prefix = arg.substring(0, arg.length() - lastProperty.length());
               return new TabCompleteHelper()
                  .append(block.getStateDefinition().getProperties().stream().map(Property::getName))
                  .filter(prop -> !usedProps.contains(prop))
                  .filterPrefix(lastProperty)
                  .sortAlphabetically()
                  .map(prop -> prefix + prop)
                  .stream();
            } else {
               String[] partsxx = splitLast(lastProperty, '=');
               String lastName = partsxx[0];
               String lastValue = partsxx[1];
               String prefix = arg.substring(0, arg.length() - lastValue.length());
               Property<?> property = block.getStateDefinition().getProperty(lastName);
               return property == null
                  ? Stream.empty()
                  : new TabCompleteHelper().append(getValues(property)).filterPrefix(lastValue).sortAlphabetically().map(val -> prefix + val).stream();
            }
         }
      }
   }

   private static String[] splitLast(String string, char chr) {
      int idx = string.lastIndexOf(chr);
      return idx == -1 ? new String[]{"", string} : new String[]{string.substring(0, idx), string.substring(idx + 1)};
   }

   private static <T extends Comparable<T>> Stream<String> getValues(Property<T> property) {
      return property.getPossibleValues().stream().map(property::getName);
   }
}
