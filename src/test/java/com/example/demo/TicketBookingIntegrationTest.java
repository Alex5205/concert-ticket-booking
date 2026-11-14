package com.example.demo;

import com.example.demo.entity.Flight;
import com.example.demo.entity.OutboxEvent;
import com.example.demo.entity.Ticket;
import com.example.demo.entity.TicketStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.FlightRepository;
import com.example.demo.repository.OutboxEventRepository;
import com.example.demo.repository.TicketRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketBookingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Ticket testTicket;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        ticketRepository.deleteAll();
        flightRepository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        Flight concert = Flight.builder()
                .flightNumber("ROCK-2026")
                .build();
        concert = flightRepository.save(concert);

        Ticket ticket = new Ticket();
        ticket.setFlight(concert);
        ticket.setSeatNumber("VIP-1");
        ticket.setStatus(TicketStatus.AVAILABLE);
        testTicket = ticketRepository.save(ticket);

        testUsers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            User user = new User();
            user.setUsername("fan_" + i);
            user.setPassword("hashed_password");
            testUsers.add(userRepository.save(user));
        }
    }

    @Test
    @DisplayName("Гонка потоков: из 10 одновременных запросов бронирует только 1")
    void concurrentReservation_OnlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            User currentUser = testUsers.get(i);
            String token = jwtService.generateToken(currentUser.getId(), currentUser.getUsername());

            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    MvcResult result = mockMvc.perform(post("/api/tickets/{id}/reserve", testTicket.getId())
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                            .andReturn();

                    statusCodes.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = statusCodes.stream().filter(code -> code == 200).count();
        long conflictCount = statusCodes.stream().filter(code -> code == 409).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(9);

        Ticket updatedTicket = ticketRepository.findById(testTicket.getId()).orElseThrow();
        assertThat(updatedTicket.getStatus()).isEqualTo(TicketStatus.RESERVED);
        assertThat(updatedTicket.getUser()).isNotNull();
    }

    @Test
    @DisplayName("Идемпотентность оплаты и Transactional Outbox")
    void payTicket_IsIdempotent_AndCreatesSingleOutboxEvent() throws Exception {
        User buyer = testUsers.get(0);
        String token = jwtService.generateToken(buyer.getId(), buyer.getUsername());
        String idempotencyKey = UUID.randomUUID().toString();

        // 1. Бронируем билет
        mockMvc.perform(post("/api/tickets/{id}/reserve", testTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 2. Первый запрос на оплату
        MvcResult firstPayResult = mockMvc.perform(post("/api/tickets/{id}/pay", testTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andReturn();

        // 3. Повторный запрос с тем же Idempotency-Key
        MvcResult secondPayResult = mockMvc.perform(post("/api/tickets/{id}/pay", testTicket.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(secondPayResult.getResponse().getContentAsString())
                .isEqualTo(firstPayResult.getResponse().getContentAsString());

        Ticket soldTicket = ticketRepository.findById(testTicket.getId()).orElseThrow();
        assertThat(soldTicket.getStatus()).isEqualTo(TicketStatus.SOLD);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("CONCERT_TICKET_PURCHASED");
        assertThat(events.get(0).getAggregateId()).isEqualTo(testTicket.getId().toString());
    }
}
