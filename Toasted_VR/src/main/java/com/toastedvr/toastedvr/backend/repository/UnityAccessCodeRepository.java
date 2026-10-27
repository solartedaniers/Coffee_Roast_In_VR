package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.UnityAccessCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnityAccessCodeRepository extends JpaRepository<UnityAccessCode, Long> {

    Optional<UnityAccessCode> findByUserId(Long userId);

    Optional<UnityAccessCode> findByLookupHash(String lookupHash);
}
