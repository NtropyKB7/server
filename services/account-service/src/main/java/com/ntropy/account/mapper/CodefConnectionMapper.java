package com.ntropy.account.mapper;

import com.ntropy.account.CodefConnection;
import org.apache.ibatis.annotations.Param;

public interface CodefConnectionMapper {

    void insert(CodefConnection codefConnection);

    CodefConnection findByUserId(@Param("userId") Long userId);
}
