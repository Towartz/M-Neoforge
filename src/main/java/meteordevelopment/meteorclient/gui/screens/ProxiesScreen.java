package meteordevelopment.meteorclient.gui.screens;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.proxies.Proxies;
import meteordevelopment.meteorclient.systems.proxies.Proxy;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class ProxiesScreen extends WindowScreen {
   private final List<WCheckbox> checkboxes = new ArrayList<>();

   public ProxiesScreen(GuiTheme theme) {
      super(theme, "Proxies");
   }

   @Override
   public void initWidgets() {
      WTable table = this.add(this.theme.table()).expandX().minWidth(400.0).widget();
      this.initTable(table);
      this.add(this.theme.horizontalSeparator()).expandX();
      WHorizontalList l = this.add(this.theme.horizontalList()).expandX().widget();
      WButton newBtn = l.add(this.theme.button("New")).expandX().widget();
      newBtn.action = () -> MeteorClient.mc.setScreen(new ProxiesScreen.EditProxyScreen(this.theme, null, this::reload));
      PointerBuffer filters = BufferUtils.createPointerBuffer(1);
      ByteBuffer txtFilter = MemoryUtil.memASCII("*.txt");
      filters.put(txtFilter);
      filters.rewind();
      WButton importBtn = l.add(this.theme.button("Import")).expandX().widget();
      importBtn.action = () -> {
         String selectedFile = TinyFileDialogs.tinyfd_openFileDialog("Import Proxies", null, filters, null, false);
         if (selectedFile != null) {
            File file = new File(selectedFile);
            MeteorClient.mc.setScreen(new ProxiesImportScreen(this.theme, file));
         }
      };
   }

   private void initTable(WTable table) {
      table.clear();
      if (!Proxies.get().isEmpty()) {
         for (Proxy proxy : Proxies.get()) {
            WCheckbox enabled = table.add(this.theme.checkbox(proxy.enabled.get())).widget();
            this.checkboxes.add(enabled);
            enabled.action = () -> {
               boolean checked = enabled.checked;
               Proxies.get().setEnabled(proxy, checked);

               for (WCheckbox checkbox : this.checkboxes) {
                  checkbox.checked = false;
               }

               enabled.checked = checked;
            };
            WLabel name = table.add(this.theme.label(proxy.name.get())).widget();
            name.color = this.theme.textColor();
            WLabel type = table.add(this.theme.label("(" + proxy.type.get() + ")")).widget();
            type.color = this.theme.textSecondaryColor();
            WHorizontalList ipList = table.add(this.theme.horizontalList()).expandCellX().widget();
            ipList.spacing = 0.0;
            ipList.add(this.theme.label(proxy.address.get()));
            ipList.add(this.theme.label(":")).widget().color = this.theme.textSecondaryColor();
            ipList.add(this.theme.label(Integer.toString(proxy.port.get())));
            WButton edit = table.add(this.theme.button(GuiRenderer.EDIT)).widget();
            edit.action = () -> MeteorClient.mc.setScreen(new ProxiesScreen.EditProxyScreen(this.theme, proxy, this::reload));
            WMinus remove = table.add(this.theme.minus()).widget();
            remove.action = () -> {
               Proxies.get().remove(proxy);
               this.reload();
            };
            table.row();
         }
      }
   }

   @Override
   public boolean toClipboard() {
      return NbtUtils.toClipboard(Proxies.get());
   }

   @Override
   public boolean fromClipboard() {
      return NbtUtils.fromClipboard(Proxies.get());
   }

   protected static class EditProxyScreen extends EditSystemScreen<Proxy> {
      public EditProxyScreen(GuiTheme theme, Proxy value, Runnable reload) {
         super(theme, value, reload);
      }

      public Proxy create() {
         return new Proxy.Builder().build();
      }

      @Override
      public boolean save() {
         return this.value.resolveAddress() && (!this.isNew || Proxies.get().add(this.value));
      }

      @Override
      public Settings getSettings() {
         return this.value.settings;
      }
   }
}
