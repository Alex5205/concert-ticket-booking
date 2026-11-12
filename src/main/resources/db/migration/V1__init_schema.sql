CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE flights (
    id BIGSERIAL PRIMARY KEY,
    flight_number VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    seat_number VARCHAR(20) NOT NULL,
    flight_id BIGINT NOT NULL REFERENCES flights(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_flight_seat UNIQUE (flight_id, seat_number)
);

CREATE INDEX idx_tickets_flight_id ON tickets(flight_id);
CREATE INDEX idx_tickets_user_id ON tickets(user_id);
