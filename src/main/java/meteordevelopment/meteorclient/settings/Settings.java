package meteordevelopment.meteorclient.settings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.render.color.RainbowColors;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

public class Settings implements ISerializable<Settings>, Iterable<SettingGroup> {
   private SettingGroup defaultGroup;
   public final List<SettingGroup> groups = new ArrayList<>(1);

   public void onActivated() {
      for (SettingGroup group : this.groups) {
         for (Setting<?> setting : group) {
            setting.onActivated();
         }
      }
   }

   public Setting<?> get(String name) {
      for (SettingGroup sg : this) {
         for (Setting<?> setting : sg) {
            if (name.equalsIgnoreCase(setting.name)) {
               return setting;
            }
         }
      }

      return null;
   }

   public void reset() {
      for (SettingGroup group : this.groups) {
         for (Setting<?> setting : group) {
            setting.reset();
         }
      }
   }

   public SettingGroup getGroup(String name) {
      for (SettingGroup sg : this) {
         if (sg.name.equals(name)) {
            return sg;
         }
      }

      return null;
   }

   public int sizeGroups() {
      return this.groups.size();
   }

   public SettingGroup getDefaultGroup() {
      if (this.defaultGroup == null) {
         this.defaultGroup = this.createGroup("General");
      }

      return this.defaultGroup;
   }

   public SettingGroup createGroup(String name, boolean expanded) {
      SettingGroup group = new SettingGroup(name, expanded);
      this.groups.add(group);
      return group;
   }

   public SettingGroup createGroup(String name) {
      return this.createGroup(name, true);
   }

   public void registerColorSettings(Module module) {
      for (SettingGroup group : this) {
         for (Setting<?> setting : group) {
            setting.module = module;
            if (setting instanceof ColorSetting) {
               RainbowColors.addSetting((Setting<SettingColor>)setting);
            } else if (setting instanceof ColorListSetting) {
               RainbowColors.addSettingList((Setting<List<SettingColor>>)setting);
            }
         }
      }
   }

   public void unregisterColorSettings() {
      for (SettingGroup group : this) {
         for (Setting<?> setting : group) {
            if (setting instanceof ColorSetting) {
               RainbowColors.removeSetting((Setting<SettingColor>)setting);
            } else if (setting instanceof ColorListSetting) {
               RainbowColors.removeSettingList((Setting<List<SettingColor>>)setting);
            }
         }
      }
   }

   public void tick(WContainer settings, GuiTheme theme) {
      for (SettingGroup group : this.groups) {
         for (Setting<?> setting : group) {
            boolean visible = setting.isVisible();
            if (visible != setting.lastWasVisible) {
               settings.clear();
               settings.add(theme.settings(this)).expandX();
            }

            setting.lastWasVisible = visible;
         }
      }
   }

   @NotNull
   @Override
   public Iterator<SettingGroup> iterator() {
      return this.groups.iterator();
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.put("groups", NbtUtils.listToTag(this.groups));
      return tag;
   }

   public Settings fromTag(CompoundTag tag) {
      for (Tag t : tag.getList("groups", 10)) {
         CompoundTag groupTag = (CompoundTag)t;
         SettingGroup sg = this.getGroup(groupTag.getString("name"));
         if (sg != null) {
            sg.fromTag(groupTag);
         }
      }

      return this;
   }
}
