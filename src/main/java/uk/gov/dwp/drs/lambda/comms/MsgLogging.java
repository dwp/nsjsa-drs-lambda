package uk.gov.dwp.drs.lambda.comms;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.dwp.drs.lambda.misc.Utils;
import uk.gov.dwp.drs.lambda.util.DrsParameters;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.TimeZone;

public class MsgLogging {
    private AmazonSQSAsync client;
    private ObjectMapper objectMapper;
    private String queueUrl;


    public MsgLogging(DrsParameters parameters) {
        String sqsHost = parameters.getSystemParameter("sqshost");

        AmazonSQSAsyncClientBuilder builder = AmazonSQSAsyncClientBuilder.standard();
        if (StringUtils.isNullOrEmpty(sqsHost)) {
            builder.withRegion("eu-west-2");
        } else {
            builder.withRequestHandlers(new RequestHandler2() {});

            builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                    sqsHost, "eu-west-2"))
                    .withCredentials(new AWSStaticCredentialsProvider(
                            new BasicAWSCredentials("", "")));
        }
        client = builder.build();
        objectMapper = new ObjectMapper();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        queueUrl = client.getQueueUrl(parameters.getParameter(DrsParameters.RESPONSE_QUEUE)).getQueueUrl();
    }

    public void logEvent(String status, String correlationId, String errordetail, String documentIdentifier) {
        Message message = new Message(status, correlationId, errordetail, documentIdentifier);
        try {
            client.sendMessage(new SendMessageRequest(queueUrl, objectMapper.writeValueAsString(message))
                    .withMessageAttributes(Collections.singletonMap(Utils.JMS_CORRELATION_ID,
                            new MessageAttributeValue().withStringValue(correlationId).withDataType("String"))));
        } catch (JsonProcessingException e) {
            // silently ignore exception - should not happen
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public class Message {
        private String status;
        private String correlationId;
        private String errorDetail;
        private String documentIdentifier;

        public Message(String status, String correlationId, String errorDetail, String documentidentifier) {
            this.status = status;
            this.correlationId = correlationId;
            this.errorDetail = errorDetail;
            this.documentIdentifier = documentidentifier;
        }

        public String getStatus() {
            return status;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public String getErrorDetail() {
            return errorDetail;
        }

        public String getDocumentIdentifier() {
            return documentIdentifier;
        }
    }
}
