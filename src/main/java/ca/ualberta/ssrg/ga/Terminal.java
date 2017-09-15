package ca.ualberta.ssrg.ga;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Terminal {
	public static ArrayList<String> run(String[] args, boolean enablePrintOutput) {
		ArrayList<String> lines = new ArrayList<String>();
		ProcessBuilder pb = new ProcessBuilder(args);
		Process pc;
		try {
			pc = pb.start();
			InputStream is = pc.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line;	 		    
			while ((line = br.readLine()) != null) {
				if (enablePrintOutput)
					System.out.println(line);
				lines.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return lines;
	}
	public static void nonBlockingRun(String[] args) {
		ProcessBuilder pb = new ProcessBuilder(args);
		try {
			pb.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
