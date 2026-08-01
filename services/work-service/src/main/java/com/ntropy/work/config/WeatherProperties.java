package com.ntropy.work.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class WeatherProperties {

    private final String serviceKey;
    private final double defaultLatitude;
    private final double defaultLongitude;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public WeatherProperties(
            @Value("${weather.service-key:}") String serviceKey,
            @Value("${weather.default-latitude:37.5665}") double defaultLatitude,
            @Value("${weather.default-longitude:126.9780}") double defaultLongitude,
            @Value("${weather.connect-timeout-ms:5000}") int connectTimeoutMillis,
            @Value("${weather.read-timeout-ms:10000}") int readTimeoutMillis
    ) {
        this.serviceKey = serviceKey;
        this.defaultLatitude = defaultLatitude;
        this.defaultLongitude = defaultLongitude;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }
}
