package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.mapper.InMemoryJobPlatformMappingMapper;
import com.ntropy.work.mapper.InMemoryPlatformMapper;

class JobPlatformMappingServiceTest {

    private static final Long JOB_ID = 1L;
    private static final Long PLATFORM_ID = 1L;

    private JobPlatformMappingService service;

    @BeforeEach
    void setUp() {
        InMemoryPlatformMapper platformMapper = new InMemoryPlatformMapper();
        platformMapper.seed(Platform.builder().platformId(PLATFORM_ID).categoryId(1L)
                .platformName("배달의민족").settlementCycle("DAILY").build());
        platformMapper.seed(Platform.builder().platformId(2L).categoryId(1L)
                .platformName("쿠팡이츠").settlementCycle("DAILY").build());
        service = new JobPlatformMappingService(new InMemoryJobPlatformMappingMapper(), platformMapper);
    }

    @Test
    @DisplayName("잡-플랫폼 매핑을 정상 등록한다")
    void register_success() {
        var mapping = service.register(JOB_ID, PLATFORM_ID);

        assertEquals(JOB_ID, mapping.getJobId());
        assertEquals(PLATFORM_ID, mapping.getPlatformId());
        assertEquals(1, service.findByJobId(JOB_ID).size());
    }

    @Test
    @DisplayName("존재하지 않는 플랫폼으로 등록하면 실패한다")
    void register_unknownPlatform_throws() {
        assertThrows(ServiceException.class, () -> service.register(JOB_ID, 999L));
    }

    @Test
    @DisplayName("이미 등록된 매핑을 다시 등록하면 실패한다")
    void register_duplicateMapping_throws() {
        service.register(JOB_ID, PLATFORM_ID);

        assertThrows(ServiceException.class, () -> service.register(JOB_ID, PLATFORM_ID));
    }

    @Test
    @DisplayName("같은 잡에 다른 플랫폼은 추가로 등록할 수 있다")
    void register_sameJobDifferentPlatform_succeeds() {
        service.register(JOB_ID, PLATFORM_ID);

        service.register(JOB_ID, 2L);

        assertEquals(2, service.findByJobId(JOB_ID).size());
    }
}
