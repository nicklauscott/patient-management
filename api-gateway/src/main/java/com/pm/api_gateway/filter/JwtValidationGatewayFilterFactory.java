package com.pm.api_gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** The 'AbstractGatewayFilterFactory' It allows you to create custom filters that can be
    applied to specific routes. */
@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final WebClient webClient;

    /**
     *
     * @param webClientBuilder This will help us to create a webclient to make a request
     * @param authServiceUrl This will allow us to pass in auth service url dynamically
     */
    public JwtValidationGatewayFilterFactory(
         WebClient.Builder webClientBuilder,
         @Value("${auth.service.url}") String authServiceUrl
    ) { this.webClient = webClientBuilder.baseUrl(authServiceUrl).build(); }

    /**
     * ServerWebExchange holds all the properties of the current request and
     * GateWayFilterChain holds all the filters that exist on the filter chain
     */
    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            /** We return 401 unauthorize status code if the request doesn't have an access token */
            if (token == null || !token.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            /** We now make a validation request to the auth server service using the webclient
             * the 'toBodilessEntity()' method tells the webclient to ignore the response body
             * then 'then(chain.filter(exchange))' forward the request to the next filter. */
            return webClient.get()
                .uri("/validate")
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .toBodilessEntity()
                .then(chain.filter(exchange));
        };
    }
}
