package cn.compar.demo.vertwiki.http;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rjeschke.txtmark.Processor;

import cn.compar.demo.vertwiki.database.WikiDatabaseService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;

public class HttpServerVerticle extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);
	public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
	public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create(Vertx.vertx());
	
	private WikiDatabaseService dbService;
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"); 
		dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);

		HttpServer server = vertx.createHttpServer();
		Router router = Router.router(vertx);
		router.get("/").handler(this::indexHandler);
		router.get("/wiki/:page").handler(this::pageRenderingHandler);
		router.post().handler(BodyHandler.create());
		router.post("/save").handler(this::pageUpdateHandler);
		router.post("/create").handler(this::pageCreateHandler);
		router.post("/delete").handler(this::pageDeletionHandler);
		int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
		server.requestHandler(router).listen(portNumber, ar -> {
			if (ar.succeeded()) {
				LOGGER.info("HTTP server running on port " + portNumber);
				startPromise.complete();
			} else {
				LOGGER.error("Could not start a HTTP server", ar.cause());
				startPromise.fail(ar.cause());
			}
		});
	}

	private void indexHandler(RoutingContext context) {
		dbService.fetchAllPages(reply -> {
			if (reply.succeeded()) {
				context.put("title", "Wiki home");
				context.put("pages", reply.result().getList());
				context.put("context", context.data());
				templateEngine.render(context.data(), "/templates/index.ftl", ar -> {
					if (ar.succeeded()) {
						context.response().putHeader("Content-Type", "text/html");
						context.response().end(ar.result());
					} else {
						context.fail(ar.cause());
					}
				});
			} else {
				context.fail(reply.cause());
			}
		});
	}

//	# Apple
//
//	涓�涓紭绉�鐨勮嫻鏋滐紒
//
//	![An apple]
//	(https://upload.wikimedia.org/wikipedia/commons/thumb/1/15/Red_Apple.jpg/265px-Red_Apple.jpg)
//
//	_from [https://upload.wikimedia.org/wikipedia/commons/thumb/1/15/Red_Apple.jpg/265px-Red_Apple.jpg]
	private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n";

	private void pageRenderingHandler(RoutingContext context) {
		String requestPage = context.request().getParam("page");
		dbService.fetchPage(requestPage, reply -> {
			if (reply.succeeded()) {
				JsonObject payLoad = (JsonObject) reply.result();
				boolean found = payLoad.getBoolean("found");
				String rawContent = payLoad.getString("rawContent", EMPTY_PAGE_MARKDOWN);
				context.put("title", requestPage);
				context.put("id", payLoad.getInteger("id", -1));
				context.put("newPage", found ? "no" : "yes");
				context.put("rawContent", rawContent);
				context.put("content", Processor.process(rawContent));
				context.put("timestamp", new Date().toString());
				context.put("context", context.data());
				templateEngine.render(context.data(), "/templates/page.ftl", ar -> {
					if (ar.succeeded()) {
						context.response().putHeader("Content-Type", "text/html");
						context.response().end(ar.result());
					} else {
						context.fail(ar.cause());
					}
				});
			} else {
				context.fail(reply.cause());
			}
		});
	}

	private void pageUpdateHandler(RoutingContext context) {
		String title = context.request().getParam("title");
		Integer id = Integer.valueOf(context.request().getParam("id"));
		String markdown = context.request().getParam("markdown");
		
		Handler<AsyncResult<Void>>  handler = reply -> {
	        if (reply.succeeded()) {
	            context.response().setStatusCode(303);
	            context.response().putHeader("Location", "/wiki/" + title);
	            context.response().end();
	        } else {
	            context.fail(reply.cause());
	        }
	    };
		

		if ("yes".equals(context.request().getParam("newPage"))) {
			dbService.createPage(title, markdown,handler);
		} else {
			 dbService.savePage(id,markdown, handler);
		}
	
	}

	private void pageCreateHandler(RoutingContext context) {
		String pageName = context.request().getParam("name");
		String location = "/wiki/" + pageName;
		if (pageName == null || pageName.isEmpty()) {
			location = "/";
		}
		context.response().setStatusCode(303);
		context.response().putHeader("Location", location);
		context.response().end();
	}

	private void pageDeletionHandler(RoutingContext context) {
		Integer id = Integer.valueOf(context.request().getParam("id"));
		
		dbService.deletePage(id, reply -> {
			if (reply.succeeded()) {
				context.response().setStatusCode(303);
				context.response().putHeader("Location", "/");
				context.response().end();
			} else {
				context.fail(reply.cause());
			}
		});
	}

}
