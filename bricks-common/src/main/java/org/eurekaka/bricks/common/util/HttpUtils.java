package org.eurekaka.bricks.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.AccountConfig;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;

public class HttpUtils {

    public static HttpClient initializeHttpClient(Map<String, String> properties) {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        int httpConnectTimeout = Integer.parseInt(
                properties.getOrDefault("http_connect_timeout", "10"));
        httpClientBuilder.connectTimeout(Duration.ofSeconds(httpConnectTimeout));

        // proxy or not, used for local test purpose
        String httpProxyHost = properties.get("http_proxy_host");
        String httpProxyPort = properties.get("http_proxy_port");
        if (httpProxyHost != null && httpProxyPort != null) {
            httpClientBuilder.proxy(ProxySelector.of(
                    InetSocketAddress.createUnresolved(
                            httpProxyHost, Integer.parseInt(httpProxyPort))));
        }

        if (Boolean.parseBoolean(properties.get("http_version_2"))) {
            httpClientBuilder.version(HttpClient.Version.HTTP_2);
        } else {
            httpClientBuilder.version(HttpClient.Version.HTTP_1_1);
        }

        // executors
        int httpConnectThreads = Integer.parseInt(
                properties.getOrDefault("http_connect_threads", "64"));
//        ThreadPoolExecutor executor = new ThreadPoolExecutor(
//                0, httpConnectThreads, 120L, TimeUnit.SECONDS,
//                new SynchronousQueue<>());
        httpClientBuilder.executor(Executors.newFixedThreadPool(httpConnectThreads));

        // ssl context
        boolean httpTrustCert = Boolean.parseBoolean(
                properties.getOrDefault("http_trust_all", "false"));
        if (httpTrustCert) {
            SSLContext sslContext = null;
            try {
                TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new SecureRandom());
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("failed to initial http clients", e);
            }
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });
        }

        return httpClientBuilder.build();
    }

    public static void shutdownHttpClient(HttpClient httpClient) {
        if (httpClient.executor().isPresent()) {
            ((AbstractExecutorService) httpClient.executor().get()).shutdownNow();
        }
    }

    public static WebSocket createWebSocket(AccountConfig accountConfig, HttpClient httpClient,
                                            WebSocket.Listener listener) throws ExchangeException {
        int httpConnectTimeout = Integer.parseInt(
                accountConfig.getProperty("http_connect_timeout", "10"));

        try {
            return httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(httpConnectTimeout))
                    .buildAsync(new URI(accountConfig.getWebsocket()), listener)
                    .get(httpConnectTimeout, TimeUnit.SECONDS);
        } catch (URISyntaxException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new ExchangeException("failed to create websocket", e);
        }
    }

    public static WebSocket createWebSocket(HttpClient httpClient, WebSocket.Listener listener,
                                            String websocket, int timeout) throws ExchangeException {
        try {
            return httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(timeout))
                    .buildAsync(new URI(websocket), listener)
                    .get(timeout, TimeUnit.SECONDS);
        } catch (URISyntaxException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new ExchangeException("failed to create websocket", e);
        }
    }

    public static String param2String(Map<String, String> paramMap) {
        StringBuilder sb = new StringBuilder();
        TreeMap<String, String> params = new TreeMap<>(paramMap);
        params.forEach((key1, value) -> {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(key1).append("=");
            sb.append(value);
        });
        return sb.toString();
    }

}
