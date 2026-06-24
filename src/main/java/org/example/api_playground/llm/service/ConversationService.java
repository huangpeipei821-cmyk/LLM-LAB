package org.example.api_playground.llm.service;

import lombok.RequiredArgsConstructor;
import org.example.api_playground.llm.model.Conversation;
import org.example.api_playground.llm.model.ConversationMessage;
import org.example.api_playground.llm.repository.ConversationMessageRepository;
import org.example.api_playground.llm.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    public Conversation createConversation(String title, String mode) {
        Conversation conversation = Conversation.builder()
                .title(title)
                .mode(mode)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return conversationRepository.save(conversation);
    }

    public List<Conversation> getConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    public Conversation getConversation(Long id) {
        return conversationRepository.findById(id).orElse(null);
    }

    @Transactional
    public void deleteConversation(Long id) {
        messageRepository.deleteByConversationId(id);
        conversationRepository.deleteById(id);
    }

    public ConversationMessage saveMessage(Long conversationId, String role, String content) {
        ConversationMessage message = ConversationMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        ConversationMessage saved = messageRepository.save(message);

        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conv);
        });

        return saved;
    }

    public List<ConversationMessage> getMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}