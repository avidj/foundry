// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoundryServerTest {

  private static final String API_KEY = "test-api-key";
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private Main.FoundryServer server;

  @AfterEach
  void stopServer() {
    if ( server != null ) { server.stop(); }
  }

  // ---- startup guards: the store must never come up without auth configured ----

  @Test
  void startFailsWhenApiKeyIsMissing(@TempDir Path dataDir) {
    IllegalStateException e = assertThrows(IllegalStateException.class,
      () -> new Main.FoundryServer().dataDir(dataDir).port(0).apiKey(null).start());
    assertThat(e.getMessage(), containsString(Main.ENV_API_KEY));
  }

  @Test
  void startFailsWhenApiKeyIsBlank(@TempDir Path dataDir) {
    IllegalStateException e = assertThrows(IllegalStateException.class,
      () -> new Main.FoundryServer().dataDir(dataDir).port(0).apiKey("   ").start());
    assertThat(e.getMessage(), containsString(Main.ENV_API_KEY));
  }

  @Test
  void startFailsWhenApiKeyWasNeverSet(@TempDir Path dataDir) {
    assertThrows(IllegalStateException.class,
      () -> new Main.FoundryServer().dataDir(dataDir).port(0).start());
  }

  @Test
  void startFailsWithoutDataDir() {
    assertThrows(IllegalStateException.class,
      () -> new Main.FoundryServer().port(0).apiKey(API_KEY).start());
  }

  // ---- auth enforcement on a running server ----

  @Test
  void healthIsReachableWithoutAuth(@TempDir Path dataDir) throws Exception {
    start(dataDir);
    HttpResponse<String> res = send(request("/v1/health").GET());
    assertThat(res.statusCode(), is(200));
    assertThat(res.body(), is("OK"));
  }

  @Test
  void requestWithoutAuthHeaderIsRejected(@TempDir Path dataDir) throws Exception {
    start(dataDir);
    assertThat(send(request("/v1/keys/k").GET()).statusCode(), is(401));
  }

  @Test
  void requestWithWrongApiKeyIsRejected(@TempDir Path dataDir) throws Exception {
    start(dataDir);
    HttpRequest req = request("/v1/keys/k").header("Authorization", "Bearer wrong").GET().build();
    assertThat(CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).statusCode(), is(401));
  }

  @Test
  void unauthenticatedWriteIsRejectedAndDoesNotReachTheStore(@TempDir Path dataDir) throws Exception {
    start(dataDir);
    HttpResponse<String> write =
      send(request("/v1/keys/k").PUT(HttpRequest.BodyPublishers.ofString("v")));
    assertThat(write.statusCode(), is(401));

    // the rejected write must not have been persisted
    HttpResponse<String> read = send(authed(request("/v1/keys/k")).GET());
    assertThat(read.statusCode(), is(404));
  }

  @Test
  void authenticatedRoundTrip(@TempDir Path dataDir) throws Exception {
    start(dataDir);
    assertThat(send(authed(request("/v1/keys/k")).PUT(HttpRequest.BodyPublishers.ofString("v")))
      .statusCode(), is(201));

    HttpResponse<String> read = send(authed(request("/v1/keys/k")).GET());
    assertThat(read.statusCode(), is(200));
    assertThat(read.body(), is("v"));

    assertThat(send(authed(request("/v1/keys/k")).DELETE()).statusCode(), is(204));
    assertThat(send(authed(request("/v1/keys/k")).GET()).statusCode(), is(404));
  }

  // ---- helpers ----

  private void start(Path dataDir) throws IOException {
    server = new Main.FoundryServer().dataDir(dataDir).port(0).apiKey(API_KEY).start();
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + server.boundPort() + path));
  }

  private HttpRequest.Builder authed(HttpRequest.Builder builder) {
    return builder.header("Authorization", "Bearer " + API_KEY);
  }

  private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
    return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
