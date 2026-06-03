package com.ye.decision.tika.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractResultTest {

    @Test
    void nullText_becomesEmptyString() {
        var r = new ExtractResult(ExtractResult.Status.FAILED, null, "text/plain", Map.of(), false, "boom");

        assertThat(r.text()).isEmpty();
    }

    @Test
    void nullMetadata_becomesEmptyMap() {
        var r = new ExtractResult(ExtractResult.Status.OK, "x", "text/plain", null, false, null);

        assertThat(r.metadata()).isEmpty();
    }

    @Test
    void metadata_isImmutableCopy() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("k", "v");
        var r = new ExtractResult(ExtractResult.Status.OK, "x", "text/plain", mutable, false, null);

        mutable.put("k2", "v2");   // 外部改动不得影响已构造的结果

        assertThat(r.metadata()).containsOnlyKeys("k");
        assertThatThrownBy(() -> r.metadata().put("z", "z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ok_reflectsStatus() {
        assertThat(new ExtractResult(ExtractResult.Status.OK, "", "", Map.of(), false, null).ok()).isTrue();
        assertThat(new ExtractResult(ExtractResult.Status.FAILED, "", "", Map.of(), false, null).ok()).isFalse();
    }
}
