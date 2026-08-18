package com.ntropy.common.client;

import java.util.List;

import com.ntropy.common.domain.UserScope;

public interface ActiveUserQueryClient {

    List<Long> findActiveUserIds(UserScope scope);
}
