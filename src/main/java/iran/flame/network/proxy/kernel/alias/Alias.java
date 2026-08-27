package iran.flame.network.proxy.kernel.alias;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import iran.flame.network.proxy.interfaces.AliasCommand;
import iran.flame.network.proxy.interfaces.IAlias;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.Arrays;
import java.util.List;

public class Alias {
    private final ProxyServer server;
    private final Object plugin;

    public Alias(ProxyServer server, Object plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    public void register(IAlias command) {
        Class<?> clazz = command.getClass();
        AliasCommand meta = clazz.getAnnotation(AliasCommand.class);

        if (meta == null) {
            throw new IllegalArgumentException(
                    "Class " + clazz.getName() + " is missing @AliasCommand annotation"
            );
        }

        if (meta.name().length == 0) {
            throw new IllegalArgumentException(
                    "@AliasCommand on " + clazz.getName() + " must have at least one name"
            );
        }

        CommandManager commandManager = server.getCommandManager();

        String primaryName = meta.name()[0];
        String[] aliases = meta.name().length > 1
                ? Arrays.copyOfRange(meta.name(), 1, meta.name().length)
                : new String[0];

        CommandMeta commandMeta = commandManager.metaBuilder(primaryName)
                .aliases(aliases)
                .plugin(plugin)
                .build();

        SimpleCommand simpleCommand = new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!hasPermission(invocation)) {
                    invocation.source().sendMessage(
                            Component.text("You don't have permission to use this command.", NamedTextColor.RED)
                    );
                    return;
                }

                AliasContext context = new AliasContext(
                        invocation.source(),
                        invocation.arguments(),
                        invocation.alias()
                );

                try {
                    command.execute(context);
                } catch (Exception e) {
                    invocation.source().sendMessage(
                            Component.text("An error occurred while executing this command.", NamedTextColor.RED)
                    );
                    e.printStackTrace();
                }
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                if (!hasPermission(invocation)) {
                    return List.of();
                }

                AliasContext context = new AliasContext(
                        invocation.source(),
                        invocation.arguments(),
                        invocation.alias()
                );

                return command.tabComplete(context);
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return meta.permission().isEmpty() || invocation.source().hasPermission(meta.permission());
            }
        };

        commandManager.register(commandMeta, simpleCommand);
    }
}