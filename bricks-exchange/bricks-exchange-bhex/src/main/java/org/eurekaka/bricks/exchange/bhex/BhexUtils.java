package org.eurekaka.bricks.exchange.bhex;

import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.Utils;

import javax.crypto.Mac;
import java.util.Map;

public class BhexUtils {

    public static String generateSignedUrl(AccountConfig accountConfig, String path, Map<String, String> params) {
        params.put("recvWindow", "5000");
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String paramString = HttpUtils.param2String(params);
        // 该对象非线程安全，而且每次生成新对象并不影响性能
        Mac sha256Mac = Utils.initialHMac(accountConfig.getAuthSecret(), "HmacSHA256");
        String signature = Utils.encodeHexString(sha256Mac.doFinal(paramString.getBytes()));
        return accountConfig.getUrl() + path + "?" + paramString + "&signature=" + signature;
    }

}
