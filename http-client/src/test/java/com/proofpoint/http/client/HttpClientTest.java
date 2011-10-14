package com.proofpoint.http.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUtils;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.concurrent.Executors;

public class HttpClientTest
{
    private TestingHttpServer server;
    private HttpClient httpClient;
    private EchoServlet servlet;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        // TODO: wrap all this stuff in a TestBootstrap class
        Injector injector = Guice.createInjector(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new JsonModule(),
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())),
                new ServletModule()
                {
                    @Override
                    public void configureServlets()
                    {
                        bind(EchoServlet.class).in(Scopes.SINGLETON);
                        serve("/*").with(Key.get(EchoServlet.class));
                    }
                });

        server = injector.getInstance(TestingHttpServer.class);
        servlet = injector.getInstance(EchoServlet.class);
        server.start();
        httpClient = new HttpClient(Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).build()));
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testGetMethod()
            throws Exception
    {
        URI uri = server.getBaseUrl().resolve("/road/to/nowhere");
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler()).checkedGet();
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "GET");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testPostMethod()
            throws Exception
    {
        URI uri = server.getBaseUrl().resolve("/road/to/nowhere");
        Request request = RequestBuilder.preparePost()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler()).checkedGet();
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "POST");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testPutMethod()
            throws Exception
    {
        URI uri = server.getBaseUrl().resolve("/road/to/nowhere");
        Request request = RequestBuilder.preparePut()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler()).checkedGet();
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "PUT");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testDeleteMethod()
            throws Exception
    {
        URI uri = server.getBaseUrl().resolve("/road/to/nowhere");
        Request request = RequestBuilder.prepareDelete()
                .setUri(uri)
                .addHeader("foo", "bar")
                .addHeader("dupe", "first")
                .addHeader("dupe", "second")
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler()).checkedGet();
        Assert.assertEquals(statusCode, 200);
        Assert.assertEquals(servlet.requestMethod, "DELETE");
        Assert.assertEquals(servlet.requestUri, uri);
        Assert.assertEquals(servlet.requestHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.requestHeaders.get("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testResponseStatusCode()
            throws Exception
    {
        servlet.responseStatusCode = 543;
        Request request = RequestBuilder.prepareGet()
                .setUri(server.getBaseUrl())
                .build();

        int statusCode = httpClient.execute(request, new ResponseStatusCodeHandler()).checkedGet();
        Assert.assertEquals(statusCode, 543);
    }

    @Test
    public void testResponseStatusMessage()
            throws Exception
    {
        servlet.responseStatusMessage = "message";

        Request request = RequestBuilder.prepareGet()
                .setUri(server.getBaseUrl())
                .build();

        String statusMessage = httpClient.execute(request, new ResponseHandler<String, Exception>()
        {
            @Override
            public Exception handleException(Request request, Exception exception)
            {
                return exception;
            }

            @Override
            public String handle(Request request, Response response)
                    throws Exception
            {
                return response.getStatusMessage();
            }
        }).checkedGet();

        Assert.assertEquals(statusMessage, "message");
    }

    @Test
    public void testResponseHeader()
            throws Exception
    {
        servlet.responseHeaders.put("foo", "bar");
        servlet.responseHeaders.put("dupe", "first");
        servlet.responseHeaders.put("dupe", "second");

        Assert.assertEquals(servlet.responseHeaders.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(servlet.responseHeaders.get("dupe"), ImmutableList.of("first", "second"));

        Request request = RequestBuilder.prepareGet()
                .setUri(server.getBaseUrl())
                .build();

        ListMultimap<String, String> headers = httpClient.execute(request, new ResponseHandler<ListMultimap<String, String>, Exception>()
        {
            @Override
            public Exception handleException(Request request, Exception exception)
            {
                return exception;
            }

            @Override
            public ListMultimap<String, String> handle(Request request, Response response)
                    throws Exception
            {
                return response.getHeaders();
            }
        }).checkedGet();

        Assert.assertEquals(headers.get("foo"), ImmutableList.of("bar"));
        Assert.assertEquals(headers.get("dupe"), ImmutableList.of("first", "second"));
    }

    @Test
    public void testResponseBodyEmpty()
            throws Exception
    {
        Request request = RequestBuilder.prepareGet()
                .setUri(server.getBaseUrl())
                .build();

        String body = httpClient.execute(request, new ResponseToStringHandler()).checkedGet();
        Assert.assertEquals(body, "");
    }

    @Test
    public void testResponseBody()
            throws Exception
    {
        servlet.responseBody = "body text";

        Request request = RequestBuilder.prepareGet()
                .setUri(server.getBaseUrl())
                .build();

        String body = httpClient.execute(request, new ResponseToStringHandler()).checkedGet();
        Assert.assertEquals(body, "body text");
    }

    @Test
    public void testErrorResponseBody()
            throws Exception
    {
        servlet.responseStatusCode = 500;
        servlet.responseBody = "body text";

        Request request = RequestBuilder.prepareGet()
                .setUri(server.getBaseUrl())
                .build();

        String body = httpClient.execute(request, new ResponseToStringHandler()).checkedGet();
        Assert.assertEquals(body, "body text");
    }

    private static final class EchoServlet extends HttpServlet
    {
        private String requestMethod;
        private URI requestUri;
        private final ListMultimap<String, String> requestHeaders = ArrayListMultimap.create();

        private int responseStatusCode = 200;
        private String responseStatusMessage;
        private final ListMultimap<String, String> responseHeaders = ArrayListMultimap.create();
        public String responseBody;

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException
        {
            requestMethod = request.getMethod();
            requestUri = URI.create(HttpUtils.getRequestURL(request).toString());

            requestHeaders.clear();
            for (String name : Collections.list(request.getHeaderNames())) {
                requestHeaders.putAll(name, Collections.list(request.getHeaders(name)));
            }

            if (responseStatusMessage != null) {
                response.sendError(responseStatusCode, responseStatusMessage);
            }
            else {
                response.setStatus(responseStatusCode);
            }
            for (Entry<String, String> entry : responseHeaders.entries()) {
                response.addHeader(entry.getKey(), entry.getValue());
            }
            if (responseBody != null) {
                response.getOutputStream().write(responseBody.getBytes(Charsets.UTF_8));
            }
        }
    }

    private static class ResponseToStringHandler implements ResponseHandler<String, Exception>
    {
        @Override
        public Exception handleException(Request request, Exception exception)
        {
            return exception;
        }

        @Override
        public String handle(Request request, Response response)
                throws Exception
        {
            return new String(ByteStreams.toByteArray(response.getInputStream()), Charsets.UTF_8);
        }
    }

    private static class ResponseStatusCodeHandler implements ResponseHandler<Integer, Exception>
    {
        @Override
        public Exception handleException(Request request, Exception exception)
        {
            return exception;
        }

        @Override
        public Integer handle(Request request, Response response)
                throws Exception
        {
            return response.getStatusCode();
        }
    }
}
