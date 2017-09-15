package ca.ualberta.ssrg.ga;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class Configuration {
	private static final String CONFIG_JSON = "config.json";
	public HashMap<String, HashMap<String, String>> versions = new HashMap<String, HashMap<String, String>>();
	public String beforeProjectDir;
	public String beforeProjectId;
	public String beforeVersionId;
	public String beforeManifest;
	public String beforeTargetApk;
	public String beforeInstrumentedApk;
	public String beforeTargetPackageName;
	public String beforeInstrumentationTarget;
	
	public String afterProjectDir;
	public String afterProjectId;
	public String afterVersionId;
	public String afterManifest;
	public String afterTargetApk;
	public String afterInstrumentedApk;
	public String afterTargetPackageName;
	public String afterInstrumentationTarget;
	
	public String targetTest;
	public String testsDir;
	
	private static final String scLogName = "trc.txt";
	private static final String epLogName = "epLog.txt";

	public String workDirPathOnPhone = "/data/local/";
	public String epLogPathOnPhone = "/sdcard/" + epLogName;
	public String scLogPathOnPhone = workDirPathOnPhone + scLogName;
	
	public String straceDirPathOnPC = "strace/";
	public String logDirOnPC = "log/";
	public String scLogPathOnPC = logDirOnPC + scLogName;
	public String epLogPathOnPC = logDirOnPC + epLogName;
	
	public String adbPath;
	public int numOfRunsPerVersion = 5;
	
	Configuration() throws IOException, ParseException, NoHeadException, GitAPIException, ParserConfigurationException, SAXException {
		JSONObject config;
		try {
			config = (JSONObject) new JSONParser().parse(new FileReader(CONFIG_JSON));
		} catch (FileNotFoundException e) {
			System.out.println("Error: File config.json not found.");
			throw e;
		} catch (IOException e) {
			System.out.println("Error: Cannot open config.json file.");
			throw e;
		} catch (ParseException e) {
			System.out.println("Error: Cannot parse config.json file.");
			throw e;
		}
		
		JSONObject before = (JSONObject)((JSONObject)config.get("versions")).get("before");
		HashMap<String, String> beforeMap = new HashMap<>();
		beforeMap.put("projectDir", (String) before.get("projectDir"));
		beforeMap.put("projectId", GitManager.getRemoteUrl(beforeMap.get("projectDir")));
		beforeMap.put("versionId", GitManager.getHeadCommitKey(beforeMap.get("projectDir")));
		beforeMap.put("manifest", (String) before.get("manifest"));
		beforeMap.put("targetApk", (String) before.get("targetApk"));
		beforeMap.put("instrumentedApk", (String) before.get("instrumentedApk"));
		beforeMap.put("targetPackageName", findPackageNameInManifest(beforeMap.get("projectDir") + "/" + beforeMap.get("manifest")));
		beforeMap.put("instrumentationTarget", (String) before.get("instrumentationTarget"));
		versions.put("before", beforeMap);
		
		JSONObject after = (JSONObject)((JSONObject)config.get("versions")).get("after");
		HashMap<String, String> afterMap = new HashMap<>();
		afterMap.put("projectDir", (String) after.get("projectDir"));
		afterMap.put("projectId", GitManager.getRemoteUrl(afterMap.get("projectDir")));
		afterMap.put("versionId", GitManager.getHeadCommitKey(afterMap.get("projectDir")));
		afterMap.put("manifest", (String) after.get("manifest"));
		afterMap.put("targetApk", (String) after.get("targetApk"));
		afterMap.put("instrumentedApk", (String) after.get("instrumentedApk"));
		afterMap.put("targetPackageName", findPackageNameInManifest(afterMap.get("projectDir") + "/" + afterMap.get("manifest")));
		afterMap.put("instrumentationTarget", (String) after.get("instrumentationTarget"));
		versions.put("after", afterMap);
		
		adbPath = findFile(new File((String) config.get("sdkDir")), "adb");
		String sdkDir = (String) config.get("sdkDir");
		String aaptPath = findFile(new File(sdkDir), "aapt");
		testsDir = (String) config.get("testsDir");
		targetTest = (String) config.get("targetTest");
	}
	
	private String findFile(File dir, String fileName) {
		for (File f : dir.listFiles()) {
			if (f.isFile() && f.getName().equals(fileName))
				return f.getAbsolutePath();
			else if (f.isDirectory()) {
				String result = findFile(f, fileName);
				if (result != null)
					return result;
			}
		}
		return null;
	}
	
	private String findApkPackageName(String aaptPath, String apkPath) throws IOException {
		Process p = null;
		try {
			p = new ProcessBuilder(aaptPath, "dump", "badging", apkPath).start();
		} catch (IOException e) {
			System.out.println("Error: Cannot open aapt file.");
			throw e;
		}
		try {
			return new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().split("'")[1];
		} catch (IOException e) {
			System.out.println("Error: Cannot extract package name from apk file: " + apkPath);
			throw e;
		}
	}
	
	private String findPackageNameInManifest(String manifestPath) throws ParserConfigurationException, SAXException, IOException {
		File xmlFile = new File(manifestPath);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(xmlFile);
		
		Element manifest = (Element) doc.getElementsByTagName("manifest").item(0);
		String result = manifest.getAttribute("package");
		return result;
	}
}
