package com.ntropy.account.mapper;

import com.ntropy.account.CodefConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodefConnectionMapper {

    void insert(CodefConnection codefConnection);

    void upsert(CodefConnection codefConnection);

    CodefConnection findByUserId(@Param("userId") Long userId);
}
