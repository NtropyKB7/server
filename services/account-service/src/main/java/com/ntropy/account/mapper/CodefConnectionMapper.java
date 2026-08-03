package com.ntropy.account.mapper;

import com.ntropy.account.domain.entity.CodefConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodefConnectionMapper {

    void insert(CodefConnection codefConnection);

    void insertIfAbsent(CodefConnection codefConnection);

    void upsert(CodefConnection codefConnection);

    CodefConnection findByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);
}
