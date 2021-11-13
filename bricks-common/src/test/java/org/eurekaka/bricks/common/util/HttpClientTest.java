package org.eurekaka.bricks.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class HttpClientTest {

    public static void main(String[] args) throws Exception {
        Map<String, String> properties = new HashMap<>();

        HttpClient httpClient = HttpUtils.initializeHttpClient(properties);
        HttpRequest request = HttpRequest
                .newBuilder(new URI("https://ftx.com/api/futures"))
                .GET()
                .build();
        String content = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        System.out.println(content);
    }

}
