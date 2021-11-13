package org.eurekaka.bricks.exchange.huobi;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.Utils;

import javax.crypto.Mac;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class HuoFutureApi implements FutureExApi {

    private final static int DEFAULT_LEVER_RATE = 5;

    private final AccountConfig accountConfig;
    private final HttpClient httpClient;
    // 张数转换器
    private final Map<String, Double> contractMultiplier;
    // 杠杆倍数，初始化获取仓位信息时，更新杠杆倍数
    private final Map<String, Integer> leverRate;

    private final static ZoneId zoneId = ZoneId.of("UTC");
    private final static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public HuoFutureApi(AccountConfig accountConfig, HttpClient httpClient) {
        this.accountConfig = accountConfig;
        this.httpClient = httpClient;

        this.contractMultiplier = new ConcurrentHashMap<>();
        this.leverRate = new ConcurrentHashMap<>();

        // 初始化需要调用一次exchangeInfo接口与accountValue接口
        try {
            getExchangeInfos();
            getAccountValue();
        } catch (ExApiException e) {
            throw new InitializeException("failed to initialize huo future api", e);
        }
    }

    @Override
    public String getAuthMessage() throws ExApiException {
        Map<String, String> ps = new HashMap<>();
        ps.put("AccessKeyId", accountConfig.getAuthKey());
        ps.put("SignatureMethod", "HmacSHA256");
        ps.put("SignatureVersion", "2");
        ps.put("Timestamp", ZonedDateTime.now(zoneId).format(formatter));

        Map<String, String> encodedPs = new HashMap<>();
        for (Map.Entry<String, String> entry : ps.entrySet()) {
            encodedPs.put(entry.getKey(), urlEncode(entry.getValue()));
        }

        // 该对象非线程安全，而且每次生成新对象并不影响性能
        Mac sha256Mac = Utils.initialHMac(accountConfig.getAuthSecret(), "HmacSHA256");
        String host = accountConfig.getUrl().substring(8);
        String signString =  "GET\n" + host + "\n/linear-swap-notification\n" + HttpUtils.param2String(encodedPs);
        String signature = Base64.getEncoder().encodeToString(sha256Mac.doFinal(signString.getBytes()));
        ps.put("Signature", signature);

        ps.put("op", "auth");
        ps.put("type", "api");

        try {
            return Utils.mapper.writeValueAsString(ps);
        } catch (JsonProcessingException e) {
            throw new ExApiException("failed to get auth message: ", e);
        }
    }

    @Override
    public List<ExSymbol> getExchangeInfos() throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("GET",
                    "/linear-swap-api/v1/swap_contract_info", new HashMap<>());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV2 resp = Utils.mapper.readValue(response.body(), HuoFutureRespV2.class);

            if (response.statusCode() != 200 || "error".equals(resp.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp);
            }

            List<ExSymbol> symbols = new ArrayList<>();
            for (HuoFutureData data : resp.data) {
                double priceP = Utils.roundPrecisionValue(data.price_tick);
                double sizeP = Utils.roundPrecisionValue(data.contract_size);
                symbols.add(new ExSymbol(data.contract_code, priceP, sizeP));
                // 通过该接口更新 multiplier参数
                contractMultiplier.put(data.contract_code, data.contract_size);
            }
            return symbols;
        } catch (Exception e) {
            throw new ExApiException("failed to get exchange info", e);
        }
    }

    @Override
    public List<AccountValue> getAccountValue() throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("margin_account", "USDT");
            HttpRequest request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_account_info", params);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV2 resp = Utils.mapper.readValue(response.body(), HuoFutureRespV2.class);

            if (response.statusCode() != 200 || "error".equals(resp.status) || resp.data.size() != 1) {
                throw new ExApiException("failed to get ok status, resp: " + resp);
            }

            HuoFutureData data = resp.data.get(0);

            for (HuoFutureDetail detail : data.contract_detail) {
                leverRate.put(detail.contract_code, detail.lever_rate);
            }

            List<AccountValue> accountValues = new ArrayList<>();
            double total = data.margin_balance + data.profit_real + data.profit_unreal;
            accountValues.add(new AccountValue(data.margin_asset, accountConfig.getName(),
                    total, data.margin_balance, data.withdraw_available));
            return accountValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get account value", e);
        }
    }

    @Override
    public String makeOrder(Order order) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("contract_code", order.getSymbol());
            if (OrderType.LIMIT.equals(order.getOrderType())) {
                params.put("order_price_type", "post_only");
                params.put("price", String.valueOf(order.getPrice()));
            } else if (OrderType.MARKET.equals(order.getOrderType())) {
                params.put("order_price_type", "opponent");
            }
            long vol = Math.round(order.getSize() / contractMultiplier.get(order.getSymbol()));
            params.put("volume", String.valueOf(vol));
            if (OrderSide.BUY.equals(order.getSide())) {
                params.put("direction", "buy");
                params.put("offset", "open");
            } else if (OrderSide.BUY_SHORT.equals(order.getSide())) {
                params.put("direction", "buy");
                params.put("offset", "close");
            } else if (OrderSide.SELL.equals(order.getSide())) {
                params.put("direction", "sell");
                params.put("offset", "open");
            } else if (OrderSide.SELL_LONG.equals(order.getSide())) {
                params.put("direction", "sell");
                params.put("offset", "close");
            }
            params.put("lever_rate", String.valueOf(leverRate.getOrDefault(order.getSymbol(), DEFAULT_LEVER_RATE)));

            HttpRequest request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_order", params);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV1 resp = Utils.mapper.readValue(response.body(), HuoFutureRespV1.class);

            if (response.statusCode() != 200 || "error".equals(resp.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp);
            }

            if (resp.data.order_id_str == null) {
                throw new ExApiException("get null order id");
            }

            return resp.data.order_id_str;
        } catch (Exception e) {
            throw new ExApiException("failed to make order", e);
        }
    }

    @Override
    public List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("contract_code", symbol);
            params.put("page_size", "49");
            HttpRequest request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_openorders", params);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV1 resp = Utils.mapper.readValue(response.body(), HuoFutureRespV1.class);

            if (response.statusCode() != 200 || "error".equals(resp.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp);
            }

            List<CurrentOrder> currentOrders = new ArrayList<>();
            for (HuoFutureOrder order : resp.data.orders) {
                int orderType = order.order_type % 2;
                if (type == 0 || type == 1 && orderType == 1 || type == 2 && orderType == 0) {
                    currentOrders.add(new CurrentOrder(order.order_id_str, order.contract_code,
                            OrderSide.valueOf(order.direction.toUpperCase()), OrderType.LIMIT,
                            order.volume, order.price, order.trade_volume));
                }
            }
            return currentOrders;
        } catch (Exception e) {
            throw new ExApiException("failed to get current orders", e);
        }
    }

    @Override
    public CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("contract_code", symbol);
            params.put("order_id", orderId);
            HttpRequest request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_cancel", params);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV1 resp = Utils.mapper.readValue(response.body(), HuoFutureRespV1.class);

            if (response.statusCode() != 200 || "error".equals(resp.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp);
            }

//            if (resp.data.successes == null || !resp.data.successes.equals(orderId)) {
//                throw new ExApiException("failed to cancel order, no successes order id: " + resp);
//            }

            // 撤单成功，查询订单状态
            params.clear();
            params.put("contract_code", symbol);
            params.put("order_id", orderId);

            request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_order_info", params);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV2 resp2 = Utils.mapper.readValue(response.body(), HuoFutureRespV2.class);

            if (response.statusCode() != 200 || "error".equals(resp2.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp2);
            }

            if (resp2.data == null || resp2.data.size() != 1) {
                throw new ExApiException("failed to get order info, resp: " + resp2);
            }

            HuoFutureData data = resp2.data.get(0);
            return new CurrentOrder(data.order_id_str, data.contract_code,
                    OrderSide.valueOf(data.direction.toUpperCase()),
                    OrderType.LIMIT, data.volume, data.price, data.trade_volume);
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order", e);
        }
    }

    /**
     * 返回持仓上限均为张数，需要外部调整
     * @return 风险限额
     * @throws ExApiException 执行失败
     */
    @Override
    public RiskLimitValue getRiskLimitValue() throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("margin_account", "USDT");
            HttpRequest request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_account_info", params);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV2 resp1 = Utils.mapper.readValue(response.body(), HuoFutureRespV2.class);

            if (response.statusCode() != 200 || "error".equals(resp1.status) || resp1.data.size() != 1) {
                throw new ExApiException("failed to get ok status, resp: " + resp1);
            }

            HuoFutureData data1 = resp1.data.get(0);

            // 查询账户position limit
            request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_position_limit", new HashMap<>());
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV2 resp2 = Utils.mapper.readValue(response.body(), HuoFutureRespV2.class);

            if (response.statusCode() != 200 || "error".equals(resp2.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp2);
            }

            Map<String, Long> limits = new HashMap<>();
            for (HuoFutureData limit : resp2.data) {
                limits.put(limit.contract_code, Math.round(Math.min(limit.buy_limit, limit.sell_limit)));
            }

            List<PositionRiskLimitValue> positionRiskLimitValues = new ArrayList<>();

            for (HuoFutureDetail detail : data1.contract_detail) {
                // 能主动更新api内部杠杆倍数
                leverRate.put(detail.contract_code, detail.lever_rate);
                long pos = Math.round(detail.margin_position * detail.lever_rate);
                double init = Math.round(detail.margin_position * 100) * 1.0 / 100;
                double maint = Math.round(detail.margin_position * detail.adjust_factor * 100) * 1.0 / 100;

                positionRiskLimitValues.add(new PositionRiskLimitValue(detail.contract_code, detail.lever_rate,
                        limits.get(detail.contract_code), pos, init, maint));
            }
            double total = data1.margin_balance + data1.profit_real + data1.profit_unreal;
            return new RiskLimitValue(total, data1.withdraw_available, positionRiskLimitValues);
        } catch (Exception e) {
            throw new ExApiException("failed to get risk limit value", e);
        }
    }

    @Override
    public void updateRiskLimit(String symbol, int leverage) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("contract_code", symbol);
            params.put("lever_rate", String.valueOf(leverage));
            HttpRequest request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_switch_lever_rate", params);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV1 resp = Utils.mapper.readValue(response.body(), HuoFutureRespV1.class);

            if (response.statusCode() != 200 || "error".equals(resp.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp);
            }
        } catch (Exception e) {
            throw new ExApiException("failed to get current orders", e);
        }
    }

    @Override
    public List<FundingValue> getFundingValue(String symbol, long lastTime) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("margin_account", "USDT");
            params.put("type", "30,31");
            params.put("contract_code", symbol);
            params.put("start_time", String.valueOf(lastTime));
            HttpRequest request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_financial_record_exact", params);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV1 resp1 = Utils.mapper.readValue(response.body(), HuoFutureRespV1.class);

            if (response.statusCode() != 200 || "error".equals(resp1.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp1);
            }

            List<FundingValue> fundingValues = new ArrayList<>();
            if (resp1.data.financial_record.size() == 1) {

                double amount = resp1.data.financial_record.get(0).amount;
                amount = Math.round(amount * PRECISION) * 1.0 / PRECISION;

                // 获取最近一次的历史资金费率
                params.clear();
                params.put("contract_code", symbol);
                params.put("page_size", "1");
                params.put("page_index", "1");
                request = generateSignedRequest("GET",
                        "/linear-swap-api/v1/swap_historical_funding_rate", params);
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                HuoFutureRespV1 resp2 = Utils.mapper.readValue(response.body(), HuoFutureRespV1.class);

                if (response.statusCode() != 200 || "error".equals(resp2.status)) {
                    throw new ExApiException("failed to get ok status, resp: " + resp2);
                }

                if (resp2.data.data == null || resp2.data.data.size() != 1) {
                    throw new ExApiException("failed to get historical funding rate, " + resp2);
                }

                double rate = resp2.data.data.get(0).realized_rate;
                rate = Math.round(rate * PRECISION) * 1.0 / PRECISION;
                long time = resp2.data.data.get(0).funding_time;

                fundingValues.add(new FundingValue(symbol,
                        accountConfig.getName(), amount, rate, time));
            }
            Thread.sleep(200);
            return fundingValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get funding value", e);
        }
    }

    @Override
    public List<PositionValue> getPositionValue(String symbol) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            if (symbol != null) {
                params.put("contract_code", symbol);
            }
            HttpRequest request = generateSignedRequest("POST",
                    "/linear-swap-api/v1/swap_cross_position_info", params);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HuoFutureRespV2 resp = Utils.mapper.readValue(response.body(), HuoFutureRespV2.class);

            if (response.statusCode() != 200 || "error".equals(resp.status)) {
                throw new ExApiException("failed to get ok status, resp: " + resp);
            }

            List<PositionValue> positionValues = new ArrayList<>();
            for (HuoFutureData data : resp.data) {
                double size = data.volume * contractMultiplier.get(data.contract_code);
                if ("sell".equals(data.direction)) {
                    size = -size;
                }
                // direction暂放在name字段中一起返回
                String name = data.contract_code + data.direction;
                positionValues.add(new PositionValue(name, data.contract_code, accountConfig.getName(),
                        size, data.last_price, Math.round(data.last_price * size),
                        0, 0, System.currentTimeMillis()));
            }
            return positionValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get position value", e);
        }
    }

    public double getSize(String symbol, double vol) {
        return contractMultiplier.get(symbol) * vol;
    }

    private HttpRequest generateSignedRequest(String method, String path,
                                              Map<String, String> params) throws Exception {
        Map<String, String> ps = new HashMap<>(params);
        ps.put("AccessKeyId", accountConfig.getAuthKey());
        ps.put("SignatureMethod", "HmacSHA256");
        ps.put("SignatureVersion", "2");
        ps.put("Timestamp", ZonedDateTime.now(zoneId).format(formatter));

        Map<String, String> encodedPs = new HashMap<>();
        for (Map.Entry<String, String> entry : ps.entrySet()) {
            encodedPs.put(entry.getKey(), urlEncode(entry.getValue()));
        }
        String paramString = HttpUtils.param2String(encodedPs);

        // 该对象非线程安全，而且每次生成新对象并不影响性能
        Mac sha256Mac = Utils.initialHMac(accountConfig.getAuthSecret(), "HmacSHA256");
        String host = accountConfig.getUrl().substring(8);
        String signString = method + "\n" + host + "\n" + path + "\n" + HttpUtils.param2String(encodedPs);
        String signature = Base64.getEncoder().encodeToString(sha256Mac.doFinal(signString.getBytes()));
        String url = accountConfig.getUrl() + path + "?" + paramString + "&Signature=" + urlEncode(signature);

        params.put("Signature", signature);
        HttpRequest.Builder builder = HttpRequest.newBuilder(new URI(url));
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(Utils.mapper.writeValueAsString(ps)));
        } else if ("GET".equals(method)) {
            builder.GET();
        }
        return builder.header("Content-Type", "application/json; charset=utf-8").build();
    }

    private String urlEncode(String str) {
        return URLEncoder.encode(str, Charset.defaultCharset()).replaceAll("\\+", "%20");
    }


    static class HuoFutureRespV1 {
        public String status;
        public int err_code;
        public String err_msg;
        public long ts;

        public HuoFutureData data;

        public HuoFutureRespV1() {
        }

        @Override
        public String toString() {
            return "HuoFutureRespV1{" +
                    "status='" + status + '\'' +
                    ", err_code=" + err_code +
                    ", err_msg='" + err_msg + '\'' +
                    ", ts=" + ts +
                    ", data=" + data +
                    '}';
        }
    }

    static class HuoFutureRespV2 {
        public String status;
        public int err_code;
        public String err_msg;
        public long ts;

        public List<HuoFutureData> data;

        public HuoFutureRespV2() {
        }

        @Override
        public String toString() {
            return "HuoFutureRespV2{" +
                    "status='" + status + '\'' +
                    ", err_code=" + err_code +
                    ", err_msg='" + err_msg + '\'' +
                    ", ts=" + ts +
                    ", data=" + data +
                    '}';
        }
    }

    static class HuoFutureData {
        public String margin_asset;
        public double margin_balance;
        public double withdraw_available;
        public double profit_real;
        public double profit_unreal;

        public List<HuoFutureDetail> contract_detail;

        public List<HuoFutureDataPosition> positions;

        public String contract_code;
        public double contract_size;
        public double price_tick;

        public double last_price;

        public double buy_limit;
        public double sell_limit;


        public String order_id_str;
        public double volume;
        public double price;
        public String order_price_type;
        public String direction;
        public String offset;
        public long trade_volume;
        public int status;

        public String successes;

        public List<HuoFutureOrder> orders;

        public List<HuoFutureFinRecord> financial_record;

        public List<HuoFutureFundingData> data;

        public HuoFutureData() {
        }

        @Override
        public String toString() {
            return "HuoFutureData{" +
                    "margin_asset='" + margin_asset + '\'' +
                    ", margin_balance=" + margin_balance +
                    ", withdraw_available=" + withdraw_available +
                    ", profit_real=" + profit_real +
                    ", profit_unreal=" + profit_unreal +
                    ", contract_detail=" + contract_detail +
                    ", positions=" + positions +
                    ", contract_code='" + contract_code + '\'' +
                    ", contract_size=" + contract_size +
                    ", price_tick=" + price_tick +
                    ", last_price=" + last_price +
                    ", buy_limit=" + buy_limit +
                    ", sell_limit=" + sell_limit +
                    ", order_id_str='" + order_id_str + '\'' +
                    ", volume=" + volume +
                    ", price=" + price +
                    ", order_price_type='" + order_price_type + '\'' +
                    ", direction='" + direction + '\'' +
                    ", offset='" + offset + '\'' +
                    ", trade_volume=" + trade_volume +
                    ", status=" + status +
                    ", successes='" + successes + '\'' +
                    ", orders=" + orders +
                    ", financial_record=" + financial_record +
                    ", data=" + data +
                    '}';
        }
    }

    static class HuoFutureDetail {
        public String contract_code;
        public int lever_rate;
        public double margin_position;
        public double adjust_factor;

        public HuoFutureDetail() {
        }

        @Override
        public String toString() {
            return "HuoFutureDetail{" +
                    "contract_code='" + contract_code + '\'' +
                    ", lever_rate=" + lever_rate +
                    '}';
        }
    }

    static class HuoFutureOrder {
        public String contract_code;
        public long volume;
        public double price;
        public String order_price_type;
        public int order_type;
        public String direction;
        public String offset;
        public String order_id_str;
        public long trade_volume;
        public int status;

        public HuoFutureOrder() {
        }

        @Override
        public String toString() {
            return "HuoFutureOrder{" +
                    "contract_code='" + contract_code + '\'' +
                    ", volume=" + volume +
                    ", price=" + price +
                    ", order_price_type='" + order_price_type + '\'' +
                    ", order_type=" + order_type +
                    ", direction='" + direction + '\'' +
                    ", offset='" + offset + '\'' +
                    ", order_id_str='" + order_id_str + '\'' +
                    ", trade_volume=" + trade_volume +
                    ", status=" + status +
                    '}';
        }
    }

    static class HuoFutureDataPosition {
        public String contract_code;
        public long volume;

        public double last_price;

        public HuoFutureDataPosition() {
        }
    }

    static class HuoFutureFinRecord {
        public double amount;
        public String contract_code;

        public HuoFutureFinRecord() {
        }

        @Override
        public String toString() {
            return "HuoFutureFinRecord{" +
                    "amount=" + amount +
                    ", contract_code='" + contract_code + '\'' +
                    '}';
        }
    }

    static class HuoFutureFundingData {
        public long funding_time;
        public double realized_rate;
        public String contract_code;

        public HuoFutureFundingData() {
        }
    }

}
