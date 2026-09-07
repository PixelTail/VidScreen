package dev.vidscreen.protocol.message;

import java.util.Objects;
import java.util.UUID;

import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class OperationResult implements WireMessage {
    private final UUID operationId;
    private final boolean success;
    private final String code;
    private final String message;

    public OperationResult(UUID operationId, boolean success, String code, String message) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.success = success;
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
    }

    @Override
    public MessageType type() {
        return MessageType.OPERATION_RESULT;
    }

    public UUID operationId() {
        return operationId;
    }

    public boolean success() {
        return success;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OperationResult)) {
            return false;
        }
        OperationResult that = (OperationResult) other;
        return success == that.success
                && operationId.equals(that.operationId)
                && code.equals(that.code)
                && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationId, success, code, message);
    }
}
