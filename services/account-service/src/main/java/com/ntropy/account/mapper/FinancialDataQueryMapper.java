package com.ntropy.account.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.mapper.projection.OwnedAccountTransactionRow;

@Mapper
public interface FinancialDataQueryMapper {

    List<Account> findAccountsByUserId(@Param("userId") Long userId);

    Account findAccountByIdAndUserId(@Param("accountId") Long accountId,
                                     @Param("userId") Long userId);

    List<OwnedAccountTransactionRow> findTransactionsByAccountIdAndUserId(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId
    );
}
