package betterquesting.api2.storage;

import betterquesting.api.utils.UuidConverter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Database that uses randomly-generated UUIDs as keys. */
public class UuidDatabase<T> implements IUuidDatabase<T> {
    private final HashBiMap<UUID, T> database = HashBiMap.create();

    private int compareEntries(Map.Entry<UUID, T> e1, Map.Entry<UUID, T> e2) {
        return UuidConverter.encodeUuid(e1.getKey()).compareTo(UuidConverter.encodeUuid(e2.getKey()));
    }

    @Override
    public UUID generateKey() {
        UUID newKey;
        do {
            newKey = UUID.randomUUID();
        } while (containsKey(newKey));
        return newKey;
    }

    @Override
    @Nullable
    public UUID lookupKey(T value) {
        return inverse().get(value);
    }

    @Override
    public Stream<Map.Entry<UUID, T>> orderedEntries() {
        return entrySet().stream().sorted(this::compareEntries);
    }

    @Override
    public Stream<T> getAll(Collection<UUID> keys) {
        return keys.stream().distinct().filter(database::containsKey).map(database::get);
    }

    @Override
    public Map<UUID, T> filterKeys(Collection<UUID> keys) {
        return keys.stream().distinct()
                .filter(database::containsKey)
                .collect(Collectors.toMap(Function.identity(), database::get));
    }

    @Override
    public BiMap<UUID, T> filterValues(Collection<T> values) {
        return Maps.filterValues(database, values::contains);
    }

    @Override
    public BiMap<UUID, T> filterEntries(BiPredicate<UUID, T> filter) {
        return Maps.filterEntries(database, entry -> filter.test(entry.getKey(), entry.getValue()));
    }

    @Override
    @Nullable
    public UUID removeValue(T value) {
        return inverse().remove(value);
    }

    @Override
    public int size() {
        return database.size();
    }

    @Override
    public boolean isEmpty() {
        return database.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return database.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return database.containsValue(value);
    }

    @Override
    @Nullable
    public T get(Object key) {
        return database.get(key);
    }

    @Override
    @Nullable
    public T put(@Nullable UUID key, @Nullable T value) {
        return database.put(key, value);
    }

    @Override
    @Nullable
    public T remove(Object key) {
        return database.remove(key);
    }

    @Override
    @Nullable
    public T forcePut(@Nullable UUID key, @Nullable T value) {
        return database.forcePut(key, value);
    }

    @Override
    public void putAll(Map<? extends UUID, ? extends T> map) {
        database.putAll(map);
    }

    @Override
    public void clear() {
        database.clear();
    }

    @Override
    public Set<UUID> keySet() {
        return database.keySet();
    }

    @Override
    public Set<T> values() {
        return database.values();
    }

    @Override
    public Set<Map.Entry<UUID, T>> entrySet() {
        return database.entrySet();
    }

    @Override
    public BiMap<T, UUID> inverse() {
        return database.inverse();
    }
}
