package swarmprot.logic;

import mbcap.logic.MBCAPText;

/** This class contains text descriptions for messages used by this protocol. */

public class SwarmProtText {
	
	public static final String PROTOCOL_TEXT = "Swarm protocol";
	public static final String SEND_MISSION_ERROR = "Error sending the mission to the UAV";
	public static final String LOAD_MISSION_KML = "Loading the .kml file into the master dron";
	public static final String CONFIGURE_MASTER_MISSION = "Set up the mission in the master dron";
	public static final String LANDING = "Landing all the drones";
	public static final String LANDING_ERROR = "Impossible to make the landing";
	public static final String ALL_LANDING = "All the Drones are landing";
	public static final String START_MISSION_ERROR = "Error starting the mission of the UAV";
	public static final String ENABLING = "Swarm protocol enabled...";
	
	//Simulator states
	public static final String START = "";
	public static final String SEND_DATA ="" ;
	public static final String WAIT_LIST = "";
	public static final String SEND_LIST = "";
	public static final String WAIT_TAKE_OFF = "";
	public static final String TAKING_OFF = "";
	public static final String MOTE_TO_WP = "";
	public static final String WP_REACHED = "";
	public static final String LANDING_UAV = "";
	public static final String FINISH = "";

}