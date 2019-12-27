package cn.compar.demo.vertwiki;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {



  @Test
  public void testThatTheServerIsStarted(TestContext tc) {
//    Async async = tc.async();
//    vertx.createHttpClient().getNow(8080, "localhost", "/", response -> {
//      tc.assertEquals(response.statusCode(), 200);
//      response.bodyHandler(body -> {
//        tc.assertTrue(body.length() > 0);
//        System.out.println(body);
//        async.complete();
//      });
//    });
  }
  @Test /*(timeout=5000)*/ 
  public void async_behavior(TestContext context) { 
      Vertx vertx = Vertx.vertx();
      context.assertEquals("foo", "foo");
      Async a1 = context.async();
      Async a2 = context.async(3);
      vertx.setTimer(100, n -> a1.complete());
      vertx.setPeriodic(100, n -> a2.countDown());
  }

  
  


}