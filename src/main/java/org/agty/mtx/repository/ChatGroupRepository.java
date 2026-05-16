package org.agty.mtx.repository;

import org.agty.mtx.entity.ChatGroup;
import org.agty.mtx.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {
    List<ChatGroup> findAllByUserOrderByUpdatedAtDesc(UserAccount user);
    List<ChatGroup> findAllByUserOrderBySortOrderAscUpdatedAtDesc(UserAccount user);

    Optional<ChatGroup> findByIdAndUser(Long id, UserAccount user);
    Optional<ChatGroup> findTopByUserOrderBySortOrderDesc(UserAccount user);

    boolean existsByUser(UserAccount user);
}
