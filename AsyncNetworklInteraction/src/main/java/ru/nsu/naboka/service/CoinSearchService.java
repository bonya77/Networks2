package ru.nsu.naboka.service;

import org.springframework.web.client.HttpClientErrorException;
import ru.nsu.naboka.model.Coin;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CoinSearchService {

    private static final String apiUrl = "https://api.coinpaprika.com/v1/search/";
    private static final int connectionTimeout = 15;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectionTimeout)).build();

    public CompletableFuture<List<Coin>> coinSearcher(String coinName, int listLength){
        if(listLength > 100 || listLength < 1){
            throw new RuntimeException("coins list is too long/short");
        }
        if(coinName.isEmpty()){
            throw new RuntimeException("empty coin name");
        }

        String resultUrl = apiUrl + "?q=" + "coinName&" + "c=currencies&limit=" + listLength;
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(resultUrl)).timeout(Duration.ofSeconds(15)).GET().build();
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenApply(this::coinParser);
    }

    private List<Coin> coinParser(HttpResponse<String> response){
        if(response.statusCode() >= 400){
            throw new RuntimeException("coin searcher spi returned an error status code: " + response.statusCode());
        }
        String jsonString = response.body();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(jsonString);

        JsonNode currencies = root.path("currencies");

        List<Coin> coins = new ArrayList<>();
        if(!currencies.isArray() || currencies.isEmpty()){
            return coins;
        }
        for(JsonNode cn : currencies){
            Coin coin = new Coin();
            coin.setId(cn.path("id").asString());
            coin.setName(cn.path("name").asString());
            coin.setSymbol(cn.path("symbol").asString());
            coin.setRank(cn.path("rank").asInt());
            coin.setNew(cn.path("is_new").asBoolean());
            coin.setActive(cn.path("is_active").asBoolean());
            coin.setType(cn.path("type").asString());

            coins.add(coin);
        }

        return coins;
    }

}
