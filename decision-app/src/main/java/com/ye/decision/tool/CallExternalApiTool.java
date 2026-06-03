package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 外部 API 调用工具。
 * @author ye
 */
@Component
public class CallExternalApiTool {

    private final RestTemplate restTemplate;
    private final String weatherUrl;
    private final String logisticsUrl;
    private final String exchangeRateUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CallExternalApiTool(RestTemplate restTemplate,
                                @Value("${decision.external.weather-url}") String weatherUrl,
                                @Value("${decision.external.logistics-url}") String logisticsUrl,
                                @Value("${decision.external.exchange-rate-url}") String exchangeRateUrl) {
        this.restTemplate = restTemplate;
        this.weatherUrl = weatherUrl;
        this.logisticsUrl = logisticsUrl;
        this.exchangeRateUrl = exchangeRateUrl;
    }

    @Tool(name = "callExternalApiTool",
          description = "调用外部第三方服务，包括天气查询（weather）、物流追踪（logistics）、汇率查询（exchange-rate）。")
    public String callExternalApi(
            @ToolParam(description = "服务名: weather / logistics / exchange-rate") String service,
            @ToolParam(description = "请求参数 JSON 字符串，如 {\"city\":\"beijing\"}") String params) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramMap = objectMapper.readValue(params, Map.class);
            return switch (service) {
                case "weather"       -> get(weatherUrl, paramMap);
                case "logistics"     -> get(logisticsUrl, paramMap);
                case "exchange-rate" -> get(exchangeRateUrl, paramMap);
                default -> errorJson("unknown_service", "不支持的外部服务: " + service);
            };
        } catch (Exception e) {
            return errorJson("api_error", e.getMessage());
        }
    }

    private String get(String baseUrl, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
        params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();
        return restTemplate.getForObject(uri, String.class);
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message + "\",\"tool\":\"callExternalApiTool\"}";
    }
}
