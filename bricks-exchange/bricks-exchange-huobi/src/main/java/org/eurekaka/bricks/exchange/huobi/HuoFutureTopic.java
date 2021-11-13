package org.eurekaka.bricks.exchange.huobi;

import org.eurekaka.bricks.common.exception.ExchangeException;

class HuoFutureTopic {

    public final String part0;
    public final String part1;
    public final String part2;

    public static HuoFutureTopic parseTopicV1(String ch) throws Exception {
        String[] parts = ch.split("\\.");
        if (parts.length == 1) {
            return new HuoFutureTopic(parts[0], null);
        } else if (parts.length == 2) {
            return new HuoFutureTopic(parts[0], parts[1]);
        } else if (parts.length >= 3) {
            return new HuoFutureTopic(parts[0], parts[1], parts[2]);
        }
        throw new ExchangeException("unknown topic: " + ch);
    }

    public HuoFutureTopic(String part0, String part1) {
        this(part0, part1, null);
    }

    public HuoFutureTopic(String part0, String part1, String part2) {
        this.part0 = part0;
        this.part1 = part1;
        this.part2 = part2;
    }

    @Override
    public String toString() {
        return "HuoFutureTopic{" +
                "part0='" + part0 + '\'' +
                ", part1='" + part1 + '\'' +
                ", part2='" + part2 + '\'' +
                '}';
    }
}
