package org.eurekaka.bricks.server.service;

import com.typesafe.config.Config;
import org.eurekaka.bricks.common.cryption.MD5;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringTokenizer;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final String secret;

    public AuthService(Config config) {
        secret = config.getString("secret");
    }

    public boolean auth(String authorization) {
        try {
            //Split name and token
            final StringTokenizer tokenizer = new StringTokenizer(authorization, ":");
            final String name = tokenizer.nextToken();
            final String timestamp = tokenizer.nextToken();
            final String token = tokenizer.nextToken();

            long time = Long.parseLong(timestamp);

            return System.currentTimeMillis() - time < 10000 &&
                    MD5.md5Hex(name + timestamp + secret).equals(token);
        } catch (Exception e) {
            logger.error("failed to auth: {}", authorization, e);
            return false;
        }
    }
}
