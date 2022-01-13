package uk.gov.dwp.drs.lambda;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.Assert;
import org.junit.Test;
import uk.gov.dwp.drs.lambda.misc.Utils;

import java.util.UUID;

public class UtilsTest {
    @Test
    public void testFakeSQSMessage() {
        String payload = "XXXXXXX";
        SQSEvent evt =  Utils.makeSQSEvent(payload);

        Assert.assertNotNull(evt);
        Assert.assertEquals(1, evt.getRecords().size());
        Assert.assertEquals(payload, evt.getRecords().get(0).getBody(), payload);
        Assert.assertEquals(1, evt.getRecords().get(0).getMessageAttributes().size());
        Assert.assertTrue(evt.getRecords().get(0).getMessageAttributes().containsKey("JMSCorrelationID"));

        String id = evt.getRecords().get(0).getMessageAttributes().get("JMSCorrelationID").getStringValue();
        UUID uuid = UUID.fromString(id);
    }

    @Test
    public void testLambdaLogger() {
        LambdaLogger logger = Utils.makeLambdaLoggerAdaptor();
        Assert.assertNotNull(logger);
        logger.log("This is a sample log message");
    }
}
