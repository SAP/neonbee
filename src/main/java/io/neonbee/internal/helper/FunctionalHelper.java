package io.neonbee.internal.helper;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FunctionalHelper {
    /**
     * This helper class cannot be instantiated.
     */
    private FunctionalHelper() {}

    /**
     * To be used like map.entrySet().stream().map(entryFunction((key, value) -&gt; ...)).
     *
     * @param function The {@link BiFunction} with the logic to be applied to the map entries
     * @param <K>      The type of the key of the map entry
     * @param <V>      The type of the value of the map entry
     * @param <R>      The type of the return value of the returned function
     * @return A function which applies the passed logic to the map
     */
    public static <K, V, R> Function<Map.Entry<K, V>, R> entryFunction(BiFunction<? super K, ? super V, R> function) {
        return entry -> function.apply(entry.getKey(), entry.getValue());
    }

    /**
     * To be used like map.entrySet().stream().forEach(entryConsumer((key, value) -&gt; ...)).
     *
     * @param consumer A {@link BiConsumer} of map key and value which contains the logic handling the map entry.
     * @param <K>      The type of the key of the returned map entry
     * @param <V>      The type of the value of the returned map entry
     * @return Returns a consumer
     */
    public static <K, V> Consumer<Map.Entry<K, V>> entryConsumer(BiConsumer<? super K, ? super V> consumer) {
        return entry -> consumer.accept(entry.getKey(), entry.getValue());
    }

    /**
     * An unchecked mapper converting one type into another.
     *
     * @param <T>   the target type
     * @param <U>   the source type
     * @param value the value to convert of type U
     * @return the converted value in type T
     */
    @SuppressWarnings({ "unchecked", "TypeParameterUnusedInFormals" })
    public static <T, U> T uncheckedMapper(U value) {
        return (T) value;
    }
}
