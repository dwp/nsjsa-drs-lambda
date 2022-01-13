package uk.gov.dwp.drs.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.dwp.drs.lambda.comms.MsgLogging;
import uk.gov.dwp.drs.lambda.comms.PostToServices;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.gov.dwp.components.drs.creator.api.MetadataCreator;
import uk.gov.dwp.drs.lambda.exceptions.BLProcessException;
import uk.gov.dwp.drs.lambda.misc.Utils;
import uk.gov.dwp.drs.lambda.util.DrsParameters;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.matches;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DrsProcess.class})
public class BLProcessTest {
    private static final Logger LOGGER = Logger.getLogger(BLProcessTest.class);

    private MetadataCreator metadataCreator;
    private DrsProcess process;
    private Context context;
    private MsgLogging msgLogging;
    private DrsParameters parameters;
    private PostToServices postToServices;


    private final static String pdfDocument = "XXXX";
    private final static String metadataDocument = "YYYY";
    private final static String test = "{\"metadata\":{\"businessUnitID\":36,\"classification\":0,\"claimRef\":\"55091b49\"," +
            "\"documentType\":9877,\"documentSource\":4,\"dateOfBirth\":20000202,\"surname\":\"Pethapuria\"," +
            "\"forename\":\"\",\"postCode\":\"TE155ST\",\"officePostcode\":\"AB112XD\"," +
            "\"nino\":{\"ninoBody\":\"AB123456\",\"ninoSuffix\":\"D\"},\"benefitType\":4}," +
            "\"payload\": \"XXXX\" }";


    @Before
    public void before() throws Exception {
        parameters = mock(DrsParameters.class);
        PowerMockito.whenNew(DrsParameters.class).withAnyArguments().thenReturn(parameters);

        postToServices = mock(PostToServices.class);
        PowerMockito.whenNew(PostToServices.class).withAnyArguments().thenReturn(postToServices);

        msgLogging = mock(MsgLogging.class);
        whenNew(MsgLogging.class).withAnyArguments().thenReturn(msgLogging);

        metadataCreator = mock(MetadataCreator.class);
        when(metadataCreator.getDocument(anyString(), anyString())).thenReturn(metadataDocument);
        whenNew(MetadataCreator.class).withAnyArguments().thenReturn(metadataCreator);

        process = new DrsProcess();

        context = mock(Context.class);
        when(context.getLogger()).thenReturn(Utils.makeLambdaLoggerAdaptor());

    }

    @Test
    public void testEntrypoint() throws Exception {
        when(postToServices.postToDRS(anyString(),anyString())).thenReturn(true);

        File file = File.createTempFile("file", ".json");
        FileUtils.writeStringToFile(file, test);

        Entrypoint.main(new String[] { file.getCanonicalPath() });

        JsonNode jsonNode = new ObjectMapper().readTree(test);
        String metadata = jsonNode.get("metadata").toString();

        Mockito.verify(metadataCreator, times(1)).getDocument(eq(metadata), eq("XXXX"));
        file.delete();
    }

    @Test
    public void testEntrypointThrowsException() throws Exception {
        when(postToServices.postToDRS(anyString(),anyString())).thenThrow(new IOException());

        File file = File.createTempFile("file", ".json");
        try {
            Entrypoint.main(new String[]{file.getCanonicalPath()});
            fail("Shoudl throw exception");
        } catch (BLProcessException e) {
            LOGGER.info("Caught exception");
        }

        file.delete();
    }

    @Test
    public void runLambdaHappyPath() throws Exception {
        SQSEvent sqsEvent = Utils.makeSQSEvent(test);
        when(postToServices.postToDRS(anyString(),anyString())).thenReturn(true);

        process.handleRequest(sqsEvent, context);

        JsonNode jsonNode = new ObjectMapper().readTree(test);
        String payload = jsonNode.get("payload").toString();
        String metadata = jsonNode.get("metadata").toString();

        Mockito.verify(metadataCreator, times(1)).getDocument(eq(metadata), eq(pdfDocument));
        Mockito.verify(msgLogging,times(1)).logEvent(eq("SUCCEEDED"), anyString(), anyString(), anyString());
        Mockito.verify(postToServices,times(1)).postToDRS(anyString(), anyString());
    }

    @Test
    public void runLambdaPostFails() throws Exception {
        SQSEvent sqsEvent = Utils.makeSQSEvent(test);
        when(postToServices.postToDRS(anyString(),anyString())).thenReturn(false);

        try {
            process.handleRequest(sqsEvent, context);
        } catch (RuntimeException e) {
            LOGGER.info("Caught exception");
        }

        JsonNode jsonNode = new ObjectMapper().readTree(test);
        String payload = jsonNode.get("payload").toString();
        String metadata = jsonNode.get("metadata").toString();

        Mockito.verify(metadataCreator, times(1)).getDocument(eq(metadata), eq(pdfDocument));
        Mockito.verify(msgLogging,times(1)).logEvent(eq("FAILED"), anyString(), anyString(), anyString());
        Mockito.verify(postToServices,times(1)).postToDRS(anyString(), anyString());
    }

    @Test
    public void runLambdaPostFailsThrowsException() throws Exception {
        SQSEvent sqsEvent = Utils.makeSQSEvent(test);
        when(postToServices.postToDRS(anyString(),anyString())).thenThrow(new IOException());

        try {
            process.handleRequest(sqsEvent, context);
        } catch (BLProcessException e) {
            LOGGER.info("Caught exception");
        }

        JsonNode jsonNode = new ObjectMapper().readTree(test);
        String payload = jsonNode.get("payload").toString();
        String metadata = jsonNode.get("metadata").toString();

        Mockito.verify(metadataCreator, times(1)).getDocument(eq(metadata), eq(pdfDocument));
        Mockito.verify(msgLogging,times(1)).logEvent(eq("FAILED"), anyString(), anyString(), anyString());
        Mockito.verify(postToServices,times(1)).postToDRS(anyString(), anyString());
    }

    @Test
    public void runLambdaMDFailsException() throws Exception {
        SQSEvent sqsEvent = Utils.makeSQSEvent(test);

        when(metadataCreator.getDocument(anyString(), anyString())).thenThrow(new BLProcessException("error"));
        try {
            process.handleRequest(sqsEvent, context);
            fail("Should throw exception.");
        } catch (RuntimeException e) {
            LOGGER.info("Caught exception");
        }
        Mockito.verify(msgLogging,times(1)).logEvent(eq("FAILED"), anyString(), anyString(), anyString());
    }

    @Test
    public void runLambdaMDFails() throws Exception {
        SQSEvent sqsEvent = Utils.makeSQSEvent(test);

        when(metadataCreator.getDocument(anyString(), anyString())).thenReturn(null);
        try {
            process.handleRequest(sqsEvent, context);
            fail("Should throw exception.");
        } catch (RuntimeException e) {
            LOGGER.info("Caught exception", e);
        }
        Mockito.verify(msgLogging,times(1)).logEvent(eq("FAILED"), anyString(), anyString(), anyString());
    }

    @Test
    public void runLambdaSameCorrelationId() throws Exception {
        SQSEvent sqsEvent = Utils.makeSQSEvent(test);
        String correlationId = sqsEvent.getRecords().get(0).getMessageAttributes().get(Utils.JMS_CORRELATION_ID).getStringValue();
        when(postToServices.postToDRS(anyString(),anyString())).thenReturn(true);

        process.handleRequest(sqsEvent, context);
        Mockito.verify(msgLogging,times(1)).logEvent(eq("SUCCEEDED"),
                eq(correlationId), anyString(), anyString());
    }

    @Test
    public void runLambdaDifferentCorrelationId() throws Exception {
        SQSEvent sqsEvent = Utils.makeSQSEvent(test);
        String correlationId = sqsEvent.getRecords().get(0).getMessageAttributes().get(Utils.JMS_CORRELATION_ID).getStringValue();
        sqsEvent.getRecords().get(0).setMessageAttributes(Collections.emptyMap());

        when(postToServices.postToDRS(anyString(),anyString())).thenReturn(true);

        process.handleRequest(sqsEvent, context);
        Mockito.verify(msgLogging,times(1)).logEvent(eq("SUCCEEDED"),
                AdditionalMatchers.not(eq(correlationId)), anyString(), anyString());
        Mockito.verify(msgLogging,times(1)).logEvent(eq("SUCCEEDED"),
                matches("[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}"),
                anyString(), anyString());
    }
}
