package main;

import java.util.concurrent.atomic.AtomicInteger;

import mbcap.logic.MBCAPText;
import swarmprot.logic.SwarmProtText;
import uavController.UAVControllerThread;

/** This class contains general parameters of the application. */

public class Param {
	
	// The experiment is done in simulator or on a real UAV?
	public static final boolean IS_REAL_UAV = false;
	
	// Whether the experiment is mission based or not
	public static boolean simulationIsMissionBased;

	// Number of UAVs to be simulated
	public static int numUAVs;
	
	// Unique UAV identifier that must be used on protocols, based on MAC address (simple enumeration while in simulator)
	public static long[] id;
	
	// Number of UAVs that need a mission to be loaded
	public static AtomicInteger numMissionUAVs = new AtomicInteger();
	
	// Verbose logging?
	public static final boolean VERBOSE_LOGGING = false;
	
	// Store additional information in files?
	public static final boolean VERBOSE_STORE = true;
	
	// Array containing the UAV controllers. Useful to set a listener to detect when a waypoint is reached, if needed.
	public static UAVControllerThread[] controllers;

	// Running Operating System
	public static final int OS_WINDOWS = 0;
	public static final int OS_LINUX = 1;
	public static volatile int runningOperatingSystem;
	
	// Wind parameters
	public static final double DEFAULT_WIND_DIRECTION = 180.0;	// (degrees) North wind by default 
	public static final double DEFAULT_WIND_SPEED = 0.0;
	public static volatile double windDirection = Param.DEFAULT_WIND_DIRECTION;
	public static double windSpeed = Param.DEFAULT_WIND_SPEED;

	// Parameters used to detect when a UAV reaches the last waypoint
	public static final double LAST_WP_THRESHOLD = 1.0; // (m) Maximum distance considered
	
	// Statistics parameters
	public static long startTime; // (ms) experiment start in local time
	public static long[] testEndTime; // (ms) experiment finish in local time

	// Selected protocol
	public static volatile Protocol selectedProtocol;
	
	// Selected wireless model
	public static WirelessModel selectedWirelessModel;
	public static double fixedRange = 800.0;				// (m) Fixed range distance threshold
	public static final double FIXED_MAX_RANGE = 1500.0;	// (m) Fixed range maximum distance accepted
	
	// Simulator state
	public static volatile SimulatorState simStatus;
	
	// Simulator finite state machine states enumerator
	public enum SimulatorState {
		CONFIGURING(0),
		CONFIGURING_PROTOCOL(1),
		STARTING_UAVS(2),
		UAVS_CONFIGURED(3),
		SETUP_IN_PROGRESS(4),
		READY_FOR_TEST(5),
		TEST_IN_PROGRESS(6),
		TEST_FINISHED(7),
		SHUTTING_DOWN(8);
		
		private final int id;
		private SimulatorState(int id) {
			this.id = id;
		}
		public int getStateId() {
			return this.id;
		}
	}
	
	// Wireless models enumerator
	public enum WirelessModel {
		NONE(0, Text.WIRELESS_MODEL_NONE),					// Unrestricted model
		FIXED_RANGE(1, Text.WIRELESS_MODEL_FIXED_RANGE),	// Fixed distance model
		DISTANCE_5GHZ(2, Text.WIRELESS_MODEL_5GHZ);			// Real distance model based on WiFi 5.18 GHz (channel 36) 
		// New models should follow the increasing numeration. The method Tools.isInRange() must also be modified
		
		private final int id;
		private final String name;
		private WirelessModel(int id, String name) {
			this.id = id;
			this.name = name;
		}
		public int getId() {
			return this.id;
		}
		public String getName() {
			return this.name;
		}
		public static WirelessModel getHighestIdModel() {
			WirelessModel res = null;
			for (WirelessModel p : WirelessModel.values()) {
				if (res == null) {
					res = p;
				} else {
					if (p.getId() > res.getId()) {
						res = p;
					}
				}
			}
			return res;
		}
		public static String getModelNameById(int id) {
			String res = "";
			for (WirelessModel p : WirelessModel.values()) {
				if (p.getId() == id) {
					res = p.getName();
					break;
				}
			}
			return res;
		}
		public static WirelessModel getModelByName(String name) {
			WirelessModel res = null;
			for (WirelessModel p : WirelessModel.values()) {
				if (p.getName().equals(name)) {
					res = p;
					break;
				}
			}
			return res;
		}
	}
	
	// Protocols enumerator
	public enum Protocol {
		NONE(0, Text.PROTOCOL_NONE, true),			// Without protocol, only a mission based experiment can be done
		MBCAP_V1(1, MBCAPText.MBCAP_V1, true),	// Changes the prediction objective when the UAV reaches a waypoint
		MBCAP_V2(2, MBCAPText.MBCAP_V2, true),	// Adapts the prediction to the theoretical like a magnet
		MBCAP_V3(3, MBCAPText.MBCAP_V3, true),	// v2 taking the UAV acceleration into account
		MBCAP_V4(4, MBCAPText.MBCAP_V4, true),	// v3 with a variable acceleration
		SWARM_PROT_V1(5, SwarmProtText.PROTOCOL_TEXT, false);
		// New protocols should follow the increasing numeration

		private final int id;
		private final String name;
		private final boolean isMissionBased;
		private Protocol(int id, String name, boolean isMissionBased) {
			this.id = id;
			this.name= name;
			this.isMissionBased = isMissionBased;
		}
		public int getId() {
			return this.id;
		}
		public String getName() {
			return this.name;
		}
		public boolean isMissionBased() {
			return this.isMissionBased;
		}
		public static Protocol getHighestIdProtocol() {
			Protocol res = null;
			for (Protocol p : Protocol.values()) {
				if (Param.simulationIsMissionBased == p.isMissionBased) {
					if (res == null) {
						res = p;
					} else if (p.getId() > res.getId()) {
						res = p;
					}
				}
			}
			return res;
		}
		public static String getProtocolNameById(int id) {
			String res = "";
			for (Protocol p : Protocol.values()) {
				if (p.getId() == id) {
					res = p.getName();
					break;
				}
			}
			return res;
		}
		public static Protocol getProtocolByName(String name) {
			Protocol res = null;
			for (Protocol p : Protocol.values()) {
				if (p.getName().equals(name)) {
					res = p;
					break;
				}
			}
			return res;
		}
	}

	

	

	

}