package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DropCommand extends Command {
   private static final SimpleCommandExceptionType NOT_SPECTATOR = new SimpleCommandExceptionType(Component.literal("Can't drop items while in spectator."));
   private static final SimpleCommandExceptionType NO_SUCH_ITEM = new SimpleCommandExceptionType(Component.literal("Could not find an item with that name!"));

   public DropCommand() {
      super("drop", "Automatically drops specified items.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("hand").executes(context -> this.drop(player -> player.drop(true))));
      builder.then(literal("offhand").executes(context -> this.drop(player -> InvUtils.drop().slotOffhand())));
      builder.then(literal("hotbar").executes(context -> this.drop(player -> {
            for (int i = 0; i < 9; i++) {
               InvUtils.drop().slotHotbar(i);
            }
         })));
      builder.then(literal("inventory").executes(context -> this.drop(player -> {
            for (int i = 9; i < player.getInventory().items.size(); i++) {
               InvUtils.drop().slotMain(i - 9);
            }
         })));
      builder.then(literal("all").executes(context -> this.drop(player -> {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
               InvUtils.drop().slot(i);
            }

            InvUtils.drop().slotOffhand();
         })));
      builder.then(literal("armor").executes(context -> this.drop(player -> {
            for (int i = 0; i < player.getInventory().armor.size(); i++) {
               InvUtils.drop().slotArmor(i);
            }
         })));
      builder.then(argument("item", ItemArgument.item(REGISTRY_ACCESS)).executes(context -> this.drop(player -> {
            ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
            if (stack != null && stack.getItem() != Items.AIR) {
               for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                  if (stack.getItem() == player.getInventory().getItem(i).getItem()) {
                     InvUtils.drop().slot(i);
                  }
               }
            } else {
               throw NO_SUCH_ITEM.create();
            }
         })));
   }

   private int drop(DropCommand.PlayerConsumer consumer) throws CommandSyntaxException {
      if (mc.player.isSpectator()) {
         throw NOT_SPECTATOR.create();
      } else {
         consumer.accept(mc.player);
         return 1;
      }
   }

   @FunctionalInterface
   private interface PlayerConsumer {
      void accept(LocalPlayer var1) throws CommandSyntaxException;
   }
}
