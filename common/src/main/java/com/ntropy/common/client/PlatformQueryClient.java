package com.ntropy.common.client;

import java.util.List;

import com.ntropy.common.dto.PlatformSummary;

public interface PlatformQueryClient {

    List<PlatformSummary> getPlatforms();

    PlatformSummary getPlatform(Long platformId);
}
