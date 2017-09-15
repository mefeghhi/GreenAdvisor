package ca.ualberta.ssrg.ga;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.analysis.integration.RombergIntegrator;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.util.MathArrays;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.hamcrest.core.DescribedAs;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;



public class GreenAdvisor {
	public static final String COUNTS_STRACE_MODE = "counts";
	public static final String TIMESTAMPS_STRACE_MODE = "timestamps";
	
	public static final String BEFORE_VERSION = "before";
	public static final String AFTER_VERSION = "after";
	
	Configuration config = null;

	public GreenAdvisor() throws IOException, ParseException, NoHeadException, GitAPIException, ParserConfigurationException, SAXException {
		config = new Configuration();
	}
	
	public void startTestCycles(boolean instrumentationEnabled, String strace_mode, String targetVersion) throws IOException, ParseException, ClassNotFoundException, SQLException, NoHeadException, GitAPIException, java.text.ParseException, RuntimeException, InterruptedException{
		DeviceCommunicator dcom = new DeviceCommunicator(config.adbPath);

		String toBeInstalledApp = config.versions.get(targetVersion).get("targetApk");
		if (instrumentationEnabled) {
			toBeInstalledApp = config.versions.get(targetVersion).get("instrumentedApk");
		}

		prepareStraceScripts(strace_mode);
//		dcom.uploadFile(config.straceDirPathOnPC + "script.sh", config.workDirPathOnPhone);
		dcom.uploadFile(config.straceDirPathOnPC + "strace", config.workDirPathOnPhone);
		dcom.uploadFile(config.straceDirPathOnPC + "strc.sh", config.workDirPathOnPhone);

		int runCycle = 1;
		while(runCycle <= config.numOfRunsPerVersion) {
			System.out.print("Removing old logs from this pc...");
			FileUtility.createOrClearDir(new File(config.logDirOnPC));
			System.out.println("[OK]");

			dcom.removeFile(config.scLogPathOnPhone);
			dcom.removeFile(config.epLogPathOnPhone);
			
			dcom.uninstallPackage(config.versions.get(targetVersion).get("targetPackageName"));

			System.out.println("Running Tests: Cycle " + runCycle + " of " + config.numOfRunsPerVersion);
			dcom.installApk(toBeInstalledApp);

			dcom.startStraceProcessOnPhone(config.workDirPathOnPhone + "strc.sh", config.versions.get(targetVersion).get("targetPackageName"));

			dcom.uploadFile(config.testsDir + "/" + config.targetTest, config.workDirPathOnPhone);
			dcom.runTargetTest(config.workDirPathOnPhone + "/" + config.targetTest);
			
			if (instrumentationEnabled) { 
				dcom.startLoggerActivity(config.versions.get(targetVersion).get("targetPackageName"), "_Logger");
			}
//			Thread.sleep(15000);
			boolean ok = false;
			JSONObject scJsonLog = new JSONObject(), epJsonLog = new JSONObject();
			if (instrumentationEnabled) {
				for(int i = 0; i < 10; i++) {
					if (!dcom.downloadFile(config.epLogPathOnPhone, config.logDirOnPC))
						Thread.sleep(5000);
					else {
						ok = true;
						break;
					}
				}
				if (!ok) continue;
				System.out.print("Reading execution path log...");
				epJsonLog = readEPLog();
				System.out.println("[OK]");
			}
			dcom.closeApp(config.versions.get(targetVersion).get("targetPackageName"));
			
			ok = false;
			for (int i = 0; i < 10; i++) {
				if (!dcom.downloadFile(config.scLogPathOnPhone, config.logDirOnPC))
					Thread.sleep(5000);
				else {
					ok = true;
					break;
				}
			} 
			if (!ok) continue;
			System.out.print("Parsing system call log...");
			if (strace_mode == COUNTS_STRACE_MODE) {
				scJsonLog = parseStraceOutput_OLD();					
			} else if (strace_mode == TIMESTAMPS_STRACE_MODE){
				scJsonLog = parseStraceOutput();
			}
			System.out.println("[OK]");


			String scJsonLogString = scJsonLog.toJSONString();
			String epJsonLogString = epJsonLog.toJSONString();
			DAO.setup();
			JSONObject key = new JSONObject();
			if (strace_mode == COUNTS_STRACE_MODE)
				key.put("strace_mode", "counts");
			else
				key.put("strace_mode", "timestamps");
			if (instrumentationEnabled)
				key.put("instrumentation", "enable");
			else
				key.put("instrumentation", "disable");
			key.put("project", config.versions.get(targetVersion).get("projectId"));
			key.put("version", config.versions.get(targetVersion).get("versionId"));
			key.put("time", new Date().toString());
			DAO.insertRun(key.toJSONString(), scJsonLogString, epJsonLogString);			
			System.out.println("Cycle " + runCycle + " of " + config.numOfRunsPerVersion + " completed.");
			runCycle++;
		}
	}
	
	// If null is given as touchedFunctions all functions are instrumented
	public void instrumentTouchedFunctionsInAVersion(String targetVersion, HashSet<String> touchedFunctions) throws IOException, SAXException, ParserConfigurationException, TransformerException {
		System.out.println("Instrumenting target project: " + config.versions.get(targetVersion).get("projectDir"));
		FileUtils.copyDirectory(new File(config.versions.get(targetVersion).get("projectDir")), new File(config.versions.get(targetVersion).get("projectDir") + "_Instrumented"));
		JavaInstrumentor ji = new JavaInstrumentor(touchedFunctions);
		ji.instrumentJavaFilesRecursively(new File (config.versions.get(targetVersion).get("projectDir") + "_Instrumented" + "/" + config.versions.get(targetVersion).get("instrumentationTarget")), config.versions.get(targetVersion).get("targetPackageName") + "._Logger");
		ji.instrumentAndStoreLogger("_Logger.java", config.versions.get(targetVersion).get("targetPackageName"), config.versions.get(targetVersion).get("projectDir") + "_Instrumented" + "/" + config.versions.get(targetVersion).get("instrumentationTarget"));
		System.out.println("Number of methods instrumented: " + ji.instrumented_count);
		
		File xmlFile = new File(config.versions.get(targetVersion).get("projectDir") + "_Instrumented" + "/" + config.versions.get(targetVersion).get("manifest"));
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(xmlFile);

		Element activity = doc.createElement("activity");
		activity.setAttribute("android:name", config.versions.get(targetVersion).get("targetPackageName") + "." + "_Logger");
		activity.setAttribute("android:label", "LOGGER ACTIVITY");
		
		Node application = doc.getElementsByTagName("application").item(0);
		application.appendChild(activity);
		
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(new File(config.versions.get(targetVersion).get("projectDir") + "_Instrumented" + "/" + config.versions.get(targetVersion).get("manifest")));
		transformer.transform(source, result);
		
		System.out.println("Created a new instrumented project: " + config.versions.get(targetVersion).get("projectDir") + "_Instrumented ");
	}
	
	public void findTouchedFunctionsAndInstrumentBothVersions() throws IOException, SAXException, ParserConfigurationException, TransformerException {
		String beforeInstrumentationTarget = config.versions.get(GreenAdvisor.BEFORE_VERSION).get("projectDir")  + "/" + config.versions.get(GreenAdvisor.BEFORE_VERSION).get("instrumentationTarget");
		String afterInstrumentationTarget = config.versions.get(GreenAdvisor.AFTER_VERSION).get("projectDir")  + "/" + config.versions.get(GreenAdvisor.AFTER_VERSION).get("instrumentationTarget");
		JavaInstrumentor ji = new JavaInstrumentor();
		HashMap<String, String> beforeFunctionBodyMap = ji.getFunctionMap(new File(beforeInstrumentationTarget));
		HashMap<String, String> afterFunctionBodyMap = ji.getFunctionMap(new File(afterInstrumentationTarget));
		HashSet<String> touchedKeys = new HashSet<String>();
		touchedKeys.addAll(beforeFunctionBodyMap.keySet());
		touchedKeys.addAll(afterFunctionBodyMap.keySet());
		System.out.println("Size of All Keys:" + touchedKeys.size());
		HashSet<String> toBeRemoved = new HashSet<String>();
		for (String key : touchedKeys) {
			if ((beforeFunctionBodyMap.containsKey(key) && afterFunctionBodyMap.containsKey(key))) {
				String beforeBody = beforeFunctionBodyMap.get(key);
				String afterBody = afterFunctionBodyMap.get(key);
				if ((beforeBody == null && afterBody == null) || 
						(beforeBody != null && beforeBody.equals(afterBody)))
					toBeRemoved.add(key);
			}
		}
		touchedKeys.removeAll(toBeRemoved);
		System.out.println("Size of Touched Keys:" + touchedKeys.size());
		instrumentTouchedFunctionsInAVersion(GreenAdvisor.BEFORE_VERSION, touchedKeys);
		instrumentTouchedFunctionsInAVersion(GreenAdvisor.AFTER_VERSION, touchedKeys);
	}
	
	private JSONObject readEPLog() throws IOException {
		JSONObject tempJson = new JSONObject();
		readEPLogIntoJson(new File(config.epLogPathOnPC), tempJson);
		JSONObject result = new JSONObject();
		if (!tempJson.isEmpty()) {
			Long absoluteRef = Long.parseLong(((String) ((JSONObject) tempJson.get("ref")).get("absolute"))) * 1000000;
			Long relativeRef = Long.parseLong(((String) ((JSONObject) tempJson.get("ref")).get("relative")));
			JSONArray events = (JSONArray) tempJson.get("events");
			for (int i = 0; i < events.size(); i++) {
				JSONObject event = (JSONObject) events.get(i);
				String timestamp = (String) event.get("timestamp");
				Long diff = (Long.parseLong(timestamp) - relativeRef);
				timestamp = String.valueOf(absoluteRef + diff);
				event.put("timestamp", timestamp);
			}
			tempJson.remove("ref");
		}
		return tempJson;
	}
	private void readEPLogIntoJson(File f, JSONObject json) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(f));
		if (!json.containsKey("events")) {
			JSONArray events = new JSONArray();
			json.put("events", events);
		}
		String line = "";
		while ((line = br.readLine()) != null) {
			JSONObject callEvent = new JSONObject();
			String[] splits = line.split(":");
			String timestamp = splits[0];
			if (timestamp.contains("(")) {
				String absoluteTimestamp = timestamp.substring(1, timestamp.indexOf(")"));
				timestamp = timestamp.substring(timestamp.indexOf(")") + 1);
				JSONObject newRef = new JSONObject();
				newRef.put("absolute", absoluteTimestamp);
				newRef.put("relative", timestamp);
				JSONObject savedRefJson = (JSONObject) json.get("ref");
				if (savedRefJson == null) {
					json.put("ref", newRef);
				} else {
					if (Long.parseLong(timestamp) < Long.parseLong((String) savedRefJson.get("relative"))) {
						json.put("ref", newRef);
					}
				}
			}
			callEvent.put("method_name", splits[1] + ":" + splits[2]);
			callEvent.put("type", splits[3]);
			callEvent.put("timestamp", timestamp);
			((JSONArray) json.get("events")).add(callEvent);
		}
	}
	private void prepareStraceScripts(String mode) throws IOException {
		mkFile(config.straceDirPathOnPC + "script.sh", "sh $1/strc.sh $2 &");
		String content = "while ! ps  | grep $1 ; do :; done;\n" +
				"ps | grep $1 | while read a b c; do\n"; 
		if (mode == COUNTS_STRACE_MODE) {
			content += "strace -f -c -p $b -o /data/local/trc.txt\n";
		} else {
			content += "strace -f -tt -p $b -o /data/local/trc.txt\n";
		}
		content += "done;";
		mkFile(config.straceDirPathOnPC + "strc.sh", content);
	}

	private void mkFile(String path, String content) throws IOException {
		FileWriter fw = new FileWriter(new File(path));
		fw.write(content);
		fw.close();
	}
	private static String fixedLengthString(String string, int length) {
		return String.format("%1$"+length+ "s", string);
	}

	private static String toAbsoluteNanoseconds(String timestamp) throws java.text.ParseException {
		String cut1 = timestamp.substring(0, timestamp.length() - 3);
		String cut2 = timestamp.substring(timestamp.length() - 3);
		SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat completeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
		return String.valueOf(completeFormat.parse(dayFormat.format(new Date()) + " " + cut1).getTime()) + cut2 + "000";
	}
	// Parser for strc.sh with below options:
	// strace -f -tt -p $b -o /data/local/trc.txt
	private JSONObject parseStraceOutput() throws FileNotFoundException, IOException, java.text.ParseException {
		String straceFile = config.scLogPathOnPC;
		Map<String, ArrayList<String>> syscall_time_map = new HashMap<String, ArrayList<String>>();
		try(BufferedReader br = new BufferedReader(new FileReader(straceFile))) {
			for(String line; (line = br.readLine()) != null; ) {
				String[] line_parts = line.split("\\s+");
				if (line_parts.length > 3 && !line_parts[2].startsWith("<...") && !line_parts[2].startsWith("+++") && !line_parts[2].startsWith("---")) {
					String syscall_key = line_parts[2].substring(0, line_parts[2].indexOf('('));
					String syscall_timestamp = toAbsoluteNanoseconds(line_parts[1]);
					if (syscall_time_map.containsKey(syscall_key)) {
						syscall_time_map.get(syscall_key).add(syscall_timestamp);
					} else {
						ArrayList<String> timestamp_list = new ArrayList<String>();
						timestamp_list.add(syscall_timestamp);
						syscall_time_map.put(syscall_key, timestamp_list);
					}
				}
			}
		}
		JSONObject myjson = new JSONObject();
		for (String syscall_key: syscall_time_map.keySet()) {
			JSONObject syscall_data = new JSONObject();
			syscall_data.put("count", syscall_time_map.get(syscall_key).size());
			JSONArray timestamps = new JSONArray();
			for (String timestamp: syscall_time_map.get(syscall_key)) {
				timestamps.add(timestamp);
			}
			syscall_data.put("invocation_timestamps", timestamps);
			myjson.put(syscall_key, syscall_data);
		} 
		return myjson;
	}

	// Parser for strc.sh with below options:
	// strace -f -c -p $b -o /data/local/trc.txt
	private JSONObject parseStraceOutput_OLD() throws FileNotFoundException, IOException {
		String straceFile = config.scLogPathOnPC;
		JSONObject callDataJSON = new JSONObject();
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(straceFile))));
		try{
			br.readLine();
			br.readLine();
			String syscallData = null;
			HashMap<String, Integer> sysCalls = new HashMap<String, Integer>();
			while((syscallData = br.readLine()) != null) {
				String[] tuple = syscallData.split("\\s+");
				if(!tuple[0].startsWith("Sys")) {
					if(!tuple[0].startsWith("%")) {
						if(!tuple[0].startsWith("100.00")) {
							if(!tuple[tuple.length-1].equals("total")) {
								if(!tuple[2].contains("-")) {
									sysCalls.put(tuple[tuple.length-1],Integer.parseInt(tuple[4]));
								}
							}
						}
					}
				}

				else if(tuple[tuple.length-1].equals("total")) {
					sysCalls.put(tuple[tuple.length-1],Integer.parseInt(tuple[2]));
				}

				else if(tuple[0].startsWith("100.00")) {
					sysCalls.put(tuple[tuple.length-1],Integer.parseInt(tuple[3]));
				}
			}
			br.close();
			for(String callName : sysCalls.keySet())
			{
				int noCalls = sysCalls.get(callName);
				JSONObject info = new JSONObject();
				info.put("count", noCalls);
				callDataJSON.put(callName, info);
			}
		} catch(Exception ex) {
			System.out.println("Error: ");
			ex.printStackTrace();
		}

		return 	callDataJSON;
	}
	// Calculates sd as a percentage of mean
	public void sdAsaPercentageOfMean() throws ClassNotFoundException, SQLException, ParseException {
		ArrayList<JSONObject> allRuns = DAO.findAll();
		HashMap<String, ArrayList<JSONObject>> runMap = new HashMap<>(); 
		for (JSONObject run : allRuns) {
			JSONObject key =  (JSONObject)run.get("key");
			String project_version = (String) key.get("project") + ":" + (String) key.get("version");
			ArrayList<JSONObject> runsOfThisVersion = new ArrayList<JSONObject>();
			if (runMap.containsKey(project_version)) {
				runsOfThisVersion = runMap.get(project_version);
			} else {
				runMap.put(project_version, runsOfThisVersion);
			}
			if (((String) key.get("instrumentation")).equals("disable")) {
				if (((String) key.get("strace_mode")).equals("counts")) {
//					if (runsOfThisVersion.size() < 5)
						runsOfThisVersion.add(run);				
				}
			}
		}
		for (String version : runMap.keySet()) {
			System.out.println("version: " + version);
			HashMap<String, ArrayList<Long>> syscall_map = new HashMap<>();
			ArrayList<JSONObject> runs = runMap.get(version);
			for (JSONObject run : runs) {
				JSONObject sc_log = (JSONObject) run.get("sc_log");
				Iterator<?> run_keys_it = sc_log.keySet().iterator();
				while(run_keys_it.hasNext()) {
				    String syscall_key = (String)run_keys_it.next();
				    JSONObject temp = (JSONObject) sc_log.get(syscall_key);
				    Long syscall_count = (Long) temp.get("count");
				    ArrayList<Long> syscall_counts = new ArrayList<>();
				    if (syscall_map.containsKey(syscall_key)) {
				    	syscall_counts = syscall_map.get(syscall_key);
				    } else {
				    	syscall_map.put(syscall_key, syscall_counts);
				    }
				    syscall_counts.add(syscall_count);
				}
			}
			for (Entry<String, ArrayList<Long>> entry : syscall_map.entrySet()) {
				while (entry.getValue().size() < runs.size()) {
					entry.getValue().add(new Long(0));
				}
				SummaryStatistics ss = new SummaryStatistics();
				for (Long i : entry.getValue()) {
					ss.addValue((double)i);
				}
//				System.out.println(entry.getKey() + "\t" + ss.getStandardDeviation() + "\t" + ss.getMean());
				System.out.println(entry.getKey() + "\t" + listToString(entry.getValue()));
			}
 		}		
	}
	
	// Compares system-call profile of COUNTS_STRACE_MODE against themselves while instrumentation was disabled
	public void blameSyscalls0() throws ClassNotFoundException, SQLException, ParseException {
		ArrayList<JSONObject> allRuns = DAO.findAll();
		HashMap<String, ArrayList<ArrayList<JSONObject>>> runMap = new HashMap<>(); 
		for (JSONObject run : allRuns) {
			JSONObject key =  (JSONObject)run.get("key");
			String project_version = (String) key.get("project") + ":" + (String) key.get("version");
			ArrayList<ArrayList<JSONObject>> runsOfThisVersion = new ArrayList<ArrayList<JSONObject>>();
			runsOfThisVersion.add(new ArrayList<JSONObject>());
			runsOfThisVersion.add(new ArrayList<JSONObject>());
			if (runMap.containsKey(project_version)) {
				runsOfThisVersion = runMap.get(project_version);
			} else {
				runMap.put(project_version, runsOfThisVersion);
			}
			if (((String) key.get("instrumentation")).equals("disable")) {
				if (((String) key.get("strace_mode")).equals("counts")) {
					if (runsOfThisVersion.get(0).size() < 5) {
						runsOfThisVersion.get(0).add(run);
					} else {
						runsOfThisVersion.get(1).add(run);
					}					
				}
			}
		}
		for (String version : runMap.keySet()) {
			System.out.println("version: " + version);
			PriorityQueue<JSONObject> unstableSyscalls = findSignificantlyChangedSyscalls(runMap.get(version).get(0), runMap.get(version).get(1));
			if (unstableSyscalls != null)
				printUnstableSyscalls(unstableSyscalls);
			System.out.println("---------------------------------------");
		}		
	}

	// Compares system-call profile of COUNTS_STRACE_MODE against TIMESTAMPS_STRACE_MODE while instrumentation was disabled
	public void blameSyscalls1() throws ClassNotFoundException, SQLException, ParseException {
		System.out.println("Compares system-call profile of COUNTS_STRACE_MODE against TIMESTAMPS_STRACE_MODE while instrumentation was disabled");
		ArrayList<JSONObject> allRuns = DAO.findAll();
		HashMap<String, ArrayList<ArrayList<JSONObject>>> runMap = new HashMap<>();  
		for (JSONObject run : allRuns) {
			JSONObject key =  (JSONObject)run.get("key");
			String project_version = (String) key.get("project") + ":" + (String) key.get("version");
			ArrayList<ArrayList<JSONObject>> runsOfThisVersion = new ArrayList<ArrayList<JSONObject>>();
			runsOfThisVersion.add(new ArrayList<JSONObject>());
			runsOfThisVersion.add(new ArrayList<JSONObject>());
			if (runMap.containsKey(project_version)) {
				runsOfThisVersion = runMap.get(project_version);
			} else {
				runMap.put(project_version, runsOfThisVersion);
			}
			if (((String) key.get("instrumentation")).equals("disable")) {
				if (((String) key.get("strace_mode")).equals("counts")) {
					runsOfThisVersion.get(0).add(run);
				} else if (((String) key.get("strace_mode")).equals("timestamps")) {
					runsOfThisVersion.get(1).add(run);
				}
			}
		}
		for (String version : runMap.keySet()) {
			System.out.println("version: " + version);
			PriorityQueue<JSONObject> unstableSyscalls = findSignificantlyChangedSyscalls(runMap.get(version).get(0), runMap.get(version).get(1));
			if (unstableSyscalls != null)
				printUnstableSyscalls(unstableSyscalls);
			System.out.println("---------------------------------------");
		}
	}
	// Compares system-call profile of instrumentation-disabled mode against instrumentation-enabled while in TIMESTAMPS_STRACE_MODE
	public void blameSyscalls2() throws ClassNotFoundException, SQLException, ParseException {
		System.out.println("Compares system-call profile of instrumentation-disabled mode against instrumentation-enabled while in TIMESTAMPS_STRACE_MODE");
		ArrayList<JSONObject> allRuns = DAO.findAll();
		HashMap<String, ArrayList<ArrayList<JSONObject>>> runMap = new HashMap<>();  
		for (JSONObject run : allRuns) {
			JSONObject key =  (JSONObject)run.get("key");
			String project_version = (String) key.get("project") + ":" + (String) key.get("version");
			ArrayList<ArrayList<JSONObject>> runsOfThisVersion = new ArrayList<ArrayList<JSONObject>>();
			runsOfThisVersion.add(new ArrayList<JSONObject>());
			runsOfThisVersion.add(new ArrayList<JSONObject>());
			if (runMap.containsKey(project_version)) {
				runsOfThisVersion = runMap.get(project_version);
			} else {
				runMap.put(project_version, runsOfThisVersion);
			}
			if (((String) key.get("strace_mode")).equals("timestamps")) {
				if (((String) key.get("instrumentation")).equals("disable")) {
					runsOfThisVersion.get(0).add(run);
				} else if (((String) key.get("instrumentation")).equals("enable")) {
					runsOfThisVersion.get(1).add(run);
				}
			}
		}
		for (String version : runMap.keySet()) {
			System.out.println("version: " + version);
			PriorityQueue<JSONObject> unstableSyscalls = findSignificantlyChangedSyscalls(runMap.get(version).get(0), runMap.get(version).get(1));
			if (unstableSyscalls != null)
				printUnstableSyscalls(unstableSyscalls);
			System.out.println("---------------------------------------");
		}		
	}
	private void printUnstableSyscalls(PriorityQueue<JSONObject> pq) {
		while(!pq.isEmpty()) {
			JSONObject top = pq.poll();
			System.out.println(fixedLengthString((String)top.get("name"), 16) + 
					" : [" + fixedLengthString(String.valueOf(top.get("pval")), 22) + "] : " +
					top.get("change") + " " + top.get("previousAvg") + " -> " + top.get("latestAvg") + 
					" : " + top.get("listA") + " -> " + top.get("listB"));
		}
	}
	private boolean bagOfWordsBlame(String body, String scName) {
		String scRegex = getBagOfWords().get(scName);
		if (scRegex == null)
			return false;
		String[] patterns = scRegex.split(";");
		return matches(body, patterns);
	}
	private HashMap<String, String> getBagOfWords() {
		HashMap<String,String> map = new HashMap<String,String>();
		map.put("access","((?s).*)new\\s+FileInputStream((?s).*);((?s).*)new\\s+FileOutputStream((?s).*);((?s).*)new\\s+InputStream((?s).*)");
		map.put("clone","((?s).*)new\\s+Thread((?s).*)");
		map.put("close","((?s).*)close\\(((?s).*)");
		map.put("fcntl64","((?s).*)open\\(((?s).*);((?s).*)getFD\\(\\)((?s).*)");
		map.put("fsync","((?s).*)sync\\(\\)((?s).*)");
		map.put("getdents64","((?s).*)read\\(((?s).*)");
		map.put("getsockname","((?s).*)getSocketLocalAddress\\(((?s).*);((?s).*)getsockname\\(((?s).*)");
		map.put("gettimeofday","((?s).*)currentTimeMillis\\(((?s).*)");
		map.put("lseek","((?s).*)seek\\(((?s).*)");
		map.put("mkdir","((?s).*)mkdir\\(\\)((?s).*)");
		map.put("nanosleep","((?s).*)sleep\\(((?s).*)");
		map.put("open","((?s).*)open\\(((?s).*)");
		map.put("pipe","((?s).*)new\\s+PipedInputStream\\(((?s).*);((?s).*)new\\s+PipedOutputStream\\(((?s).*)");
		map.put("poll","((?s).*)open\\(((?s).*)");
		map.put("read","((?s).*)read\\(((?s).*);((?s).*)readLine\\(\\)((?s).*)");
		map.put("recvfrom","((?s).*)read\\(((?s).*)");
		map.put("recvmsg","((?s).*)recvmsg\\(((?s).*);((?s).*)receive\\(((?s).*)");
		map.put("sendmsg","((?s).*)sendmsg\\(((?s).*);((?s).*)send\\(((?s).*)");
		map.put("sento","((?s).*)new\\s+DataOutputStream\\(((?s).*)");
		map.put("socket","((?s).*)new\\s+socket\\(((?s).*)");
		map.put("unlink","((?s).*)delete\\(((?s).*)");
		map.put("write","((?s).*)write\\(((?s).*);((?s).*)new\\s+BufferedWriter((?s).*);((?s).*)new\\s+PrintWriter((?s).*);((?s).*)\\.print\\(((?s).*);((?s).*)\\.println\\(((?s).*)");
		map.put("writev","((?s).*)write\\(((?s).*);((?s).*)new\\s+BufferedWriter((?s).*);((?s).*)new\\s+PrintWriter((?s).*);((?s).*)flush\\(\\)((?s).*)");
		return map;
	}
	public void blameMethods_OLD(boolean fullLog) throws ClassNotFoundException, SQLException, ParseException {
		ArrayList<JSONObject> allRuns = DAO.findAll();
		HashMap<String, ArrayList<JSONObject>> runs = askForRunsOfTwoVersions_OLD(allRuns);
		String[] versions = new String[2];
		int j = 0;
		for(Entry<String, ArrayList<JSONObject>> entry: runs.entrySet()) {
			versions[j] = entry.getKey();
			j++;
		}
		if (runs == null) {
			System.out.println("GreenAdvisor needs to be run on at least two versions of the project.");
			return;
		} else {
			System.out.println("----------------------------------------");
			PriorityQueue<JSONObject> unstableSyscalls = findSignificantlyChangedSyscalls(runs.get(versions[1]), runs.get(versions[0]));
			String diffString = "";
			while (!unstableSyscalls.isEmpty()) {
				JSONObject json = unstableSyscalls.poll();
				String scName = (String) json.get("name");
				String scPatterns = (String) json.get("patterns");
				String change = (String) json.get("change");
				double syscallPval = (double) json.get("pval");
				Double latestAvg = Math.floor((double)  json.get("latestAvg"));
				Double previousAvg = Math.floor((double)  json.get("previousAvg"));
				
				// KARAN's Code
				diffString += "<hr class=\"big\">";
				diffString += "<br><b><a name=\"" + scName + "\"><h3>" + scName + ":</h3>" + "</a></b><br>";
				String codeMatch = "";
				if (!scPatterns.equals("Empty")) {
					String[] patterns = scPatterns.split(";");
					String[] diffs = GitManager.diffVersions(config.versions.get(GreenAdvisor.AFTER_VERSION).get("projectDir"), versions[0], versions[1]);
					for (String diff: diffs) {
						int lastLine = 0, counter = 5, lineNo = 1, skipLines = 5;
						String header = "", codeFile = "";
						if (matches(diff, patterns)) {
							String[] lines = diff.split("\\n");
							for (String line: lines) {
								if (skipLines == 5) {
									header += line.subSequence(line.indexOf("src"), line.indexOf(".java") + 5) + "</b>\n";
									skipLines--;
									continue;
								}
								if (skipLines > 0) {
									skipLines--;
									header += "<b>&emsp;&emsp;&emsp;&emsp;"+line+"</b>\n";
									continue;
								}
								if (line.matches("\\+\\s+\\*(.*)") || line.trim().startsWith("\\+\\s+\\/+\\*(.*)")|| line.trim().startsWith("\\-\\s+\\*(.*)") || line.trim().startsWith("\\-\\s+\\/+\\*(.*)")) {
										lineNo++;
										continue;
								} else {
									if (matches(line, patterns)) {
										int dist = lineNo - lastLine;
										if(dist > 5) {
											if(dist > 10)
												codeFile += "\n\n&emsp;&emsp;&emsp;..\n&emsp;&emsp;&emsp;..\n\n";
											int startIndex = 5;
											if (dist < 10)
												startIndex = dist - 5;
											for(int i = startIndex - 1; i > 0; i--) {
												int ln = lineNo - i;
												codeFile += String.format("%-4d",ln).replaceAll(" ", "&nbsp;")  + "&nbsp;&nbsp;"+lines[lineNo-i+4].replace(" ", "&nbsp;").replace("</br>", "") + "\n";
											}
											lastLine = lineNo;
											codeFile += String.format("%-4d",lineNo).replaceAll(" ", "&nbsp;") +"<span style=\"background-color:#FFCC99;\">&nbsp;&nbsp;"+line.replace(" ", "&nbsp;").replace("</br>", "")+"</span>\n";
											String laterString = "";
											
											for(int i = 1; i < 6 && lineNo + i + 4 < lines.length - 1; i++)
											{
												int ln = lineNo + i;
												if(!matches(lines[lineNo+i+4],patterns))
													laterString+=String.format("%-4d",ln).replaceAll(" ", "&nbsp;")  + "&nbsp;&nbsp;"+lines[lineNo+i+4].replace(" ", "&nbsp;").replace("</br>", "")+"\n";
												else {
													laterString="";
													break;
												}
											}
											codeFile+=laterString;
										} else {
											for(int i = dist - 1; i > 0; i--) {  
												int ln = lineNo - i;
												codeFile += String.format("%-4d",ln).replaceAll(" ", "&nbsp;")  + "&nbsp;&nbsp;" + lines[lineNo-i+4].replace(" ", "&nbsp;").replace("</br>", "") + "\n";
											}
											lastLine = lineNo;
											codeFile += String.format("%-4d",lineNo).replaceAll(" ", "&nbsp;") + "<span style=\"background-color:#9AFF9A;\">&nbsp;&nbsp;" + line.replace(" ", "&nbsp;").replace("</br>", "") + "</span>\n";
											String laterString= "";
											
											for(int i = 1; i < 6 && lineNo + i + 4 < lines.length - 1; i++) {
												int ln = lineNo + i;
												if(!matches(lines[lineNo + i + 4], patterns))
													laterString += String.format("%-4d",ln).replaceAll(" ", "&nbsp;")  + "&nbsp;&nbsp;"+lines[lineNo+i+4].replace(" ", "&nbsp;").replace("\\n", "")+"\n";
												else {
													laterString = "";
													break;
												}
											}
											codeFile += laterString;	
										}
									}
								}
								lineNo++;
							}
						}
						if(!codeFile.isEmpty())
							codeMatch += header+codeFile;
					}
				}
				if(codeMatch.isEmpty())
					diffString += "Unable to locate the associated code<br>";
				else {
					System.out.println("Code Located!");
					diffString += codeMatch;
				}
				System.out.println("-------------------");
			}
			System.out.println(diffString.replaceAll("\n", "<br>"));
		}
	}
	private boolean matches(String target, String[] patterns) {
		if (target == null)
			return false;
		for(String pattern: patterns) {
			if (target.matches(pattern))
				return true;
		}
		return false;
	}
	public void blameMethodsKaran(HashSet<String> goods, HashSet<String> bads) throws ClassNotFoundException, SQLException, ParseException, FileNotFoundException {
		ArrayList<JSONObject> versionARuns = DAO.findRuns(config.versions.get(GreenAdvisor.BEFORE_VERSION).get("projectId"), config.versions.get(GreenAdvisor.BEFORE_VERSION).get("versionId"), true, GreenAdvisor.TIMESTAMPS_STRACE_MODE);
		ArrayList<JSONObject> versionBRuns = DAO.findRuns(config.versions.get(GreenAdvisor.AFTER_VERSION).get("projectId"), config.versions.get(GreenAdvisor.AFTER_VERSION).get("versionId"), true, GreenAdvisor.TIMESTAMPS_STRACE_MODE);
		if (versionARuns.isEmpty() || versionBRuns.isEmpty()) {
			System.out.println("GreenAdvisor did not found runs of the two versions in the database.");
			return;
		} else {
			// finding touched keys
			String beforeInstrumentationTarget = config.versions.get(GreenAdvisor.BEFORE_VERSION).get("projectDir")  + "/" + config.versions.get(GreenAdvisor.BEFORE_VERSION).get("instrumentationTarget");
			String afterInstrumentationTarget = config.versions.get(GreenAdvisor.AFTER_VERSION).get("projectDir")  + "/" + config.versions.get(GreenAdvisor.AFTER_VERSION).get("instrumentationTarget");
			JavaInstrumentor ji = new JavaInstrumentor();
			HashMap<String, String> beforeFunctionBodyMap = ji.getFunctionMap(new File(beforeInstrumentationTarget));
			HashMap<String, String> afterFunctionBodyMap = ji.getFunctionMap(new File(afterInstrumentationTarget));
			HashSet<String> touchedKeys = new HashSet<String>();
			touchedKeys.addAll(beforeFunctionBodyMap.keySet());
			touchedKeys.addAll(afterFunctionBodyMap.keySet());
			System.out.println("Size of All Keys:" + touchedKeys.size());
			HashSet<String> toBeRemoved = new HashSet<String>();
			for (String key : touchedKeys) {
				if ((beforeFunctionBodyMap.containsKey(key) && afterFunctionBodyMap.containsKey(key))) {
					String beforeBody = beforeFunctionBodyMap.get(key);
					String afterBody = afterFunctionBodyMap.get(key);
					if ((beforeBody == null && afterBody == null) || 
							(beforeBody != null && beforeBody.equals(afterBody)))
						toBeRemoved.add(key);
				}
			}
			touchedKeys.removeAll(toBeRemoved);
			
			System.out.println("----------------------------------------");
			int tp = 0, fp = 0, tn = 0, fn = 0;
			PriorityQueue<JSONObject> unstableSyscalls = findSignificantlyChangedSyscalls(versionARuns, versionBRuns);
			while (!unstableSyscalls.isEmpty()) {
				int ctp = 0, cfp = 0, ctn = 0, cfn = 0;
				JSONObject json = unstableSyscalls.poll();
				String scName = (String) json.get("name");
				String change = (String) json.get("change");
				double syscallPval = (double) json.get("pval");
				Double latestAvg = Math.floor((double)  json.get("latestAvg"));
				Double previousAvg = Math.floor((double)  json.get("previousAvg"));
				for (String method_name : afterFunctionBodyMap.keySet()) {
					String method_body = afterFunctionBodyMap.get(method_name);
					if (bagOfWordsBlame(method_body, scName)) {
						if (bads.contains(method_name))
							ctp++;
						else if (goods.contains(method_name))
							cfp++;
					}
				}
				tp += ctp;
				fp += cfp;
				tn += goods.size() - cfp;
				fn += bads.size() - ctp;
			}
			double accuracy = (double)(tp + tn) / (double)(tp + tn + fp + fn);
			double precision = (double)(tp) / (double)(tp + fp);
			double recall = (double)(tp) / (double)(tp + fn);
			double f1 = (double)(2 * precision * recall) / (double)(precision + recall);
			System.out.println("ACCURACY: " + accuracy);
			System.out.println("PRECISION: " + precision);
			System.out.println("RECALL: " + recall);
			System.out.println("F1: " + f1);
		}
	}
	public void blameMethods(boolean fullLog, HashSet<String> goods, HashSet<String> bads, boolean decide_random) throws ClassNotFoundException, SQLException, ParseException {
		ArrayList<JSONObject> versionARuns = DAO.findRuns(config.versions.get(GreenAdvisor.BEFORE_VERSION).get("projectId"), config.versions.get(GreenAdvisor.BEFORE_VERSION).get("versionId"), true, GreenAdvisor.TIMESTAMPS_STRACE_MODE);
		ArrayList<JSONObject> versionBRuns = DAO.findRuns(config.versions.get(GreenAdvisor.AFTER_VERSION).get("projectId"), config.versions.get(GreenAdvisor.AFTER_VERSION).get("versionId"), true, GreenAdvisor.TIMESTAMPS_STRACE_MODE);
		if (versionARuns.isEmpty() || versionBRuns.isEmpty()) {
			System.out.println("GreenAdvisor did not found runs of the two versions in the database.");
			return;
		} else {
			System.out.println("----------------------------------------");
			int tp = 0, fp = 0, tn = 0, fn = 0;
			PriorityQueue<JSONObject> unstableSyscalls = findSignificantlyChangedSyscalls(versionARuns, versionBRuns);
			HashSet<String> affectedCalls = findAffectedCalls();
			while (!unstableSyscalls.isEmpty()) {
				int ctp = 0, cfp = 0, ctn = 0, cfn = 0;
				JSONObject json = unstableSyscalls.poll();
				String scName = (String) json.get("name");
//				if (!scName.equals("fstat64") && !scName.equals("openat") && !scName.equals("close")) continue;
				if (affectedCalls.contains(scName))
					continue;
				String change = (String) json.get("change");
				double syscallPval = (double) json.get("pval");
				Double latestAvg = Math.floor((double)  json.get("latestAvg"));
				Double previousAvg = Math.floor((double)  json.get("previousAvg"));
				if (previousAvg >= latestAvg)
					continue;
				PriorityQueue<JSONObject> pq = new PriorityQueue<JSONObject>(10, new Comparator<JSONObject>(){
					public int compare(JSONObject o1, JSONObject o2){
						if ((double) o1.get("pval") < (double) o2.get("pval"))
							return -1;
						else if (o1.get("pval") == o2.get("pval"))
							return 0;
						else
							return +1;
					}
				});
				HashMap<String, HashMap<String, ArrayList<ArrayList<Long>>>> completeMap = getCompleteHitMaps(versionARuns, versionBRuns, scName);
				for (Entry<String, HashMap<String, ArrayList<ArrayList<Long>>>> entry: completeMap.entrySet()) {
					String method_name = entry.getKey();
					if (fullLog) {						
						System.out.println(method_name);
					}
					double[] sumListA = {}, sumListB = {};
					HashMap<String, ArrayList<ArrayList<Long>>> methodsMap = entry.getValue();
					for (Entry<String, ArrayList<ArrayList<Long>>> e2 : methodsMap.entrySet()) {
						String version = e2.getKey();
						ArrayList<ArrayList<Long>> listOfHitLists = e2.getValue();
						double[] sumList = getSumList(listOfHitLists);
						if (version.equals("latest")) {
							sumListB = sumList;
						} else {
							sumListA = sumList;
						}
						if (fullLog) {
							System.out.println("\tHits in " + version + " version:");
							for (int i = 0; i < listOfHitLists.size(); i++) {
								System.out.println("\t\tRun #" + i + ": [avg: " + sumList[i] + "] : " + listOfLongsToString(listOfHitLists.get(i)));
							}
						}
					}
					if (sumListA.length == 0) {
						sumListA = new double[sumListB.length];
					} else if (sumListB.length == 0) {
						sumListB = new double[sumListA.length];
					}
					if (sumListA.length != sumListB.length) {
						System.out.println("Error: Method was not executed in all runs of both versions.");
						continue;
					}
//					for (int i = 0; i < sumListB.length; i++) {
//						sumListB[i] = sumListB[i] / 10;
//					}
					double methodPval = new TTest().pairedTTest(sumListA, sumListB);
					Random rand = new Random();
					int  n = rand.nextInt(100) + 1;
					boolean condition = decide_random? (n > 50) : methodPval < 0.1 / ((double) completeMap.size());
					if (condition) {
						JSONObject outputJson = new JSONObject();
						outputJson.put("pval", methodPval);
						outputJson.put("method_name", method_name);
						outputJson.put("log", "Method Pval[" + methodPval + "] : AVG [" + avg(sumListA) + "] -> [" + avg(sumListB) + "] : " + method_name);
						pq.add(outputJson);
					}
				}
				if (pq.size() == 0)
					continue;
				System.out.println("SYSCALL TO BLAME: " + scName  + " " + change + ": Syscall Pval[" + syscallPval + "] : SUM [" + previousAvg + "]->[" + latestAvg + "]");
				System.out.println("Bonferroni Threshold: 0.1 / " + completeMap.size() + " = " + (0.1/(double) completeMap.size()));
				while (!pq.isEmpty()) {
					JSONObject j = pq.poll();
					String log = (String) j.get("log");
					String method_name = (String) j.get("method_name");
					if (decide_random) {
						Random rand = new Random();
						int  n = rand.nextInt(100) + 1;
						if (n > 50)
							ctp++;
						else
							cfp++;
					} else {
						if (bads.contains(method_name))
							ctp++;
						else if (goods.contains(method_name))
							cfp++;						
					}
					System.out.println(log);
				}
				tp += ctp;
				fp += cfp;
				tn += goods.size() - cfp;
				fn += bads.size() - ctp;
				System.out.println("-----------------------------");
			}
			double accuracy = (double)(tp + tn) / (double)(tp + tn + fp + fn);
			double precision = (double)(tp) / (double)(tp + fp);
			double recall = (double)(tp) / (double)(tp + fn);
			double f1 = (double)(2 * precision * recall) / (double)(precision + recall);
			System.out.println("ACCURACY: " + accuracy);
			System.out.println("PRECISION: " + precision);
			System.out.println("RECALL: " + recall);
			System.out.println("F1: " + f1);
		}
	}
	private HashSet<String> findAffectedCalls() throws ClassNotFoundException, SQLException, ParseException {
		HashSet<String> affectedCalls = new HashSet<String>();
		System.out.println("Finding system calls affected by instrumentation...");
		ArrayList<JSONObject> PARuns = DAO.findRuns(config.beforeProjectId, config.beforeVersionId, false, GreenAdvisor.COUNTS_STRACE_MODE);
		ArrayList<JSONObject> PBRuns = DAO.findRuns(config.afterProjectId, config.afterVersionId, false, GreenAdvisor.COUNTS_STRACE_MODE);
		ArrayList<JSONObject> IARuns = DAO.findRuns(config.beforeProjectId, config.beforeVersionId, true, GreenAdvisor.TIMESTAMPS_STRACE_MODE);
		ArrayList<JSONObject> IBRuns = DAO.findRuns(config.afterProjectId, config.afterVersionId, true, GreenAdvisor.TIMESTAMPS_STRACE_MODE);
		if (PARuns.size() != PBRuns.size() || PARuns.size() != IARuns.size() || PARuns.size() != IBRuns.size()) {
			System.out.println("Error: Different number of runs were found for different versions of the application under test.");
			return null;
		}
		HashMap<String, double[]> ADiffs = new HashMap<String, double[]>();
		HashMap<String, double[]> BDiffs = new HashMap<String, double[]>();
		for (int i = 0; i < PARuns.size(); i++) {
			JSONObject PARun = (JSONObject) PARuns.get(i).get("sc_log");
			JSONObject IARun = (JSONObject) IARuns.get(i).get("sc_log");
			Iterator<?> it = IARun.keySet().iterator();
			while (it.hasNext()) {
				String scName = (String) it.next();
				double[] callDiffs = null;
				if (ADiffs.containsKey(scName)) {
					callDiffs = ADiffs.get(scName);
				} else {
					callDiffs = new double[PARuns.size()];
					ADiffs.put(scName, callDiffs);
				}
				Long valP = (Long)((JSONObject)PARun.get(scName)).get("count");
				Long valI = (Long)((JSONObject)IARun.get(scName)).get("count");
				callDiffs[i] = valI.doubleValue() - valP.doubleValue();
			}
		}
		for (int i = 0; i < PBRuns.size(); i++) {
			JSONObject PBRun = (JSONObject) PBRuns.get(i).get("sc_log");
			JSONObject IBRun = (JSONObject) IBRuns.get(i).get("sc_log");
			Iterator<?> it = IBRun.keySet().iterator();
			while (it.hasNext()) {
				String scName = (String) it.next();
				double[] callDiffs = null;
				if (BDiffs.containsKey(scName)) {
					callDiffs = BDiffs.get(scName);
				} else {
					callDiffs = new double[PBRuns.size()];
					BDiffs.put(scName, callDiffs);
				}
				Long valP = (Long)((JSONObject)PBRun.get(scName)).get("count");
				Long valI = (Long)((JSONObject)IBRun.get(scName)).get("count");
				callDiffs[i] = valI.doubleValue() - valP.doubleValue();
			}
		}
		Iterator<?> it = ADiffs.keySet().iterator();
		while (it.hasNext()) {
			String callKey = (String) it.next();
			double pval = new TTest().pairedTTest(ADiffs.get(callKey), BDiffs.get(callKey));
			if (pval < 0.1 / ((double) ADiffs.size())) {
				System.out.println("Affected call found with p-value: " + pval + " " + listToString(ADiffs.get(callKey)) + " - " + listToString(BDiffs.get(callKey)));
				affectedCalls.add(callKey);
			}
		}
		System.out.println("Found " + affectedCalls.size() + " affected system-calls in total.");
		return affectedCalls;
	}

	private HashMap<String, ArrayList<JSONObject>> askForRunsOfTwoVersions_OLD(ArrayList<JSONObject> allRuns) {
		HashMap<String, ArrayList<JSONObject>> map = new HashMap<String, ArrayList<JSONObject>>();
		for (JSONObject run : allRuns) {
			String projectKey = (String) ((JSONObject) run.get("key")).get("project");
			String versionKey = (String) ((JSONObject) run.get("key")).get("version");
			String instrumentation = (String) ((JSONObject) run.get("key")).get("instrumentation");
			String strace_mode = (String) ((JSONObject) run.get("key")).get("strace_mode");
			if (projectKey.equals(config.versions.get(GreenAdvisor.AFTER_VERSION).get("projectId")) && instrumentation.equals("disable") && strace_mode.equals("counts")) {
				ArrayList<JSONObject> listOfRuns = new ArrayList<>();
				if (map.containsKey(versionKey)) {
					listOfRuns = map.get(versionKey);
				} else {
					map.put(versionKey, listOfRuns);
				}
				listOfRuns.add(run);
			}
		}
		System.out.println("Available versions:");
		ArrayList<String> versionList = new ArrayList<>();
		for (Entry<String, ArrayList<JSONObject>> entry : map.entrySet()) {
			versionList.add(entry.getKey());
		}
		for (int i = 0; i < versionList.size(); i++) {
			System.out.println(i + " : " + versionList.get(i));
		}
		if (versionList.size() < 2)
			return null;
		Scanner in = new Scanner(System.in);
		System.out.println("Please select version A: ");
		int a = in.nextInt();
		System.out.println("Please select version B: ");
		int b = in.nextInt();
		HashMap<String, ArrayList<JSONObject>> result = new HashMap<>();
		result.put(versionList.get(a), map.get(versionList.get(a)));
		result.put(versionList.get(b), map.get(versionList.get(b)));
		return result;
	}
	private ArrayList<ArrayList<JSONObject>> askForRunsOfTwoVersions(ArrayList<JSONObject> allRuns, boolean instrumentationEnabled, String wantedStraceMode) {
		HashMap<String, ArrayList<JSONObject>> map = new HashMap<String, ArrayList<JSONObject>>();
		String wantedInstrumentationMode = instrumentationEnabled? "enable" : "disable";
		for (JSONObject run : allRuns) {
			String projectKey = (String) ((JSONObject) run.get("key")).get("project");
			String versionKey = (String) ((JSONObject) run.get("key")).get("version");
			String instrumentation = (String) ((JSONObject) run.get("key")).get("instrumentation");
			String strace_mode = (String) ((JSONObject) run.get("key")).get("strace_mode");
			if (projectKey.equals(config.versions.get(GreenAdvisor.AFTER_VERSION).get("projectId")) && instrumentation.equals(wantedInstrumentationMode) && strace_mode.equals(wantedStraceMode)) {
				ArrayList<JSONObject> listOfRuns = new ArrayList<>();
				if (map.containsKey(versionKey)) {
					listOfRuns = map.get(versionKey);
				} else {
					map.put(versionKey, listOfRuns);
				}
				listOfRuns.add(run);
			}
		}
		System.out.println("Available versions:");
		ArrayList<String> versionList = new ArrayList<>();
		for (Entry<String, ArrayList<JSONObject>> entry : map.entrySet()) {
			versionList.add(entry.getKey());
		}
		for (int i = 0; i < versionList.size(); i++) {
			System.out.println(i + " : " + versionList.get(i));
		}
		if (versionList.size() < 2)
			return null;
		Scanner in = new Scanner(System.in);
		System.out.println("Please select version A: ");
		int a = in.nextInt();
		System.out.println("Please select version B: ");
		int b = in.nextInt();
		ArrayList<ArrayList<JSONObject>> result = new ArrayList<>();
		result.add(map.get(versionList.get(a)));
		result.add(map.get(versionList.get(b)));
		return result;
	}

	private double[] getSumList(ArrayList<ArrayList<Long>> listOfHitsInAllRuns) {
		double[] result = new double[listOfHitsInAllRuns.size()];
		for (int i = 0; i < result.length; i++) {
			ArrayList<Long> hitsInOneRun = listOfHitsInAllRuns.get(i);
			double sum = 0.0;
			for (Long hit : hitsInOneRun) {
				sum += hit;
			}
			result[i] = Math.floor(sum / (double) hitsInOneRun.size());
		}
		return result;
	}
	private String listOfLongsToString(ArrayList<Long> list) {
		String result = "";
		for (Long l: list)
			result += String.valueOf(l) + " ";
		return result;
	}
	// Returns priority queue of system call with significant change in number of invocations
	// Head of the queue is the one with the most change
	// Items of the queue are of JSONObject type:
	// {
	//		"name": "syscall_name",
	//		"pval": pval,
	//		"change": "increase/decrease", from A to B
	//		"latestAvg": latest_average,
	//		"previousAvg": previous_average,
	// }
	private PriorityQueue<JSONObject> findSignificantlyChangedSyscalls(ArrayList<JSONObject> listOfRunsA, ArrayList<JSONObject> listOfRunsB) throws ClassNotFoundException, SQLException, ParseException {
		ArrayList<ArrayList<JSONObject>> listsOfRuns = new ArrayList<ArrayList<JSONObject>>();
		listsOfRuns.add(listOfRunsA);
		listsOfRuns.add(listOfRunsB);
		if (listsOfRuns.get(0).size() < config.numOfRunsPerVersion || listsOfRuns.get(1).size() < config.numOfRunsPerVersion) {
			System.out.println("GreenAdvisor failed to find " + config.numOfRunsPerVersion + " runs of all test cases in each run list.");
			return null;
		}
		while (listsOfRuns.get(0).size() > listsOfRuns.get(1).size()) {
			listsOfRuns.get(0).remove(listsOfRuns.get(0).size() - 1);
		}
		while (listsOfRuns.get(0).size() < listsOfRuns.get(1).size()) {
			listsOfRuns.get(1).remove(listsOfRuns.get(1).size() - 1);
		}
		HashMap<String, double[][]> scCounts = new HashMap<>();
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < listsOfRuns.get(i).size(); j++) {
				JSONObject run = (JSONObject) (listsOfRuns.get(i).get(j)).get("sc_log");
				Iterator<?> it = run.keySet().iterator();
				while (it.hasNext()) {
					String scName = (String) it.next();
					if (!scCounts.containsKey(scName)) {
						double[][] ListsOfCountsInRuns = new double[2][listsOfRuns.get(0).size()];
						Long val = (Long)((JSONObject)run.get(scName)).get("count");
						if (val > 1) {
							ListsOfCountsInRuns[i][j] = val.doubleValue();
							scCounts.put(scName, ListsOfCountsInRuns);
						}
					} else {
						double[][] ListsOfCountsInRuns = scCounts.get(scName);
						Long val = (Long)((JSONObject)run.get(scName)).get("count");
						if (val > 1)
							ListsOfCountsInRuns[i][j] = val.doubleValue();
					}
				}
			}
		}
		HashMap<String, String> bagOfWords = getBagOfWords();
		PriorityQueue<JSONObject> pq = new PriorityQueue<JSONObject>(10, new Comparator<JSONObject>(){
			public int compare(JSONObject o1, JSONObject o2){
				if ((double) o1.get("pval") < (double) o2.get("pval"))
					return -1;
				else if (o1.get("pval") == o2.get("pval"))
					return 0;
				else
					return +1;
			}
		});
		Iterator<?> it = scCounts.keySet().iterator();
		while(it.hasNext()) {
			String scName = (String)it.next();
			String scPatterns = (bagOfWords.containsKey(scName))? bagOfWords.get(scName) : "Empty";
			double[][] listsOfCountsInRuns = scCounts.get(scName);
			double pval = new TTest().pairedTTest(listsOfCountsInRuns[0], listsOfCountsInRuns[1]);
			double latestAvg = avg(listsOfCountsInRuns[1]);
			double previousAvg = avg(listsOfCountsInRuns[0]);
			String change = (latestAvg > previousAvg) ? "increase" : "decrease";
			if (pval < 0.1/ ((double) scCounts.size())) {
				JSONObject json = new JSONObject();
				json.put("name", scName);
				json.put("patterns", scPatterns);
				json.put("pval", pval);
				json.put("change", change);
				json.put("latestAvg", latestAvg);
				json.put("previousAvg", previousAvg);
				json.put("listA", listToString(listsOfCountsInRuns[0]));
				json.put("listB", listToString(listsOfCountsInRuns[1]));
				pq.offer(json);
			}
		}
		return pq;
	}

	private HashMap<String, HashMap<String, ArrayList<ArrayList<Long>>>> getCompleteHitMaps (
			ArrayList<JSONObject> runsA, ArrayList<JSONObject> runsB, String scName) {
		HashMap<String, HashMap<String, ArrayList<ArrayList<Long>>>> completeMap = new HashMap<String, HashMap<String, ArrayList<ArrayList<Long>>>>();
		putHitMapsOfAllRunsInCompleteMap(completeMap, runsA, "previous", scName);
		putHitMapsOfAllRunsInCompleteMap(completeMap, runsB, "latest", scName);
		return completeMap;
	}
	private void putHitMapsOfAllRunsInCompleteMap(HashMap<String, HashMap<String, ArrayList<ArrayList<Long>>>> completeMap,
			ArrayList<JSONObject> runs, String keyOfRuns, String scName) {
		for (JSONObject run: runs) {
			HashMap<String, ArrayList<Long>> hitMap = getHitMap(run, scName);
			for (Entry<String, ArrayList<Long>> entry: hitMap.entrySet()) {
				String method_name = entry.getKey();
				ArrayList<Long> methodHitList = entry.getValue();
				if (!completeMap.containsKey(method_name)) {
					HashMap<String, ArrayList<ArrayList<Long>>> methodsMap = new HashMap<>();
					ArrayList<ArrayList<Long>> listOfHitLists = new ArrayList<ArrayList<Long>>();
					listOfHitLists.add(methodHitList);
					methodsMap.put(keyOfRuns, listOfHitLists);
					completeMap.put(method_name, methodsMap);
				} else {
					HashMap<String, ArrayList<ArrayList<Long>>> methodsMap = completeMap.get(method_name);
					if (methodsMap.containsKey(keyOfRuns)) {
						methodsMap.get(keyOfRuns).add(methodHitList);
					} else {
						ArrayList<ArrayList<Long>> listOfHitLists = new ArrayList<ArrayList<Long>>();
						listOfHitLists.add(methodHitList);
						methodsMap.put(keyOfRuns, listOfHitLists);
					}
				}
			}
		}
	}
	private HashMap<String, ArrayList<Long>> getHitMap(JSONObject run, String scName) {
		HashMap<String, ArrayList<Long>> map = new HashMap<String, ArrayList<Long>>();
		PriorityQueue<JSONObject> pq = getHitPQ(run, scName);
		while (!pq.isEmpty()) {
			JSONObject json = pq.poll();
			String method_name = (String) json.get("name");
			Long hits = (Long) json.get("hits");
			if (map.containsKey(method_name)) {
				map.get(method_name).add(hits);
			} else {
				ArrayList<Long> listOfHits = new ArrayList<Long>();
				listOfHits.add(hits);
				map.put(method_name, listOfHits);
			}
		}
		return map;
	}
	// Returns a priority queue of method calls with number of hits of a particular system call
	// Head of the queue is the one with the most hits
	// Items of the queue are of JSONObject type:
	// {
	//		"name": "method_name",
	//		"hits": number_of_hits
	// }
	private PriorityQueue<JSONObject> getHitPQ(JSONObject run, String scName) {
		PriorityQueue<JSONObject> result = new PriorityQueue<JSONObject>(10, new Comparator<JSONObject>(){
			public int compare(JSONObject o1, JSONObject o2){
				if ((Long) o1.get("hits") < (Long) o2.get("hits"))
					return 1;
				else if ((Long) o1.get("hits") == (Long) o2.get("hits"))
					return 0;
				else
					return -1;
			}
		});
		PriorityQueue<JSONObject> sc_ep_merged = mergeSCAndEPLogs(scName, (JSONObject) run.get("sc_log"), (JSONObject) run.get("ep_log"));
//		int starts = 0, ends = 0;
//		while(!sc_ep_merged.isEmpty()) {
//			JSONObject event = sc_ep_merged.poll();
//			String eType = (String) event.get("type");
//			String name = (String) event.get("name");
//			String timestamp = (String) event.get("timestamp");
//			if (eType.equals("mcs") && name.contains("refreshHeaderView"))
//				starts++;
//			if (eType.equals("mce") && name.contains("refreshHeaderView"))
//				ends++;
//		}
//		String key = ((JSONObject) run.get("key")).toJSONString();
//		System.out.println("starts: " + starts + ", and ends: " + ends + "key: " + key);
		
		Stack<JSONObject> openMethods = new Stack<JSONObject>();
		long notEnclosedCount = 0;
		while (!sc_ep_merged.isEmpty()) {
			JSONObject event = sc_ep_merged.poll();
			String eType = (String) event.get("type");
			String name = (String) event.get("name");
			String timestamp = (String) event.get("timestamp");
			if (eType.equals("mcs")) {
				JSONObject openMethod = new JSONObject();
				openMethod.put("name", name);
				openMethod.put("hits", new Long(0));
				openMethods.push(openMethod);
			} else if (eType.equals("sc")) {
				if (openMethods.isEmpty()) {
					notEnclosedCount ++;
				} else {
					Long hits = (Long) openMethods.peek().get("hits");
					hits += 1;
					openMethods.peek().put("hits", hits);
				}
			} else {
				boolean topIsTheRightOne = false;
				Stack<JSONObject> alt = new Stack<JSONObject>();
				JSONObject top = null;
				while (!topIsTheRightOne) {
					if (openMethods.isEmpty()) 
						System.out.println("Unexpected end of method found in epLog.");
					top = openMethods.pop();
					if (!top.get("name").equals(name)) {
						alt.push(top);
					} else {
						topIsTheRightOne = true;
					}
				}
				while(!alt.isEmpty())
					openMethods.push(alt.pop());
				result.offer(top);
			}
		}
//		JSONObject notEnclosed = new JSONObject();
//		notEnclosed.put("name", "NOT_ENCLOSED");
//		notEnclosed.put("hits", notEnclosedCount);
//		result.offer(notEnclosed);
		return result;
	}

	// Returns a priority queue of merged execution path and a certain system call log events
	// Head of the queue is the one that happened first
	// Items of the queue are of JSONObject type:
	// {
	//		"timestamp": "time_stamp",
	//		"type": "sc for system call or mcs for method call start or mce for method call end"
	//		"name": "name of method or system call"
	// }
	private PriorityQueue<JSONObject> mergeSCAndEPLogs(String scName, JSONObject scLog, JSONObject epLog) {
		PriorityQueue<JSONObject> sc_ep_merge_queue = new PriorityQueue<JSONObject>(10, new Comparator<JSONObject>(){
			public int compare(JSONObject o1, JSONObject o2){
				if (Long.parseLong((String) o1.get("timestamp")) < Long.parseLong((String) o2.get("timestamp")))
					return -1;
				else if (Long.parseLong((String) o1.get("timestamp")) == Long.parseLong((String) o2.get("timestamp"))) {
					if (((String) o1.get("type")).endsWith("mcs"))
						return -1;
					else if (((String) o1.get("type")).endsWith("mce"))
						return 1;
					else
						return 0;
				}
				else
					return 1;
			}
		});
		JSONObject scJson = (JSONObject) scLog.get(scName);
		if (scJson != null) {
			JSONArray scTimestamps = (JSONArray) (scJson).get("invocation_timestamps");
			for (Object timestampObject: scTimestamps) {
				String timestamp = (String) timestampObject;
				JSONObject event = new JSONObject();
				event.put("timestamp", timestamp);
				event.put("type", "sc");
				event.put("name", scName);
				sc_ep_merge_queue.offer(event);
			}
		}
		JSONArray epEvents = (JSONArray) epLog.get("events");
		for (int i = 0; i < epEvents.size(); i++) {
			JSONObject epEvent = (JSONObject) epEvents.get(i);
			JSONObject event = new JSONObject();
			String type = ((String) epEvent.get("type")).equals("start") ? "mcs" : "mce";
			String timestamp = (String) epEvent.get("timestamp");
			event.put("timestamp", timestamp);
			event.put("name", epEvent.get("method_name"));
			event.put("type", type);
			sc_ep_merge_queue.offer(event);
		}
		return sc_ep_merge_queue;
	}
	private double avg(double[] m) {
		double sum = 0;
		for (int i = 0; i < m.length; i++) {
			sum += m[i];
		}
		return sum / m.length;
	}
	private String listToString(double[] list) {
		String result = "[ ";
		for (double d : list) {
			result += d + " ";
		}
		return result + "]";
	}
	private String listToString(ArrayList<Long> list) {
		String result = "[ ";
		for (Long d : list) {
			result += d + " ";
		}
		return result + "]";
	}

	public void runPhase1() throws ClassNotFoundException, SQLException, ParseException {
		ArrayList<JSONObject> allRuns = DAO.findAll();
		ArrayList<ArrayList<JSONObject>> runs = askForRunsOfTwoVersions(allRuns, false, GreenAdvisor.COUNTS_STRACE_MODE);
		PriorityQueue<JSONObject> unstableSyscalls = findSignificantlyChangedSyscalls(runs.get(0), runs.get(1));
		if (unstableSyscalls != null)
			printUnstableSyscalls(unstableSyscalls);
		System.out.println("---------------------------------------");
	}
}
