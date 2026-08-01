package com.ntropy.bff.controller.work;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.common.client.WeatherQueryClient;
import com.ntropy.common.dto.work.summary.WeatherForecastList;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherQueryClient weatherQueryClient;

    @GetMapping
    public ApiResponse<WeatherForecastList> getForecasts(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        return ApiResponse.success(weatherQueryClient.getForecasts(latitude, longitude));
    }
}
