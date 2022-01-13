package uk.gov.dwp.drs.lambda.exceptions;

/**
 * Created by matthew.stack on 22/07/2019.
 */
public class BLProcessException extends RuntimeException {
    public BLProcessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BLProcessException(String message) {
        super(message);
    }
}
