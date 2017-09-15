package ca.ualberta.ssrg.ga;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DeviceCommunicator {
	private String adbPath = null;
	
	DeviceCommunicator(String adbPath) {
		this.adbPath = adbPath;
	}
	public void removeFile(String target) {
		System.out.print("Removing path '" + target + "'...");
		Terminal.run(new String[]{adbPath, "shell", "rm", "-rf", target}, false);
		System.out.println("[OK]");
	}
	public void createOrClearDir(String path) {
		removeFile(path);
		System.out.print("Making directory '" + path + "'...");
		Terminal.run(new String[]{adbPath, "shell", "mkdir", path}, false);
		System.out.println("[OK]");
	}
	public void uploadFile(String from, String to) {
		System.out.print("Uploading file '" + from + "'...");
		Terminal.run(new String[]{adbPath, "push", from, to}, false);
		System.out.println("[OK]");
	}
	
	public boolean downloadFile(String from, String to) {
		System.out.print("Downloading file '" + from + "'...");
		ArrayList<String> lines = Terminal.run(new String[]{adbPath, "shell", "ls", from}, false);
		if (lines.size() > 0 && lines.get(0).equals(from)) {
			Terminal.run(new String[]{adbPath, "pull", from, to}, false);
			System.out.println("[OK]");
			return true;
		} else {
			System.out.println("[Failure: File not found.]");
			return false;
		}
	}
	public void downloadDir(String from, String to) {
		System.out.print("Downloading contents of directory '" + from + "'...");
		Terminal.run(new String[]{adbPath, "pull", from, to}, false);
		System.out.println("[OK]");
	}
	public void installApk(String apkPath) {
		System.out.println("Installing '" + apkPath + "'");
		Terminal.run(new String[] {adbPath, "install", apkPath}, true);
	}
	public void uninstallPackage(String _package) {
		System.out.println("Uninstalling '" + _package + "'");
		Terminal.run(new String[] {adbPath, "uninstall", _package}, true);
	}
	
	public void startStraceProcessOnPhone(String scriptPathOnPhone, String packageName) {
		System.out.print("Starting strace listener on phone...");
		boolean isRunning = false;
		while (!isRunning) {
			Terminal.nonBlockingRun(new String[]{adbPath, "shell", "sh", scriptPathOnPhone, packageName, "&"});
			ArrayList<String> lines = Terminal.run(new String[]{adbPath, "shell","ps | grep -w sh"}, false);
			for(String line: lines) {
				if(line.endsWith(" sh") && line.startsWith("root")) { 
					isRunning = true;
					break;
				}
			}
		}
		System.out.println("[OK]");
	}
	
	public void runTargetTest(String targetTestPathOnPhone) {
		System.out.println("Starting target test...");
		ArrayList<String> lines = Terminal.run(new String[]{adbPath, "shell", "sh", targetTestPathOnPhone}, true);
		for (String line : lines) {
			System.out.println(line);
		}
		System.out.println("Target test is complete.");
	}
	
	// returns total execution time of test cases, -1 if execution aborts
	public double runAllTestCases(String testPackageName) {
		ArrayList<String> lines = Terminal.run(new String[]{adbPath, "shell", "am", "instrument", "-w", testPackageName + "/android.test.InstrumentationTestRunner"}, true);
		for (String line: lines) {
			if (line.startsWith("Time:")) {
				return Double.parseDouble(line.split(" ")[1]);
			}
		}
		System.out.println("Broken cycle...");
		return -1;
	}
	
	public void startLoggerActivity(String packageName, String loggerClassName) {
		ArrayList<String> lines = Terminal.run(new String[]{adbPath, "shell", "am", "start", "-n", packageName + "/." + loggerClassName}, true);
		for (String line : lines) {
			System.out.println(line);
		}
	}
	public void closeApp(String targetPackageName) {
		ArrayList<String> lines = Terminal.run(new String[]{adbPath, "shell", "am", "force-stop", targetPackageName}, true);
		for (String line : lines) {
			System.out.println(line);
		}
	}
}
