package osgi.enroute.trains.location.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import osgi.enroute.trains.cloud.api.Segment;
import osgi.enroute.trains.cloud.api.TrackForSegment;
import osgi.enroute.trains.controller.api.TrainLocator;

@Designate(ocd = LocationConfig.class)
@Component(name = LocationConfig.LOCATION_CONFIG_PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true)
public class TrainLocationClient implements TrainLocator {
    static final Logger log = LoggerFactory.getLogger(TrainLocationClient.class);

    private static final String ACCOUNT_NAME = "NetLogix-WB";
    private static final String BROKER_URL = "mqtts://broker-sandbox.everyware-cloud.com:8883";
    private static final String USERNAME = "osgiTrain";
    private static final String PASSWORD = "osg!TrainDem0";

    private static final String CLIENT_ID = "TrainLocator";

    private MqttClient mqttClient = null;
    private Map<String, Integer> tag2code = new HashMap<>();
    private Map<Integer, String> code2segment = new HashMap<>();
    private Deferred<String> nextLocation = new Deferred<String>();

    @Reference
    private TrackForSegment trackInfo;

    //
    // Activate: create client configuration, and set its properties
    //
    @Activate
    void activate(LocationConfig config) {

        initConfig(config.code2tag());

        BiFunction<String, String, String> getOrDefault = (t, u) -> t != null ? t : u;

        String accountName = getOrDefault.apply(config.accountName(), ACCOUNT_NAME);
        String brokerUrl = getOrDefault.apply(config.brokerUrl(), BROKER_URL);
        
        // paho mqtt client impl uses different URL schemes to EDC client
        // user should not be aware of impl, so map schemes
        brokerUrl = brokerUrl.replaceFirst("^mqtt:", "tcp:").replaceFirst("^mqtts:", "ssl:");
        
        String username = getOrDefault.apply(config.username(), USERNAME);
        String password = getOrDefault.apply(config.password(), PASSWORD);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());

        try {
            // Connect and start the session
            info("Connecting to MQTT broker <{}>", brokerUrl);
            mqttClient = new MqttClient(brokerUrl, CLIENT_ID);
            mqttClient.setCallback(new MyCallbackHandler());
            mqttClient.connect(options);

            // Subscribe
            info("Subscribing to topic <{}>", "+/TrainDemo/#");
            mqttClient.subscribe(new String[] {"+", "TrainDemo/#"});
        } catch (MqttException e) {
            error(e.toString());
            throw new RuntimeException(e);
        }
    }

    //
    // Stop the session and close the connection
    //
    @Deactivate
    void deactivate() {
        info("Terminating MQTT client");
        try {
            mqttClient.disconnect();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    void initConfig(String[] code2tag) {
        if (code2tag == null) {
            final String msg = "configuration MUST contain code2tag";
            error("{}: {}.\n", getClass().getSimpleName(), msg);
            throw new Error(msg);
        }

        for (String s : code2tag) {
            String[] split = s.split("\\s*:\\s*", 2);

            String tag = split[1];
            int code = 0;
            try {
                code = Integer.parseInt(split[0]);
            } catch (NumberFormatException e) {
                error("non-numeric code<{}> for tag<{}>", split[0], tag);
                continue;
            }

            if (tag2code.containsValue(code)) {
                final int c = code;
                List<String> keys = tag2code.entrySet().stream()
                        .filter(e -> e.getValue().equals(c))
                        .map(e -> e.getKey())
                        .collect(Collectors.toList());
                keys.add(tag);
                warn("duplicate code {} for tags {}", code, keys);
            } else {
                Integer dup = tag2code.put(tag, code);
                if (dup != null && dup != 0) {
                    warn("duplicate codes [{}, {}] for tag {}", dup, code, tag);
                }
            }
        }

        for (Segment s : trackInfo.getSegments().values()) {
            if (s.tagCode != 0) {
                String segment = s.id;
                int code = s.tagCode;

                if (code2segment.containsValue(segment)) {
                    List<Integer> keys = code2segment.entrySet().stream()
                            .filter(e -> e.getValue().equals(segment))
                            .map(e -> e.getKey())
                            .collect(Collectors.toList());
                    keys.add(code);
                    warn("duplicate segment {} for codes {}", segment, keys);
                } else {
                    String dup = code2segment.put(code, segment);
                    if (dup != null) {
                        warn("duplicate segments [{}, {}] for code {}", dup, segment, code);
                    }
                    
                    if (!tag2code.containsValue(code)) {
                        warn("no tag configured with code<{}> for segment<{}>", code, segment);
                    }
                }
            }
        }

//        info("tag2code=" + tag2code);
//        info("code2segment=" + code2segment);
    }

    @Override
    public synchronized Promise<String> nextLocation() {
        return nextLocation.getPromise();
    }

    private synchronized void trigger(String trainId, String segment) {
        // info("trigger: trainId={} segment={}", trainId, segment);
        Deferred<String> currentLocation = nextLocation;
        nextLocation = new Deferred<String>();
        currentLocation.resolve(trainId + ":" + segment);
    }

    // logging is configured to use OSGi logger and so need webconsole to view
    // these methods ensures errors are visible
    private static void error(String fmt, Object... args) {
        System.err.printf("ERROR: " + fmt.replaceAll("\\{}", "%s") + "\n", args);
        log.error(fmt, args);
    }

    private static void warn(String fmt, Object... args) {
        System.err.printf("WARNING: " + fmt.replaceAll("\\{}", "%s") + "\n", args);
        log.warn(fmt, args);
    }

    private static void info(String fmt, Object... args) {
        System.out.printf("Locator: " + fmt.replaceAll("\\{}", "%s") + "\n", args);
        log.info(fmt, args);
    }

    /**
     * MQTT Callback class
     */
    class MyCallbackHandler implements MqttCallback {
        // display data messages received from broker
        @Override
        public void messageArrived(String topic, MqttMessage msg) throws Exception {
            info("messageArrived on topic=<{}>: {}", topic, msg);

            byte[] payload = msg.getPayload();

            String trainId = String.valueOf(getMetric("train", payload));
            Object location = getMetric("location", payload);

            if (location != null) {
                String tag = location.toString();
                Integer code = tag2code.get(tag);
                if (code == null || code == 0) {
                    warn("unknown tag <{}>", tag);
                } else {
                    String segment = code2segment.get(code);
                    if (segment == null) {
                        warn("no segment defined for code <{}>", code);
                    } else {
                        trigger(trainId, segment);
                    }
                }
            } else if (getMetric("connection", payload) != null) {
                info("trainId<{}>: {}", trainId, getMetric("connection", payload));
            } else {
                info("Data publish arrived on semantic topic: " + topic);
            }
        }

        @Override
        public void connectionLost(Throwable t) {
            info("MQTT client connection lost: " + t);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            info("MQTT client delivery complete: " + token);
        }
        
        
        String getMetric(String metric, byte[] payload) {
            return null;
        }

    }

}
