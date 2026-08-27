package iran.flame.network.proxy.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import iran.flame.network.proxy.Main;
import iran.flame.network.proxy.enums.LimboType;
import iran.flame.network.proxy.kernel.balancer.Balancer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Optional;
import java.util.UUID;

public class KickListener {
    private static final String LIMBO_SERVER_NAME = "limbo";
    private final Main plugin;
    private final Balancer balancer;

    public KickListener(Main plugin) {
        this.plugin = plugin;
        this.balancer = new Balancer(plugin.getServer(), plugin.getRedis(), plugin.getLogger());
    }

    @Subscribe public void onKicked(KickedFromServerEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        Optional<RegisteredServer> lobby = balancer.pickLobby();

        if (lobby.isPresent()) {
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(lobby.get()));
            return;
        }

        Optional<RegisteredServer> limbo = plugin.getServer().getServer(LIMBO_SERVER_NAME).filter(this::isOnline);

        if (limbo.isPresent()) {
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(limbo.get()));
            plugin.getLimbo().publish(LimboType.EMPTY, uuid);
            return;
        }

        event.setResult(KickedFromServerEvent.DisconnectPlayer.create(
                Component.text("No available lobby or limbo server. Please reconnect later.", NamedTextColor.RED, TextDecoration.ITALIC)
        ));
    }

    private boolean isOnline(RegisteredServer registeredServer) {
        try {
            registeredServer.ping().join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}