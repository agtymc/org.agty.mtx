package org.agty.mtx.repository;

import org.agty.mtx.entity.ChatGroup;
import org.agty.mtx.entity.ChatThread;
import org.agty.mtx.entity.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {
    @EntityGraph(attributePaths = {"group"})
    List<ChatThread> findAllByUserOrderByUpdatedAtDesc(UserAccount user);
    @EntityGraph(attributePaths = {"group"})
    List<ChatThread> findAllByUserOrderBySortOrderAscUpdatedAtDesc(UserAccount user);

    @EntityGraph(attributePaths = {"group"})
    List<ChatThread> findAllByUserAndGroupOrderByUpdatedAtDesc(UserAccount user, ChatGroup group);
    @EntityGraph(attributePaths = {"group"})
    List<ChatThread> findAllByUserAndGroupOrderBySortOrderAscUpdatedAtDesc(UserAccount user, ChatGroup group);
    @EntityGraph(attributePaths = {"group"})
    List<ChatThread> findAllByUserAndGroupIsNullOrderBySortOrderAscUpdatedAtDesc(UserAccount user);

    @EntityGraph(attributePaths = {"group"})
    Optional<ChatThread> findByIdAndUser(Long id, UserAccount user);

    Optional<ChatThread> findTopByUserAndGroupOrderBySortOrderDesc(UserAccount user, ChatGroup group);

    Optional<ChatThread> findTopByUserAndGroupIsNullOrderBySortOrderDesc(UserAccount user);

    long countByUserAndGroup(UserAccount user, ChatGroup group);

    boolean existsByUser(UserAccount user);
}
