package com.ye.decision.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service")
public interface UserServiceClient extends DownstreamClient {
    @GetMapping("/internal/users")
    @Override
    String query(@RequestParam("query") String query);
}
