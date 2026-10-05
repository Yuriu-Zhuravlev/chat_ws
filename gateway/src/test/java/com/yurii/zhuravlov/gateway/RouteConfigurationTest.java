package com.yurii.zhuravlov.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The routing table is pure configuration, so the thing worth testing is that it parses
 * and that no route is silently missing — a typo in the property prefix leaves the
 * gateway running happily with zero routes, returning 404 for everything.
 */
@SpringBootTest
class RouteConfigurationTest {

    @Autowired
    RouteLocator routeLocator;

    @Test
    void definesEveryRoute() {
        List<String> ids = routeLocator.getRoutes()
                .map(Route::getId)
                .collectList()
                .block();

        assertThat(ids).containsExactlyInAnyOrder("auth", "chat", "notification-ws");
    }

    /** ws:// rather than http://: the gateway must upgrade, not proxy a plain request. */
    @Test
    void routesWebSocketTrafficOverWsScheme() {
        String scheme = routeLocator.getRoutes()
                .filter(route -> "notification-ws".equals(route.getId()))
                .map(route -> route.getUri().getScheme())
                .blockFirst();

        assertThat(scheme).isEqualTo("ws");
    }
}
