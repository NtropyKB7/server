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

    Account findByIdAndUserIdAndProvider(@Param("id") Long id,
                                         @Param("userId") Long userId,
                                         @Param("provider") String provider);

    List<Account> findByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    boolean existsAnyByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    void deleteByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);
}
