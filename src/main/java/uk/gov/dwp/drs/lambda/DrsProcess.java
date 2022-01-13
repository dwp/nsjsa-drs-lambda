package uk.gov.dwp.drs.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.dwp.drs.lambda.comms.MsgLogging;
import uk.gov.dwp.drs.lambda.comms.PostToServices;
import io.puresec.FunctionShield;
import org.json.JSONObject;
import uk.gov.dwp.components.drs.creator.api.MetadataCreator;
import uk.gov.dwp.drs.lambda.exceptions.BLProcessException;
import uk.gov.dwp.drs.lambda.misc.Utils;
import uk.gov.dwp.drs.lambda.util.DrsParameters;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

public class DrsProcess implements RequestHandler<SQSEvent,Void> {
    private MetadataCreator metadataCreator;
    private MsgLogging msgLogging;
    private PostToServices postToServices;
    private static final String BLOCK = "block";

    static
    {
        FunctionShield.configure(new JSONObject()
                .put("policy", new JSONObject()
                        .put("read_write_tmp", BLOCK)
                        .put("create_child_process", BLOCK)
                        .put("outbound_connectivity", BLOCK)
                        .put("read_handler", BLOCK))
                .put("token", System.getenv("FUNCTION_SHIELD_TOKEN")));
    }

    public DrsProcess() throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, IOException, KeyManagementException, KeyStoreException, InvalidKeySpecException {
        DrsParameters parameters = new DrsParameters();
        metadataCreator = new MetadataCreator(
                parameters.getParameter(DrsParameters.SIGNKEY_PRV),
                parameters.getParameter(DrsParameters.SIGNKEY_PUB),
                parameters.getParameter(DrsParameters.DRSREQUEST_USERNAME));
        postToServices = new PostToServices(parameters);
        msgLogging = new MsgLogging(parameters);
    }

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {
        LambdaLogger logger = context == null ? Utils.makeLambdaLoggerAdaptor() : context.getLogger();

        logger.log("========================== BL Process ==================================");

        ObjectMapper objectMapper = new ObjectMapper();
        sqsEvent.getRecords()
                .forEach(c -> {
                    String metadata = "";
                    String correlationId = "";
                    try {
                        SQSEvent.MessageAttribute messageAttribute = c.getMessageAttributes().get(Utils.JMS_CORRELATION_ID);
                        correlationId = messageAttribute == null ? UUID.randomUUID().toString() : messageAttribute.getStringValue();

                        logger.log("Processing message with correlation id " + correlationId);

                        JsonNode jsonNode = objectMapper.readTree(c.getBody());
                        String pdf = jsonNode.get("payload").textValue();
                        metadata = jsonNode.get("metadata").toString();

                        String doc = metadataCreator.getDocument(metadata,
                                (pdf.startsWith("\"") && pdf.endsWith("\"")) ? pdf.substring(1, pdf.length()-1) : pdf);
                        if (doc == null) {
                            throw new BLProcessException("No document was generated. (" + correlationId + ")");
                        }

                        boolean result = postToServices.postToDRS(doc, correlationId);
                        if (result) {
                            logger.log("Application successfully sent to DRS (" + correlationId + ")");
                            msgLogging.logEvent("SUCCEEDED", correlationId, null, null);
                        } else {
                            logger.log("Application failed to send to DRS. (" + correlationId + ")");
                            throw new BLProcessException("Application failed to send to DRS. (" + correlationId + ")");
                        }

                    } catch (IOException e) {
                        logger.log(e.getMessage());
                        msgLogging.logEvent("FAILED", correlationId, e.getLocalizedMessage(), null);
                        throw new BLProcessException("Failed to process message", e);
                    } catch (BLProcessException e) {
                        logger.log(e.getMessage());
                        msgLogging.logEvent("FAILED", correlationId, e.getLocalizedMessage(), null);
                        throw e;
                    }
                });

        return null;
    }
}
