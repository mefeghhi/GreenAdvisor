package ca.ualberta.ssrg.ga;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Scanner;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.BCELifier;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.xml.sax.SAXException;

public class Main {
	public static String toString(double[][] m) {
        String result = "";
        for(int i = 0; i < m.length; i++) {
            for(int j = 0; j < m[i].length; j++) {
                result += String.format("%11.2f", m[i][j]);
            }
            result += "\n";
        }
        return result;
    }

	public static void main(String[] args) throws NoHeadException, IOException, ParseException, GitAPIException, ClassNotFoundException, SQLException, java.text.ParseException, RuntimeException, SAXException, ParserConfigurationException, TransformerException, InterruptedException{

		GreenAdvisor ga = new GreenAdvisor();

		Scanner in = new Scanner(System.in);
		System.out.println("Please select target phase to execute [0 or 1 or 2]: ");
		int p = in.nextInt();

		if (p == 0) {
			// Phase 0: debug
			ga.findTouchedFunctionsAndInstrumentBothVersions();
		}
		if (p == 1) {
			// Phase 1
//			ga.startTestCycles(false, GreenAdvisor.COUNTS_STRACE_MODE, GreenAdvisor.BEFORE_VERSION);
			ga.startTestCycles(false, GreenAdvisor.COUNTS_STRACE_MODE, GreenAdvisor.AFTER_VERSION);
//			ga.startTestCycles(true, GreenAdvisor.TIMESTAMPS_STRACE_MODE, GreenAdvisor.BEFORE_VERSION);
//			ga.startTestCycles(true, GreenAdvisor.TIMESTAMPS_STRACE_MODE, GreenAdvisor.AFTER_VERSION);
		}

		if (p == 2) {
			// Phase 2
			HashSet<String> bad = new HashSet<>();
			HashSet<String> good = new HashSet<>();
			bad.add("de.danoeh.antennapod.activity.MainActivity:loadData");
			bad.add("de.danoeh.antennapod.activity.MainActivity:handleNavIntent");
			bad.add("de.danoeh.antennapod.activity.OnlineFeedViewActivity:parseFeed");
			bad.add("de.danoeh.antennapod.activity.MediaplayerActivity:onCreate");
			bad.add("de.danoeh.antennapod.fragment.QueueFragment:saveScrollPosition");
			bad.add("de.danoeh.antennapod.fragment.ExternalPlayerFragment:setupPlaybackController");
			bad.add("de.danoeh.antennapod.fragment.ItunesSearchFragment:onDestroy");
			bad.add("de.danoeh.antennapod.fragment.ItemlistFragment:refreshHeaderView");
			bad.add("de.danoeh.antennapod.fragment.ItemFragment:load");
			bad.add("de.danoeh.antennapod.adapter.NavListAdapter:getFeedView");
			
			good.add("de.danoeh.antennapod.activity.MainActivity:onResume");
			good.add("de.danoeh.antennapod.activity.MainActivity:onOptionsItemSelected");
			good.add("de.danoeh.antennapod.activity.OnlineFeedViewActivity:showFeedInformation");
			good.add("de.danoeh.antennapod.activity.MediaplayerActivity:onResume");
			good.add("de.danoeh.antennapod.fragment.QueueFragment:onFragmentLoaded");
			good.add("de.danoeh.antennapod.fragment.ExternalPlayerFragment:onResume");
			good.add("de.danoeh.antennapod.fragment.ItunesSearchFragment:loadToplist");
			good.add("de.danoeh.antennapod.fragment.ItemlistFragment:onListItemClick");
			good.add("de.danoeh.antennapod.fragment.ItemFragment:updateAppearance");
			good.add("de.danoeh.antennapod.adapter.NavListAdapter:getCount");
			ga.blameMethods(false, good, bad, false);
//			ga.blameMethodsKaran(good, bad);
		}
	}
	
}
