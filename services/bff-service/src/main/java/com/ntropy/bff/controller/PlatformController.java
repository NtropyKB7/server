package com.ntropy.bff.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.work.PlatformsResponse;
import com.ntropy.common.client.PlatformQueryClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/platforms")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformQueryClient platformQueryClient;

    @GetMapping
    public PlatformsResponse getPlatforms() {
        return new PlatformsResponse(platformQueryClient.getPlatforms());
    }
}
