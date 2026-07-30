package com.ntropy.bff.controller.work;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.work.response.PlatformsResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.common.client.PlatformQueryClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/platforms")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformQueryClient platformQueryClient;

    @GetMapping
    public ApiResponse<PlatformsResponse> getPlatforms() {
        return ApiResponse.success(new PlatformsResponse(platformQueryClient.getPlatforms()));
    }
}
