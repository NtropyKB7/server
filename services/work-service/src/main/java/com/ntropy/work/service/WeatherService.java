package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.common.dto.work.summary.WeatherForecast;
import com.ntropy.common.dto.work.summary.WeatherForecastList;
import com.ntropy.work.client.kma.KmaForecastClient;
import com.ntropy.work.client.kma.KmaForecastItem;
import com.ntropy.work.config.WeatherProperties;
import com.ntropy.work.util.KmaGridConverter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final int FORECAST_DAYS = 3;
    private static final String PREFERRED_TIME = "1200"; // 하루 대표값으로 쓸 시각(정오)
    private static final DateTimeFormatter FCST_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String CATEGORY_SKY = "SKY";
    private static final String CATEGORY_PTY = "PTY";
    private static final String CATEGORY_TMP = "TMP";
    private static final String PTY_NONE = "0";

    private static final Map<String, String> SKY_STATUS = Map.of(
            "1", "맑음",
            "3", "구름많음",
            "4", "흐림"
    );
    private static final Map<String, String> PRECIPITATION_TYPE = Map.of(
            "0", "없음",
            "1", "비",
            "2", "비/눈",
            "3", "눈",
            "4", "소나기",
            "5", "빗방울",
            "6", "빗방울눈날림",
            "7", "눈날림"
    );

    private final KmaForecastClient kmaForecastClient;
    private final WeatherProperties properties;

    public WeatherForecastList getForecasts(Double latitude, Double longitude) {
        double lat = latitude != null ? latitude : properties.getDefaultLatitude();
        double lon = longitude != null ? longitude : properties.getDefaultLongitude();
        KmaGridConverter.Grid grid = KmaGridConverter.toGrid(lat, lon);

        List<KmaForecastItem> items = kmaForecastClient.fetchForecastItems(grid.nx(), grid.ny());

        Map<String, List<KmaForecastItem>> byDate = items.stream()
                .collect(Collectors.groupingBy(KmaForecastItem::getFcstDate, LinkedHashMap::new, Collectors.toList()));

        List<WeatherForecast> forecasts = new ArrayList<>();
        for (Map.Entry<String, List<KmaForecastItem>> entry : byDate.entrySet()) {
            if (forecasts.size() >= FORECAST_DAYS) {
                break;
            }
            forecasts.add(toForecast(entry.getKey(), entry.getValue()));
        }
        return new WeatherForecastList(forecasts);
    }

    private WeatherForecast toForecast(String fcstDate, List<KmaForecastItem> dayItems) {
        String targetTime = nearestTime(dayItems);
        Map<String, String> valuesAtTime = dayItems.stream()
                .filter(item -> targetTime.equals(item.getFcstTime()))
                .collect(Collectors.toMap(KmaForecastItem::getCategory, KmaForecastItem::getFcstValue, (a, b) -> a));

        String skyCode = valuesAtTime.get(CATEGORY_SKY);
        String ptyCode = valuesAtTime.get(CATEGORY_PTY);
        String tmpValue = valuesAtTime.get(CATEGORY_TMP);

        String skyStatus = SKY_STATUS.get(skyCode);
        String precipitationType = PRECIPITATION_TYPE.get(ptyCode);
        boolean isRainSurcharge = ptyCode != null && !PTY_NONE.equals(ptyCode);
        Integer temperature = tmpValue != null ? (int) Math.round(Double.parseDouble(tmpValue)) : null;

        LocalDate date = LocalDate.parse(fcstDate, FCST_DATE_FORMAT);
        return new WeatherForecast(date, skyStatus, precipitationType, isRainSurcharge, temperature);
    }

    /** 그날 데이터 중 PREFERRED_TIME(정오)에 가장 가까운 fcstTime을 고른다. */
    private String nearestTime(List<KmaForecastItem> dayItems) {
        int preferred = Integer.parseInt(PREFERRED_TIME);
        return dayItems.stream()
                .map(KmaForecastItem::getFcstTime)
                .distinct()
                .min(Comparator.comparingInt(time -> Math.abs(Integer.parseInt(time) - preferred)))
                .orElse(PREFERRED_TIME);
    }
}
