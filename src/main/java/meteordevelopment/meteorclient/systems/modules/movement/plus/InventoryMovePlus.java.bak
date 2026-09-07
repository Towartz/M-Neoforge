package meteordevelopment.meteorclient.systems.modules.movement.plus;

import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerTickMovementEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.GuiKeyEvents;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.mixin.CreativeInventoryScreenAccessor;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTabs;

public class InventoryMovePlus extends Module {
    public enum Bypass {
        No_Open_Packet,
        None;
        @Override public String toString() { return super.toString().replace('_', ' '); }
    }

    public enum NoSprint {
        Real,
        Packet_Spoof,
        None;
        @Override public String toString() { return super.toString().replace('_', ' '); }
    }

    public enum Screens {
        GUI,
        Inventory,
        Both
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Bypass> bypassSetting = sgGeneral.add(new EnumSetting.Builder<Bypass>()
        .name("bypass")
        .description("Bypass mode.")
        .defaultValue(Bypass.None)
        .build()
    );

    private final Setting<NoSprint> noSprintSetting = sgGeneral.add(new EnumSetting.Builder<NoSprint>()
        .name("no-sprint")
        .description("NoSprint Bypass mode.")
        .defaultValue(NoSprint.None)
        .build()
    );

    private final Setting<Boolean> noMoveClicks = sgGeneral.add(new BoolSetting.Builder()
        .name("no-move-clicks")
        .description("Block clicks while moving.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Screens> screens = sgGeneral.add(new EnumSetting.Builder<Screens>()
        .name("guis")
        .description("Which GUIs to move in.")
        .defaultValue(Screens.Both)
        .build()
    );

    private final Setting<Boolean> jump = sgGeneral.add(new BoolSetting.Builder()
        .name("jump")
        .description("Allows you to jump while in GUIs.")
        .defaultValue(true)
        .onChanged(val -> { if (isActive() && !val && mc != null) set(mc.options.keyJump, false); })
        .build()
    );

    private final Setting<Boolean> sneak = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak")
        .description("Allows you to sneak while in GUIs.")
        .defaultValue(false)
        .onChanged(val -> { if (isActive() && !val && mc != null) set(mc.options.keyShift, false); })
        .build()
    );

    private final Setting<Boolean> arrowsRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("arrows-rotate")
        .description("Allows you to use arrow keys to rotate while in GUIs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> rotateSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotate-speed")
        .description("Rotation speed while in GUIs.")
        .defaultValue(4.0)
        .min(0.0)
        .build()
    );

    public InventoryMovePlus() {
        super(Categories.PlusMovement, "gui-move+", "Move in inventories.");
    }

    @Override
    public void onDeactivate() {
        if (mc == null) return;
        set(mc.options.keyUp, false);
        set(mc.options.keyDown, false);
        set(mc.options.keyLeft, false);
        set(mc.options.keyRight, false);
        if (jump.get()) set(mc.options.keyJump, false);
        if (sneak.get()) set(mc.options.keyShift, false);
        if (noSprintSetting.get() == NoSprint.None) set(mc.options.keySprint, false);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundContainerClickPacket && noMoveClicks.get() && PlayerUtils.isMoving()) {
            event.cancel();
            return;
        }

        if (event.packet instanceof ServerboundPlayerCommandPacket packet) {
            if (packet.getAction() == ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY && bypassSetting.get() == Bypass.No_Open_Packet) {
                if (noSprintSetting.get() == NoSprint.Packet_Spoof && mc.player != null) {
                    if (mc.player.isSprinting()) {
                        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                    }
                    if (mc.player.isShiftKeyDown()) {
                        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                    }
                }
                event.cancel();
            }
        }

        if (event.packet instanceof ServerboundContainerClosePacket && noSprintSetting.get() == NoSprint.Packet_Spoof && mc.player != null) {
            if (mc.player.isSprinting()) {
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            }
            if (mc.player.isShiftKeyDown()) {
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            }
        }
    }

    @EventHandler
    private void onPlayerMoveEvent(PlayerTickMovementEvent event) {
        if (!skip() && isScreenValid()) {
            set(mc.options.keyUp, Input.isPressed(mc.options.keyUp));
            set(mc.options.keyDown, Input.isPressed(mc.options.keyDown));
            set(mc.options.keyLeft, Input.isPressed(mc.options.keyLeft));
            set(mc.options.keyRight, Input.isPressed(mc.options.keyRight));

            if (jump.get()) set(mc.options.keyJump, Input.isPressed(mc.options.keyJump));
            if (sneak.get()) set(mc.options.keyShift, Input.isPressed(mc.options.keyShift));
            if (noSprintSetting.get() == NoSprint.None) set(mc.options.keySprint, Input.isPressed(mc.options.keySprint));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!skip() && isScreenValid() && arrowsRotate.get() && mc.player != null) {
            float rotationDelta = Math.min((float)(rotateSpeed.get() * event.frameTime * 20.0), 100.0F);
            float yaw = mc.player.getYRot();
            float pitch = mc.player.getXRot();

            if (Input.isKeyPressed(263)) yaw -= rotationDelta;
            if (Input.isKeyPressed(262)) yaw += rotationDelta;
            if (Input.isKeyPressed(265)) pitch -= rotationDelta;
            if (Input.isKeyPressed(264)) pitch += rotationDelta;

            pitch = Mth.clamp(pitch, -90.0F, 90.0F);
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    private void set(KeyMapping bind, boolean pressed) {
        boolean wasPressed = bind.isDown();
        bind.setDown(pressed);
        Key key = ((KeyBindingAccessor) bind).getKey();
        if (wasPressed != pressed && key.getType() == Type.KEYSYM) {
            MeteorClient.EVENT_BUS.post(KeyEvent.get(key.getValue(), 0, pressed ? KeyAction.Press : KeyAction.Release));
        }
    }

    public boolean isScreenValid() {
        if (mc == null || mc.screen == null) return false;
        Screens mode = screens.get();
        boolean isWidget = mc.screen instanceof WidgetScreen;
        if (mode == Screens.Both) return true;
        if (mode == Screens.GUI) return isWidget;
        if (mode == Screens.Inventory) return !isWidget;
        return false;
    }

    public boolean skip() {
        if (mc == null || mc.screen == null) return true;
        if (mc.screen instanceof ChatScreen
            || mc.screen instanceof SignEditScreen
            || mc.screen instanceof AnvilScreen
            || mc.screen instanceof AbstractCommandBlockEditScreen
            || mc.screen instanceof StructureBlockEditScreen) return true;
        if (mc.screen instanceof CreativeModeInventoryScreen && CreativeInventoryScreenAccessor.getSelectedTab() == CreativeModeTabs.searchTab()) return true;
        if (mc.screen instanceof WidgetScreen && !GuiKeyEvents.canUseKeys) return true;
        if (mc.screen.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) return true;
        return false;
    }
}
