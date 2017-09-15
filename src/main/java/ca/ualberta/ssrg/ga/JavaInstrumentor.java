package ca.ualberta.ssrg.ga;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;

import org.jboss.forge.roaster.ParserException;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;

import com.jcraft.jsch.ConfigRepository.Config;

public class JavaInstrumentor {
	public HashSet<String> targetFunctions = new HashSet<String>();
	public int instrumented_count = 0;
	
	public JavaInstrumentor(HashSet<String> targetFunctions) {
		this.targetFunctions = targetFunctions;
	}

	public JavaInstrumentor() {}

	public void instrumentJavaFilesRecursively(File root, String loggerClass) throws IOException {
		for (File f : root.listFiles()) {
			if (f.isDirectory()) {
				instrumentJavaFilesRecursively(f, loggerClass);
			} else if (f.getName().endsWith(".java")) {
				instrumentJavaFile(f, loggerClass);
			}
		}
	}

	private void instrumentJavaFile(File javaFile, String loggerClass) throws IOException {
		try {
			JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, javaFile);
			javaClass.addImport("java.io.File");
			javaClass.addImport("java.io.FileOutputStream");
			javaClass.addImport("java.io.IOException");
			javaClass.addImport("java.io.OutputStreamWriter");
			javaClass.addImport(loggerClass);
			for (MethodSource<JavaClassSource> method : javaClass.getMethods()) {
				boolean allow_instrumentation = !method.isConstructor() && !method.isAbstract() && ((targetFunctions == null) || (targetFunctions.contains(javaClass.getQualifiedName() + ":" + method.getName()))); 
				System.out.println("Allow instrumenting " + javaClass.getQualifiedName() + ":" + method.getName() + ": " + allow_instrumentation);
				if (allow_instrumentation) {
					instrumented_count++;
					String beginningCode = "_Logger.keepLog(\"(\" + System.currentTimeMillis() + \")\" + System.nanoTime() + \":" + javaClass.getQualifiedName() + ":" + method.getName() + ":start\\n\");\n";
					beginningCode += "try {";
					String endCode = "} finally {";
					endCode += "_Logger.keepLog(System.nanoTime() + \":" + javaClass.getQualifiedName() + ":" + method.getName() + ":end\\n\");\n}";
					method.setBody(beginningCode + method.getBody() + endCode);
				}
				if (method.isAbstract())
					method.setAbstract(true);
			}
			FileWriter fw = new FileWriter(javaFile, false);
			fw.write(javaClass.toString());
			fw.close();
		} catch (ParserException e) {
			System.out.println("Warning: Found a java interface, ignoring it...");
		}
	}

	public void instrumentAndStoreLogger(String basicLoggerFile, String dstPackageName, String pathToStoreIn) {
		try {
			String[] parts = basicLoggerFile.split("/");
			String javaFileName = parts[parts.length - 1];
			FileWriter fw = new FileWriter(pathToStoreIn + "/" + javaFileName, false);
			fw.write("package " + dstPackageName + ";\n");
			BufferedReader br = new BufferedReader(new FileReader(new File(basicLoggerFile)));
			String line;
			while ((line = br.readLine()) != null) {
				fw.write(line + "\n");
			}
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public HashMap<String, String> getFunctionMap(File root) throws FileNotFoundException {
		HashMap<String, String> rootBodyMap = new HashMap<String, String>();
		for (File f : root.listFiles()) {
			if (f.isDirectory()) {
				rootBodyMap.putAll(getFunctionMap(f));
			} else if (f.getName().endsWith(".java")) {
				try {
					JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, f);
					for (MethodSource<JavaClassSource> method : javaClass.getMethods()) {
						rootBodyMap.put(javaClass.getQualifiedName() + ":" + method.getName(), method.getBody());
					}
				} catch (ParserException e) {
					System.out.println("Warning: Found a java interface, ignoring it...");
				}
			}
		}
		return rootBodyMap;
	}
}
