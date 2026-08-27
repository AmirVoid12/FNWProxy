package iran.flame.network.proxy.kernel.alias;

import iran.flame.network.proxy.interfaces.IAlias;
import iran.flame.network.proxy.interfaces.SubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractAliasCommand implements IAlias {
    private final Map<String, IAlias> subCommands = new LinkedHashMap<>();

    protected void addSubCommand(IAlias command) {
        SubCommand meta = command.getClass().getAnnotation(SubCommand.class);

        if (meta == null) {
            throw new IllegalArgumentException(
                    "Missing @SubCommand on " + command.getClass().getName()
            );
        }

        for (String name : meta.name()) {
            subCommands.put(name.toLowerCase(), command);
        }
    }

    @Override public Map<String, IAlias> getSubCommands() {
        return Collections.unmodifiableMap(subCommands);
    }

    @Override public void execute(AliasContext context) {
        if (context.argsLength() == 0) {
            onDefault(context);
            return;
        }

        String subName = context.getArg(0).toLowerCase();
        IAlias sub = subCommands.get(subName);

        if (sub == null) {
            onDefault(context);
            return;
        }

        SubCommand meta = sub.getClass().getAnnotation(SubCommand.class);

        if (!context.hasPermission(meta.permission())) {
            context.reply(Component.text("You don't have permission to use this subcommand.", NamedTextColor.RED));
            return;
        }

        sub.execute(context.shift());
    }

    @Override public List<String> tabComplete(AliasContext context) {
        if (context.argsLength() <= 1) {
            String prefix = context.argsLength() == 1 ? context.getArg(0).toLowerCase() : "";

            return subCommands.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .filter(entry -> {
                        SubCommand meta = entry.getValue().getClass().getAnnotation(SubCommand.class);
                        return context.hasPermission(meta.permission());
                    })
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        String subName = context.getArg(0).toLowerCase();
        IAlias sub = subCommands.get(subName);

        if (sub != null) {
            return sub.tabComplete(context.shift());
        }

        return Collections.emptyList();
    }

    protected void onDefault(AliasContext context) {
        context.reply(Component.text("Unknown subcommand. Available: " + String.join(", ", subCommands.keySet()), NamedTextColor.RED));
    }
}