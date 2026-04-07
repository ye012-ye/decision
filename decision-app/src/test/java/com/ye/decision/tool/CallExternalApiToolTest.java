package com.ye.decision.tool;

import com.ye.decision.domain.dto.ApiCallReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CallExternalApiToolTest {

    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    CallExternalApiTool tool;

    @BeforeEach
    void setUp() {
        tool = new CallExternalApiTool(
            restTemplate,
            "http://weather.test/current",
            "http://logistics.test/track",
            "http://exchange.test/rate"
        );
    }

    @Test
    void weather_callsCorrectUrl() {
        server.expect(requestTo("http://weather.test/current?city=%E5%8C%97%E4%BA%AC"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"temp\":\"20°C\"}", MediaType.APPLICATION_JSON));

        String result = tool.apply(new ApiCallReq("weather", "{\"city\":\"北京\"}"));
        assertThat(result).contains("20°C");
        server.verify();
    }

    @Test
    void unknownService_returnsErrorJson() {
        String result = tool.apply(new ApiCallReq("unknown", "{}"));
        assertThat(result).contains("\"error\"").contains("unknown_service");
    }

    @Test
    void httpError_returnsErrorJson() {
        server.expect(requestTo("http://logistics.test/track?trackingNo=SF123"))
            .andRespond(withServerError());
        String result = tool.apply(new ApiCallReq("logistics", "{\"trackingNo\":\"SF123\"}"));
        assertThat(result).contains("\"error\"");
    }
}
