package cc.aerial.client.features;

public final class UnknownModuleException extends RuntimeException {
    public UnknownModuleException(String id) {
        super("Unknown module: " + id);
    }
}
