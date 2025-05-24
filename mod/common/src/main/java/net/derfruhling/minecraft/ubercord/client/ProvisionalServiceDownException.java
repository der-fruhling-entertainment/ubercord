package net.derfruhling.minecraft.ubercord.client;

public class ProvisionalServiceDownException extends RuntimeException {
    public ProvisionalServiceDownException() {
    }

    public ProvisionalServiceDownException(String message) {
        super(message);
    }

    public ProvisionalServiceDownException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProvisionalServiceDownException(Throwable cause) {
        super(cause);
    }
}
