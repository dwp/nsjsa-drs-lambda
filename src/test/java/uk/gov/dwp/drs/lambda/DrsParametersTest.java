package uk.gov.dwp.drs.lambda;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.gov.dwp.drs.lambda.util.DrsParameters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DrsParameters.class, AWSSimpleSystemsManagementClientBuilder.class})
public class DrsParametersTest {
    private DrsParameters parameters;
    private AWSSimpleSystemsManagement client;

    private void prepareParameter(String par, String name, String value) {
        when(System.getenv(par)).thenReturn("@/test2/path2/" + name);
        when(client.getParameter(new GetParameterRequest().withWithDecryption(true).withName("/test2/path2/" + name)))
                .thenReturn(new GetParameterResult().withParameter(new Parameter().withName("/test2/path2/" + name).withValue(value)));
    }

    @Before
    public void before() {
        PowerMockito.mockStatic(System.class);
        when(System.getenv(DrsParameters.APIKEY)).thenReturn("apikey1234");
        when(System.getenv(DrsParameters.CACERT)).thenReturn("cacert1234");
        when(System.getenv(DrsParameters.DRS_URL)).thenReturn("drsurl1234");
        when(System.getenv(DrsParameters.DRSREQUEST_USERNAME)).thenReturn("drsrequestusername1234");

        PowerMockito.mockStatic(AWSSimpleSystemsManagementClientBuilder.class);

        AWSSimpleSystemsManagementClientBuilder builder = mock(AWSSimpleSystemsManagementClientBuilder.class);
        when(AWSSimpleSystemsManagementClientBuilder.standard()).thenReturn(builder);

        client = mock(AWSSimpleSystemsManagement.class);
        when(builder.build()).thenReturn(client);
        when(client.getParametersByPath(new GetParametersByPathRequest().withPath("/test/path").withWithDecryption(true))).thenReturn(
                new GetParametersByPathResult().withParameters(
                        new Parameter().withName("/test/path/" + DrsParameters.APIKEY).withValue("apikey4321"),
                        new Parameter().withName("/test/path/" + DrsParameters.CACERT).withValue("cacert4321"))
                        .withNextToken("xyz"));
        when(client.getParametersByPath(new GetParametersByPathRequest().withPath("/test/path").withNextToken("xyz").withWithDecryption(true))).thenReturn(
                new GetParametersByPathResult().withParameters(
                        new Parameter().withName("/test/path/" + DrsParameters.DRS_URL).withValue("drsurl4321"),
                        new Parameter().withName("/test/path/" + DrsParameters.DRSREQUEST_USERNAME).withValue("drsrequestusername4321")));
    }

    @Test
    public void itWillPickupSystemEnvironment() throws Exception {
        DrsParameters parameters = new DrsParameters();

        Assert.assertEquals("apikey1234", parameters.getParameter(DrsParameters.APIKEY));
        Assert.assertEquals("cacert1234", parameters.getParameter(DrsParameters.CACERT));
        Assert.assertEquals("drsurl1234", parameters.getParameter(DrsParameters.DRS_URL));
        Assert.assertEquals("drsrequestusername1234", parameters.getParameter(DrsParameters.DRSREQUEST_USERNAME));
    }

    @Test
    public void itWillOverrideSystemEnvironmentFromAWS() {
        when(System.getenv(DrsParameters.SSM_PATH)).thenReturn("/test/path");
        DrsParameters parameters = new DrsParameters();

        Assert.assertEquals("apikey4321", parameters.getParameter(DrsParameters.APIKEY));
        Assert.assertEquals("cacert4321", parameters.getParameter(DrsParameters.CACERT));
        Assert.assertEquals("drsurl4321", parameters.getParameter(DrsParameters.DRS_URL));
        Assert.assertEquals("drsrequestusername4321", parameters.getParameter(DrsParameters.DRSREQUEST_USERNAME));
    }

    @Test
    public void itWillReadIndirectParameters() {
        prepareParameter(DrsParameters.APIKEY, "DRS_APIKEY", "apikey8888");
        prepareParameter(DrsParameters.CACERT, "DRS_CACERT", "cacert8888");
        prepareParameter(DrsParameters.DRS_URL, "DRS_URL", "drsurl8888");
        prepareParameter(DrsParameters.DRSREQUEST_USERNAME, "DRS_USERNAME", "drsrequestusername8888");

        DrsParameters parameters = new DrsParameters();

        Assert.assertEquals("apikey8888", parameters.getParameter(DrsParameters.APIKEY));
        Assert.assertEquals("cacert8888", parameters.getParameter(DrsParameters.CACERT));
        Assert.assertEquals("drsurl8888", parameters.getParameter(DrsParameters.DRS_URL));
        Assert.assertEquals("drsrequestusername8888", parameters.getParameter(DrsParameters.DRSREQUEST_USERNAME));
    }


    private Parameter p(String name, String value) {
        return new Parameter().withName(name).withValue(value);
    }

    private String encode(String data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream stream = new GZIPOutputStream(out);
        stream.write(data.getBytes());
        stream.finish();
        stream.close();

        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    public void checkUnpack() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
        DrsParameters parameters = new DrsParameters();
        Method unpack = DrsParameters.class.getDeclaredMethod("unpack", Parameter.class);
        unpack.setAccessible(true);

        Assert.assertEquals("hello", unpack.invoke(parameters, p("hello","hello")));

        String data = encode("hello");

        Assert.assertEquals(data, unpack.invoke(parameters, p("hello", data)));
        Assert.assertEquals("hello", unpack.invoke(parameters, p("hello_prv", data)));
        Assert.assertEquals("hello", unpack.invoke(parameters, p("hello_pub", data)));
        Assert.assertEquals("hello", unpack.invoke(parameters, p("hello_crt", data)));
    }
}
