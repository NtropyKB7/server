-- ============================================================
-- account-service DDL
-- CODEF_CONNECTION: 이슈 #5(CODEF 초기 세팅/PoC) 범위.
-- ACCOUNT, ACCOUNT_TRANSACTION: 이슈 #20(계좌/거래내역 파싱·저장) 범위.
-- ============================================================

CREATE TABLE IF NOT EXISTS CODEF_CONNECTION
(
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT       NOT NULL COMMENT 'user-service USER.id 참조 (크로스 도메인 FK 없음)',
    connected_id                VARCHAR(100) NOT NULL COMMENT 'CODEF 커넥티드 아이디 (계정 등록 API 응답값)',
    registered_institution_keys TEXT         NULL COMMENT '등록 완료 기관코드 JSON 배열, 예: ["0004","0088"]. 동일 기관 중복 /account/add 요청 방지용',
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_codef_connection_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 이미 CODEF_CONNECTION이 생성되어 있는 기존 로컬 DB라면 위 컬럼이 반영되지 않으므로 아래 ALTER를 한 번 수동 실행한다.
-- (이 스키마 파일은 마이그레이션 도구 없이 수동 적용하는 방식이라 IF NOT EXISTS ADD COLUMN 같은 자동 가드는 넣지 않는다)
-- ALTER TABLE CODEF_CONNECTION
--     ADD COLUMN registered_institution_keys TEXT NULL
--         COMMENT '등록 완료 기관코드 JSON 배열, 예: ["0004","0088"]. 동일 기관 중복 /account/add 요청 방지용'
--         AFTER connected_id;

-- CODEF OAuth2 accessToken 캐시. client_credentials 방식이라 사용자 단위가 아닌 클라이언트(서비스) 단위로 존재.
-- 지금은 DB로만 캐싱하고, 추후 Redis 도입 시 CodefTokenStore의 Redis 구현체로 대체 예정 (DEVLOG 참고).
CREATE TABLE IF NOT EXISTS CODEF_TOKEN
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_type VARCHAR(20)   NOT NULL COMMENT 'SANDBOX, DEMO, API',
    client_id    VARCHAR(100)  NOT NULL COMMENT '토큰을 발급받은 CODEF OAuth 클라이언트 식별자',
    access_token VARCHAR(2000) NOT NULL,
    expires_at   DATETIME      NOT NULL COMMENT 'accessToken 만료 시각 (발급 시각 + expires_in)',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_codef_token_lookup (service_type, client_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- CODEF 보유계좌 조회(account-list) 응답을 저장. 예금/신탁·외화·펀드·대출·보험 5개 그룹을 account_group으로 구분하는 단일 테이블.
-- 계좌번호 원문은 저장하지 않고 표시용 마스킹 값과 중복 판별용 해시만 저장한다.
CREATE TABLE IF NOT EXISTS ACCOUNT
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    codef_connection_id BIGINT        NOT NULL COMMENT 'CODEF_CONNECTION.id 참조',
    user_id             BIGINT        NOT NULL COMMENT 'user-service USER.id 참조 (크로스 도메인 FK 없음), 조회 편의를 위한 비정규화',
    organization_code   VARCHAR(10)   NOT NULL COMMENT 'CODEF 기관코드',
    account_group       VARCHAR(20)   NOT NULL COMMENT 'DEPOSIT_TRUST, FOREIGN_CURRENCY, FUND, LOAN, INSURANCE',
    deposit_type_code   VARCHAR(2)    NOT NULL COMMENT 'resAccountDeposit 분류값 (10~99)',
    account_no_masked   VARCHAR(64)   NOT NULL COMMENT 'resAccountDisplay 표시용 마스킹 계좌번호',
    account_no_hash     CHAR(64)      NOT NULL COMMENT 'SHA-256(기관코드+실제계좌번호) 중복 판별용 해시. 원문 계좌번호는 저장하지 않음',
    account_name        VARCHAR(100)  NULL COMMENT 'resAccountNickName 우선, 없으면 resAccountName',
    balance             DECIMAL(18,2) NULL COMMENT 'resAccountBalance',
    currency_code       VARCHAR(3)    NOT NULL DEFAULT 'KRW' COMMENT 'resAccountCurrency (ISO 4217)',
    account_start_date  DATE          NULL COMMENT 'resAccountStartDate',
    account_end_date    DATE          NULL COMMENT 'resAccountEndDate',
    last_tran_date      DATE          NULL COMMENT 'resLastTranDate',
    account_lifetime    VARCHAR(20)   NULL COMMENT 'resAccountLifetime (예금/신탁)',
    overdraft_yn        BOOLEAN       NULL COMMENT 'resOverdraftAcctYN (예금/신탁)',
    loan_kind           VARCHAR(50)   NULL COMMENT 'resLoanKind (예금/신탁 마이너스통장)',
    loan_balance        DECIMAL(18,2) NULL COMMENT 'resLoanBalance (예금/신탁 마이너스통장)',
    loan_start_date     DATE          NULL COMMENT 'resLoanStartDate (예금/신탁 마이너스통장)',
    loan_end_date       DATE          NULL COMMENT 'resLoanEndDate (예금/신탁 마이너스통장)',
    invested_cost       DECIMAL(18,2) NULL COMMENT 'resAccountInvestedCost (펀드)',
    earnings_rate       DECIMAL(9,4)  NULL COMMENT 'resEarningsRate (펀드)',
    loan_exec_no        VARCHAR(50)   NULL COMMENT 'resAccountLoanExecNo (대출)',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_connection_hash (codef_connection_id, account_no_hash),
    CONSTRAINT fk_account_codef_connection FOREIGN KEY (codef_connection_id) REFERENCES CODEF_CONNECTION (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- CODEF 수시입출 거래내역(transaction-list) 응답을 저장. 이번 이슈에서는 거래 중복 방지를 적용하지 않고 조회 시마다 그대로 insert한다.
CREATE TABLE IF NOT EXISTS ACCOUNT_TRANSACTION
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id    BIGINT        NOT NULL COMMENT 'ACCOUNT.id 참조',
    tran_date     DATE          NOT NULL COMMENT 'resAccountTrDate',
    tran_time     TIME          NULL COMMENT 'resAccountTrTime (hhmmss)',
    out_amount    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT 'resAccountOut',
    in_amount     DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT 'resAccountIn',
    after_balance DECIMAL(18,2) NOT NULL COMMENT 'resAfterTranBalance',
    desc1         VARCHAR(255)  NULL COMMENT 'resAccountDesc1, 은행별 의미가 달라 원본 필드명 유지',
    desc2         VARCHAR(255)  NULL COMMENT 'resAccountDesc2, 은행별 의미가 달라 원본 필드명 유지',
    desc3         VARCHAR(255)  NULL COMMENT 'resAccountDesc3, 은행별 의미가 달라 원본 필드명 유지',
    desc4         VARCHAR(255)  NULL COMMENT 'resAccountDesc4, 은행별 의미가 달라 원본 필드명 유지',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_account_transaction_account_date (account_id, tran_date),
    CONSTRAINT fk_account_transaction_account FOREIGN KEY (account_id) REFERENCES ACCOUNT (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
