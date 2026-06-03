package com.ye.decision.tika.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractorConfigTest {

    @Test
    void builder_appliesDefaults() {
        var config = ExtractorConfig.builder().build();

        assertThat(config.writeLimit()).isEqualTo(10_000_000);
        assertThat(config.maxEmbeddedResources()).isEqualTo(200);
        assertThat(config.parseTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.allowedMimeTypes()).isEmpty();
        assertThat(config.passwordProvider()).isNull();
    }

    @Test
    void zeroTimeout_throws() {
        assertThatThrownBy(() -> ExtractorConfig.builder().parseTimeout(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parseTimeout");
    }

    @Test
    void nullTimeout_throws() {
        assertThatThrownBy(() -> ExtractorConfig.builder().parseTimeout(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowedMimeTypes_isDefensivelyCopied() {
        Set<String> mutable = new HashSet<>(Set.of("application/pdf"));
        var config = ExtractorConfig.builder().allowedMimeTypes(mutable).build();

        mutable.add("text/plain");

        assertThat(config.allowedMimeTypes()).containsExactly("application/pdf");
    }

    @Test
    void nullAllowedMimeTypes_becomesEmpty() {
        var config = ExtractorConfig.builder().allowedMimeTypes(null).build();

        assertThat(config.allowedMimeTypes()).isEmpty();
    }
}
