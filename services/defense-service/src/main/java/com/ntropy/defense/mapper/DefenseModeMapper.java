package com.ntropy.defense.mapper;

import com.ntropy.defense.domain.DefenseMode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DefenseModeMapper {
    DefenseMode findById(Long defenseId);
    DefenseMode findActiveByUserId(Long userId);
    int insert(DefenseMode defenseMode);
    int release(DefenseMode defenseMode);
}
