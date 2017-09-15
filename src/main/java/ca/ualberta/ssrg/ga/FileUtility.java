package ca.ualberta.ssrg.ga;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileUtility {
	private List<String> fileList;
	public static void delete(File f) throws IOException {
		if (f.exists()) {
			if (f.isDirectory()) {
				for (File c : f.listFiles())
					delete(c);
			}
			if (!f.delete())
				throw new FileNotFoundException("Failed to delete file: " + f);
		}
	}
	public static void createOrClearDir(File dirFile) throws IOException {
		delete(dirFile);
		mkDirs(dirFile);
	}
	public static void mkDirs(File f) {
		if (!f.exists()) {
			f.mkdirs();
		}	
	}

	public static String unzipIt(String zipFile, String outputFolder){
		System.out.println("Unpacking " + zipFile);
		byte[] buffer = new byte[1024];
		try{
			//create output directory is not exists
			File folder = new File(outputFolder);
			if(!folder.exists()){
				folder.mkdir();
			}
			//get the zip file content
			ZipInputStream zis = 
					new ZipInputStream(new FileInputStream(zipFile));
			//get the zipped file list entry
			ZipEntry ze = zis.getNextEntry();

			while(ze!=null){

				String fileName = ze.getName();
				File newFile = new File(outputFolder + File.separator + fileName);

				//create all non exists folders
				//else you will hit FileNotFoundException for compressed folder
				new File(newFile.getParent()).mkdirs();

				FileOutputStream fos = new FileOutputStream(newFile);             

				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}

				fos.close();   
				ze = zis.getNextEntry();
			}

			zis.closeEntry();
			zis.close();

		}catch(IOException ex){
			ex.printStackTrace(); 
		}
		return outputFolder;
	}    
	public static String UnzipJar(String jarFile, String outputFolder) throws IOException {
		System.out.println("Unpacking " + jarFile);
		java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile);
		java.util.Enumeration _enum = jar.entries();
		while (_enum.hasMoreElements()) {
			java.util.jar.JarEntry file = (java.util.jar.JarEntry) _enum.nextElement();
			java.io.File f = new java.io.File(outputFolder + java.io.File.separator + file.getName());
			if (file.isDirectory()) { // if its a directory, create it
				f.mkdirs();
				continue;
			}
			java.io.InputStream is = jar.getInputStream(file); // get the input stream
			java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
			while (is.available() > 0) {  // write contents of 'is' to 'fos'
				fos.write(is.read());
			}
			fos.close();
			is.close();
		}
		return outputFolder;
	}

	public static String packJarFile(String unzippedJarFolder, String outputJar) throws IOException
	{
		System.out.println("Packing jar file " + outputJar);
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		JarOutputStream target = new JarOutputStream(new FileOutputStream(outputJar), manifest);
		addToPack(new File(unzippedJarFolder), target);
		target.close();
		return outputJar;
	}

	private static void addToPack(File source, JarOutputStream target) throws IOException
	{
		BufferedInputStream in = null;
		try
		{
			if (source.isDirectory())
			{
				String name = source.getPath().replace("\\", "/");
				if (!name.isEmpty())
				{
					if (!name.endsWith("/"))
						name += "/";
					JarEntry entry = new JarEntry(name);
					entry.setTime(source.lastModified());
					target.putNextEntry(entry);
					target.closeEntry();
				}
				for (File nestedFile: source.listFiles())
					addToPack(nestedFile, target);
				return;
			}

			JarEntry entry = new JarEntry(source.getPath().replace("\\", "/"));
			entry.setTime(source.lastModified());
			target.putNextEntry(entry);
			in = new BufferedInputStream(new FileInputStream(source));

			byte[] buffer = new byte[1024];
			while (true)
			{
				int count = in.read(buffer);
				if (count == -1)
					break;
				target.write(buffer, 0, count);
			}
			target.closeEntry();
		}
		finally
		{
			if (in != null)
				in.close();
		}
	}

	public static String dex2Jar(String dexFile, String outputJar) {
		System.out.println("Converting dex to jar");
		String[] commands;
		if (System.getProperty("os.name").contains("win")) {
			commands = new String[]{"dex2jar-2.0/d2j-dex2jar.bat", "-o", outputJar, dexFile};
		} else {
			commands = new String[]{"dex2jar-2.0/d2j-dex2jar.sh", "-o", outputJar, dexFile};
		}
		Terminal.run(commands, true);
		System.out.println("Jar file ready");
		return outputJar;
	}

	public static String Jar2dex(String jarFile, String outputDex) {
		System.out.println("Converting jar to dex");
		String[] commands;
		if (System.getProperty("os.name").contains("win")) {
			commands = new String[]{"dex2jar-2.0/d2j-jar2dex.bat", "-o", outputDex, jarFile};
		} else {
			commands = new String[]{"dex2jar-2.0/d2j-jar2dex.sh", "-o", outputDex, jarFile};
		}
		Terminal.run(commands, true);
		System.out.println("Dex file ready");
		return outputDex;
	}
	
	public static String zipIt(String sourceFolder, String zipOutput) throws IOException {
		System.out.println("Packing " + zipOutput);
		File directoryToZip = new File(sourceFolder);
		List<File> fileList = new ArrayList<File>();
		getAllFiles(directoryToZip, fileList);
		writeZipFile(directoryToZip, zipOutput, fileList);
		return zipOutput;
	}

	private static void getAllFiles(File dir, List<File> fileList) {

		File[] files = dir.listFiles();
		for (File file : files) {
			fileList.add(file);
			if (file.isDirectory()) {
				getAllFiles(file, fileList);
			}
		}

	}

	private static void writeZipFile(File directoryToZip, String zipOutput, List<File> fileList) {
		try {
			FileOutputStream fos = new FileOutputStream(zipOutput);
			ZipOutputStream zos = new ZipOutputStream(fos);

			for (File file : fileList) {
				if (!file.isDirectory()) { // we only zip files, not directories
					addToZip(directoryToZip, file, zos);
				}
			}
			zos.close();
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void addToZip(File directoryToZip, File file, ZipOutputStream zos) throws FileNotFoundException,
	IOException {

		FileInputStream fis = new FileInputStream(file);

		// we want the zipEntry's path to be a relative path that is relative
		// to the directory being zipped, so chop off the rest of the path
		String zipFilePath = file.getCanonicalPath().substring(directoryToZip.getCanonicalPath().length() + 1,
				file.getCanonicalPath().length());
		ZipEntry zipEntry = new ZipEntry(zipFilePath);
		zos.putNextEntry(zipEntry);

		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zos.write(bytes, 0, length);
		}

		zos.closeEntry();
		fis.close();
	}
	
}
