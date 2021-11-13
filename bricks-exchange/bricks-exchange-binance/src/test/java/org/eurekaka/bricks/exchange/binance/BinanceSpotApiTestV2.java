package org.eurekaka.bricks.exchange.binance;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.ExApi;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AssetTransfer;
import org.eurekaka.bricks.common.model.AssetTransferHistory;
import org.eurekaka.bricks.common.util.HttpUtils;

public class BinanceSpotApiTestV2 {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/binance-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, null,
                null, null, null, "https://api.binance.com", null,
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        ExApi api = new BinanceSpotApi(accountConfig,
                HttpUtils.initializeHttpClient(accountConfig.getProperties()));

        AccountConfig toAccountConfig = new AccountConfig(0, "n2", 1, "", "",
                "", "", "https://api.binance.com",
                null, null, null, true);

        accountConfig.setProperty("account_type", "SPOT");
        accountConfig.setProperty("account_email", "xxx");

        toAccountConfig.setProperty("account_type", "USDT_FUTURE");
        toAccountConfig.setProperty("account_email", "xxx");

        AssetTransfer transfer = new AssetTransfer(accountConfig, toAccountConfig, "USDT", 10);
//        api.transferAsset(transfer);

//        TBtbrpuGroFqawxmNp7iAHSR9b3rVh5bJF
        AccountConfig toAccountConfig2 = new AccountConfig(0, "n2", 1, "", "",
                "", "", "https://api.binance.com",
                null, null, null, true);

        toAccountConfig2.setProperty("address", "TBtbrpuGroFqawxmNp7iAHSR9b3rVh5bJF");

//        api.withdrawAsset(new AssetTransfer(accountConfig, toAccountConfig2, "USDT", 10));

        System.out.println(api.getAssetRecords(new AssetTransferHistory("n1", 2,
                System.currentTimeMillis() - 3600000, System.currentTimeMillis(), 3)));
    }
}
