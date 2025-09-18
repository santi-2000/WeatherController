package com.example.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    private static final String WEATHER_API = "https://api.openweathermap.org/data/2.5/weather";
    private static final String GEO_API     = "https://api.openweathermap.org/geo/1.0/direct";
    private static final String API_KEY     =
            System.getenv().getOrDefault("OPENWEATHER_API_KEY", "5a7876a58dcdee34f7cfd242e8126b33");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @GetMapping("/by-coords")
    public ResponseEntity<String> getByLatLon(@RequestParam("lat") String lat,
                                              @RequestParam("lon") String lon) {
        String url = UriComponentsBuilder.fromHttpUrl(WEATHER_API)
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("units", "metric")
                .queryParam("appid", API_KEY)
                .toUriString();

        String result = restTemplate.getForObject(url, String.class);
        return (result != null)
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body("Error: Unable to fetch weather data.");
    }

    @GetMapping("/by-city")
    public ResponseEntity<String> getByCity(@RequestParam String city) {
        try {
            String cleanCity = city == null ? "" : city.trim();
            if (cleanCity.isEmpty()) {
                return ResponseEntity.badRequest().body("Error: 'city' query param is required.");
            }

            String geoUrl = UriComponentsBuilder.fromHttpUrl(GEO_API)
                    .queryParam("q", cleanCity)
                    .queryParam("limit", 1)
                    .queryParam("appid", API_KEY)
                    .toUriString();

            String geoJson = restTemplate.getForObject(geoUrl, String.class);
            if (geoJson == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("Error: Unable to fetch geocoding data.");
            }

            GeoResponse[] places = objectMapper.readValue(geoJson, GeoResponse[].class);

            if (places.length == 0) {
                String weatherByNameUrl = UriComponentsBuilder.fromHttpUrl(WEATHER_API)
                        .queryParam("q", cleanCity)
                        .queryParam("units", "metric")
                        .queryParam("appid", API_KEY)
                        .toUriString();

                String weatherByNameJson = restTemplate.getForObject(weatherByNameUrl, String.class);
                return (weatherByNameJson != null)
                        ? ResponseEntity.ok(weatherByNameJson)
                        : ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body("Error: City not found in geocoding API and fallback also failed for: " + cleanCity);
            }

            double lat = places[0].lat;
            double lon = places[0].lon;

            String weatherUrl = UriComponentsBuilder.fromHttpUrl(WEATHER_API)
                    .queryParam("lat", lat)
                    .queryParam("lon", lon)
                    .queryParam("units", "metric")
                    .queryParam("appid", API_KEY)
                    .toUriString();

            String weatherJson = restTemplate.getForObject(weatherUrl, String.class);
            return (weatherJson != null)
                    ? ResponseEntity.ok(weatherJson)
                    : ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body("Error: Unable to fetch weather data.");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeoResponse {
        public String name;
        public double lat;
        public double lon;
        public String country;
        public String state;
    }
}
