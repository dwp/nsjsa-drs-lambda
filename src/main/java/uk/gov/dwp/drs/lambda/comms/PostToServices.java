package uk.gov.dwp.drs.lambda.comms;

import org.apache.log4j.Logger;
import uk.gov.dwp.drs.lambda.util.DrsParameters;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class PostToServices {
    private DrsParameters parameters;
    private SSLSocketFactory sslSocketFactory;
    private static final Logger LOGGER = Logger.getLogger(PostToServices.class);
    private final static int STD_TIMEOUT = 10000;

    public PostToServices(DrsParameters parameters) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, IOException, KeyManagementException, KeyStoreException, InvalidKeySpecException {
        this.parameters = parameters;
        this.sslSocketFactory = initSSL();
    }

    private Certificate createCertFromString(String publicKey) throws CertificateException {
        // One funny idiosyncrasy of this interface is that the BEGIN CERTIFICATE requires a LF at the end of the begin
        // mark.  Not mentioned in the spec, but it's required to insert one if it doesn't exist.
        String publicKey1 = publicKey.replaceFirst("-----BEGIN CERTIFICATE-----[\n\r]*", "-----BEGIN CERTIFICATE-----\r\n");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(publicKey1.getBytes());
        BufferedInputStream bis = new BufferedInputStream(inputStream);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(bis);
    }

    private SSLSocketFactory initSSL() throws NoSuchAlgorithmException, IOException, KeyStoreException, CertificateException, InvalidKeySpecException, KeyManagementException, UnrecoverableKeyException {
        String privateKey = parameters.getParameter(DrsParameters.TRANSKEY_PRV);
        String publicKey = parameters.getParameter(DrsParameters.TRANSKEY_PUB);

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        Certificate localCert = createCertFromString(publicKey);
        trustStore.setCertificateEntry("home", localCert);
        try {
            trustStore.setCertificateEntry("caroot", createCertFromString(parameters.getParameter(DrsParameters.CACERT)));
        } catch (CertificateException e) {
            LOGGER.warn("No 'caroot' certificate", e);
        }
        try {
            trustStore.setCertificateEntry("caroot2", createCertFromString(parameters.getParameter(DrsParameters.CACERT2)));
        } catch (CertificateException e) {
            LOGGER.warn("No 'caroot2' certificate", e);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(trustStore);

        KeyStore privateKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        privateKeyStore.load(null, null);

        String privKeyPEM = privateKey.replaceAll("-----(BEGIN|END)( RSA)? PRIVATE KEY-----", "").replace("\n", "");
            byte[] decoded = Base64.getDecoder().decode(privKeyPEM);

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        privateKeyStore.setKeyEntry("prv",
                kf.generatePrivate(spec),
                "".toCharArray(),
                new Certificate[] { localCert });


        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(privateKeyStore, "".toCharArray());


        SSLContext context = SSLContext.getInstance("TLS");

        //We now provide our alternate KeyManager
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    public boolean postToDRS(String data, String correlationId) throws IOException {
        URL url = new URL(parameters.getParameter(DrsParameters.DRS_URL));

        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setRequestProperty("Authorization", "P8Admin");
        conn.setConnectTimeout(STD_TIMEOUT);
        conn.setReadTimeout(STD_TIMEOUT);
        String apiKey = parameters.getParameter(DrsParameters.APIKEY);
        if (apiKey != null) {
            conn.setRequestProperty("apiKey", apiKey);
        }
        conn.setRequestProperty("correlationId", correlationId);
        conn.setDoOutput(true);

        conn.setSSLSocketFactory(sslSocketFactory);
        conn.connect();

        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(data);
        wr.flush();
        wr.close();

        LOGGER.info("DRS returns code code " + conn.getResponseCode());

        return conn.getResponseCode() == 200;
    }
}
