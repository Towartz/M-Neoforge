package meteordevelopment.meteorclient.systems.modules.misc.plus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

public class AutoAccept extends Module {
    public enum Mode {
        Default,
        DonutSMP,
        Custom
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Server teleport pattern.")
        .defaultValue(Mode.Default)
        .build()
    );

    private final Setting<String> customPattern = this.sgGeneral.add(new StringSetting.Builder()
        .name("custom-pattern")
        .description("Regex pattern with capture group 1 as sender name.")
        .defaultValue("(?i)(\\w+) has requested to teleport")
        .visible(() -> this.mode.get() == Mode.Custom)
        .build()
    );

    private final Setting<String> acceptCommand = this.sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("Command to run to accept request.")
        .defaultValue("/tpaccept")
        .build()
    );

    private final Setting<Boolean> friendsOnly = this.sgGeneral.add(new BoolSetting.Builder()
        .name("friends-only")
        .description("Only accepts requests from friends.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = this.sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks before accepting.")
        .defaultValue(10)
        .min(0)
        .sliderMax(60)
        .build()
    );

    private int delayTimer = -1;
    private String pendingCommand = null;

    public AutoAccept() {
        super(Categories.Misc, "Auto Accept", "Automatically accepts incoming teleport requests with pattern filters.");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        Pattern pattern = switch (this.mode.get()) {
            case Default -> Pattern.compile("(?i)(\\w+) (?:has requested to teleport|wants to teleport)");
            case DonutSMP -> Pattern.compile("(?i)\\*\\* (\\w+) wants to teleport to you\\.");
            case Custom -> Pattern.compile(this.customPattern.get());
        };

        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            String sender = matcher.group(1);
            if (this.friendsOnly.get() && (sender == null || Friends.get().get(sender) == null)) {
                return;
            }
            this.pendingCommand = this.acceptCommand.get();
            this.delayTimer = this.delay.get();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.delayTimer > 0) {
            this.delayTimer--;
        } else if (this.delayTimer == 0 && this.pendingCommand != null) {
            ChatUtils.sendPlayerMsg(this.pendingCommand);
            this.pendingCommand = null;
            this.delayTimer = -1;
        }
    }
}
