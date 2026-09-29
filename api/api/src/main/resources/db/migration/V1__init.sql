-- ERD 기준 User / Refresh_Token 테이블
-- 주의: `user`는 MySQL 예약어라 테이블명은 `users`로 실제 생성한다 (ERD상 개념 이름은 User로 동일).

CREATE TABLE user (
    id             CHAR(36)     NOT NULL,
    nickname       VARCHAR(20),
    email          VARCHAR(255),
    provider       VARCHAR(50)  NOT NULL,
    social_id      VARCHAR(50)  NOT NULL,
    current_level  INT          NOT NULL DEFAULT 0,
    timezone       VARCHAR(50)  NOT NULL DEFAULT 'Asia/Seoul',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_provider_social_id (provider, social_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_token (
    id             CHAR(36)     NOT NULL,
    user_id        CHAR(36)     NOT NULL,
    token_hash     VARCHAR(255) NOT NULL,
    device_info    VARCHAR(255),
    expires_at     DATETIME     NOT NULL,
    revoked_at     DATETIME     NULL,
    last_used_at   DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user_id (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
