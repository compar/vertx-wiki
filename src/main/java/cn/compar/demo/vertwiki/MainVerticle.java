package cn.compar.demo.vertwiki;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.compar.demo.vertwiki.database.WikiDatabaseVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {

	private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);


	@Override
	public void start(Future<Void> startFuture) throws Exception {		
		Future<String> dbVerticleDeployment = Future.future(); 
		vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment.completer());
		
		dbVerticleDeployment.compose(id -> {
	        Future<String> httpVerticleDeployment = Future.future();
	        vertx.deployVerticle("cn.compar.demo.vertwiki.http.HttpServerVerticle", 
	                new DeploymentOptions().setInstances(2), 
	                httpVerticleDeployment.completer());
	        return httpVerticleDeployment; 
	        }).setHandler(ar -> {
	            if (ar.succeeded()) {
	                startFuture.complete();
	            } else {
	                startFuture.fail(ar.cause());
	            }
	        });
	    }

}
