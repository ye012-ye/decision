package com.ye.decision.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service")
public interface OrderServiceClient extends DownstreamClient {
    @GetMapping("/internal/orders")
    @Override
    String query(@RequestParam("query") String query);
}
