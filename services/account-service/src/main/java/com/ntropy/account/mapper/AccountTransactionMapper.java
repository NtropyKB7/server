package com.ntropy.account.mapper;

import java.util.List;

import com.ntropy.account.domain.entity.AccountTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountTransactionMapper {

    void insertAll(@Param("list") List<AccountTransaction> transactions);
}
