package com.ntropy.account.mapper;

import com.ntropy.account.domain.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    void upsert(Account account);

    Account findByConnectionIdAndAccountNoHash(@Param("codefConnectionId") Long codefConnectionId,
                                               @Param("accountNoHash") String accountNoHash);
}
