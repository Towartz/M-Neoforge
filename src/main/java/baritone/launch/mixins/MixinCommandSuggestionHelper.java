package baritone.launch.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.event.events.TabCompleteEvent;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CommandSuggestions.class})
public class MixinCommandSuggestionHelper {
   @Shadow
   @Final
   EditBox input;
   @Shadow
   @Final
   private List<String> commandUsage;
   @Shadow
   private ParseResults currentParse;
   @Shadow
   private CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow
   private SuggestionsList suggestions;
   @Shadow
   boolean keepSuggestions;

   @Inject(
      method = {"updateCommandInfo"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void preUpdateSuggestion(CallbackInfo ci) {
      String prefix = this.input.getValue().substring(0, Math.min(this.input.getValue().length(), this.input.getCursorPosition()));
      TabCompleteEvent event = new TabCompleteEvent(prefix);
      BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onPreTabComplete(event);
      if (event.isCancelled()) {
         ci.cancel();
      } else {
         if (event.completions != null) {
            ci.cancel();
            this.currentParse = null;
            if (this.keepSuggestions) {
               return;
            }

            this.input.setSuggestion(null);
            this.suggestions = null;
            this.commandUsage.clear();
            if (event.completions.length == 0) {
               this.pendingSuggestions = Suggestions.empty();
            } else {
               StringRange range = StringRange.between(prefix.lastIndexOf(" ") + 1, prefix.length());
               List<Suggestion> suggestionList = Stream.of(event.completions).map(s -> new Suggestion(range, s)).collect(Collectors.toList());
               Suggestions suggestions = new Suggestions(range, suggestionList);
               this.pendingSuggestions = new CompletableFuture<>();
               this.pendingSuggestions.complete(suggestions);
            }

            ((CommandSuggestions)(Object)this).showSuggestions(true);
         }
      }
   }
}
