DRS-Lambda Code
===============

Parameters
----------

Can be set in either SSM or environment:

transkey_prv, transkey_pub
--------------------------
TLS keys for communication with DRS services, with API gateway.  _prv should be our server key,
_pub should be our server crt.

signkey_prv, signkey_pub
------------------------
Keys for signing DRS documents.  Just create private key, create signing request and get it signed by QuoVardis, I think.
Not even sure that it is checked.

caroot_crt, caroot_crt
----------------------
Two spaces for root signing certificates.  Would be in practice our root signing key for internal network and another for
API gateway.

apikey
------
Should be provided by API gateway team - not sure if they use it or still provide it.

ssmpath
-------
Path to SSM variables for example /nsjsa_dev/signkey_prv would be "/nsjsa_dev".  This cannot be set in SSM for obvious
reasons.

drsrequest_username
-------------------
Username to place into DRS request.  Not sure if it's ever used, but I had a value of 1234 for BL.

drsurl
------
URL for DRS submissions - probably: https://gateway.integr-test.dwpcloud.uk:8443/DRSWebService/services/DocumentUploadService
for non-prod.

response_queue
--------------
name of the response queue.  Only used for debugging.  Still needed.

pdfurl
------
URL for pdf generator.  Would be https://claim-statement-service:8082/nsjsa/v1/claim-statement/claimpdf on local.


    public static final String RESPONSE_QUEUE = "response_queue";
    public static final String PDF_URL = "pdfurl";
