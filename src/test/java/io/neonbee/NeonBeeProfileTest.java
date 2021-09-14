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
    }

    @Test
    void parseActiveProfile() {
        List<NeonBeeProfile> profiles = parseProfiles("");
        assertThat(profiles).contains(ALL);
        assertThat(profiles).hasSize(1);
        profiles = parseProfiles("CORE");
        assertThat(profiles).contains(CORE);
        assertThat(profiles).hasSize(1);
        profiles = parseProfiles("CORE,STABLE");
        assertThat(profiles).contains(CORE);
        assertThat(profiles).contains(STABLE);
        assertThat(profiles).hasSize(2);
        profiles = parseProfiles("CORE,anything");
        assertThat(profiles).contains(CORE);
        assertThat(profiles).hasSize(1);
        profiles = parseProfiles("anything");
        assertThat(profiles).contains(ALL);
        assertThat(profiles).hasSize(1);
        profiles = parseProfiles("");
        assertThat(profiles).contains(ALL);
        assertThat(profiles).hasSize(1);
    }
}
