package com.ye.decision.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.domain.dto.ApiCallReq;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

/**
 * 外部 API 调用工具。
 * @author ye
 */
public class CallExternalApiTool implements Function<ApiCallReq, String> {

    private final RestTemplate restTemplate;
    private final String weatherUrl;
    private final String logisticsUrl;
    private final String exchangeRateUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CallExternalApiTool(RestTemplate restTemplate,
                                String weatherUrl,
                                String logisticsUrl,
                                String exchangeRateUrl) {
        this.restTemplate = restTemplate;
        this.weatherUrl = weatherUrl;
        this.logisticsUrl = logisticsUrl;
        this.exchangeRateUrl = exchangeRateUrl;
    }

    @Override
    public String apply(ApiCallReq req) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(req.params(), Map.class);
            return switch (req.service()) {
                case "weather"       -> get(weatherUrl, params);
                case "logistics"     -> get(logisticsUrl, params);
                case "exchange-rate" -> get(exchangeRateUrl, params);
                default -> errorJson("unknown_service", "不支持的外部服务: " + req.service());
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
