package com.ntropy.user.config;

import com.ntropy.user.client.GoogleOAuthClient;
import com.ntropy.user.client.KakaoOAuthClient;
import com.ntropy.user.mapper.UserMapper;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestConfig {

    @Bean
    public GoogleOAuthClient googleOAuthClient() {
        return Mockito.mock(GoogleOAuthClient.class);
    }

    @Bean
    public KakaoOAuthClient kakaoOAuthClient() {
        return Mockito.mock(KakaoOAuthClient.class);
    }

    @Bean
    public UserMapper userMapper() {
        return Mockito.mock(UserMapper.class);
    }
}