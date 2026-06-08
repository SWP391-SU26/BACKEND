package com.courseqa.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.courseqa.model.entity.User;

// TODO: Extend JpaRepository after completing entity class.
// Example:
// public interface UserRepository extends JpaRepository<User, UUID> {}
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
Optional<User> findByEmail(String email);
boolean existsByEmail(String email);

}
