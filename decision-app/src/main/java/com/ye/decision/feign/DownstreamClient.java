package com.ye.decision.feign;

/**
 * @author ye
 */
public interface DownstreamClient {
    String query(String query);
}
