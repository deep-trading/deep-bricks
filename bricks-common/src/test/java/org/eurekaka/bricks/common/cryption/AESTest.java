package org.eurekaka.bricks.common.cryption;

import org.junit.Assert;
import org.junit.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.util.Base64;

import static org.eurekaka.bricks.common.cryption.AES.CBC_ALGORITHM;

public class AESTest {

    @Test
    public void testAES() throws Exception {
        String plainText = "nB9hA4ukKauWXRl4SIVCW6AWR4kB0mQJpqF9VjOzs6NSkvmpbz9eiP25qy5G4BfG";
        String password = "bhex";
        String salt = "2021";

        IvParameterSpec iv = AES.generateIv();
        SecretKey key = AES.getKeyFromPassword(password, salt);
        String cipherText = AES.encrypt(CBC_ALGORITHM, plainText, key, iv);
        System.out.println(cipherText.length());
        System.out.println(Base64.getDecoder().decode(cipherText).length);
        System.out.println(cipherText);

        String ivString = Base64.getEncoder().encodeToString(iv.getIV());

        String decryptedCipherText = AES.decrypt(CBC_ALGORITHM, cipherText, key,
                new IvParameterSpec(Base64.getDecoder().decode(ivString)));
        Assert.assertEquals(plainText, decryptedCipherText);
    }
}
