package com.example.demo.service;

import com.example.demo.dto.IdempotencyRecord;
import com.example.demo.exception.IdempotencyConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:payment:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Пытается захватить ключ идемпотентности.
     *
     * @return Optional.empty(), если ключ захвачен впервые и можно выполнять оплату.
     *         Optional с готовым ответом, если запрос уже был успешно выполнен ранее.
     * @throws IdempotencyConflictException если запрос с этим ключом прямо сейчас выполняется.
     */
    public Optional<IdempotencyRecord> acquireOrGet(String idempotencyKey) {
        String redisKey = buildKey(idempotencyKey);
        IdempotencyRecord inProgressRecord = new IdempotencyRecord(
                IdempotencyRecord.Status.IN_PROGRESS,
                0,
                null
        );

        String serializedLock = serialize(inProgressRecord);

        // Атомарная команда SET key value NX EX 120
        Boolean isAcquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, serializedLock, LOCK_TTL);

        if (Boolean.TRUE.equals(isAcquired)) {
            log.debug("Захвачен новый ключ идемпотентности: {}", redisKey);
            return Optional.empty();
        }

        // Если ключ уже существует — читаем, что там лежит
        String existingJson = redisTemplate.opsForValue().get(redisKey);
        if (existingJson == null) {
            // Редкий случай: ключ истёк ровно между setIfAbsent и get
            throw new IdempotencyConflictException("Не удалось проверить статус операции, повторите запрос");
        }

        IdempotencyRecord existingRecord = deserialize(existingJson);

        if (existingRecord.status() == IdempotencyRecord.Status.IN_PROGRESS) {
            log.warn("Дублирующий запрос во время выполнения операции: {}", redisKey);
            throw new IdempotencyConflictException("Оплата по данному ключу уже обрабатывается");
        }

        log.info("Возвращён кэшированный результат для ключа идемпотентности: {}", redisKey);
        return Optional.of(existingRecord);
    }

    /**
     * Сохраняет финальный результат успешной операции на 24 часа.
     */
    public void saveCompleted(String idempotencyKey, int httpStatus, Object responseObject) {
        String redisKey = buildKey(idempotencyKey);
        String responseBody = serialize(responseObject);

        IdempotencyRecord completedRecord = new IdempotencyRecord(
                IdempotencyRecord.Status.COMPLETED,
                httpStatus,
                responseBody
        );

        redisTemplate.opsForValue().set(redisKey, serialize(completedRecord), COMPLETED_TTL);
        log.debug("Сохранён результат идемпотентной операции на 24ч: {}", redisKey);
    }

    /**
     * Удаляет ключ при непредвиденной ошибке, чтобы клиент мог повторить попытку.
     */
    public void releaseOnError(String idempotencyKey) {
        String redisKey = buildKey(idempotencyKey);
        redisTemplate.delete(redisKey);
        log.debug("Ключ идемпотентности освобождён из-за ошибки: {}", redisKey);
    }

    private String buildKey(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка сериализации данных для Redis", e);
        }
    }

    private IdempotencyRecord deserialize(String json) {
        try {
            return objectMapper.readValue(json, IdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка десериализации данных из Redis", e);
        }
    }
}
