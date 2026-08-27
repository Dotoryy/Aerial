package cc.aerial.client.event.impl.game.server;

import cc.aerial.client.event.EventCancellable;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class ServerConnectEvent extends EventCancellable {
    private final ServerAddress serverAddress;

    public ServerConnectEvent(ServerAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    public ServerAddress getServerAddress() {
        return serverAddress;
    }
}
