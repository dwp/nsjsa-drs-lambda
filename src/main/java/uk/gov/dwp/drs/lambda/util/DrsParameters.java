package uk.gov.dwp.drs.lambda.util;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.util.StringUtils;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class DrsParameters {
    public static final String TRANSKEY_PRV = "transkey_prv";
    public static final String TRANSKEY_PUB = "transkey_pub";
    public static final String CACERT = "caroot_crt";
    public static final String CACERT2 = "caroot2_crt";
    public static final String APIKEY = "apikey";
    public static final String SIGNKEY_PRV = "signkey_prv";
    public static final String SIGNKEY_PUB = "signkey_pub";
    public static final String DRSREQUEST_USERNAME = "drsrequest_username";
    public static final String DRS_URL = "drsurl";
    public static final String SSM_PATH = "ssmpath";
    public static final String RESPONSE_QUEUE = "response_queue";

    private Map<String,String> parMap;
    private AWSSimpleSystemsManagement client = null;

    public DrsParameters() {
        refresh();
    }

    private AWSSimpleSystemsManagement getClient() {
        if (client == null) {
            AWSSimpleSystemsManagementClientBuilder ssmBuilder = AWSSimpleSystemsManagementClientBuilder.standard();

            String ssmHost = getSystemParameter("ssmhost");
            if (!StringUtils.isNullOrEmpty(ssmHost)) {
                ssmBuilder.setEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(ssmHost, "eu-west-2"));
                ssmBuilder.setCredentials(
                        new AWSStaticCredentialsProvider(new BasicAWSCredentials("", "")));
            }
            client =  ssmBuilder.build();
        }
        return client;
    }

    private void refresh() {
        String[] pars = {
            TRANSKEY_PRV,
            TRANSKEY_PUB,
            CACERT,
            CACERT2,
            APIKEY,
            SIGNKEY_PRV,
            SIGNKEY_PUB,
            DRSREQUEST_USERNAME,
            DRS_URL,
            RESPONSE_QUEUE
        };

        if (parMap == null) {
            parMap = new HashMap<>();
        } else {
            parMap.clear();
        }

        for (String par : pars) {
            String value = getSystemParameter(par);

            if (!StringUtils.isNullOrEmpty(value) && value.startsWith("@")) {
                value = getClient().getParameter(new GetParameterRequest()
                    .withName(value.substring(1)).withWithDecryption(true)).getParameter().getValue();
            }

            parMap.put(par, value);
        }

        String ssmPath = getSystemParameter(SSM_PATH);
        if (!StringUtils.isNullOrEmpty(ssmPath)) {
            GetParametersByPathResult result;
            GetParametersByPathRequest request = new GetParametersByPathRequest().withPath(ssmPath).withWithDecryption(true);

            do {
                result = getClient().getParametersByPath(request);
                result.getParameters().forEach(p -> parMap.put(p.getName().replaceFirst("^/.*/", ""), unpack(p)));

                if (!StringUtils.isNullOrEmpty(result.getNextToken())) {
                    request.setNextToken(result.getNextToken());
                }
            } while (!StringUtils.isNullOrEmpty(result.getNextToken()));
        }
    }

    private String unpack(Parameter parameter) {
        if ((parameter.getName().endsWith("_pub") ||
                parameter.getName().endsWith("_prv") ||
                parameter.getName().endsWith("_crt")) &&
                !parameter.getValue().startsWith("-----BEGIN")) {
            try {
                ByteArrayInputStream out = new ByteArrayInputStream(Base64.getDecoder().decode(parameter.getValue()));
                GZIPInputStream stream = new GZIPInputStream(out);
                byte[] data = IOUtils.toByteArray(stream);
                return new String(data);
            } catch (IOException | java.lang.IllegalArgumentException e) {
                return parameter.getValue();
            }
        }

        return parameter.getValue();
    }

    public String getParameter(String parameter) {
        return parMap.getOrDefault(parameter, null);
    }

    public String getSystemParameter(String parameter) {
        return System.getenv(parameter);
    }
}
