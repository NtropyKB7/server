package com.ntropy.common.client;

import java.util.List;

public interface ActiveUserQueryClient {

    List<Long> findActiveUserIds();
}
