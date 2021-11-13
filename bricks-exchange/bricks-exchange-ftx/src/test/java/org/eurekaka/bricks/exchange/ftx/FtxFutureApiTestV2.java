package org.eurekaka.bricks.exchange.ftx;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.model.AccountAssetRecord;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AssetTransfer;
import org.eurekaka.bricks.common.model.AssetTransferHistory;
import org.eurekaka.bricks.common.util.HttpUtils;

import java.net.http.HttpClient;
import java.util.List;

public class FtxFutureApiTestV2 {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/ftx-secret.conf");

        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, "", "",
                "", "", "https://ftx.com", null,
                config.getString("key"), config.getString("secret"), true);

//        accountConfig.setProperty("sub_account", "test");
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        HttpClient httpClient = HttpUtils.initializeHttpClient(accountConfig.getProperties());

        String name = "EOS_USDT";
        String symbol = "EOS-PERP";

        FutureExApi api = new FtxFutureApi(accountConfig, httpClient);

        AccountConfig toAccountConfig = new AccountConfig(0, "n2", 1, "", "",
                "", "", "https://ftx.com",
                "test", null, null, true);
        AssetTransfer transfer = new AssetTransfer(accountConfig, toAccountConfig, "USDT", 10);
//        api.transferAsset(transfer);

        toAccountConfig.setProperty("address", "TA1TNzacSFJ2AunUq2ASfns2AJ8MWZGYJh");
        api.withdrawAsset(new AssetTransfer(accountConfig, toAccountConfig, "USDT", 10));

        List<AccountAssetRecord> records = api.getAssetRecords(new AssetTransferHistory(
                "n1", 1,
                System.currentTimeMillis() - 24 * 3600000,
                System.currentTimeMillis() + 100000, 3));
        System.out.println(records);
    }

}
