package examples;

import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import io.vertx.rxjava.core.file.AsyncFile;
import io.vertx.rxjava.core.file.FileSystem;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.http.HttpServerRequest;
import io.vertx.rxjava.core.http.ServerWebSocket;
import io.vertx.rxjava.core.http.WebSocket;
import io.vertx.rxjava.core.http.WebSocketStream;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.plugins.RxJavaSchedulersHook;
import rx.plugins.RxJavaHooks;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class RxifiedExamples {

  public void readStream(Vertx vertx) {
    FileSystem fs = vertx.fileSystem();
    fs.open("/data.txt", new OpenOptions(), result -> {
      AsyncFile file = result.result();
      Observable<Buffer> observable = file.toObservable();
      observable.forEach(data -> System.out.println("Read data: " + data.toString("UTF-8")));
    });
  }

  public void observableFuture(Vertx vertx) {
    vertx.createHttpServer(
        new HttpServerOptions().setPort(1234).setHost("localhost")
    ).listenObservable().
        subscribe(
            server -> {
              // Server is listening
            },
            failure -> {
              // Server could not start
            }
        );
  }

  public void scheduler(Vertx vertx) {
    Scheduler scheduler = io.vertx.rxjava.core.RxHelper.scheduler(vertx);
    Observable<Long> timer = Observable.timer(100, 100, TimeUnit.MILLISECONDS, scheduler);
  }

  public void schedulerHook(Vertx vertx) {
    RxJavaSchedulersHook hook = io.vertx.rxjava.core.RxHelper.schedulerHook(vertx);
      RxJavaHooks.setOnIOScheduler(f -> hook.getIOScheduler());
      RxJavaHooks.setOnNewThreadScheduler(f -> hook.getNewThreadScheduler());
      RxJavaHooks.setOnComputationScheduler(f -> hook.getComputationScheduler());
  }

  public void unmarshaller(FileSystem fileSystem) {
    fileSystem.open("/data.txt", new OpenOptions(), result -> {
      AsyncFile file = result.result();
      Observable<Buffer> observable = file.toObservable();
      observable.lift(io.vertx.rxjava.core.RxHelper.unmarshaller(MyPojo.class)).subscribe(
          mypojo -> {
            // Process the object
          }
      );
    });
  }

  public void embedded() {
    Vertx vertx = io.vertx.rxjava.core.Vertx.vertx();
  }

  public void verticle() {
    class MyVerticle extends io.vertx.rxjava.core.AbstractVerticle {
      public void start() {
        // Use Rxified Vertx here
      }
    }
  }

  public void eventBusMessages(Vertx vertx) {
    EventBus eb = vertx.eventBus();
    MessageConsumer<String> consumer = eb.<String>consumer("the-address");
    Observable<Message<String>> observable = consumer.toObservable();
    Subscription sub = observable.subscribe(msg -> {
      // Got message
    });

    // Unregisters the stream after 10 seconds
    vertx.setTimer(10000, id -> {
      sub.unsubscribe();
    });
  }

  public void eventBusBodies(Vertx vertx) {
    EventBus eb = vertx.eventBus();
    MessageConsumer<String> consumer = eb.<String>consumer("the-address");
    Observable<String> observable = consumer.bodyStream().toObservable();
  }

  public void eventBusMapReduce(Vertx vertx) {
    Observable<Double> observable = vertx.eventBus().
        <Double>consumer("heat-sensor").
        bodyStream().
        toObservable();

    observable.
        buffer(1, TimeUnit.SECONDS).
        map(samples -> samples.
            stream().
            collect(Collectors.averagingDouble(d -> d))).
        subscribe(heat -> {
          vertx.eventBus().send("news-feed", "Current heat is " + heat);
        });
  }

  public void websocketServer(HttpServer server) {
    Observable<ServerWebSocket> socketObservable = server.websocketStream().toObservable();
    socketObservable.subscribe(
        socket -> System.out.println("Web socket connect"),
        failure -> System.out.println("Should never be called"),
        () -> {
          System.out.println("Subscription ended or server closed");
        }
    );
  }

  public void websocketServerBuffer(Observable<ServerWebSocket> socketObservable) {
    socketObservable.subscribe(
        socket -> {
          Observable<Buffer> dataObs = socket.toObservable();
          dataObs.subscribe(buffer -> {
            System.out.println("Got message " + buffer.toString("UTF-8"));
          });
        }
    );
  }

  public void websocketClient(Vertx vertx) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    WebSocketStream stream = client.websocketStream(8080, "localhost", "/the_uri");
    stream.toObservable().subscribe(
        ws -> {
          // Use the websocket
        },
        error -> {
          // Could not connect
        }
    );
  }

  public void websocketClientBuffer(Observable<WebSocket> socketObservable) {
    socketObservable.subscribe(
        socket -> {
          Observable<Buffer> dataObs = socket.toObservable();
          dataObs.subscribe(buffer -> {
            System.out.println("Got message " + buffer.toString("UTF-8"));
          });
        }
    );
  }

  public void httpClientRequest(Vertx vertx) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    HttpClientRequest request = client.request(HttpMethod.GET, 8080, "localhost", "/the_uri");
    request.toObservable().subscribe(
        response -> {
          // Process the response
        },
        error -> {
          // Could not connect
        }
    );
    request.end();
  }

  public void httpClientResponse(HttpClientRequest request) {
    request.toObservable().
        subscribe(
            response -> {
              Observable<Buffer> observable = response.toObservable();
              observable.forEach(
                  buffer -> {
                    // Process buffer
                  }
              );
            }
        );
  }

  public void httpClientResponseFlatMap(HttpClientRequest request) {
    request.toObservable().
        flatMap(HttpClientResponse::toObservable).
        forEach(
            buffer -> {
              // Process buffer
            }
        );
  }

  private class MyPojo {
  }

  public void httpClientResponseFlatMapUnmarshall(HttpClientRequest request) {
    request.toObservable().
        flatMap(HttpClientResponse::toObservable).
        lift(io.vertx.rxjava.core.RxHelper.unmarshaller(MyPojo.class)).
        forEach(
            pojo -> {
              // Process pojo
            }
        );
  }

  public void httpServerRequest(HttpServer server) {
    Observable<HttpServerRequest> requestObservable = server.requestStream().toObservable();
    requestObservable.subscribe(request -> {
      // Process request
    });
  }

  public void httpServerRequestObservable(HttpServer server) {
    Observable<HttpServerRequest> requestObservable = server.requestStream().toObservable();
    requestObservable.subscribe(request -> {
      Observable<Buffer> observable = request.toObservable();
    });
  }

  public void httpServerRequestObservableUnmarshall(HttpServer server) {
    Observable<HttpServerRequest> requestObservable = server.requestStream().toObservable();
    requestObservable.subscribe(request -> {
      Observable<MyPojo> observable = request.
          toObservable().
          lift(io.vertx.rxjava.core.RxHelper.unmarshaller(MyPojo.class));
    });
  }

  public void timer(Vertx vertx) {
    vertx.timerStream(1000).
        toObservable().
        subscribe(
            id -> {
              System.out.println("Callback after 1 second");
            }
        );
  }

  public void periodic(Vertx vertx) {
    vertx.periodicStream(1000).
        toObservable().
        subscribe(
            id -> {
              System.out.println("Callback every second");
            }
        );
  }

  public void periodicUnsubscribe(Vertx vertx) {
    vertx.periodicStream(1000).
        toObservable().
        subscribe(new Subscriber<Long>() {
          public void onNext(Long aLong) {
            // Callback
            unsubscribe();
          }
          public void onError(Throwable e) {}
          public void onCompleted() {}
        });
  }
}

