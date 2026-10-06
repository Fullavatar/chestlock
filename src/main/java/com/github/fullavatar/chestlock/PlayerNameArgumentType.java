package com.github.fullavatar.chestlock;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

public final class PlayerNameArgumentType extends ArgumentType<String> {

    private final Function<CommandSender, List<String>> candidates;

    public PlayerNameArgumentType(@Nonnull String name,
                                  @Nonnull String usage,
                                  @Nonnull Function<CommandSender, List<String>> candidates) {
        super(Message.translation(name), Message.raw(usage), 1, "PlayerName");
        this.candidates = candidates;
    }

    @Nonnull
    @Override
    public String parse(@Nonnull String[] input, @Nonnull ParseResult parseResult) {
        return input[0];
    }

    @Override
    public void suggest(@Nonnull CommandSender sender,
                        @Nonnull String partial,
                        int numParametersTyped,
                        @Nonnull SuggestionResult result) {
        candidates.apply(sender).stream()
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .filter(name -> name.regionMatches(true, 0, partial, 0, partial.length()))
            .forEach(result::suggest);
        result.markTruncated();
    }
}
