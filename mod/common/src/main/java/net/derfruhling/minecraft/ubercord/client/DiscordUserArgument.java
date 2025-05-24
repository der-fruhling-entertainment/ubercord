package net.derfruhling.minecraft.ubercord.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.derfruhling.discord.socialsdk4j.Relationship;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DiscordUserArgument implements ArgumentType<Long> {
    private final Map<String, Long> users = new HashMap<>();

    @Override
    public Long parse(StringReader reader) throws CommandSyntaxException {
        int back = reader.getCursor();
        String s = reader.readUnquotedString();

        if(users.containsKey(s)) {
            return users.get(s);
        } else {
            reader.setCursor(back);
        }

        return reader.readLong();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        synchronized (users) {
            users.clear();

            for (Relationship relationship : UbercordClient.get().getClient().getRelationships()) {
                if(relationship.discordType() == Relationship.Type.Friend || relationship.gameType() == Relationship.Type.Friend) {
                    if(relationship.user() != null) {
                        users.put(relationship.user().getUsername(), relationship.id());
                        builder.suggest(relationship.user().getUsername());
                    }
                }
            }
        }

        return builder.buildFuture();
    }
}
