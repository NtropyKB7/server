package com.ntropy.account.mapper;

import java.util.List;

import com.ntropy.account.domain.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    void upsert(Account account);

    void updateAccountDetails(Account account);

    Account findByConnectionIdAndAccountNoHash(@Param("codefConnectionId") Long codefConnectionId,
                                               @Param("accountNoHash") String accountNoHash);

    Account findByIdAndProvider(@Param("id") Long id, @Param("provider") String provider);

    List<Account> findByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);
}
