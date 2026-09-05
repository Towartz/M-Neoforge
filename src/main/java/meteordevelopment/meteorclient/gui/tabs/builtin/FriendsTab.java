package meteordevelopment.meteorclient.gui.tabs.builtin;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.gui.screens.Screen;

public class FriendsTab extends Tab {
   public FriendsTab() {
      super("Friends");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new FriendsTab.FriendsScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof FriendsTab.FriendsScreen;
   }

   private static class FriendsScreen extends WindowTabScreen {
      public FriendsScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
      }

      @Override
      public void initWidgets() {
         WTable table = this.add(this.theme.table()).expandX().minWidth(400.0).widget();
         this.initTable(table);
         this.add(this.theme.horizontalSeparator()).expandX();
         WHorizontalList list = this.add(this.theme.horizontalList()).expandX().widget();
         WTextBox nameW = list.add(this.theme.textBox("", (text, c) -> c != ' ')).expandX().widget();
         nameW.setFocused(true);
         WPlus add = list.add(this.theme.plus()).widget();
         add.action = () -> {
            String name = nameW.get().trim();
            Friend friend = new Friend(name);
            if (Friends.get().add(friend)) {
               nameW.set("");
               this.reload();
               MeteorExecutor.execute(() -> {
                  friend.updateInfo();
                  this.reload();
               });
            }
         };
         this.enterAction = add.action;
      }

      private void initTable(WTable table) {
         table.clear();
         if (!Friends.get().isEmpty()) {
            Friends.get().forEach(friendx -> MeteorExecutor.execute(() -> {
                  if (friendx.headTextureNeedsUpdate()) {
                     friendx.updateInfo();
                     this.reload();
                  }
               }));

            for (Friend friend : Friends.get()) {
               table.add(this.theme.texture(32.0, 32.0, friend.getHead().needsRotate() ? 90.0 : 0.0, friend.getHead()));
               table.add(this.theme.label(friend.getName()));
               WMinus remove = table.add(this.theme.minus()).expandCellX().right().widget();
               remove.action = () -> {
                  Friends.get().remove(friend);
                  this.reload();
               };
               table.row();
            }
         }
      }

      @Override
      public boolean toClipboard() {
         return NbtUtils.toClipboard(Friends.get());
      }

      @Override
      public boolean fromClipboard() {
         return NbtUtils.fromClipboard(Friends.get());
      }
   }
}
