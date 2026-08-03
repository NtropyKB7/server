package com.ntropy.work.client;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.WeatherQueryClient;
import com.ntropy.common.dto.work.summary.WeatherForecastList;
import com.ntropy.work.service.WeatherService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalWeatherQueryClient implements WeatherQueryClient {

    private final WeatherService weatherService;

    @Override
    public WeatherForecastList getForecasts(Double latitude, Double longitude) {
        return weatherService.getForecasts(latitude, longitude);
    }
}
