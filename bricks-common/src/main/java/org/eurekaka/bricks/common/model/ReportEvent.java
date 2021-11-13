package org.eurekaka.bricks.common.model;

public class ReportEvent {

    private EventType eventType;
    private EventLevel level;
    private String content;

    public ReportEvent(EventType eventType, EventLevel level, String content) {
        this.eventType = eventType;
        this.level = level;
        this.content = content;
    }


    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public EventLevel getLevel() {
        return level;
    }

    public void setLevel(EventLevel level) {
        this.level = level;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }


    @Override
    public String toString() {
        return "ReportEvent{" +
                "eventType=" + eventType +
                ", level=" + level +
                ", content='" + content + '\'' +
                '}';
    }

    public enum  EventType {
        MONITOR_TEST("MONITOR: TEST"),

        HEDGING_MAKE_ORDER_FAILED("HEDGING: failed to make an order"),
        HEDGING_GET_POSITION_FAILED("HEDGING: failed to get positions"),
        HEDGING_WEBSOCKET_FAILED("HEDGING: websocket closed"),
        HEDGING_AGENT_FAILED("HEDGING: exchange agent dead"),
        HEDGING_FAILED("HEDGING: thread failed."),
        HEDGING_GET_NET_VALUE_FAILED("HEDGING: failed to get net values"),
        HEDGING_LIMIT_PROCESSOR_FAILED("HEDGING: limit order processor failed"),
        HEDGING_MARKET_PROCESSOR_FAILED("HEDGING: market order processor failed"),
        HEDGING_EXCEEDED_QUANTITY_ORDER("HEDGING: order exceeded max quantity"),

        HEDGING_CONTRACT_INFO("HEDGING: contract info not match"),

        MARKET_ACCOUNT_VALUE_FAILED("MARKET: failed to get account value"),
        MARKET_FEE_RATE_TOO_LOW("MARKET: fee rate is lower than funding rate"),

        MONITOR_ACCOUNT_VALUE("MARKET: account value too low"),
        MONITOR_MARKET_MIN_PRICE("MARKET: min price too small"),
        MONITOR_HEDGING_LEVERAGE_LEVEL("HEDGER: leverage level too high"),
        MONITOR_HEDGING_LIQ_PRICE("HEDGER: very close to liq price"),
        MONITOR_HEDGING_AVAILABLE_MARGIN("HEDGER: too low available margin"),
        MONITOR_HEDGING_RISK_LIMIT("HEDGER: too high risk limit ratio"),
        MONITOR_HEDGING_FUNDING_EXPENSE("HEDGER: funding rate too high"),

        STRATEGY_FAILED("STRATEGY: running failed."),
        STRATEGY_NOT_BALANCE("STRATEGY: unbalance"),

        ARBITRAGE_HISTORY_ORDER_FAILED("ARBITRAGE: failed to process history order"),
        ARBITRAGE_FUTURE_PROCESSOR_FAILED("ARBITRAGE: future processor failed");

        private String description;

        EventType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public enum EventLevel {
        SERIOUS("#DC143C"), // 红色
        WARNING("#FF4500"), // 橙色
        NORMAL("#6495ED"),  // 蓝色
        ;


        private String color;

        EventLevel(String color) {
            this.color = color;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }
}
