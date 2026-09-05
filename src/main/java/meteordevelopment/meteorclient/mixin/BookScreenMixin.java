package meteordevelopment.meteorclient.mixin;

import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.EditBookTitleAndAuthorScreen;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BookViewScreen.class})
public abstract class BookScreenMixin extends Screen {
   @Shadow
   private BookAccess bookAccess;
   @Shadow
   private int currentPage;

   public BookScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      this.addRenderableWidget(new Builder(Component.literal("Copy"), button -> {
         ListTag listTag = new ListTag();

         for (int i = 0; i < this.bookAccess.getPageCount(); i++) {
            listTag.add(StringTag.valueOf(this.bookAccess.getPage(i).getString()));
         }

         CompoundTag tag = new CompoundTag();
         tag.put("pages", listTag);
         tag.putInt("currentPage", this.currentPage);
         FastByteArrayOutputStream bytes = new FastByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(bytes);

         try {
            NbtIo.writeUnnamedTagWithFallback(tag, out);
         } catch (IOException var11) {
            var11.printStackTrace();
         }

         String encoded = Base64.getEncoder().encodeToString(bytes.array);
         long available = (long)MemoryStack.stackGet().getPointer();
         long size = (long)MemoryUtil.memLengthUTF8(encoded, true);
         if (size > available) {
            ChatUtils.error("Could not copy to clipboard: Out of memory.");
         } else {
            GLFW.glfwSetClipboardString(MeteorClient.mc.getWindow().getWindow(), encoded);
         }
      }).pos(4, 4).size(120, 20).build());
      ItemStack itemStack = MeteorClient.mc.player.getMainHandItem();
      InteractionHand hand = InteractionHand.MAIN_HAND;
      if (itemStack.getItem() != Items.WRITTEN_BOOK) {
         itemStack = MeteorClient.mc.player.getOffhandItem();
         hand = InteractionHand.OFF_HAND;
      }

      if (itemStack.getItem() == Items.WRITTEN_BOOK) {
         ItemStack book = itemStack;
         InteractionHand hand2 = hand;
         this.addRenderableWidget(
            new Builder(
                  Component.literal("Edit title & author"), button -> MeteorClient.mc.setScreen(new EditBookTitleAndAuthorScreen(GuiThemes.get(), book, hand2))
               )
               .pos(4, 26)
               .size(120, 20)
               .build()
         );
      }
   }
}
