package ru.vk.itmo.test.kovalevigor.server.strategy;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import ru.vk.itmo.test.kovalevigor.server.util.Headers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import static ru.vk.itmo.test.kovalevigor.server.strategy.ServerDaoStrategy.log;
import static ru.vk.itmo.test.kovalevigor.server.util.ServerUtil.REMOTE_TIMEOUT;

public class ServerRemoteStrategy extends ServerRejectStrategy {
    private final HttpClient httpClient;
    private final String remoteUrl;

    public ServerRemoteStrategy(HttpClient httpClient, String remoteUrl) {
        this.httpClient = httpClient;
        this.remoteUrl = remoteUrl;
    }

    @Override
    public Response handleRequest(Request request, HttpSession session) {
        try {
            return handleRequestAsync(request, session).get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.log(Level.SEVERE, "Exception while redirection", e.getCause());
        } catch (TimeoutException e) {
            log.log(Level.SEVERE, "Exception while redirection", e);
        }
        return null;
    }

    @Override
    public CompletableFuture<Response> handleRequestAsync(Request request, HttpSession session) {
        HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder(URI.create(remoteUrl + request.getURI()))
                .method(
                        request.getMethodName(),
                        request.getBody() == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                )
                .timeout(REMOTE_TIMEOUT);
        for (Headers header : Headers.values()) {
            String headerValue = Headers.getHeader(request, header);
            if (headerValue != null) {
                httpRequestBuilder.header(header.getName(), headerValue);
            }
        }
        return httpClient.sendAsync(httpRequestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .orTimeout(1, TimeUnit.SECONDS)
                .thenApply(ServerRemoteStrategy::mapResponse);
    }

    @Override
    public CompletableFuture<Response> handleRequestAsync(Request request, HttpSession session, Executor executor) {
        return handleRequestAsync(request, session);
    }

    private static Response mapResponse(HttpResponse<byte[]> httpResponse) {
        Response response = new Response(
                Integer.toString(httpResponse.statusCode()),
                httpResponse.body()
        );
        for (Headers header : Headers.values()) {
            Headers.addHeader(response, header, httpResponse.headers().firstValue(header.getName()));
        }
        return response;
    }
}
