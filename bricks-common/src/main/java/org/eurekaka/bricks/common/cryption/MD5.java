package org.eurekaka.bricks.common.cryption;

import org.eurekaka.bricks.common.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 {
    private final static Logger logger = LoggerFactory.getLogger(MD5.class);

    public static String md5Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data.getBytes(Charset.defaultCharset()));
            return Utils.encodeHexString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            logger.error("failed to load md5 algo", e);
        }
        return "";
    }

}
