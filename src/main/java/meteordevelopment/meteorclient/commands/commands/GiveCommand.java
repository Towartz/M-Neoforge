package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;

public class GiveCommand extends Command {
   private static final SimpleCommandExceptionType NOT_IN_CREATIVE = new SimpleCommandExceptionType(
      Component.literal("You must be in creative mode to use this.")
   );
   private static final SimpleCommandExceptionType NO_SPACE = new SimpleCommandExceptionType(Component.literal("No space in hotbar."));

   public GiveCommand() {
      super("give", "Gives you any item.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(((RequiredArgumentBuilder)argument("item", ItemArgument.item(REGISTRY_ACCESS)).executes(context -> {
         if (!mc.player.getAbilities().instabuild) {
            throw NOT_IN_CREATIVE.create();
         } else {
            ItemStack item = ItemArgument.getItem(context, "item").createItemStack(1, false);
            FindItemResult fir = InvUtils.find(ItemStack::isEmpty, 0, 8);
            if (!fir.found()) {
               throw NO_SPACE.create();
            } else {
               mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + fir.slot(), item));
               return 1;
            }
         }
      })).then(argument("number", IntegerArgumentType.integer()).executes(context -> {
         if (!mc.player.getAbilities().instabuild) {
            throw NOT_IN_CREATIVE.create();
         } else {
            ItemStack item = ItemArgument.getItem(context, "item").createItemStack(IntegerArgumentType.getInteger(context, "number"), false);
            FindItemResult fir = InvUtils.find(ItemStack::isEmpty, 0, 8);
            if (!fir.found()) {
               throw NO_SPACE.create();
            } else {
               mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + fir.slot(), item));
               return 1;
            }
         }
      })));
   }
}
