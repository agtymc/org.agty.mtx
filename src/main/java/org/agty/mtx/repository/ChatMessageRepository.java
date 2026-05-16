package org.agty.mtx.repository;

import org.agty.mtx.entity.ChatMessage;
import org.agty.mtx.entity.ChatThread;
import org.agty.mtx.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByChatOrderByMessageOrderAsc(ChatThread chat);

    long countByChat(ChatThread chat);

    List<ChatMessage> findAllByChatUserAndRoleOrderByCreatedAtAsc(UserAccount user, String role);

    void deleteAllByChat(ChatThread chat);
}
