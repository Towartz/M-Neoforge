package meteordevelopment.meteorclient.systems.modules.combat.plus;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class AutoLeave extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> visualRangeIgnoreFriends = this.sgGeneral.add(new BoolSetting.Builder().name("ignore-friends").description("Ignores friends.").defaultValue(true).build());
    private final Setting<Boolean> autoDisable = this.sgGeneral.add(new BoolSetting.Builder().name("auto-disable").description("Disables module after player detect.").defaultValue(true).build());
    private final Setting<Boolean> command = this.sgGeneral.add(new BoolSetting.Builder().name("command").description("Send command instead of leave.").defaultValue(false).build());
    private final Setting<String> commandStr = this.sgGeneral.add(new StringSetting.Builder().name("command:").description("Command to send in chat.").defaultValue("/spawn").visible(this.command::get).build());

    public AutoLeave() {
        super(Categories.PlusCombat, "auto-leave", "Automatically logs out or warps away when someone enters your render distance.");
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (this.mc.player == null || !(event.entity instanceof Player target) || target == this.mc.player) return;

        if (this.visualRangeIgnoreFriends.get() && Friends.get().isFriend(target)) return;

        if (this.command.get()) {
            ChatUtils.sendPlayerMsg(this.commandStr.get());
            this.info("Player §c" + target.getName().getString() + "§r was detected! Dispatched command.", new Object[0]);
        } else if (this.mc.getConnection() != null) {
            this.mc.getConnection().getConnection().disconnect(Component.literal("[AutoLeave] Player " + target.getName().getString() + " was detected in render distance!"));
        }

        if (this.autoDisable.get()) {
            this.toggle();
        }
    }
}
