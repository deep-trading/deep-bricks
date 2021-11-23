-- 账户配置表，用户根据账户名称自行选择，通过ExchangeManager创建注册，完成初始化
CREATE TABLE IF NOT EXISTS account_config (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    type SMALLINT NOT NULL,
    clz VARCHAR(256) NOT NULL,
    listener_clz VARCHAR(256) NULL,
    api_clz VARCHAR(256) NULL,
    websocket VARCHAR(256) NULL,
    url VARCHAR(256) NOT NULL,
    uid VARCHAR(128) NULL,
    -- key, secret 加密后存储在数据库
    iv CHAR(32) NOT NULL,
    auth_key VARCHAR(1024) NOT NULL,
    auth_secret VARCHAR(1024) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    properties VARCHAR(1024) NULL,
    priority SMALLINT NOT NULL,
    taker_rate NUMERIC DEFAULT 0,
    maker_rate NUMERIC DEFAULT 0,
    UNIQUE (name)
);

-- 配置数据表

CREATE TABLE IF NOT EXISTS asset_base (
    id SERIAL PRIMARY KEY,
    asset VARCHAR(32) NOT NULL,
    account VARCHAR(10) NOT NULL,
    value NUMERIC NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (asset, account),
    FOREIGN KEY (account) REFERENCES account_config (name)
);

-- 任何资产的变更，均需在此处做记录，防止丢失

CREATE TABLE IF NOT EXISTS asset_history (
   id SERIAL PRIMARY KEY,
   asset VARCHAR(32) NOT NULL,
   account VARCHAR(10) NOT NULL,
   last_value NUMERIC NOT NULL,
   update_value NUMERIC NOT NULL,
   current_value NUMERIC NOT NULL,
   comment VARCHAR(256),
   time TIMESTAMP NOT NULL,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- CREATE INDEX IF NOT EXISTS idx_asset_history_time_desc ON asset_history
--     (asset, account, time DESC);


-- CREATE TABLE IF NOT EXISTS future_symbol (
--     id SERIAL PRIMARY KEY,
--     name VARCHAR(32) NOT NULL,
--     symbol VARCHAR(32) NOT NULL,
--     account VARCHAR(10) NOT NULL,
--     depth_qty INTEGER NOT NULL,
--     side VARCHAR(4) DEFAULT 'ALL',
--     price_precision NUMERIC NOT NULL DEFAULT 1000,
--     size_precision NUMERIC NOT NULL DEFAULT 100,
--     enabled BOOLEAN DEFAULT TRUE,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     UNIQUE (name, account),
--     FOREIGN KEY (account) REFERENCES account_config (name)
-- );


-- 余额盈利表

CREATE TABLE IF NOT EXISTS checking_profit (
   id SERIAL PRIMARY KEY,
   asset VARCHAR(32) NOT NULL,
   account VARCHAR(32) NOT NULL,
   last_size NUMERIC NOT NULL,
   size NUMERIC NOT NULL,
   price NUMERIC NOT NULL,
   result BIGINT NOT NULL,
   time TIMESTAMP NOT NULL,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- CREATE INDEX IF NOT EXISTS idx_checking_profit_time_desc ON checking_profit
--     (asset, time DESC);


-- 期货仓位数据表

CREATE TABLE IF NOT EXISTS position_value (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    account VARCHAR(32) NOT NULL,
    size NUMERIC NOT NULL,
    price NUMERIC NOT NULL,
    result BIGINT NOT NULL,
    entry_price NUMERIC NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC NOT NULL DEFAULT 0,
    time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- CREATE INDEX IF NOT EXISTS idx_position_value_time_desc ON position_value
--     (name, account, time DESC);

-- 资金费用表
CREATE TABLE IF NOT EXISTS funding (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    account VARCHAR(32) NOT NULL,
    rate NUMERIC DEFAULT 0,
    value NUMERIC NOT NULL,
    time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- CREATE INDEX IF NOT EXISTS idx_funding_time_desc ON funding
--     (name, account, time DESC);

-- 成交记录表

CREATE TABLE IF NOT EXISTS history_order (
    id SERIAL PRIMARY KEY,
    fill_id VARCHAR(64) NOT NULL,
    client_order_id VARCHAR(64) NULL,
    order_id VARCHAR(64) NOT NULL,
    name VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    account VARCHAR(32) NOT NULL,
    side VARCHAR(4) NOT NULL,
    type VARCHAR(8) NOT NULL,
    price NUMERIC NOT NULL,
    size NUMERIC NOT NULL,
    result NUMERIC NOT NULL,
    fee_asset VARCHAR(32) NOT NULL,
    fee NUMERIC NOT NULL,
    time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 计划订单记录表

CREATE TABLE IF NOT EXISTS hedging_plan_order (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    quantity BIGINT NOT NULL,
    symbol_price BIGINT NOT NULL,
    left_quantity BIGINT NOT NULL,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 生成订单记录表

CREATE TABLE IF NOT EXISTS hedging_order (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    symbol VARCHAR(32),
    account VARCHAR(10),
    side VARCHAR(4) NOT NULL,
    order_type VARCHAR(8) NOT NULL,
    quantity BIGINT NOT NULL,
    size NUMERIC NOT NULL,
    price NUMERIC NOT NULL,
    committed BOOLEAN DEFAULT FALSE,
    order_id VARCHAR(64) DEFAULT NULL,
    last_price NUMERIC NOT NULL,
    plan_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 下单结果记录表

CREATE TABLE IF NOT EXISTS hedging_order_result (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    left_size NUMERIC NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- letf project tables

-- CREATE TABLE IF NOT EXISTS market_symbol (
--     id SERIAL PRIMARY KEY,
--     name VARCHAR(32) UNIQUE NOT NULL,
--     symbol VARCHAR(32) NOT NULL,
--     account VARCHAR(32) NOT NULL,
--     future VARCHAR(32) NOT NULL,
--     asset VARCHAR(32) NOT NULL,
--     rate INTEGER NOT NULL,
--     ratio_limit NUMERIC NOT NULL DEFAULT 0.15,
--     min_price NUMERIC NOT NULL,
--     max_price NUMERIC NOT NULL,
--     mu NUMERIC NOT NULL,
--     sigma NUMERIC NOT NULL,
--     expense NUMERIC NOT NULL,
--     price_precision NUMERIC NOT NULL DEFAULT 1000,
--     size_precision NUMERIC NOT NULL DEFAULT 100,
--     enabled BOOLEAN DEFAULT TRUE,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     UNIQUE (name),
--     UNIQUE (symbol)
-- );

CREATE TABLE IF NOT EXISTS net_value (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32),
    price BIGINT NOT NULL,
    time timestamp NOT NULL default current_timestamp,
    symbol_price BIGINT NOT NULL,
    symbol_time TIMESTAMP NOT NULL default current_timestamp,
    created_at TIMESTAMP default current_timestamp
);

-- CREATE INDEX IF NOT EXISTS idx_net_value_time_desc ON net_value (name, time DESC);


CREATE TABLE IF NOT EXISTS account_value (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    value NUMERIC NOT NULL,
    time TIMESTAMP NOT NULL default current_timestamp,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- CREATE INDEX IF NOT EXISTS idx_account_value_time_desc ON account_value (name, time DESC);

-- version 2

CREATE TABLE IF NOT EXISTS symbol_info (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    account VARCHAR(10) NOT NULL,
    type SMALLINT NOT NULL,
    price_precision NUMERIC NOT NULL DEFAULT 1000,
    size_precision NUMERIC NOT NULL DEFAULT 100,
    properties VARCHAR(1024) NULL,
    enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name, account),
    FOREIGN KEY (account) REFERENCES account_config (name)
);

CREATE TABLE IF NOT EXISTS strategy_config (
    id SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    clz VARCHAR(256) NOT NULL,
--     factory VARCHAR(256) NOT NULL,
    info_name VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 1,
    properties VARCHAR(1024) NULL,
    enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name)
);
