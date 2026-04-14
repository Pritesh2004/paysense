package com.paysense.notification.service;

import com.paysense.notification.entity.Notification;
import com.paysense.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    
    // Store active SSE streams per user
    private final Map<UUID, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    @Transactional
    public void createAndSendNotification(UUID userId, String type, String title, String body, String channel, Map<String, Object> metadata) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .channel(channel)
                .isRead(false)
                .sentAt(LocalDateTime.now())
                .metadata(metadata)
                .build();
                
        Notification saved = notificationRepository.save(notification);
        log.info("Saved notification {} for user {}", saved.getId(), userId);
        
        sendToSseStream(userId, saved);
    }

    public SseEmitter createSseConnection(UUID userId) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1 hour timeout
        activeEmitters.put(userId, emitter);
        
        emitter.onCompletion(() -> activeEmitters.remove(userId));
        emitter.onTimeout(() -> {
            emitter.complete();
            activeEmitters.remove(userId);
        });
        emitter.onError((e) -> activeEmitters.remove(userId));
        
        // Send a connected event
        try {
            emitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).name("CONNECTED").data("Connected to Notification Service"));
        } catch (IOException e) {
            log.error("Failed to send initial connection event to user {}", userId, e);
            emitter.completeWithError(e);
            activeEmitters.remove(userId);
        }
        
        log.info("SSE Connection created for user {}", userId);
        return emitter;
    }

    private void sendToSseStream(UUID userId, Notification notification) {
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.getId().toString())
                        .name(notification.getType())
                        .data(notification));
                log.info("Successfully pushed notification {} to user {} via SSE", notification.getId(), userId);
            } catch (IOException e) {
                log.error("Failed to push notification to user {}, removing emitter.", userId, e);
                emitter.completeWithError(e);
                activeEmitters.remove(userId);
            }
        }
    }

    public List<Notification> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(UUID id) {
        Optional<Notification> notificationOpt = notificationRepository.findById(id);
        if (notificationOpt.isPresent()) {
            Notification notification = notificationOpt.get();
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
            log.info("Marked notification {} as read", id);
        }
    }
}
