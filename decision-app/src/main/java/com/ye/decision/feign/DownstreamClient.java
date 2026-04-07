package com.ye.decision.feign;

public interface DownstreamClient {
    String query(String query);
}
