package com.ye.decision.tool;

import com.ye.decision.dto.QueryMysqlReq;
import com.ye.decision.feign.DownstreamClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryMysqlToolTest {

    DownstreamClient orderClient = mock(DownstreamClient.class);
    DownstreamClient userClient = mock(DownstreamClient.class);
    QueryMysqlTool tool;

    @BeforeEach
    void setUp() {
        tool = new QueryMysqlTool(Map.of(
            "order-service", orderClient,
            "user-service", userClient
        ));
    }

    @Test
    void knownTarget_delegatesToCorrectClient() {
        when(orderClient.query("userId=1")).thenReturn("[{\"id\":1}]");
        String result = tool.apply(new QueryMysqlReq("order-service", "userId=1"));
        assertThat(result).isEqualTo("[{\"id\":1}]");
        verify(orderClient).query("userId=1");
    }

    @Test
    void unknownTarget_returnsErrorJson() {
        String result = tool.apply(new QueryMysqlReq("unknown-service", "anything"));
        assertThat(result).contains("\"error\"").contains("unknown_target");
    }

    @Test
    void feignException_returnsErrorJson() {
        when(orderClient.query(any())).thenThrow(new RuntimeException("timeout"));
        String result = tool.apply(new QueryMysqlReq("order-service", "userId=1"));
        assertThat(result).contains("\"error\"");
    }
}
