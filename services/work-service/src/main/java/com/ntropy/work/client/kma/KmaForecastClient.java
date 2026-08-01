package com.ntropy.work.client.kma;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.ntropy.work.config.WeatherProperties;

/**
 * 기상청 단기예보(getVilageFcst) 호출 전담 클라이언트.
 * service-key는 공공데이터포털의 "Decoding" 키를 그대로 넣으면 UriComponentsBuilder가 인코딩한다
 * ("Encoding" 키를 넣으면 이중 인코딩되어 401이 난다).
 */
@Component
public class KmaForecastClient {

    private static final String ENDPOINT = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final DateTimeFormatter BASE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int[] BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int PROVIDE_DELAY_MINUTES = 10; // 발표 후 실제 제공까지의 지연

    private final RestTemplate restTemplate;
    private final WeatherProperties properties;

    public KmaForecastClient(
            @Qualifier("weatherRestTemplate") RestTemplate restTemplate,
            WeatherProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<KmaForecastItem> fetchForecastItems(int nx, int ny) {
        BaseDateTime baseDateTime = resolveBaseDateTime(LocalDateTime.now());

        var uri = UriComponentsBuilder.fromHttpUrl(ENDPOINT)
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("dataType", "JSON")
                .queryParam("numOfRows", 1000)
                .queryParam("pageNo", 1)
                .queryParam("base_date", baseDateTime.date().format(BASE_DATE_FORMAT))
                .queryParam("base_time", String.format("%02d00", baseDateTime.hour()))
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build()
                .encode()
                .toUri();

        KmaApiResponse response = restTemplate.getForObject(uri, KmaApiResponse.class);

        if (response == null || response.getResponse() == null
                || response.getResponse().getBody() == null
                || response.getResponse().getBody().getItems() == null
                || response.getResponse().getBody().getItems().getItem() == null) {
            return Collections.emptyList();
        }
        return response.getResponse().getBody().getItems().getItem();
    }

    /**
     * 단기예보 발표시각(02/05/08/11/14/17/20/23시) 중, 지금 시각 기준으로
     * 이미 발표되고 제공 지연(10분)까지 지난 가장 최근 시각을 찾는다.
     */
    private BaseDateTime resolveBaseDateTime(LocalDateTime now) {
        LocalDateTime adjusted = now.minusMinutes(PROVIDE_DELAY_MINUTES);
        LocalDate date = adjusted.toLocalDate();
        int hour = adjusted.getHour();

        for (int i = BASE_HOURS.length - 1; i >= 0; i--) {
            if (BASE_HOURS[i] <= hour) {
                return new BaseDateTime(date, BASE_HOURS[i]);
            }
        }
        return new BaseDateTime(date.minusDays(1), BASE_HOURS[BASE_HOURS.length - 1]);
    }

    private record BaseDateTime(LocalDate date, int hour) {
    }
}
