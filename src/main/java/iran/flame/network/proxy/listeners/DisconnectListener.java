package iran.flame.network.proxy.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import iran.flame.network.proxy.kernel.limbo.Limbo;

public class DisconnectListener {
    private final Limbo limbo;

    public DisconnectListener(Limbo limbo) {
        this.limbo = limbo;
    }

    @Subscribe public void onDisconnect(DisconnectEvent event) {
        limbo.removePlayer(event.getPlayer().getUniqueId());
    }
}