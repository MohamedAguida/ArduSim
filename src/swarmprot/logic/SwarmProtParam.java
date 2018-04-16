package swarmprot.logic;

import api.pojo.GeoCoordinates;
import api.pojo.Point3D;

public class SwarmProtParam {
	public static final int posMaster = 0; // Position of master UAV into array of UAVs
	public static long idMaster; // Id real
	public static final String[] MAC = new String[] { "b8:27:eb:74:0c:d1", "00:c0:ca:90:32:05" };// MACs of master with standard format
	public static final long[] MACId = new long[] { 202481593486545L, 202481593486545L };// MACs of master with long format
	public static final long idMasterSimulation = 0;
	public static final int ALTITUDE_WAIT = 500; // Time between checks while take off (ms)
	public static double[] initial_speeds; // (m/s) Initial UAVs speed
	public static double masterHeading; // Master UAV Heading
	public static int initialDistanceBetweenUAV = 2; // Initial distance between uav's on simulation
	public static int initialDistanceBetweenUAVreal = 5; // Initial distance between uav's on real flight
	public static int firstStepAltitude = 3; // Altitude for the first step of take off

	// TCP parameters
	public static final int DGRAM_MAX_LENGTH = 1472; // (B) 1500-20-8 (MTU - IP - UDP)

	public static final String BROADCAST_IP_LOCAL = "127.0.0.1"; // Broadcast IP on simulator

	public static final String BROADCAST_IP_REAL = "192.168.1.255"; // Broadcast IP on real life
	public static int port = 14600; // Simulated broadcast port
	public static int portTalker = 15100; // Simulated broadcast port
	public static final int recTimeOut = 500; // The port is unlocked after this time (ms)

	// Protocol state included
	public static SwarmProtState[] state;

	// Waiting time in listening or reading threads
	public static final int waitState = 250;
	
	//Wait between ACK
	public static int swarmStateWait = 200; // (ms) Time between sends
	
	//Broadcast MAC comes from FFFFFFFFFFFF MAC address
	public static final long broadcastMAC = 281474976710655L;
	
	//Maximum number of waypoints
	public static final int maxWaypoints = 60;
	
	//Maximum number of waypoints reached
	public static final String maxWpMes = "Maximum number of waypoints reached, please use less than 60";
	
	//Matriz con missiones individuales de cada Dron
	public static Point3D[][] flightListPersonalized;
	public static Double[][] flightListPersonalizedAlt;
	
	//Matriz con missiones individuales de cada Dron
	public static GeoCoordinates[][] flightListPersonalizedGeo;
	public static Double[][] flightListPersonalizedAltGeo;
	
	//Matriz con numero de UAV y su idPrev y idNext en ese orden
	public static long[][] fightPrevNext;
	
	//Matriz con detección de ultimo punto de la Mission
	public static boolean[][] WpLast;
	
	//Distancia respecto al WP destino en la cual se acepta como que ha llegado a dicho punto (meters)
	public static final double acceptableRadiusDistanceWP = 2.0;
	
	//Distancia respecto del suelo para concluir que el experimento ha acabado
	public static final int landingAtiFinal = 1;
	
	//Velocidad maxima a la que se puede ir para determinar que esta aterrizado (m/s)
	public static final double speedLanding = 0.2;
	
	// Tiempo que espera el maestro a los esclavos en la fase START
	public static final int waitTimeForSlaves = 20000;
	
	//
	public static double missionHeading = 0;
	
	// Distance from which it is accepted that you have reached the WP
	public static double distToAcceptPointReached = 1.0;


	// SwarmProt finite state machine states enumerator
	public enum SwarmProtState {
		START((short) 1, SwarmProtText.START), 
		SEND_DATA((short) 2, SwarmProtText.SEND_DATA), 
		WAIT_LIST((short) 3, SwarmProtText.WAIT_LIST), 
		SEND_LIST((short) 4, SwarmProtText.SEND_LIST), 
		WAIT_TAKE_OFF((short) 5, SwarmProtText.WAIT_TAKE_OFF),
		SEND_TAKE_OFF((short) 6, SwarmProtText.SEND_TAKE_OFF),
		TAKING_OFF((short) 7, SwarmProtText.TAKING_OFF), 
		MOVE_TO_WP((short) 8, SwarmProtText.MOVE_TO_WP), 
		WP_REACHED((short) 9, SwarmProtText.WP_REACHED), 
		LANDING((short) 10, SwarmProtText.LANDING), 
		FINISH((short) 11, SwarmProtText.FINISH),;

		private final short id;
		private final String name;

		private SwarmProtState(short id, String name) {
			this.id = id;
			this.name = name;
		}

		public short getId() {
			return this.id;
		}

		public String getName() {
			return this.name;
		}

		public static SwarmProtState getSatateById(short id) {
			SwarmProtState res = null;
			for (SwarmProtState p : SwarmProtState.values()) {
				if (p.getId() == id) {
					res = p;
					break;
				}
			}
			return res;
		}
	}

}
