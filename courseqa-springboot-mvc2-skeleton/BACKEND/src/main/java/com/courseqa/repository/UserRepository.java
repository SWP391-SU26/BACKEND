package com.courseqa.repository;

import com.courseqa.model.entity.User;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByIsActiveTrue();

    List<User> findTop50ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(String email, String fullName);
}
