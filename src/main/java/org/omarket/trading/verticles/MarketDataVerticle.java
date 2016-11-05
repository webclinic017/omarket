package org.omarket.trading.verticles;

import com.ib.client.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.omarket.trading.ibrokers.CurrencyProduct;
import org.omarket.trading.ibrokers.IBrokersMarketDataCallback;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Christophe on 01/11/2016.
 */
public class MarketDataVerticle extends AbstractVerticle {
    public final static String ADDRESS_SUBSCRIBE = "oot.marketData.subscribe";
    public final static String ADDRESS_SUBSCRIBE_MULTIPLE = "oot.marketData.subscribeMultiple";
    public final static String ADDRESS_UNSUBSCRIBE = "oot.marketData.unsubscribe";
    public final static String ADDRESS_UNSUBSCRIBE_MULTIPLE = "oot.marketData.unsubscribeMultiple";
    public final static String ADDRESS_UNSUBSCRIBE_ALL = "oot.marketData.unsubscribeAll";
    public final static String ADDRESS_CONTRACT_DETAILS = "oot.marketData.contractDetails";
    public final static String ADDRESS_ORDER_BOOK_LEVEL_ONE = "oot.orderBookLevelOne";
    private final static Logger logger = LoggerFactory.getLogger(MarketDataVerticle.class.getName());
    private Map<String, JsonObject> subscribedProducts = new HashMap<>();

    static public String createChannelOrderBookLevelOne(Integer ibCode){
        return ADDRESS_ORDER_BOOK_LEVEL_ONE + "." + ibCode;
    }

    static private IBrokersMarketDataCallback ibrokers_connect(String ibrokersHost, int ibrokersPort, int ibrokersClientId, EventBus eventBus) {

        final EReaderSignal readerSignal = new EJavaSignal();
        final IBrokersMarketDataCallback ewrapper = new IBrokersMarketDataCallback(eventBus);
        final EClientSocket clientSocket = new EClientSocket(ewrapper, readerSignal);
        ewrapper.setClient(clientSocket);
        clientSocket.eConnect(ibrokersHost, ibrokersPort, ibrokersClientId);

        /*
        Launching IBrokers client thread
         */
        new Thread() {
            public void run() {
                EReader reader = new EReader(clientSocket, readerSignal);
                reader.start();
                while (clientSocket.isConnected()) {
                    readerSignal.waitForSignal();
                    try {
                        logger.info("IBrokers thread waiting for signal");
                        reader.processMsgs();
                    } catch (Exception e) {
                        logger.error("Exception", e);
                    }
                }
                if (clientSocket.isConnected()) {
                    clientSocket.eDisconnect();
                }
            }
        }.start();
        return ewrapper;
    }

    public void start() {
        logger.info("starting market data");

        String ibrokersHost = config().getString("ibrokers.host");
        Integer ibrokersPort = config().getInteger("ibrokers.port");
        Integer ibrokersClientId = config().getInteger("ibrokers.clientId");
        final IBrokersMarketDataCallback ibrokers_client = ibrokers_connect(ibrokersHost, ibrokersPort, ibrokersClientId, vertx.eventBus());
        logger.info("starting market data verticle");

        MessageConsumer<JsonObject> consumerSubscribe = vertx.eventBus().consumer(ADDRESS_SUBSCRIBE);
        consumerSubscribe.handler(message -> {
            final JsonObject contractDetails = message.body();
            logger.info("received subscription request for: " + contractDetails);
            String status = "failed";
            JsonObject contract_json = contractDetails.getJsonObject("m_contract");
            int productCode = contract_json.getInteger("m_conid");
            if (!subscribedProducts.containsKey(Integer.toString(productCode))) {
                // subscription takes place here
                Contract contract = new Contract();
                contract.conid(productCode);
                contract.currency(contract_json.getString("m_currency"));
                contract.exchange(contract_json.getString("m_exchange"));
                contract.secType(contract_json.getString("m_sectype"));
                ibrokers_client.subscribe(contract, contractDetails.getDouble("m_minTick"));
                subscribedProducts.put(Integer.toString(productCode), contractDetails);
                status = "registered";
            } else {
                status = "already_registered";
            }
            final JsonObject reply = new JsonObject().put("status", status);
            message.reply(reply);
        });

        MessageConsumer<JsonObject> consumerUnsubscribe = vertx.eventBus().consumer(ADDRESS_UNSUBSCRIBE);
        consumerUnsubscribe.handler(message -> {
            final JsonObject contract = message.body();
            logger.info("received unsubscription request for: " + contract);
            String status = "failed";
            String productCode = contract.getString("conId");
            if (subscribedProducts.containsKey(productCode)) {
                // un-subscription takes place here
                subscribedProducts.remove(productCode);
                status = "unsubscribed";
            } else {
                status = "missing";
            }
            final JsonObject reply = new JsonObject().put("status", status);
            message.reply(reply);
        });
        logger.info("started market data verticle");

        MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(ADDRESS_CONTRACT_DETAILS);
        consumer.handler(message -> {
            final JsonObject body = message.body();
            logger.info("received: " + body);
            int productCode = Integer.parseInt(body.getString("conId"));
            Contract contract = new Contract();
            contract.conid(productCode);
            ibrokers_client.request(contract, message);
        });

        for (String currencyCross: CurrencyProduct.IB_CODES.keySet()) {
            Integer ibCode = CurrencyProduct.IB_CODES.get(currencyCross);
            subscribeProduct(vertx, ibCode);
        }
    }

    public static void subscribeProduct(Vertx vertx, Integer ibCode) {
        JsonObject product = new JsonObject().put("conId", Integer.toString(ibCode));
        vertx.eventBus().send(MarketDataVerticle.ADDRESS_CONTRACT_DETAILS, product, reply -> {
            if (reply.succeeded()) {
                JsonObject contractDetails = (JsonObject) reply.result().body();
                logger.info("received contract details: " + contractDetails);
                vertx.eventBus().send(MarketDataVerticle.ADDRESS_SUBSCRIBE, contractDetails, mktDataReply -> {
                    logger.info("subscription result: " + mktDataReply.result().body());
                });
            } else {
                logger.error("failed to retrieve contract details");
            }
        });
    }
}
