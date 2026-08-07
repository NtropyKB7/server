package com.ntropy.config;

import org.springframework.lang.Nullable;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.Filter;

public class WebConfig extends AbstractAnnotationConfigDispatcherServletInitializer {
    @Nullable
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class[] { RootConfig.class };
    }

    @Nullable
    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class[] { ServletConfig.class, SwaggerConfig.class };
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" };
    }

    @Nullable
    @Override
    protected Filter[] getServletFilters() {
        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();

        characterEncodingFilter.setEncoding("UTF-8");
        characterEncodingFilter.setForceEncoding(true);

        // SecurityConfig가 만드는 springSecurityFilterChain을 서블릿 필터로 등록한다.
        // 이게 없으면 SecurityFilterChain 빈이 생성만 되고 요청 경로에는 들어가지 않는다.
        DelegatingFilterProxy springSecurityFilterChain =
                new DelegatingFilterProxy("springSecurityFilterChain");

        return new Filter[] { characterEncodingFilter, springSecurityFilterChain };
    }
}
