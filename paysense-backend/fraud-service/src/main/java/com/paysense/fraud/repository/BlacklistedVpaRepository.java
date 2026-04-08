package com.paysense.fraud.repository;

import com.paysense.fraud.entity.BlacklistedVpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlacklistedVpaRepository extends JpaRepository<BlacklistedVpa, UUID> {

    boolean existsByVpa(String vpa);

    Optional<BlacklistedVpa> findByVpa(String vpa);

    void deleteByVpa(String vpa);
}
