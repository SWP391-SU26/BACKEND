package com.courseqa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.courseqa.model.entity.UserRole;

// TODO: Extend JpaRepository after completing entity class.
// Example:
// public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {}

public interface UserRoleRepository extends JpaRepository<UserRole,UUID> {
List<UserRole> findByUserId(UUID userId);
//boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

boolean existsByUserIdAndRoleName(UUID userId, String roleName);

}
