package com.toastedvr.toastedvr.backend.repository;

import java.util.Optional;

import com.toastedvr.toastedvr.backend.domain.OneTimeCode;
import com.toastedvr.toastedvr.backend.domain.OneTimeCodePurpose;
import com.toastedvr.toastedvr.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, Long> {

    Optional<OneTimeCode> findByUserAndPurpose(User user, OneTimeCodePurpose purpose);
}
