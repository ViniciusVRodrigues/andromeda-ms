package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A real HTTP server on a loopback port that replays queued responses and records every request
 * (the MockWebServer equivalent without a new build dependency). Responses are served in order;
 * when the queue is empty it answers 500.
 */
final class FakeAzureDevOpsServer implements AutoCloseable {

    record Recorded(String method, String rawPathAndQuery, String authorization) {}

    record Reply(int status, String body, Map<String, String> headers) {}

    private final HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    FakeAzureDevOpsServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    String orgUrl(String organization) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + organization;
    }

    FakeAzureDevOpsServer enqueue(int status, String body) {
        return enqueue(status, body, Map.of());
    }

    synchronized FakeAzureDevOpsServer enqueue(int status, String body, Map<String, String> headers) {
        replies.add(new Reply(status, body, headers));
        return this;
    }

    FakeAzureDevOpsServer enqueueFixture(String fixture) {
        return enqueue(200, fixture(fixture));
    }

    List<Recorded> requests() {
        return new ArrayList<>(requests);
    }

    static String fixture(String name) {
        try (InputStream in = FakeAzureDevOpsServer.class.getResourceAsStream("/fixtures/azure-repos/" + name)) {
            if (in == null) throw new IllegalArgumentException("Missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()),
                exchange.getRequestHeaders().getFirst("Authorization")));
        Reply reply;
        synchronized (this) {
            reply = replies.isEmpty() ? new Reply(500, "{}", Map.of()) : replies.poll();
        }
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
