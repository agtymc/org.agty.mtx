package org.agty.mtx.repository;

import org.agty.mtx.entity.UserAccount;
import org.agty.mtx.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    Optional<UserPreference> findByUserAndKey(UserAccount user, String key);
}
