package dansplugins.rpsystem.integrations.truedeath;

import dansplugins.rpsystem.cards.CharacterServiceImpl.TrueDeathContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/** Reflection is intentional: PatriamUtils remains a completely optional private integration. */
final class TrueDeathRecordReader {

    private TrueDeathRecordReader() {
    }

    static TrueDeathContext fromEvent(Object event) throws ReflectiveOperationException {
        Object death = invoke(event, "death");
        return fromRecord(death);
    }

    static TrueDeathContext fromRecord(Object death) throws ReflectiveOperationException {
        UUID player = cast(invoke(death, "player"), UUID.class, "player");
        long declaredAt = number(invoke(death, "declaredAt"), "declaredAt");
        long approvedAt = number(invoke(death, "approvedAt"), "approvedAt");
        UUID approvedBy = cast(invoke(death, "approvedBy"), UUID.class, "approvedBy");
        Object rawReason = invoke(death, "reason");
        String reason;
        if (rawReason instanceof Optional<?> optional) {
            reason = optional.map(Object::toString).orElse("");
        } else {
            reason = rawReason == null ? "" : rawReason.toString();
        }
        return new TrueDeathContext(player, declaredAt, approvedAt, approvedBy, reason);
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            throw new ReflectiveOperationException("cannot read " + name + " from null");
        }
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw new ReflectiveOperationException("reading " + name + " failed", exception);
            }
            throw e;
        }
    }

    private static long number(Object value, String name) throws ReflectiveOperationException {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new ReflectiveOperationException(name + " was not numeric");
    }

    private static <T> T cast(Object value, Class<T> type, String name)
            throws ReflectiveOperationException {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new ReflectiveOperationException(name + " was not a " + type.getSimpleName());
    }
}
