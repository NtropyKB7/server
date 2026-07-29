package com.ntropy.user.mapper;

import com.ntropy.user.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    // 1. OAuth 소셜 로그인 관련 메서드
    Optional<User> findByProviderAndProviderId(@Param("provider") String provider, @Param("providerId") String providerId);

    void insertUser(User user);

    void updateLoginInfo(User user);

    // 2. 일반 회원 프로필 CRUD 관련 메서드
    User findById(Long userId);

    void updateUser(User user);

    void deleteUser(Long userId);
}