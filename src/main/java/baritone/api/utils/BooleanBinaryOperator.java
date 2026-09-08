package baritone.api.utils;

@FunctionalInterface
public interface BooleanBinaryOperator {
   boolean applyAsBoolean(boolean var1, boolean var2);
}
