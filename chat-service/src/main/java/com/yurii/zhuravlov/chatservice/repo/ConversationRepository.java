package com.yurii.zhuravlov.chatservice.repo;

import com.yurii.zhuravlov.chatservice.entities.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

}