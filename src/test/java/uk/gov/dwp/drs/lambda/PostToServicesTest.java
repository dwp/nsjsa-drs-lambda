package uk.gov.dwp.drs.lambda;

import uk.gov.dwp.drs.lambda.comms.PostToServices;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.gov.dwp.drs.lambda.util.DrsParameters;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({KeyManagerFactory.class, PostToServices.class})
@PowerMockIgnore({"javax.net.ssl.*"})
public class PostToServicesTest {
    private static final Logger LOGGER = Logger.getLogger(BLProcessTest.class);
    private KeyStore keyStore;
    private KeyManagerFactory keyManagerFactory;
    private DrsParameters parameters;
    private PostToServices postToServices;
    private HttpsURLConnection connection;
    private URL drsurl;
    private ByteArrayOutputStream out;

    @Before
    public void before() throws Exception {
        fakeParameters();
        fakeInitSSL();
        fakeHttps();

        postToServices = new PostToServices(parameters);
    }


    private void fakeHttps() throws Exception {
        drsurl = mock(URL.class);
        connection = mock(HttpsURLConnection.class);
        when(drsurl.openConnection()).thenReturn(connection);

        out = new ByteArrayOutputStream();
        when(connection.getOutputStream()).thenReturn(out);
        when(connection.getResponseCode()).thenReturn(200);

        PowerMockito.mockStatic(URL.class);
        whenNew(URL.class).withAnyArguments().thenReturn(drsurl);
    }

    private void fakeInitSSL() throws Exception {
        PowerMockito.mockStatic(KeyStore.class);
        keyStore = mock(KeyStore.class);
        when(KeyStore.getInstance(anyObject())).thenReturn(keyStore);

        PowerMockito.mockStatic(KeyManagerFactory.class);
        keyManagerFactory = mock(KeyManagerFactory.class);
        when(KeyManagerFactory.getInstance(anyString())).thenReturn(keyManagerFactory);

        PowerMockito.mockStatic(KeyFactory.class);
        KeyFactory keyFactory = mock(KeyFactory.class);
        when(KeyFactory.getInstance(anyString())).thenReturn(keyFactory);

        CertificateFactory certificateFactory = mock(CertificateFactory.class);
        PowerMockito.mockStatic(CertificateFactory.class);
        when(CertificateFactory.getInstance(anyObject())).thenReturn(certificateFactory);

        Certificate certificate = mock(Certificate.class);
        when(certificateFactory.generateCertificate(any())).thenReturn(certificate);

        PKCS8EncodedKeySpec spec = mock(PKCS8EncodedKeySpec.class);
        whenNew(PKCS8EncodedKeySpec.class).withAnyArguments().thenReturn(spec);

    }

    private String splitCrs(String s) {
        int mid = s.length()/2;
        return s.substring(0, mid) + "\n" + s.substring(mid);
    }

    private String getCertFake(String s) {
        return "-----BEGIN CERTIFICATE-----\n"
                + splitCrs(Base64.getEncoder().encodeToString(s.getBytes()))
                + "-----END CERTIFICATE-----";
    }

    private String getKeyFake(String s) {
        return "-----BEGIN RSA PRIVATE KEY-----\n"
                + splitCrs(Base64.getEncoder().encodeToString(s.getBytes()))
                + "-----END RSA PRIVATE KEY-----";
    }


    private void fakeParameters() throws Exception {
        PowerMockito.mockStatic(DrsParameters.class);
        parameters = mock(DrsParameters.class);
        when(parameters.getParameter(DrsParameters.CACERT)).thenReturn(getCertFake("rootCA2.pem"));
        when(parameters.getParameter(DrsParameters.CACERT2)).thenReturn(getCertFake("rootCA.pem"));
        when(parameters.getParameter(DrsParameters.SIGNKEY_PUB)).thenReturn(getCertFake("drssigning_1.crt"));
        when(parameters.getParameter(DrsParameters.SIGNKEY_PRV)).thenReturn(getKeyFake("drssigning.key"));
        when(parameters.getParameter(DrsParameters.TRANSKEY_PUB)).thenReturn(getCertFake("drscommunicator_2.crt"));
        when(parameters.getParameter(DrsParameters.TRANSKEY_PRV)).thenReturn(getKeyFake("drscommunicator.key"));
        when(parameters.getParameter(DrsParameters.DRS_URL)).thenReturn("https://drsmock:9090/submit");
        whenNew(DrsParameters.class).withNoArguments().thenReturn(parameters);
    }

    @Test
    public void tryInitSSL() throws Exception {
        Mockito.verify(keyStore, times(1)).setCertificateEntry(eq("home"), anyObject());
        Mockito.verify(keyManagerFactory, times(1)).init(keyStore, "".toCharArray());
    }

    @Test
    public void tryPostToDRS() throws Exception {
        when(parameters.getParameter(DrsParameters.APIKEY)).thenReturn("abcd1234");

        Assert.assertTrue(postToServices.postToDRS("DATA", "12345678"));

        Mockito.verify(connection, times(1)).setRequestProperty(eq("Authorization"), eq("P8Admin"));
        Mockito.verify(connection, times(1)).setRequestProperty(eq("apiKey"), eq("abcd1234"));
        Assert.assertEquals("DATA", new String(out.toByteArray()));
    }

    @Test
    public void runLambdaDRSFailsToConnect() throws Exception {
        when(drsurl.openConnection()).thenThrow(new IOException());
        try {
            postToServices.postToDRS("DATA", "12345678");
            fail("Should throw exception.");
        } catch (IOException e) {
            LOGGER.info("Caught exception");
        }
    }

    @Test
    public void runLambdaDRSreturnsError5xx() throws Exception {
        when(connection.getResponseCode()).thenReturn(501);
        Assert.assertFalse(postToServices.postToDRS("DATA", "12345678"));
    }

    @Test
    public void runLambdaDRSreturnsError4xx() throws Exception {
        when(connection.getResponseCode()).thenReturn(400);
        Assert.assertFalse(postToServices.postToDRS("DATA", "12345678"));
    }

    @Test
    public void runLambdaDRSreturnsError200() throws Exception {
        when(connection.getResponseCode()).thenReturn(200);
        Assert.assertTrue(postToServices.postToDRS("DATA", "12345678"));
    }
}
