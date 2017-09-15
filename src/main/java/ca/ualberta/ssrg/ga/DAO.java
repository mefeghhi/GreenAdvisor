package ca.ualberta.ssrg.ga;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class DAO {
	private static final String dbFileName = "experiment.db";

	public static void setup() throws ClassNotFoundException, SQLException {
		String query =
				"CREATE TABLE IF NOT EXISTS runs (" +
						"id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
						"key TEXT NOT NULL," +
						"sc_log TEXT NOT NULL," +
						"ep_log TEXT NOT NULL" +
				");";
		executeUpdate(query);
	}

	public static void insertRun(String key, String scLog, String epLog) throws ClassNotFoundException, SQLException {
		String query = "INSERT INTO runs(key, sc_log, ep_log) VALUES('" + key + "', '" + scLog + "', '" + epLog + "');";
		executeUpdate(query);
	}
	
	private static void executeUpdate(String query) throws ClassNotFoundException, SQLException {
		Class.forName("org.sqlite.JDBC");
		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFileName);
		Statement statement = connection.createStatement();
		statement.executeUpdate(query);
		statement.close();
		connection.close();
	}
	public static ArrayList<JSONObject> findAll() throws ClassNotFoundException, SQLException, ParseException {
		ArrayList<JSONObject> result = new ArrayList<JSONObject>();
		Class.forName("org.sqlite.JDBC");
		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFileName);
		Statement statement = connection.createStatement();	
		ResultSet qResult = statement.executeQuery("SELECT * FROM runs");
		while(qResult.next()) {
			JSONObject run = new JSONObject(); 
			String keyString = qResult.getString("key");
			run.put("key", (JSONObject)(new JSONParser().parse(keyString)));
			run.put("sc_log", (JSONObject)(new JSONParser().parse(qResult.getString("sc_log"))));
			run.put("ep_log", (JSONObject)(new JSONParser().parse(qResult.getString("ep_log"))));
			result.add(run);
		}
		statement.close();
		connection.close();
		return result;
	}
	
	public static ArrayList<JSONObject> findRuns(String projectId, String versionId, boolean instrumentationEnabled, String wantedStraceMode) throws ClassNotFoundException, SQLException, ParseException {
		ArrayList<JSONObject> allRuns = findAll();
		ArrayList<JSONObject> filtered = new ArrayList<JSONObject>();
		String wantedInstrumentationMode = instrumentationEnabled? "enable" : "disable";
		for (JSONObject run : allRuns) {
			String projectKey = (String) ((JSONObject) run.get("key")).get("project");
			String versionKey = (String) ((JSONObject) run.get("key")).get("version");
			String instrumentation = (String) ((JSONObject) run.get("key")).get("instrumentation");
			String straceMode = (String) ((JSONObject) run.get("key")).get("strace_mode");
			if (projectKey.equals(projectId) && versionKey.equals(versionId) && instrumentation.equals(wantedInstrumentationMode) && straceMode.equals(wantedStraceMode)) {
				filtered.add(run);
			}
		}
		return filtered;
	}
	
	public static void main(String[] args) throws ClassNotFoundException, SQLException, ParseException {
	}
}
