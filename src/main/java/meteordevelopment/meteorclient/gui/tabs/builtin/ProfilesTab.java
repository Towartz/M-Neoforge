package meteordevelopment.meteorclient.gui.tabs.builtin;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.systems.profiles.Profile;
import meteordevelopment.meteorclient.systems.profiles.Profiles;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.client.gui.screens.Screen;

public class ProfilesTab extends Tab {
   public ProfilesTab() {
      super("Profiles");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new ProfilesTab.ProfilesScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof ProfilesTab.ProfilesScreen;
   }

   private static class EditProfileScreen extends WindowScreen {
      private WContainer settingsContainer;
      private final Profile profile;
      private final boolean isNew;
      private final Runnable action;

      public EditProfileScreen(GuiTheme theme, Profile profile, Runnable action) {
         super(theme, profile == null ? "New Profile" : "Edit Profile");
         this.isNew = profile == null;
         this.profile = this.isNew ? new Profile() : profile;
         this.action = action;
      }

      @Override
      public void initWidgets() {
         this.settingsContainer = this.add(this.theme.verticalList()).expandX().minWidth(400.0).widget();
         this.settingsContainer.add(this.theme.settings(this.profile.settings)).expandX();
         this.add(this.theme.horizontalSeparator()).expandX();
         WButton save = this.add(this.theme.button(this.isNew ? "Create" : "Save")).expandX().widget();
         save.action = () -> {
            if (!this.profile.name.get().isEmpty()) {
               if (this.isNew) {
                  for (Profile p : Profiles.get()) {
                     if (this.profile.equals(p)) {
                        return;
                     }
                  }
               }

               List<String> valid = new ArrayList<>();

               for (String address : this.profile.loadOnJoin.get()) {
                  if (Utils.resolveAddress(address)) {
                     valid.add(address);
                  }
               }

               this.profile.loadOnJoin.set(valid);
               if (this.isNew) {
                  Profiles.get().add(this.profile);
               } else {
                  Profiles.get().save();
               }

               this.onClose();
            }
         };
         this.enterAction = save.action;
      }

      public void tick() {
         this.profile.settings.tick(this.settingsContainer, this.theme);
      }

      @Override
      protected void onClosed() {
         if (this.action != null) {
            this.action.run();
         }
      }
   }

   private static class ProfilesScreen extends WindowTabScreen {
      public ProfilesScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
      }

      @Override
      public void initWidgets() {
         WTable table = this.add(this.theme.table()).expandX().minWidth(400.0).widget();
         this.initTable(table);
         this.add(this.theme.horizontalSeparator()).expandX();
         WButton create = this.add(this.theme.button("Create")).expandX().widget();
         create.action = () -> MeteorClient.mc.setScreen(new ProfilesTab.EditProfileScreen(this.theme, null, this::reload));
      }

      private void initTable(WTable table) {
         table.clear();
         if (!Profiles.get().isEmpty()) {
            for (Profile profile : Profiles.get()) {
               table.add(this.theme.label(profile.name.get())).expandCellX();
               WButton save = table.add(this.theme.button("Save")).widget();
               save.action = profile::save;
               WButton load = table.add(this.theme.button("Load")).widget();
               load.action = profile::load;
               WButton edit = table.add(this.theme.button(GuiRenderer.EDIT)).widget();
               edit.action = () -> MeteorClient.mc.setScreen(new ProfilesTab.EditProfileScreen(this.theme, profile, this::reload));
               WMinus remove = table.add(this.theme.minus()).widget();
               remove.action = () -> {
                  Profiles.get().remove(profile);
                  this.reload();
               };
               table.row();
            }
         }
      }

      @Override
      public boolean toClipboard() {
         return NbtUtils.toClipboard(Profiles.get());
      }

      @Override
      public boolean fromClipboard() {
         return NbtUtils.fromClipboard(Profiles.get());
      }
   }
}
