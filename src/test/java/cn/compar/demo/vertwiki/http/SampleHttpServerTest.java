package cn.compar.demo.vertwiki.http;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;


@RunWith(VertxUnitRunner.class)
public class SampleHttpServerTest {
	  private Vertx vertx;
	  @Before
	  public void setUp(TestContext tc) {
	    vertx = Vertx.vertx();
//	    vertx.deployVerticle(MainVerticle.class.getName(), tc.asyncAssertSuccess());
	  }
	  @After
	  public void finish(TestContext context) {
	    vertx.close(context.asyncAssertSuccess());
	  }
	  @Test
	  public void start_http_server(TestContext context) {
	      Async async = context.async();
	      vertx.createHttpServer()
	              .requestHandler(req -> req.response().putHeader("Content-Type", "text/plain").end("Ok"))
	              .listen(8080,context.asyncAssertSuccess(server -> {
	                  WebClient webClient = WebClient.create(vertx);
	                  webClient.get(8080, "localhost", "/").send(ar -> {
	                      if (ar.succeeded()) {
	                          HttpResponse<Buffer> response = ar.result();
	                          context.assertTrue(response.headers().contains("Content-Type"));
	                          context.assertEquals("text/plain",response.getHeader("Content-Type"));
	                          context.assertEquals("Ok", response.body().toString());
	                          webClient.close();
	                          async.complete();
	                      } else {
	                    	  async.resolve(Future.failedFuture(ar.cause()));
	                      }
	                  });
	              }));
	  }
}
