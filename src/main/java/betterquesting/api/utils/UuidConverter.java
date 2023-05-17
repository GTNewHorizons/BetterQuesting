package betterquesting.api.utils;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

public class UuidConverter {
    /** Converts a legacy integer ID to a UUID. */
    public static UUID convertLegacyId(int legacyId) {
        // Negative legacy IDs are invalid, and are used to indicate an unset ID.
        Preconditions.checkArgument(legacyId >= 0);
        return new UUID(0L, legacyId);
    }

    /** Returns a compact string representation of a UUID. */
    public static String encodeUuid(UUID uuid) {
        byte[] upper = Longs.toByteArray(uuid.getMostSignificantBits());
        byte[] lower = Longs.toByteArray(uuid.getLeastSignificantBits());

        return Base64.getUrlEncoder().encodeToString(Bytes.concat(upper, lower));
    }

    /**
     * Returns a compact string representation of a UUID, with trailing '=' removed.
     * This is used for quest book translation keys, which must not contain '='.
     */
    public static String encodeUuidStripPadding(UUID uuid) {
        return encodeUuid(uuid).replace("=", "");
    }

    /** Decodes a compact string representation of a UUID. */
    public static UUID decodeUuid(String string) {
        byte[] bytes = Base64.getUrlDecoder().decode(string);
        byte[] upper = Arrays.copyOfRange(bytes, 0, 8);
        byte[] lower = Arrays.copyOfRange(bytes, 8, 16);

        return new UUID(Longs.fromByteArray(upper), Longs.fromByteArray(lower));
    }
}
