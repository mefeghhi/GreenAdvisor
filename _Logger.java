import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import android.app.Activity;
import android.os.Bundle;

public class _Logger extends Activity {
	
	private static String log  = "";
	
	public static void keepLog(String toBeLogged) {
		log += toBeLogged;
	}

	private void writeAllLogs() {
		try {
			OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(new File("sdcard/epLog.txt"), false));
			osw.write(log);
			osw.close();
		} catch (IOException __e) {
			__e.printStackTrace();
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		writeAllLogs();
		super.onCreate(savedInstanceState);
	}
}