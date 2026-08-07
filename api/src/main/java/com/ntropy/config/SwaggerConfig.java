package com.ntropy.config;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * Swagger(springfox 2.9.2) 설정.
 *
 * 컨트롤러는 각 서비스 모듈(com.ntropy.*)에 흩어져 있고, ServletConfig가
 * "com.ntropy" 하위를 전부 스캔하고 있어서 Docket도 동일한 basePackage로 잡음.
 *
 * springfox 2.9.2/3.0.0이 Spring Framework 5.2.4+ 에서 PatternsRequestCondition을
 * 못 찾아 "Failed to start bean 'documentationPluginsBootstrapper'" NPE로 죽는
 * 알려진 호환성 이슈가 있어(springfox GitHub issue #3462), BeanPostProcessor로 우회함.
 * 이 프로젝트는 Spring 5.3.39라 이 우회 처리가 없으면 기동 자체가 안 됨.
 */
@Configuration
@EnableSwagger2
public class SwaggerConfig {

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.ntropy"))
                .paths(PathSelectors.any())
                .build()
                // Authentication은 인증 필터가 채우는 파라미터라 문서/입력 대상이 아니다.
                // @ApiParam(hidden = true)만으로는 springfox 2.9.2가 필드를 펼쳐버려서 명시적으로 무시 등록한다.
                .ignoredParameterTypes(org.springframework.security.core.Authentication.class)
                .securitySchemes(Collections.singletonList(bearerAuth()))
                .securityContexts(Collections.singletonList(authenticatedApiSecurityContext()));
    }

    private ApiKey bearerAuth() {
        return new ApiKey("Bearer", "Authorization", "header");
    }

    /**
     * SecurityConfig의 permitAll 경로를 제외한 모든 /api/** 에 Bearer 인증 요구를 표시한다.
     * springfox는 각 오퍼레이션에 이 표시가 있어야 Swagger UI의 Authorize 값을
     * 실제 요청 헤더에 자동으로 실어준다 - 표시가 없으면 Authorize를 해도 헤더가 안 붙는다.
     */
    private SecurityContext authenticatedApiSecurityContext() {
        return SecurityContext.builder()
                .securityReferences(Collections.singletonList(
                        new SecurityReference("Bearer", new AuthorizationScope[] {
                                new AuthorizationScope("global", "인증 필요 API 접근")
                        })
                ))
                .forPaths(PathSelectors.regex(
                        "^/api/(?!auth/oauth|auth/refresh|subscriptions/webhook|subscriptions/plans).*$"
                ))
                .build();
    }

    @Bean
    public static BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof WebMvcRequestHandlerProvider) {
                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
                }
                return bean;
            }

            private <T extends RequestMappingInfoHandlerMapping> void customizeSpringfoxHandlerMappings(List<T> mappings) {
                List<T> copy = mappings.stream()
                        .filter(mapping -> mapping.getPatternParser() == null)
                        .collect(Collectors.toList());
                mappings.clear();
                mappings.addAll(copy);
            }

            @SuppressWarnings("unchecked")
            private List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
                try {
                    Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
                    field.setAccessible(true);
                    return (List<RequestMappingInfoHandlerMapping>) field.get(bean);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}
