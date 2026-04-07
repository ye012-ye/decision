package com.ye.decision.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResultTest {

    @Test
    void ok_setsCode200AndData() {
        Result<String> r = Result.ok("hello");
        assertThat(r.code()).isEqualTo(200);
        assertThat(r.msg()).isEqualTo("success");
        assertThat(r.data()).isEqualTo("hello");
    }

    @Test
    void error_setsCode500AndNullData() {
        Result<Object> r = Result.error("something went wrong");
        assertThat(r.code()).isEqualTo(500);
        assertThat(r.msg()).isEqualTo("something went wrong");
        assertThat(r.data()).isNull();
    }
}
