package com.protocols.mission.logic;

import com.api.*;
import com.api.formations.Formation;
import com.api.pojo.AtomicDoubleArray;
import com.api.pojo.location.LogPoint;
import com.api.pojo.location.Waypoint;
import com.protocols.followme.logic.FollowMeListenerThread;
import com.protocols.followme.logic.FollowMeTalkerThread;
import com.protocols.mbcap.pojo.ErrorPoint;
import com.protocols.mission.gui.MissionDialogApp;
import com.protocols.mission.gui.MissionSimProperties;
import com.setup.Param;
import com.setup.Text;
import com.setup.sim.logic.SimParam;
import com.uavController.UAVParam;
import es.upv.grc.mapper.Location2DGeo;
import es.upv.grc.mapper.Location2DUTM;
import es.upv.grc.mapper.Location3D;
import es.upv.grc.mapper.LocationNotReadyException;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.javatuples.Pair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.IntStream;

/** Implementation of the protocol Mission to allow the user to simply follow missions. It is based on MBCAP implementation.
 * <p>Developed by: Francisco Jos&eacute; Fabra Collado, from GRC research group in Universitat Polit&egrave;cnica de Val&egrave;ncia (Valencia, Spain).</p> */

public class MissionHelper extends ProtocolHelper {
	public volatile static double distance[][];
	public volatile static double rssi[][];
	public  static AtomicIntegerArray statusGlobal;
	public volatile static int cnt=0;
	public static AtomicInteger pauseIt;
//	public static AtomicInteger st[];
	//public volatile static int counterTest=0;
	public static ArrayList<Integer> maliciousUAVs;
	public double maliciousUAV ;
	public Random rand = new Random();
	@Override
	public void setProtocol() {
		this.protocolString = "Mission";
	}

	@Override
	public boolean loadMission() {
		return true;
	}

	@Override
	public JDialog openConfigurationDialog() { return null;}

	@Override
	public void openConfigurationDialogFX() {
		Platform.runLater(()->new MissionDialogApp().start(new Stage()));
	}

	@Override
	public void configurationCLI() {
		MissionSimProperties properties = new MissionSimProperties();
		ResourceBundle resources;
		try {
			FileInputStream fis = new FileInputStream(SimParam.protocolParamFile);
			resources = new PropertyResourceBundle(fis);
			fis.close();
			Properties p = new Properties();
			for(String key: resources.keySet()){
				if (key.equals("maliciousUAV")){
					maliciousUAV = Double.parseDouble(resources.getString(key)) ;
				}
				p.setProperty(key,resources.getString(key));
			}
			properties.storeParameters(p,resources);
		} catch (IOException e) {
			e.printStackTrace();
			ArduSimTools.warnGlobal(Text.LOADING_ERROR, Text.PROTOCOL_PARAMETERS_FILE_NOT_FOUND );
			System.exit(0);
		}
	}

	@Override
	public void initializeDataStructures() {}

	@Override
	public String setInitialState() {
		return null;
	}

	@Override
	public Pair<Location2DGeo, Double>[] setStartingLocation() {
		int numUAVs = API.getArduSim().getNumUAVs();
		Pair<Location2DGeo, Double>[] startingLocation = new Pair[numUAVs];

		com.api.MissionHelper missionHelper = API.getCopter(0).getMissionHelper();
		List<Waypoint>[] missions = missionHelper.getMissionsLoaded();
		// missions[0].get(0) is somewhere in Africa
		Location2DUTM start = missions[0].get(1).getUTM();
		Formation f = UAVParam.groundFormation.get();
		double yaw = 0;
		for(int i = 0;i<numUAVs;i++){
			try {
				startingLocation[i] = new Pair<>(f.get2DUTMLocation(start,i).getGeo(),yaw);
			} catch (LocationNotReadyException e) {
				e.printStackTrace();
				return null;
			}
		}
		return startingLocation;
	}

	@Override
	public boolean sendInitialConfiguration(int numUAV) { return true; }

	@Override
	public void startThreads() {
		int numUAVs = API.getArduSim().getNumUAVs();
		this.distance = new double[numUAVs][numUAVs];
		this.rssi = new double[numUAVs][numUAVs];
		this.statusGlobal = new AtomicIntegerArray(numUAVs);
		for (int i = 0;i<statusGlobal.length();i++){
			statusGlobal.set(i,0);
		}
		int numberOfMalicious = (int) (numUAVs*maliciousUAV);
		pauseIt = new AtomicInteger(0);
		//this.malicousUAVs = new int[numberOfMalicious];
		API.getGUI(0).log("number of malicious UAV:"+ numberOfMalicious);
		//IntStream UAVID=  rand.ints(numberOfMalicious,0, numUAVs);
		maliciousUAVs = new ArrayList<>();

		ArrayList<Integer> list = new ArrayList<>(numUAVs);
		if (numberOfMalicious == numUAVs) {
			for(int i = 0; i < numUAVs; i++) {
				list.add(i);
			}
		}
		else {
			for (int i = 5; i < numUAVs; i++) {
				list.add(i);
			}
		}

		Random rand = new Random();
		int  j=0 ;
		while( j<  numberOfMalicious) {
			int index = rand.nextInt(list.size());
			maliciousUAVs.add(list.get(index));
			list.remove(index);
			j++;
		}

		//this.maliciousUAVs = list.to;
		for (int i =0; i<maliciousUAVs.size();i++){
			API.getGUI(maliciousUAVs.get(i)).log("malicious UAV:"+ maliciousUAVs.get(i));
		}

		for (int i = 0; i < numUAVs; i++) {
			new MissionListenerThread(i).start();
		}
	}

	@Override
	public void setupActionPerformed() {
		int numUAVs = API.getArduSim().getNumUAVs();
		List<Thread> threads = new ArrayList<>();
		for(int i = 0;i<numUAVs;i++){
			Thread t = API.getCopter(i).takeOff(10, new TakeOffListener() {
				@Override
				public void onCompleteActionPerformed() {

				}

				@Override
				public void onFailure() {

				}
			});
			threads.add(t);
			t.start();
		}
		//wait for take off to be finished
		for(Thread t:threads){
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	public void startExperimentActionPerformed() throws FileNotFoundException {
		int numUAVs = API.getArduSim().getNumUAVs();
		GUI gui;
		Timer timer = new Timer();
		com.api.MissionHelper missionHelper = API.getCopter(0).getMissionHelper();
		List<Waypoint>[] missions = missionHelper.getMissionsLoaded();
		List<Thread> threads = new ArrayList<>();
		Formation f = UAVParam.groundFormation.get();
		for (int wp_index =2;wp_index<missions[0].size();wp_index++) {
			Waypoint wp = missions[0].get(wp_index);
			for(int numUAV=0;numUAV<numUAVs;numUAV++) {
				API.getGUI(numUAV).logUAV("Moving to WP: " + (wp_index-1));
				//API.getCopter(numUAV).setPlannedSpeed(numUAV*4+10);
				//gui = API.getGUI(numUAV);
				//gui.log("this is test from missionHelper: \r\n");
				//gui.log("The battery level of the UAV"+numUAV+" is:"+API.getCopter(numUAV).getBattery()+" \r\n");

				try {
					Location2DUTM locInSwarm = f.get2DUTMLocation(wp.getUTM(),numUAV);
					Location3D loc = new Location3D(locInSwarm.getGeo(),10);
					//timer.schedule(new MissionTalkerThread(numUAV),
					//		0,        //initial delay
					//		10*1000);  //subsequent rate}
					new MissionTalkerThread(numUAV).start();
					Thread t = API.getCopter(numUAV).moveTo(loc, new MoveToListener() {
						@Override
						public void onCompleteActionPerformed() {

						}

						@Override
						public void onFailure() {

						}
					});
					threads.add(t);
					t.start();
				} catch (LocationNotReadyException e) {
					e.printStackTrace();
				}
			}
			for(Thread t:threads){
				try {
					t.join(9000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		}

		for(int numUAV=0;numUAV<numUAVs;numUAV++){
			API.getCopter(numUAV).land();
		}
		String path = "/home/test/Desktop/results.json";


		JSONParser jsonParser = new JSONParser();

		try {
			Object obj = jsonParser.parse(new FileReader(path));
			JSONArray jsonArray = getJsonObjectOrJsonArray(obj);
			JSONArray malicDetect = new JSONArray();
			JSONArray malicSelect = new JSONArray();


			JSONObject json = new JSONObject();
			String maliciousDetected = "[";
			String maliciousSelected = "[";
			int counter = 0;
			int vote = API.getArduSim().getNumUAVs()/2;
			for (int i=0;i<statusGlobal.length();i++){
				if (statusGlobal.get(i)>=vote){
					malicDetect.add(i);
					//maliciousDetected = maliciousDetected + i +",";
					counter++;
				}
				API.getGUI(i).log("Status Global UAV"+i+":"+statusGlobal.get(i));
			}
			for(int i=0;i<maliciousUAVs.size();i++){
				malicSelect.add(maliciousUAVs.get(i));
				//maliciousSelected = maliciousSelected + maliciousUAVs.get(i) +",";
			}
			maliciousSelected = maliciousSelected + "]";
			maliciousDetected = maliciousDetected + "]";



			try {
				json.put("NumUAVs", numUAVs);
				json.put("MaliciousUAVs",maliciousUAV );
				json.put("NumMaliciousUAVs",(int) (numUAVs*maliciousUAV));
				json.put("NumMaliciousDetected",malicDetect.size());
				//json.put("MaliciousSelected",malicSelect );
				//json.put("MaliciousDetected",malicDetect );
			} catch (Exception e) {
				e.printStackTrace();
			}

			jsonArray.add(json);

			System.out.println(jsonArray);

			FileWriter file = new FileWriter(path);
			file.write(jsonArray.toJSONString());
			file.flush();
			file.close();

		} catch (Exception e) {
			e.printStackTrace();
		}


	}

	@Override
	public void forceExperimentEnd() {

	}

	public JSONArray getJsonObjectOrJsonArray(Object object){
		JSONArray jsonArray = new JSONArray();
		if (object instanceof Map){
			JSONObject jsonObject = new JSONObject();
			jsonObject.putAll((Map)object);
			jsonArray.add(jsonObject);
		}
		else if (object instanceof List){
			jsonArray.addAll((List)object);
		}
		return jsonArray;
	}

	@Override
	public String getExperimentResults() {
		return null;
	}

	@Override
	public String getExperimentConfiguration() {
		return null;
	}

	@Override
	public void logData(String folder, String baseFileName, long baseNanoTime) {
		// Logging to file the error predicting the location during the experiment (and the beacons itself if needed).
		ArduSim ardusim = API.getArduSim();
		int numUAVs = ardusim.getNumUAVs();
		@SuppressWarnings("unchecked")
		List<ErrorPoint>[] realUAVPaths = new ArrayList[numUAVs];

		// 1. UAV path calculus (only experiment path, and ignoring repeated positions)
		LogPoint realPostLocation, realPrevLocation;
		double time;
		int inTestState = Param.SimulatorState.TEST_IN_PROGRESS.getStateId();
		double x, y;
		for (int i=0; i<numUAVs; i++) {
			realPrevLocation = null;
			realUAVPaths[i] = new ArrayList<>();

			List<LogPoint> fullPath = ardusim.getUTMPath()[i];
			for (LogPoint logPoint : fullPath) {
				realPostLocation = logPoint;

				// Considers only not repeated locations and only generated during the experiment
				if (realPostLocation.getSimulatorState() == inTestState) {
					time = realPostLocation.getTime();
					x = realPostLocation.x;
					y = realPostLocation.y;
					if (realPrevLocation == null) {
						// First test location
						realUAVPaths[i].add(new ErrorPoint(0, x, y));
						if (realPostLocation.getTime() != 0) {
							realUAVPaths[i].add(new ErrorPoint(time, x, y));
						}
						realPrevLocation = realPostLocation;
					} else if (realPostLocation.x != realPrevLocation.x || realPostLocation.y != realPrevLocation.y || realPostLocation.z != realPrevLocation.z) {
						// Moved
						realUAVPaths[i].add(new ErrorPoint(realPostLocation.getTime(), x, y));
						realPrevLocation = realPostLocation;
					}
				}
			}
		}
	}

	@Override
	public void openPCCompanionDialog(JFrame PCCompanionFrame) {}
}
