package com.paysense.payment.repository;

import com.paysense.payment.entity.VpaRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VpaRegistryRepository extends JpaRepository<VpaRegistry, UUID> {

    Optional<VpaRegistry> findByVpaAndIsActiveTrue(String vpa);

    Optional<VpaRegistry> findByVpa(String vpa);

    List<VpaRegistry> findByAccountIdAndIsActiveTrue(UUID accountId);

    boolean existsByVpa(String vpa);
}
