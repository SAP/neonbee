package io.neonbee;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Strings;

/**
 * NeonBee deployment profile.
 */
@SuppressWarnings("checkstyle:JavadocVariable")
public enum NeonBeeProfile {
    WEB, CORE, STABLE, INCUBATOR, ALL;

    /**
     * Returns true if this profile is inside the passed active profiles.
     *
     * @param activeProfiles The active profiles
     * @return true if this profile is active
     */
    public boolean isActive(List<NeonBeeProfile> activeProfiles) {
        return activeProfiles.contains(ALL) || activeProfiles.contains(this) || this == ALL;
    }

    /**
     * Parses a comma separated string of profile values.
     *
     * @param values string with profile values
     * @return a List with the parsed {@link NeonBeeProfile}s
     */
    public static List<NeonBeeProfile> parseProfiles(String values) {
        return Optional.ofNullable(values).map(Strings::emptyToNull)
                .map(nonEmptyValues -> Arrays.stream(nonEmptyValues.split(",")).map(value -> {
                    try {
                        return NeonBeeProfile.valueOf(value);
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList())).filter(Predicate.not(Collection::isEmpty))
                .orElse(List.of(ALL));
    }
}
