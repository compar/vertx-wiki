package cn.compar.demo.vertwiki.http;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rjeschke.txtmark.Processor;

import cn.compar.demo.vertwiki.database.WikiDatabaseService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.shiro.ShiroAuth;
import io.vertx.ext.auth.shiro.ShiroAuthOptions;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.FormLoginHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.RedirectAuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;

public class HttpServerVerticle extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);
	public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
	public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	private FreeMarkerTemplateEngine templateEngine;

	private WikiDatabaseService dbService;
	private WebClient webClient;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
		dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);
		webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setUserAgent("vert-x3"));

		AuthProvider auth = ShiroAuth.create(vertx, new ShiroAuthOptions().setType(ShiroAuthRealmType.PROPERTIES)
				.setConfig(new JsonObject().put("properties_path", "classpath:wiki-users.properties")));

		HttpServer server = vertx.createHttpServer(new HttpServerOptions().setSsl(true)
				.setKeyStoreOptions(new JksOptions().setPath("server-keystore.jks").setPassword("secret")));
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());
		router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)).setAuthProvider(auth));
		AuthHandler authHandler = RedirectAuthHandler.create(auth, "/login");
		router.route("/").handler(authHandler);
		router.route("/wiki/*").handler(authHandler);
		router.route("/action/*").handler(authHandler);

		router.get("/").handler(this::indexHandler);
		router.get("/wiki/:page").handler(this::pageRenderingHandler);
		router.post("/action/save").handler(this::pageUpdateHandler);
		router.post("/action/create").handler(this::pageCreateHandler);
		router.post("/action/delete").handler(this::pageDeletionHandler);
		router.get("/action/backup").handler(this::backupHandler);

		router.get("/login").handler(this::loginHandler);
		router.post("/login-auth").handler(FormLoginHandler.create(auth));
		router.get("/logout").handler(context -> {
			context.clearUser();
			context.response().setStatusCode(302).putHeader("Location", "/").end();
		});

		templateEngine = FreeMarkerTemplateEngine.create(vertx);

		Router apiRouter = Router.router(vertx);
		JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
				.setKeyStore(new KeyStoreOptions().setPath("keystore.jceks").setType("jceks").setPassword("secret")));
		apiRouter.route().handler(JWTAuthHandler.create(jwtAuth, "/api/token"));
		apiRouter.get("/token").handler(context -> {
			JsonObject creds = new JsonObject().put("username", context.request().getHeader("login")).put("password",
					context.request().getHeader("password"));
			auth.authenticate(creds, authResult -> {
				if (authResult.succeeded()) {
					User user = authResult.result();
					user.isAuthorized("create", canCreate -> {
						user.isAuthorized("delete", canDelete -> {
							user.isAuthorized("update", canUpdate -> {
								String token = jwtAuth.generateToken(
										new JsonObject().put("username", context.request().getHeader("login"))
												.put("canCreate", canCreate.succeeded() && canCreate.result())
												.put("canDelete", canDelete.succeeded() && canDelete.result())
												.put("canUpdate", canUpdate.succeeded() && canUpdate.result()),
										new JWTOptions().setSubject("Wiki API").setIssuer("Vert.x"));
								context.response().putHeader("Content-Type", "text/plain").end(token);
							});
						});
					});
				} else {
					context.fail(401);
				}
			});
		});

		apiRouter.get("/pages").handler(this::apiRoot);
		apiRouter.get("/pages/:id").handler(this::apiGetPage);
		apiRouter.post().handler(BodyHandler.create());
		apiRouter.post("/pages").handler(this::apiCreatePage);
		apiRouter.put().handler(BodyHandler.create());
		apiRouter.put("/pages/:id").handler(this::apiUpdatePage);
		apiRouter.delete("/pages/:id").handler(this::apiDeletePage);
		router.mountSubRouter("/api", apiRouter);

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

	private void apiRoot(RoutingContext context) {
		dbService.fetchAllPagesData(reply -> {
			JsonObject response = new JsonObject();
			if (reply.succeeded()) {
				List<JsonObject> pages = reply.result().stream()
						.map(obj -> new JsonObject().put("id", obj.getInteger("ID")).put("name", obj.getString("NAME")))
						.collect(Collectors.toList());
				response.put("success", true).put("pages", pages);
				context.response().setStatusCode(200);
				context.response().putHeader("Content-Type", "application/json");
				context.response().end(response.encode());
			} else {
				response.put("success", false).put("error", reply.cause().getMessage());
				context.response().setStatusCode(500);
				context.response().putHeader("Content-Type", "application/json");
				context.response().end(response.encode());
			}
		});
	}

	private void apiGetPage(RoutingContext context) {
		int id = Integer.valueOf(context.request().getParam("id"));
		dbService.fetchPageById(id, reply -> {
			JsonObject response = new JsonObject();
			if (reply.succeeded()) {
				JsonObject dbObject = reply.result();
				if (dbObject.getBoolean("found")) {
					JsonObject payload = new JsonObject().put("name", dbObject.getString("name"))
							.put("id", dbObject.getInteger("id")).put("markdown", dbObject.getString("content"))
							.put("html", Processor.process(dbObject.getString("content")));
					response.put("success", true).put("page", payload);
					context.response().setStatusCode(200);
				} else {
					context.response().setStatusCode(404);
					response.put("success", false).put("error", "There is no page with ID " + id);
				}
			} else {
				response.put("success", false).put("error", reply.cause().getMessage());
				context.response().setStatusCode(500);
			}
			context.response().putHeader("Content-Type", "application/json");
			context.response().end(response.encode());
		});
	}

	private void apiCreatePage(RoutingContext context) {
		if (context.user().principal().getBoolean("canCreate", false)) {
			JsonObject page = context.getBodyAsJson();
			if (!validateJsonPageDocument(context, page, "name", "markdown")) {
				return;
			}
			dbService.createPage(page.getString("name"), page.getString("markdown"), reply -> {
				if (reply.succeeded()) {
					context.response().setStatusCode(201);
					context.response().putHeader("Content-Type", "application/json");
					context.response().end(new JsonObject().put("success", true).encode());
				} else {
					context.response().setStatusCode(500);
					context.response().putHeader("Content-Type", "application/json");
					context.response().end(
							new JsonObject().put("success", false).put("error", reply.cause().getMessage()).encode());
				}
			});
		} else {
			context.fail(401);
		}
	}

	private boolean validateJsonPageDocument(RoutingContext context, JsonObject page, String... expectedKeys) {
		if (!Arrays.stream(expectedKeys).allMatch(page::containsKey)) {
			LOGGER.error("Bad page creation JSON payload: " + page.encodePrettily() + " from "
					+ context.request().remoteAddress());
			context.response().setStatusCode(400);
			context.response().putHeader("Content-Type", "application/json");
			context.response().end(new JsonObject().put("success", false).put("error", "Bad request payload").encode());
			return false;
		}
		return true;
	}

	private void apiUpdatePage(RoutingContext context) {
		if (context.user().principal().getBoolean("canUpdate", false)) {
			int id = Integer.valueOf(context.request().getParam("id"));
			JsonObject page = context.getBodyAsJson();
			if (!validateJsonPageDocument(context, page, "markdown")) {
				return;
			}
			dbService.savePage(id, page.getString("markdown"), reply -> {
				handleSimpleDbReply(context, reply);
			});
		} else {
			context.fail(401);
		}
	}

	private void handleSimpleDbReply(RoutingContext context, AsyncResult<Void> reply) {
		if (reply.succeeded()) {
			context.response().setStatusCode(200);
			context.response().putHeader("Content-Type", "application/json");
			context.response().end(new JsonObject().put("success", true).encode());
		} else {
			context.response().setStatusCode(500);
			context.response().putHeader("Content-Type", "application/json");
			context.response()
					.end(new JsonObject().put("success", false).put("error", reply.cause().getMessage()).encode());
		}
	}

	private void apiDeletePage(RoutingContext context) {
		if (context.user().principal().getBoolean("canDelete", false)) {
			int id = Integer.valueOf(context.request().getParam("id"));
			dbService.deletePage(id, reply -> {
				handleSimpleDbReply(context, reply);
			});
		} else {
			context.fail(401);
		}
	}

	private void indexHandler(RoutingContext context) {
		context.user().isAuthorized("create", res -> {
			boolean canCreatePage = res.succeeded() && res.result();
			dbService.fetchAllPages(reply -> {
				if (reply.succeeded()) {
					context.put("title", "Wiki home");
					context.put("pages", reply.result().getList());
					context.put("canCreatePage", canCreatePage);
					context.put("username", context.user().principal().getString("username"));
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
		});

	}

//	# Apple
//
//一个优秀的苹果！
//
//	![An apple]
//	(https://upload.wikimedia.org/wikipedia/commons/thumb/1/15/Red_Apple.jpg/265px-Red_Apple.jpg)
//
//	_from [https://upload.wikimedia.org/wikipedia/commons/thumb/1/15/Red_Apple.jpg/265px-Red_Apple.jpg]
	private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n";

	private void pageRenderingHandler(RoutingContext context) {
		context.user().isAuthorized("update", updateResponse -> {
			boolean canSavePage = updateResponse.succeeded() && updateResponse.result();
			context.user().isAuthorized("delete", deleteResponse -> {
				boolean canDeletePage = deleteResponse.succeeded() && deleteResponse.result();
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
						context.put("username", context.user().principal().getString("username"));
						context.put("canSavePage", canSavePage);
						context.put("canDeletePage", canDeletePage);

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
			});
		});
	}

	private void pageUpdateHandler(RoutingContext context) {
		boolean pageCreation = "yes".equals(context.request().getParam("newPage"));
		context.user().isAuthorized(pageCreation ? "create" : "update", res -> {
			if (res.succeeded() && res.result()) {
				String title = context.request().getParam("title");
				Integer id = Integer.valueOf(context.request().getParam("id"));
				String markdown = context.request().getParam("markdown");

				Handler<AsyncResult<Void>> handler = reply -> {
					if (reply.succeeded()) {
						context.response().setStatusCode(303);
						context.response().putHeader("Location", "/wiki/" + title);
						context.response().end();
					} else {
						context.fail(reply.cause());
					}
				};

				if (pageCreation) {
					dbService.createPage(title, markdown, handler);
				} else {
					dbService.savePage(id, markdown, handler);
				}
			} else {
				context.response().setStatusCode(403).end();
			}
		});
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
		context.user().isAuthorized("delete", res -> {
			if (res.succeeded() && res.result()) {
				dbService.deletePage(Integer.valueOf(context.request().getParam("id")), reply -> {
					if (reply.succeeded()) {
						context.response().setStatusCode(303);
						context.response().putHeader("Location", "/");
						context.response().end();
					} else {
						context.fail(reply.cause());
					}
				});
			} else {
				context.response().setStatusCode(403).end();
			}
		});

	}

	private void backupHandler(RoutingContext context) {
		context.user().isAuthorized("role:writer", res -> {
			if (res.succeeded() && res.result()) {
				dbService.fetchAllPagesData(reply -> {
					if (reply.succeeded()) {
						JsonObject filesObject = new JsonObject();
						JsonObject gistPayload = new JsonObject().put("files", filesObject)
								.put("description", "A wiki backup").put("public", true);
						reply.result().forEach(page -> {
							JsonObject fileObject = new JsonObject();
							filesObject.put(page.getString("NAME"), fileObject);
							fileObject.put("content", page.getString("CONTENT"));
						});
						webClient.post(443, "api.github.com", "/gists")
								.putHeader("Accept", "application/vnd.github.v3+json")
								.putHeader("Content-Type", "application/json").as(BodyCodec.jsonObject())
								.sendJsonObject(gistPayload, ar -> {
									if (ar.succeeded()) {
										HttpResponse<JsonObject> response = ar.result();
										if (response.statusCode() == 201) {
											context.put("backup_gist_url", response.body().getString("html_url"));
											indexHandler(context);
										} else {
											StringBuilder message = new StringBuilder()
													.append("Could not backup the wiki: ")
													.append(response.statusMessage());
											JsonObject body = response.body();
											if (body != null) {
												message.append(System.getProperty("line.separator"))
														.append(body.encodePrettily());
											}
											LOGGER.error(message.toString());
											context.fail(502);
										}
									} else {
										Throwable err = ar.cause();
										LOGGER.error("HTTP Client error", err);
										context.fail(err);
									}
								});
					} else {
						context.fail(reply.cause());
					}
				});
			} else {
				context.response().setStatusCode(403).end();
			}
		});

	}

	private void loginHandler(RoutingContext context) {
		context.put("title", "Login");
		templateEngine.render(context.data(), "/templates/login.ftl", ar -> {
			if (ar.succeeded()) {
				context.response().putHeader("Content-Type", "text/html");
				context.response().end(ar.result());
			} else {
				context.fail(ar.cause());
			}
		});
	}
}
