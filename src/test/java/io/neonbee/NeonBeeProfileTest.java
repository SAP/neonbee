package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeProfile.ALL;
import static io.neonbee.NeonBeeProfile.CORE;
import static io.neonbee.NeonBeeProfile.INCUBATOR;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.neonbee.NeonBeeProfile.STABLE;
import static io.neonbee.NeonBeeProfile.WEB;
import static io.neonbee.NeonBeeProfile.parseProfiles;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NeonBee deployment profile.
 */
class NeonBeeProfileTest {
    @Test
    @DisplayName("is profile active")
    void isActive() {
        assertThat(CORE.isActive(List.<NeonBeeProfile>of(ALL))).isTrue();
        assertThat(STABLE.isActive(List.<NeonBeeProfile>of(ALL))).isTrue();
        assertThat(WEB.isActive(List.<NeonBeeProfile>of(ALL))).isTrue();

        // NO_WEB should always take precedence
        assertThat(WEB.isActive(List.<NeonBeeProfile>of(ALL, NO_WEB))).isFalse();
        assertThat(WEB.isActive(List.<NeonBeeProfile>of(NO_WEB, ALL))).isFalse();
        assertThat(CORE.isActive(List.<NeonBeeProfile>of(NO_WEB))).isFalse();
        assertThat(STABLE.isActive(List.<NeonBeeProfile>of(NO_WEB, ALL))).isTrue();

        assertThat(CORE.isActive(List.<NeonBeeProfile>of(CORE))).isTrue();
        assertThat(STABLE.isActive(List.<NeonBeeProfile>of(STABLE, CORE))).isTrue();
        assertThat(INCUBATOR.isActive(List.<NeonBeeProfile>of(STABLE, ALL))).isTrue();
        assertThat(CORE.isActive(List.<NeonBeeProfile>of(STABLE, INCUBATOR))).isFalse();

        assertThat(ALL.isActive(List.<NeonBeeProfile>of(STABLE))).isTrue();

        assertThat(ALL.isActive(Set.of())).isFalse();
        assertThat(CORE.isActive(Set.of())).isFalse();
        assertThat(NO_WEB.isActive(Set.of())).isFalse();
    }

    @Test
    void parseActiveProfile() {
        assertThat(parseProfiles("ALL")).containsExactly(ALL);
        assertThat(parseProfiles("CORE")).containsExactly(CORE);
        assertThat(parseProfiles("CORE,STABLE")).containsExactly(CORE, STABLE);
        assertThat(parseProfiles("CORE,anything")).containsExactly(CORE);
        assertThat(parseProfiles("anything")).isEmpty();
        assertThat(parseProfiles("")).isEmpty();
    }
}
