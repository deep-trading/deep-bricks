package org.eurekaka.bricks.common.util;

import org.eurekaka.bricks.common.model.KLineValue;

import java.util.List;

public class StatisticsUtils {

    public static double getMeanAverage(List<KLineValue> kLineValues, int N) {
        if (kLineValues.size() < N) {
            throw new IllegalArgumentException("period size N is smaller than kline size: " +
                    N + " < " + kLineValues.size());
        }

        return kLineValues.subList(kLineValues.size() - N, kLineValues.size()).stream()
                .mapToDouble(e -> e.close).average().orElse(0);
    }

    public static double getSMA_RSI(List<KLineValue> kLineValues, int N) {
        if (kLineValues.size() < N + 1) {
            throw new IllegalArgumentException("period tickle size N is smaller than kline size: " +
                    (N+1) + " < " + kLineValues.size());
        }
        double[] closePrices = kLineValues.subList(kLineValues.size() - N - 1,
                kLineValues.size()).stream().mapToDouble(e -> e.close).toArray();

        double avgU = 0;
        double avgD = 0;

        for (int i = 0; i < closePrices.length - 1; i++) {
            if (closePrices[i] < closePrices[i + 1]) {
                avgU += closePrices[i + 1] - closePrices[i];
            } else {
                avgD += closePrices[i] - closePrices[i + 1];
            }
        }
        avgU = avgU / N;
        avgD = avgD / N;

        if (avgD == 0) {
            return 100;
        }
        return 100 - 100D / (1 + avgU / avgD);
    }

    public static double getEMA_RSI(List<KLineValue> kLineValues, int N) {
        if (kLineValues.size() < N + 1 || N < 2) {
            throw new IllegalArgumentException("period tickle size N is smaller than kline size: " +
                    (N+1) + " < " + kLineValues.size());
        }
        double[] closePrices = kLineValues.subList(kLineValues.size() - N - 1,
                kLineValues.size()).stream().mapToDouble(e -> e.close).toArray();

        double avgU = 0;
        double avgD = 0;
        double a = 2D / (N + 1);

        for (int i = 0; i < closePrices.length - 1; i++) {
            double up = 0;
            double down = 0;
            if (closePrices[i + 1] > closePrices[i]) {
                up = closePrices[i + 1] - closePrices[i];
            } else {
                down = closePrices[i] - closePrices[i + 1];
            }
            if (i == 0) {
                avgU = up;
                avgD = down;
            } else {
                avgU = a * up + (1 - a) * avgU;
                avgD = a * down + (1 - a) * avgD;
            }
        }

        if (avgD == 0) {
            return 100;
        }
        return 100 - 100D / (1 + avgU / avgD);
    }

}
