package uk.gov.dwp.drs.lambda.misc;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Utils {
    public static final String JMS_CORRELATION_ID = "JMSCorrelationID";

    private Utils() {}

    public static SQSEvent makeSQSEvent(String payload) {
        SQSEvent sqsEvent = new SQSEvent();
        SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
        SQSEvent.MessageAttribute messageAttribute = new SQSEvent.MessageAttribute();
        messageAttribute.setStringValue(UUID.randomUUID().toString());
        sqsMessage.setBody(payload);
        sqsMessage.setMessageAttributes(Collections.singletonMap(JMS_CORRELATION_ID, messageAttribute));
        sqsEvent.setRecords(Collections.singletonList(sqsMessage));
        return sqsEvent;
    }

    public static LambdaLogger makeLambdaLoggerAdaptor() {
        return new LambdaLogger() {
            Logger logger = Logger.getLogger("LambdaLog");
            @Override
            public void log(String message) {
                logger.log(Level.INFO, message);
            }

            @Override
            public void log(byte[] message) {
                // not used
            }
        };
    }
}
