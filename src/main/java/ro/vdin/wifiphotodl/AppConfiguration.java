package ro.vdin.wifiphotodl;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfiguration {
    private static final Logger log=LoggerFactory.getLogger(AppConfiguration.class);
    
    @Bean
    public RestTemplate restTemplate() {
        log.info("Creating restTemplate!");
        
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        // BufferingClientHttpRequestFactory allows us to read the response more than once - Necessary for debugging.
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient()));
        
        return restTemplate;
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClientBuilder.create().build();
    }
}
