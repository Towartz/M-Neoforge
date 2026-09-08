package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public enum ItemById implements IDatatypeFor<Item> {
   INSTANCE;

   public Item get(IDatatypeContext ctx) throws CommandException {
      ResourceLocation id = ResourceLocation.parse(ctx.getConsumer().getString());
      Item item;
      if ((item = (Item)BuiltInRegistries.ITEM.getOptional(id).orElse(null)) == null) {
         throw new IllegalArgumentException("No item found by that id");
      } else {
         return item;
      }
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString))
         .filterPrefixNamespaced(ctx.getConsumer().getString())
         .sortAlphabetically()
         .stream();
   }
}
