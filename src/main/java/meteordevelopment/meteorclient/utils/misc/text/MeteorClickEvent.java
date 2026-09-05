package meteordevelopment.meteorclient.utils.misc.text;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Action;

public class MeteorClickEvent extends ClickEvent {
   public MeteorClickEvent(Action action, String value) {
      super(action, value);
   }
}
