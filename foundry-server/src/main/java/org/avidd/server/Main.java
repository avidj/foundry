package org.avidd.server;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import org.avidd.bitcask.BitcaskKVStore;
import org.avidd.kvstore.KVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  public static final String ENV_DATA_DIR = "DATA_DIR";
  private static final Path DEFAULT_DATA_DIR = Path.of(".","data");
  public static final String ENV_PORT = "PORT";
  private static final int DEFAULT_PORT = 7070;
  public static final String ENV_API_KEY = "API_KEY";
  private static FoundryServer srv;

  public static void main(String[] args) throws IOException, InterruptedException {
    // collect configuration
    String strDataDir = System.getenv().get(ENV_DATA_DIR);
    Path dataDir = strDataDir != null ? Path.of(strDataDir) : DEFAULT_DATA_DIR;
    String strPort = System.getenv().get(ENV_PORT);
    int port = strPort != null ? Integer.parseInt(strPort) : DEFAULT_PORT;

    // start server
    srv = new FoundryServer()
      .dataDir(dataDir)
      .port(port)
      .start();

    // shutdown hook (on runtime shutdown)
    CountDownLatch shutDown = new CountDownLatch(1);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      srv.javalin.stop();
      shutDown.countDown();
    }));
    shutDown.await();
  }

  private static class FoundryServer {
    private Path dataDir;
    private BitcaskKVStore kvs;
    private int port =  DEFAULT_PORT;
    private Javalin javalin;

    public FoundryServer dataDir(Path dataDir) {
      this.dataDir = dataDir;
      return this;
    }

    public FoundryServer port(int port) {
      this.port = port;
      return this;
    }

    public FoundryServer start() throws IOException {
      if ( this.dataDir == null ) {
        throw new IllegalStateException("dataDir has not been set");
      }
      this.kvs = new BitcaskKVStore(this.dataDir);

      // register routes
      javalin = Javalin.create();
      String apiKey = System.getenv(ENV_API_KEY);
      if (apiKey != null && !apiKey.isBlank()) {
        javalin.before(ctx -> {
          if (ctx.path().equals("/v1/health")) return;
          String auth = ctx.header("Authorization");
          if (auth == null || !auth.equals("Bearer " + apiKey)) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.skipRemainingHandlers();
          }
        });
      }
      javalin.get("/v1/health", ctx -> { ctx.result("OK"); });
      javalin.get("/v1/keys/{key}", ctx -> {
        String key = ctx.pathParam("key");
        String value = kvs.get(key);
        if ( value == null ) { ctx.status(HttpStatus.NOT_FOUND); }
        else { ctx.result(value); }
      });
      javalin.put("/v1/keys/{key}", ctx -> {
        String key = ctx.pathParam("key");
        String value = ctx.body();
        kvs.put(key, value);
        ctx.status(HttpStatus.CREATED);
      });
      javalin.delete("/v1/keys/{key}", ctx -> {
        String key = ctx.pathParam("key");
        kvs.delete(key);
        ctx.status(HttpStatus.NO_CONTENT);
      });
      javalin.post("/v1/compact", ctx -> {
        kvs.compact();
        ctx.status(HttpStatus.NO_CONTENT);
      });

      // start listening
      javalin.start(port);
      return this;
    }
  }
}
