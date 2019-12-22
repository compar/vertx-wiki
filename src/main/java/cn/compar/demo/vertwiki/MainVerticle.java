package cn.compar.demo.vertwiki;

import cn.compar.demo.vertwiki.database.WikiDatabaseVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
//		Future<String> dbVerticleDeployment = Future.future(); 
		Promise<String> dbVerticleDeployment = Promise.promise();
		vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment);
		
		
		dbVerticleDeployment.future().compose(id -> {
//	        Future<String> httpVerticleDeployment = Future.future();
	        Promise<String> httpVerticleDeployment = Promise.promise();
	        vertx.deployVerticle("cn.compar.demo.vertwiki.http.HttpServerVerticle", 
	                new DeploymentOptions().setInstances(2), 
	                httpVerticleDeployment);
	        return httpVerticleDeployment.future(); 
	        }).setHandler(ar -> {
	            if (ar.succeeded()) {
	            	startPromise.complete();
	            } else {
	            	startPromise.fail(ar.cause());
	            }
	        });
	    }

}
