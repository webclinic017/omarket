package org.omarket.trading.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.util.Date;
import java.util.Random;

/**
 * Created by Christophe on 11/11/2016.
 */
public class MonitorVerticle  extends AbstractVerticle {
    private final static Logger logger = LoggerFactory.getLogger(MonitorVerticle.class);
    public static final String ADDRESS_MONITOR_STRATEGY = "oot.monitor.strategy";

    public void start() {
        Router router = Router.router(vertx);
        BridgeOptions bridgeOptions = new BridgeOptions();
        PermittedOptions permittedOptions = new PermittedOptions();
        permittedOptions.setAddress(ADDRESS_MONITOR_STRATEGY);
        bridgeOptions.addOutboundPermitted(permittedOptions);

        router.route("/oot/*").handler(SockJSHandler.create(vertx).bridge(bridgeOptions));
        router.route().handler(StaticHandler.create());

        HttpServer server = vertx.createHttpServer();
        server.requestHandler(router::accept);
        String host = config().getString("oot.monitor.host", "0.0.0.0");
        Integer port = config().getInteger("oot.monitor.port", 8080);
        server.listen(port, host);

        vertx.setPeriodic(1000, t -> vertx.eventBus().publish(ADDRESS_MONITOR_STRATEGY,
                new JsonObject()
                        .put("creatTime", System.currentTimeMillis())
                        .put("cpuTime", new Random().nextDouble())));
    }
}