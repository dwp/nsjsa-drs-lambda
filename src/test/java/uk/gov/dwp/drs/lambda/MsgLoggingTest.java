package uk.gov.dwp.drs.lambda;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.dwp.drs.lambda.comms.MsgLogging;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.gov.dwp.drs.lambda.util.DrsParameters;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AmazonSQSAsyncClientBuilder.class})
public class MsgLoggingTest {
    private AmazonSQSAsync sqsClient;
    private DrsParameters parameters;

    @Before
    public void before() {
        PowerMockito.mockStatic(AmazonSQSAsyncClientBuilder.class);
        AmazonSQSAsyncClientBuilder builder = mock(AmazonSQSAsyncClientBuilder.class);
        when(AmazonSQSAsyncClientBuilder.standard()).thenReturn(builder);

        sqsClient = mock(AmazonSQSAsync.class);
        when(builder.build()).thenReturn(sqsClient);
        when(sqsClient.getQueueUrl(anyString())).thenReturn(new GetQueueUrlResult().withQueueUrl("http://hello:8080/resq"));

        parameters = mock(DrsParameters.class);
    }

    @Test
    public void getInstance() {
        MsgLogging msgLogging = new MsgLogging(parameters);
        Assert.assertNotNull(msgLogging);
    }

    @Test
    public void onLogItem() throws Exception {
        MsgLogging msgLogging = new MsgLogging(parameters);
        // Thursday, 27 June 2019 10:00:00 GMT+01:00
        msgLogging.logEvent("PENDING", "corresid", "errordetail", "documentidentifier");
        ArgumentCaptor<SendMessageRequest> sendMessageRequestArgumentCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        Mockito.verify(sqsClient, times(1)).sendMessage(any());
        Mockito.verify(sqsClient).sendMessage(sendMessageRequestArgumentCaptor.capture());

        SendMessageRequest req = sendMessageRequestArgumentCaptor.getValue();
        Assert.assertNotNull(req);

        JsonNode jsonNode = new ObjectMapper().readTree(req.getMessageBody());
        Assert.assertEquals("PENDING", jsonNode.get("status").asText());
        Assert.assertEquals("corresid", jsonNode.get("correlationId").asText());
        Assert.assertEquals("errordetail", jsonNode.get("errorDetail").asText());
        Assert.assertEquals("documentidentifier", jsonNode.get("documentIdentifier").asText());
//        Assert.assertEquals("2019-06-27 09:00:00 GMT", jsonNode.get("timestamp").asText());
        Assert.assertTrue(req.getMessageAttributes().containsKey("JMSCorrelationID"));
        Assert.assertEquals("corresid", req.getMessageAttributes().get("JMSCorrelationID").getStringValue());
    }

    @Test
    public void onLogItemMissingValues() throws Exception {
        MsgLogging msgLogging = new MsgLogging(parameters);
        // Thursday, 27 June 2019 10:00:00 GMT+01:00
        msgLogging.logEvent("PENDING", "corresid", null, null);
        ArgumentCaptor<SendMessageRequest> sendMessageRequestArgumentCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        Mockito.verify(sqsClient, times(1)).sendMessage(any());
        Mockito.verify(sqsClient).sendMessage(sendMessageRequestArgumentCaptor.capture());

        SendMessageRequest req = sendMessageRequestArgumentCaptor.getValue();
        Assert.assertNotNull(req);

        JsonNode jsonNode = new ObjectMapper().readTree(req.getMessageBody());
        Assert.assertEquals("PENDING", jsonNode.get("status").asText());
        Assert.assertEquals("corresid", jsonNode.get("correlationId").asText());
        Assert.assertNull(jsonNode.get("errorDetail"));
        Assert.assertNull(jsonNode.get("documentIdentifier"));
//        Assert.assertEquals("2019-06-27 09:00:00 GMT", jsonNode.get("timestamp").asText());
        Assert.assertTrue(req.getMessageAttributes().containsKey("JMSCorrelationID"));
        Assert.assertEquals("corresid", req.getMessageAttributes().get("JMSCorrelationID").getStringValue());
    }

}
