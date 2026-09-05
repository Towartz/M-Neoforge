package meteordevelopment.meteorclient.mixin;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessage.Line;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ChatComponent.class})
public interface ChatHudAccessor {
   @Accessor("trimmedMessages")
   List<Line> getVisibleMessages();

   @Accessor("allMessages")
   List<GuiMessage> getMessages();
}
