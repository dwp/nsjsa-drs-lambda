package uk.gov.dwp.drs.lambda;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import uk.gov.dwp.drs.lambda.exceptions.BLProcessException;
import uk.gov.dwp.drs.lambda.misc.Utils;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;

// To test this: set environment ssmhost to http://localhost:4583
// and direct through proxy for localstack environment:
//    -Dhttp.proxyHost=localhost
//    -Dhttp.proxyPort=3128
//    -Dhttps.proxyHost=localhost
//    -Dhttps.proxyPort=3128
public class Entrypoint {
    private static final Logger LOGGER = Logger.getLogger(Entrypoint.class);
    public static void main(String[] args) {
        DrsProcess process;
        String application;
        try {
            process = new DrsProcess();
            application = FileUtils.readFileToString(new File(args[0]));
        } catch (CertificateException | UnrecoverableKeyException | IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException | InvalidKeySpecException e) {
            LOGGER.info("Failed to run DRS communicator process", e);
            throw new BLProcessException(e.getMessage(), e);
        }


        process.handleRequest(Utils.makeSQSEvent(application), null);
    }

}
