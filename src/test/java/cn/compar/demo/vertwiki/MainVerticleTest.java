package cn.compar.demo.vertwiki;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import cn.compar.demo.vertwiki.database.WikiDatabaseService;
import cn.compar.demo.vertwiki.database.WikiDatabaseVerticle;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

  private Vertx vertx;
  private WikiDatabaseService service;
  
  @Before
  public void setUp(TestContext tc) {
    vertx = Vertx.vertx();
//    vertx.deployVerticle(MainVerticle.class.getName(), tc.asyncAssertSuccess());

	    JsonObject conf = new JsonObject()
	        .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL,               "jdbc:hsqldb:mem:testdb;shutdown=true")
	        .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);
	    vertx.deployVerticle(new WikiDatabaseVerticle(), new DeploymentOptions().setConfig(conf),
	    		tc.asyncAssertSuccess(id ->
	        service = WikiDatabaseService.createProxy(vertx,WikiDatabaseVerticle.CONFIG_WIKIDB_QUEUE)));
  }

  @After
  public void tearDown(TestContext tc) {
    vertx.close(tc.asyncAssertSuccess());
  }

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
  @Test
  public void crud_operations(TestContext context) {
      Async async = context.async();
      service.createPage("Test","Some content",context.asyncAssertSuccess(v1 -> {
          service.fetchPage("Test",context.asyncAssertSuccess(json1 -> {
              context.assertTrue(json1.getBoolean("found"));
              context.assertTrue(json1.containsKey("id"));
              context.assertEquals("Some content",json1.getString("rawContent"));
              service.savePage(json1.getInteger("id"),"Yo!",context.asyncAssertSuccess(v2 -> {
                  service.fetchAllPages(context.asyncAssertSuccess(array1 -> {
                      context.assertEquals(1,array1.size());
                      service.fetchPage("Test",context.asyncAssertSuccess(json2 -> {
                          context.assertEquals("Yo!",json2.getString("rawContent"));
                          service.deletePage(json1.getInteger("id"),v3 -> {
                              service.fetchAllPages(context.asyncAssertSuccess(array2 -> {
                                  context.assertTrue(array2.isEmpty());
                                  async.complete();
                                  }));
                              });
                          }));
                      }));
                  }));
              }));
          }));
      async.awaitSuccess(5000);
  }
}