package cn.compar.demo.vertwiki.database;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.serviceproxy.ServiceBinder;

public class WikiDatabaseVerticle extends AbstractVerticle {
	public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
	public static final String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
	public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
	public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";
	public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

	
	@Override
	public void start(Promise<Void> promise) throws Exception {
		HashMap<SqlQuery, String> sqlQueries = loadSqlQueries();
		JDBCClient dbClient = JDBCClient.createShared(vertx, new JsonObject()
				.put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
				.put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
				.put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)));
		
		WikiDatabaseService.create(dbClient, sqlQueries,ready->{
			if(ready.succeeded()) {
				ServiceBinder binder = new ServiceBinder(vertx);
				binder.setAddress(CONFIG_WIKIDB_QUEUE)    //binder.setAddress(config().getString(CONFIG_WIKIDB_QUEUE,"wikidb.queue"))
						.register(WikiDatabaseService.class, ready.result());
				promise.complete();
			}else {
				promise.fail(ready.cause());
			}
		});
		

	}


	private HashMap<SqlQuery, String> loadSqlQueries() throws IOException {
		String queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE);
		InputStream queriesInputStream;
		if (queriesFile != null) {
			queriesInputStream = new FileInputStream(queriesFile);
		} else {
			queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
		}
		Properties queriesProps = new Properties();
		queriesProps.load(queriesInputStream);
		queriesInputStream.close();
		HashMap<SqlQuery, String> sqlQueries = new HashMap<>();
		sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
		sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
		sqlQueries.put(SqlQuery.GET_PAGE, queriesProps.getProperty("get-page"));
		sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
		sqlQueries.put(SqlQuery.SAVE_PAGE, queriesProps.getProperty("save-page"));
		sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
		sqlQueries.put(SqlQuery.ALL_PAGES_DATA, queriesProps.getProperty("all-pages-data"));
		sqlQueries.put(SqlQuery.GET_PAGE_BY_ID, queriesProps.getProperty("get-page-by-id"));
		return sqlQueries;
	}
	
	
}
