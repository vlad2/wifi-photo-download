package ro.vdin.wifiphotodl;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfiguration {
    @Bean
    public RestTemplate restTemplate() {
        // BufferingClientHttpRequestFactory allows us to read the response more than once - Necessary for debugging.
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient()));
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClientBuilder.create().build();
    }
}
