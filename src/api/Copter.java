package api;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.javatuples.Quintet;
import org.javatuples.Triplet;
import org.mavlink.messages.MAV_CMD;

import api.pojo.FlightMode;
import api.pojo.GeoCoordinates;
import api.pojo.Point3D;
import api.pojo.RCValues;
import api.pojo.UTMCoordinates;
import api.pojo.Waypoint;
import api.pojo.WaypointSimplified;
import main.ArduSimTools;
import main.Param;
import main.Text;
import mbcap.logic.MBCAPText;
import sim.board.BoardParam;
import sim.logic.SimParam;
import sim.pojo.IncomingMessage;
import uavController.UAVControllerThread;
import uavController.UAVParam;
import uavController.UAVParam.ControllerParam;

/** This class contains exclusively static methods to control the UAV "numUAV" in the arrays included in the application.
 * <p>Methods that start with "API" are single commands, while other methods are complex commands made of several of the previous commands. */

public class Copter {
	
	/** API: Sets a new value for a flight controller parameter.
	 * <p>Parameters that start with "SIM_" are only available on simulation.
	 * <p>Returns true if the command was successful. */
	public static boolean setParameter(int numUAV, ControllerParam parameter, double value) {
		UAVParam.newParam[numUAV] = parameter;
		UAVParam.newParamValue.set(numUAV, value);
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_SET_PARAM);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_1_PARAM) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_1_PARAM) {
			GUI.log(SimParam.prefix[numUAV] + Text.PARAMETER_ERROR_1 + " " + parameter.getId() + ".");
			return false;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.PARAMETER_1 + " " + parameter.getId() + " = " + value);
			return true;
		}
	}
	
	/** API: Gets the value of a flight controller parameter.
	 * <p>Parameters that start with "SIM_" are only available on simulation.
	 * <p>Returns the parameter value if the command was successful.
	 * <p>Returns null if an error happens. */
	public static Double getParameter(int numUAV, ControllerParam parameter) {
		UAVParam.newParam[numUAV] = parameter;
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_GET_PARAM);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_2_PARAM) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_2_PARAM) {
			GUI.log(SimParam.prefix[numUAV] + Text.PARAMETER_ERROR_2 + " " + parameter.getId() + ".");
			return null;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.PARAMETER_2 + " " + parameter.getId() + " = " + UAVParam.newParamValue.get(numUAV));
			return UAVParam.newParamValue.get(numUAV);
		}
	}

	/** API: Changes a UAV flight mode.
	 * <p>Returns true if the command was successful. */
	public static boolean setFlightMode(int numUAV, FlightMode mode) {
		UAVParam.newFlightMode[numUAV] = mode;
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_REQUEST_MODE);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_MODE) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_MODE) {
			GUI.log(SimParam.prefix[numUAV] + Text.FLIGHT_MODE_ERROR_1);
			return false;
		} else {
			long start = System.currentTimeMillis();
			boolean changed = false;
			FlightMode currentMode;
			while (!changed) {
				currentMode = UAVParam.flightMode.get(numUAV);
				if (currentMode.getCustomMode() == mode.getCustomMode()) {
					changed = true;
				} else {
					if (System.currentTimeMillis() - start > UAVParam.MODE_CHANGE_TIMEOUT) {
						return false;
					} else {
						Tools.waiting(UAVParam.COMMAND_WAIT);
					}
				}
			}
			return true;
		}
	}

	/** API: Gets the last flight mode value provided by a flight controller. */
	public static FlightMode getFlightMode(int numUAV) {
		return UAVParam.flightMode.get(numUAV);
	}
	
	/** API: Returns true if the UAV is flying. */
	public static boolean isFlying(int numUAV) {
		return !(Copter.getFlightMode(numUAV).getBaseMode() < UAVParam.MIN_MODE_TO_BE_FLYING);
	}
	
	/** API: Arms the engines.
	 * <p>Previously, you have to press the hardware switch for safety arm, if available.
	 * <p>The UAV must be in an armable flight mode (STABILIZE, LOITER, ALT_HOLD, GUIDED).
	 * <p>Returns true if the command was successful. */
	public static boolean armEngines(int numUAV) {
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_REQUEST_ARM);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_ARM) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_ARM) {
			GUI.log(SimParam.prefix[numUAV] + Text.ARM_ENGINES_ERROR);
			return false;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.ARM_ENGINES);
			return true;
		}
	}

	/** API: Takes off a UAV previously armed.
	 * <p>altitude. Target altitude over the ground.
	 * <p>The UAV must be armed and in GUIDED mode.
	 * <p>Returns true if the command was successful. */
	public static boolean guidedTakeOff(int numUAV, double altitude) {
		UAVParam.takeOffAltitude.set(numUAV, altitude);
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_REQUEST_TAKE_OFF);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_TAKE_OFF) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_TAKE_OFF) {
			GUI.log(SimParam.prefix[numUAV] + Text.TAKE_OFF_ERROR_2);
			return false;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.TAKE_OFF);
			return true;
		}
	}
	
	/** Takes off a UAV and starts the planned mission.
	 * <p>Previously, you have to press the hardware switch for safety arm, if available.
	 * <p>The UAV must be in an armable flight mode (STABILIZE, LOITER, ALT_HOLD, GUIDED).
	 * <p>Returns true if all the commands were successful. */
	public static boolean startMissionFromGround(int numUAV) {
		// Documentation says: While on the ground, 1st arm, 2nd auto mode, 3rd some throttle, and the mission begins
		//    If the copter is flying, the take off waypoint will be considered to be completed, and the UAV goes to the next waypoint
		if (armEngines(numUAV)
				&& setFlightMode(numUAV, FlightMode.AUTO)
				&& setHalfThrottle(numUAV)
				) {
			return true;
		}
		return false;
	}
	
	/** Takes off all the UAVs: changes mode to guided, arms engines, and then performs the guided take off.
	 * <p>Blocking method. It waits until all the UAVs reach 95% of the target altitude. */
	public static void takeOffAllUAVs() {
		List<Waypoint>[] missions = Tools.getLoadedMissions();
		for (int i = 0; i < Param.numUAVs; i++) {
			if (!Copter.setFlightMode(i, FlightMode.GUIDED) || !Copter.armEngines(i) || !Copter.guidedTakeOff(i, missions[i].get(1).getAltitude())) {
				GUI.exit(Text.TAKE_OFF_ERROR_1 + " " + Param.id[i]);
			}
		}
		// The application must wait until all UAVs reach the planned altitude
		double targetAltitude;
		for (int i = 0; i < Param.numUAVs; i++) {
			targetAltitude = missions[i].get(1).getAltitude();
			while (UAVParam.uavCurrentData[i].getZRelative() < 0.95 * targetAltitude) {
				if (Param.VERBOSE_LOGGING) {
					GUI.log(SimParam.prefix[i] + Text.ALTITUDE_TEXT
							+ " = " + String.format("%.2f", UAVParam.uavCurrentData[i].getZ())
							+ " " + Text.METERS);
				}
				Tools.waiting(UAVParam.ALTITUDE_WAIT);
			}
		}
	}
	
	/** Takes off until the target altitude: changes mode to guided, arms engines, and then performs the guided take off.
	 * <p>Blocking method. It waits until the UAV reaches 95% of the target altitude. */
	public static void takeOff(int numUAV, double altitude) {
		if (!setFlightMode(numUAV, FlightMode.GUIDED) || !armEngines(numUAV) || !guidedTakeOff(numUAV, altitude)) {
			GUI.exit(Text.TAKE_OFF_ERROR_1 + " " + Param.id[numUAV]);
		}
	
		// The application must wait until all UAVs reach the planned altitude
		while (UAVParam.uavCurrentData[numUAV].getZRelative() < 0.95 * altitude) {
			if (Param.VERBOSE_LOGGING) {
				GUI.log(SimParam.prefix[numUAV] + Text.ALTITUDE_TEXT
						+ " = " + String.format("%.2f", UAVParam.uavCurrentData[numUAV].getZ())
						+ " " + Text.METERS);
			}
			Tools.waiting(UAVParam.ALTITUDE_WAIT);
		}
	}
	
	/** API: Changes the planned flight speed (m/s).
	 * <p>Returns true if the command was successful. */
	public static boolean setSpeed(int numUAV, double speed) {
		UAVParam.newSpeed[numUAV] = speed;
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_SET_SPEED);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_SET_SPEED) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_SET_SPEED) {
			GUI.log(SimParam.prefix[numUAV] + Text.SPEED_2_ERROR);
			return false;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.SPEED_2 + " = " + speed);
			return true;
		}
	}

	/** API: Modifies the current waypoint of the mission stored on the UAV.
	 * <p>Returns true if the command was successful. */
	public static boolean setCurrentWaypoint(int numUAV, int currentWP) {
		UAVParam.newCurrentWaypoint[numUAV] = currentWP;
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_SET_CURRENT_WP);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_CURRENT_WP) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_CURRENT_WP) {
			GUI.log(SimParam.prefix[numUAV] + Text.CURRENT_WAYPOINT_ERROR);
			return false;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.CURRENT_WAYPOINT + " = " + currentWP);
			return true;
		}
	}

	/** Suspends temporally a mission in AUTO mode, entering on loiter flight mode to force a fast stop.
	 * <p>Returns true if all the commands were successful. */
	public static boolean stopUAV(int numUAV) {
		if (Copter.setHalfThrottle(numUAV) && setFlightMode(numUAV, FlightMode.LOITER)) {
			long time = System.nanoTime();
			while (UAVParam.uavCurrentData[numUAV].getSpeed() > UAVParam.STABILIZATION_SPEED) {
				Tools.waiting(UAVParam.STABILIZATION_WAIT_TIME);
				if (System.nanoTime() - time > UAVParam.STABILIZATION_TIMEOUT) {
					GUI.log(SimParam.prefix[numUAV] + Text.STOP_ERROR_1);
					return false;
				}
			}

			GUI.log(SimParam.prefix[numUAV] + Text.STOP);
			return true;
		}
		GUI.log(SimParam.prefix[numUAV] + Text.STOP_ERROR_2);
		return false;
	}

	/** API: Moves the throttle stick to half power using the corresponding RC channel.
	 * <p>Returns true if the command was successful.
	 * <p>Useful for starting auto flight when being on the ground, or to stabilize altitude when going out of auto mode. */
	public static boolean setHalfThrottle(int numUAV) {
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_THROTTLE_ON);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_THROTTLE_ON_ERROR) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_THROTTLE_ON_ERROR) {
			GUI.log(SimParam.prefix[numUAV] + Text.STABILIZE_ALTITUDE_ERROR);
			return false;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.STABILIZE_ALTITUDE);
			return true;
		}
	}
	
	/** API: Overrides the remote control output.
	 * <p>Channel values in microseconds. Typically chan1=roll, chan2=pitch, chan3=throttle, chan4=yaw.
	 * <p>Value 0 means that the control of that channel must be returned to the RC radio.
	 * <p>Value UINT16_MAX means to ignore this field.
	 * <p>Standard modulation: 1000 (0%) - 2000 (100%).
	 * <p>Values are not applied immediately, but each time a message is received from the flight controller. */
	public static void channelsOverride(int numUAV, int roll, int pitch, int throttle, int yaw) {
		UAVParam.rcs[numUAV].set(new RCValues(roll, pitch, throttle, yaw));//TODO analizar la frecuencia de recepción de mensajes
		// y analizar si vale la pena hacer la lectura no bloqueante para enviar esto con más frecuencia y de otra forma
		// ¿con qué frecuencia aceptaremos el channels override? Hay experimentos que indican que algunos valores se ignoran
		// si se envian en intervalos menores a 0.393 segundos (quizá una cola fifo con timeout facilite resolver el problema)
	}

	/** API: Moves the UAV to a new position.
	 * <p>geo. Geographic coordinates the UAV has to move to.
	 * <p>relAltitude. Relative altitude the UAV has to move to.
	 * <p>destThreshold. Horizontal distance from the destination to assert that the UAV has reached there.
	 * <p>altThreshold. Vertical distance from the destination to assert that the UAV has reached there.
	 * <p>Blocking method.
	 * <p>Returns true if the command was successful.
	 * <p>The UAV must be in guided mode. */
	public static boolean moveUAV(int numUAV, GeoCoordinates geo, float relAltitude, double destThreshold, double altThreshold) {
		UAVParam.newLocation[numUAV][0] = (float)geo.latitude;
		UAVParam.newLocation[numUAV][1] = (float)geo.longitude;
		UAVParam.newLocation[numUAV][2] = relAltitude;
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_MOVE_UAV);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_MOVE_UAV_ERROR) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_MOVE_UAV_ERROR) {
			GUI.log(SimParam.prefix[numUAV] + Text.MOVING_ERROR_1);
			return false;
		} else {
			UTMCoordinates utm = Tools.geoToUTM(geo.latitude, geo.longitude);
			Point2D.Double destination = new Point2D.Double(utm.Easting, utm.Northing);
			// Once the command is issued, we have to wait until the UAV approaches to destination
			while (UAVParam.uavCurrentData[numUAV].getUTMLocation().distance(destination) > destThreshold
					|| Math.abs(relAltitude - UAVParam.uavCurrentData[numUAV].getZRelative()) > altThreshold) {
				Tools.waiting(UAVParam.STABILIZATION_WAIT_TIME);
			}
			return true;
		}
	}

	/** API: Removes the current mission from the UAV.
	 * <p>Returns true if the command was successful. */
	public static boolean clearMission(int numUAV) {
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_CLEAR_WP_LIST);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_CLEAR_WP_LIST) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_CLEAR_WP_LIST) {
			GUI.log(SimParam.prefix[numUAV] + Text.MISSION_DELETE_ERROR);
			return false;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.MISSION_DELETE);
			return true;
		}
	}

	/** API: Sends a new mission to the UAV.
	 * <p>Returns true if the command was successful.
	 * <p>The waypoint 0 must be the current coordinates retrieved from the controller.
	 * <p>The waypoint 1 must be take off.
	 * <p>The last waypoint can be land or RTL. */
	public static boolean sendMission(int numUAV, List<Waypoint> list) {
		if (list == null || list.size() < 2) {
			GUI.log(SimParam.prefix[numUAV] + Text.MISSION_SENT_ERROR_1);
			return false;
		}
		// There is a minimum altitude to fly (waypoint 0 is home)
		if (list.get(1).getAltitude() < UAVParam.MIN_FLYING_ALTITUDE) {
			GUI.log(SimParam.prefix[numUAV] + Text.MISSION_SENT_ERROR_2 + "(" + UAVParam.MIN_FLYING_ALTITUDE + " " + Text.METERS+ ").");
			return false;
		}
		int current = 0;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).isCurrent()) {
				current = i;
			}
		}
		// Specify the current waypoint
		UAVParam.currentWaypoint.set(numUAV, current);
		UAVParam.newCurrentWaypoint[numUAV] = current;
		// Relative altitude calculus to take off
		UAVParam.takeOffAltitude.set(numUAV, list.get(1).getAltitude());

		UAVParam.currentGeoMission[numUAV].clear();
		for (int i = 0; i < list.size(); i++) {
			UAVParam.currentGeoMission[numUAV].add(list.get(i).clone());
		}
		Point2D.Double geo = UAVParam.uavCurrentData[numUAV].getGeoLocation();
		// The take off waypoint must be modified to include current coordinates
		UAVParam.currentGeoMission[numUAV].get(1).setLatitude(geo.y);
		UAVParam.currentGeoMission[numUAV].get(1).setLongitude(geo.x);
		
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_SEND_WPS);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_SENDING_WPS) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_SENDING_WPS) {
			GUI.log(SimParam.prefix[numUAV] + Text.MISSION_SENT_ERROR_3);
			return false;
		} else {
			GUI.log(SimParam.prefix[numUAV] + Text.MISSION_SENT);
			return true;
		}
	}
	
	/** API: Retrieves the mission stored on the UAV.
	 * <p>Returns true if the command was successful.
	 * <p>New value available on UAVParam.currentGeoMission[numUAV].
	 * <p>Simplified version of the mission in UTM coordinates available on UAVParam.missionUTMSimplified[numUAV]. */
	public static boolean retrieveMission(int numUAV) {
		UAVParam.MAVStatus.set(numUAV, UAVParam.MAV_STATUS_REQUEST_WP_LIST);
		while (UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_OK
				&& UAVParam.MAVStatus.get(numUAV) != UAVParam.MAV_STATUS_ERROR_REQUEST_WP_LIST) {
			Tools.waiting(UAVParam.COMMAND_WAIT);
		}
		if (UAVParam.MAVStatus.get(numUAV) == UAVParam.MAV_STATUS_ERROR_SENDING_WPS) {
			GUI.log(SimParam.prefix[numUAV] + Text.MISSION_GET_ERROR);
			return false;
		} else {
			Copter.simplifyMission(numUAV);
			GUI.log(SimParam.prefix[numUAV] + Text.MISSION_GET);
			return true;
		}
	}
	
	/** Creates the simplified mission shown on screen, and forces view to rescale.*/
	private static void simplifyMission(int numUAV) {
		List<WaypointSimplified> missionUTMSimplified = new ArrayList<WaypointSimplified>();
	
		// Hypothesis:
		//   The first take off waypoint retrieved from the UAV is used as "home"
		//   The "home" coordinates are no modified along the mission
		WaypointSimplified first = null;
		boolean foundFirst = false;
		Waypoint wp;
		UTMCoordinates utm;
		for (int i=0; i<UAVParam.currentGeoMission[numUAV].size(); i++) {
			wp = UAVParam.currentGeoMission[numUAV].get(i);
			switch (wp.getCommand()) {
			case MAV_CMD.MAV_CMD_NAV_WAYPOINT:
			case MAV_CMD.MAV_CMD_NAV_LOITER_UNLIM:
			case MAV_CMD.MAV_CMD_NAV_LOITER_TURNS:
			case MAV_CMD.MAV_CMD_NAV_LOITER_TIME:
			case MAV_CMD.MAV_CMD_NAV_SPLINE_WAYPOINT:
			case MAV_CMD.MAV_CMD_PAYLOAD_PREPARE_DEPLOY:
			case MAV_CMD.MAV_CMD_NAV_LOITER_TO_ALT:
			case MAV_CMD.MAV_CMD_NAV_LAND:
				utm = Tools.geoToUTM(wp.getLatitude(), wp.getLongitude());
				WaypointSimplified swp = new WaypointSimplified(wp.getNumSeq(), utm.Easting, utm.Northing, wp.getAltitude());
				missionUTMSimplified.add(swp);
				break;
			case MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH:
				if (!foundFirst) {
					GUI.log(Text.SIMPLIFYING_WAYPOINT_LIST_ERROR + Param.id[numUAV]);
				} else {
					WaypointSimplified s = new WaypointSimplified(wp.getNumSeq(),
							first.x, first.y, UAVParam.RTLAltitude[numUAV]);
					missionUTMSimplified.add(s);
				}
				break;
			case MAV_CMD.MAV_CMD_NAV_TAKEOFF:
				// The geographic coordinates have been set by the flight controller
				utm = Tools.geoToUTM(wp.getLatitude(), wp.getLongitude());
				WaypointSimplified twp = new WaypointSimplified(wp.getNumSeq(), utm.Easting, utm.Northing, wp.getAltitude());
				missionUTMSimplified.add(twp);
				if (!foundFirst) {
					utm = Tools.geoToUTM(wp.getLatitude(), wp.getLongitude());
					first = new WaypointSimplified(wp.getNumSeq(), utm.Easting, utm.Northing, wp.getAltitude());
					foundFirst = true;
				}
				break;
			}
		}
		UAVParam.missionUTMSimplified.set(numUAV, missionUTMSimplified);
	}
	
	/** Deletes the current mission of the UAV, sends a new one, and gets it to be shown on the GUI.
	 * <p>Blocking method.
	 * <p>Must be used only once per UAV, and if the UAV must follow a mission.
	 * <p>Returns true if all the commands were successful.*/
	public static boolean cleanAndSendMissionToUAV(int numUAV, List<Waypoint> mission) {
		boolean success = false;
		if (Copter.clearMission(numUAV)
				&& Copter.sendMission(numUAV, mission)
				&& Copter.retrieveMission(numUAV)
				&& Copter.setCurrentWaypoint(numUAV, 0)) {
			Param.numMissionUAVs.incrementAndGet();
			if (!Tools.isRealUAV()) {
				BoardParam.rescaleQueries.incrementAndGet();
			}
			success = true;
		}
		return success;
	}
	
	/** Ads a Class as listener for the event: waypoint reached (more than one can be set). */
	public static void setWaypointReachedListener(WaypointReachedListener listener) {
		ArduSimTools.listeners.add(listener);
	}
	
	/** API: Gets the current waypoint for a UAV.
	 * <p><p>Use only when the UAV is performing a planned mission. */
	public static int getCurrentWaypoint(int numUAV) {
		return UAVParam.currentWaypoint.get(numUAV);
	}
	
	/** API: Identifies if the UAV has reached the last waypoint of the mission.
	 * <p>Use only when the UAV is performing a planned mission. */
	public static boolean isLastWaypointReached(int numUAV) {
		return UAVParam.lastWaypointReached[numUAV];
	}
	
	/** Lands the UAV if it is close enough to the last waypoint.
	 * <p>Use only when the UAV is performing a planned mission. */
	public static void landIfMissionEnded(int numUAV) {
		String prefix = GUI.getUAVPrefix(numUAV);
		List<WaypointSimplified> mission = Tools.getUAVMissionSimplified(numUAV);
		FlightMode mode = Copter.getFlightMode(numUAV);
		int currentWaypoint = Copter.getCurrentWaypoint(numUAV);
		if (mission != null
				&& mode != FlightMode.LAND_ARMED
				&& mode != FlightMode.LAND
				&& currentWaypoint > 0
				&& currentWaypoint == mission.get(mission.size()-1).numSeq) {
			
			if (!UAVParam.lastWaypointReached[numUAV]) {
				UAVParam.lastWaypointReached[numUAV] = true;
				GUI.log(prefix + Text.LAST_WAYPOINT_REACHED);
			}
			
			if (Copter.getUTMLocation(numUAV).distance(mission.get(mission.size()-1)) < UAVParam.LAST_WP_THRESHOLD) {
				List<Waypoint> missionGeo = Tools.getUAVMission(numUAV);
				int command = missionGeo.get(missionGeo.size() - 1).getCommand();
				// There is no need of landing when the last waypoint is the command LAND or when it is RTL under some circumstances
				if (command != MAV_CMD.MAV_CMD_NAV_LAND
						&& !(command == MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH && UAVParam.RTLAltitudeFinal[numUAV] == 0)) {
					// Land the UAV when reaches the last waypoint and mark as finished
					if (Copter.setFlightMode(numUAV, FlightMode.LAND_ARMED)) {
					} else {
						GUI.log(prefix + Text.LAND_ERROR);
					}
				}
			}
		}
	}
	
	
	
	
	/** Method that provides the controller thread of an specific UAV.
	 * <p>Temporary ussage for debugging purposes.
	 * <p>HANDLE WITH CARE.*/
	public static UAVControllerThread getController(int numUAV) {
		return Param.controllers[numUAV];
	}
	
	/** API: Sends a message to the other UAVs.
	 * <p>Blocking method.*/
	public static void sendBroadcastMessage(int numUAV, byte[] message) {
		if (Param.IS_REAL_UAV) {
			UAVParam.sendPacket[numUAV].setData(message);
			try {
				UAVParam.sendSocket[numUAV].send(UAVParam.sendPacket[numUAV]);
				UAVParam.sentPacket[numUAV]++;
			} catch (IOException e) {
				GUI.log(SimParam.prefix[numUAV] + MBCAPText.ERROR_BEACON);
			}
		} else {
			long now = System.nanoTime();
			// 1. Can not send until the last transmission has finished
			IncomingMessage prevMessage = UAVParam.prevSentMessage.get(numUAV);
			if (prevMessage != null) {
				boolean messageWaited = false;
				while (prevMessage.end > now) {
					Tools.waiting(UAVParam.MESSAGE_WAITING_TIME);
					if (!messageWaited) {
						UAVParam.packetWaitedPrevSending[numUAV]++;
						messageWaited = true;
					}
					now = System.nanoTime();
				}
			}
			
			// 2. Wait if packet transmissions are detected in the UAV range (carrier sensing)
			boolean mediaIsAvailable = false;
			boolean messageWaited = false;
			if (UAVParam.carrierSensingEnabled) {
				while (!mediaIsAvailable) {
					boolean wait = false;
					now = System.nanoTime();
					IncomingMessage prevMessageOther;
					for (int i = 0; i < Param.numUAVs && !wait; i++) {
						prevMessageOther = UAVParam.prevSentMessage.get(i);
						if (i != numUAV && UAVParam.isInRange[numUAV][i].get() && prevMessageOther != null && prevMessageOther.end > now) {
							wait = true;
							if (!messageWaited) {
								UAVParam.packetWaitedMediaAvailable[numUAV]++;
								messageWaited = true;
							}
						}
					}
					if (wait) {
						Tools.waiting(UAVParam.MESSAGE_WAITING_TIME);
					} else {
						mediaIsAvailable = true;
					}
				}
			}
			now = System.nanoTime();
			
			// 3. Send the packet to all UAVs but the sender
			IncomingMessage prevMessageOther;
			IncomingMessage sendingMessage = new IncomingMessage(numUAV, now, message);
			UAVParam.sentPacket[numUAV]++;
			UAVParam.prevSentMessage.set(numUAV, sendingMessage);
			for (int i = 0; i < Param.numUAVs; i++) {
				if (i != numUAV) {
					if(UAVParam.isInRange[numUAV][i].get()) {
						prevMessageOther = UAVParam.prevSentMessage.get(i);
						// The message can not be received if the destination UAV is already sending
						if (prevMessageOther == null || prevMessageOther.end <= now) {
							if (UAVParam.pCollisionEnabled) {
								if (UAVParam.vBufferUsedSpace.get(i) + message.length <= UAVParam.receivingvBufferSize) {
									UAVParam.vBuffer[i].add(sendingMessage.clone());
									UAVParam.vBufferUsedSpace.addAndGet(i, message.length);
									UAVParam.successfullyReceived.incrementAndGet(i);
								} else {
									UAVParam.receiverVirtualQueueFull.incrementAndGet(i);
								}
							} else {
								// Add to destination queue if it fits and, and update the media occupancy time
								//	As collision detection is disabled and both variables are read by one thread and wrote by many:
								//	a) The inserted elements in mBuffer are no longer sorted
								//	b) and maxTEndTime must be accessed on a synchronized way
								if (UAVParam.mBuffer[i].offerLast(sendingMessage.clone())) {
									UAVParam.successfullyReceived.incrementAndGet(i);
								} else {
									UAVParam.receiverQueueFull.incrementAndGet(i);
								}
							}
						} else {
							UAVParam.receiverWasSending.incrementAndGet(i);
						}
					} else {
						UAVParam.receiverOutOfRange.incrementAndGet(i);
					}
				}
			}
		}
	}
	
	/** API: Receives a message from another UAV.
	 * <p>Blocking method.
	 * <p>Returns null if a fatal error with the socket happens. */
	public static byte[] receiveMessage(int numUAV) {
		if (Param.IS_REAL_UAV) {
			UAVParam.receivePacket[numUAV].setData(new byte[Tools.DATAGRAM_MAX_LENGTH], 0, Tools.DATAGRAM_MAX_LENGTH);
			try {
				UAVParam.receiveSocket[numUAV].receive(UAVParam.receivePacket[numUAV]);
				UAVParam.receivedPacket[numUAV]++;
				return UAVParam.receivePacket[numUAV].getData();
			} catch (IOException e) { return null; }
		} else {
			if (UAVParam.pCollisionEnabled) {
				byte[] receivedBuffer = null;
				while (receivedBuffer == null) {
					if (UAVParam.mBuffer[numUAV].isEmpty()) {
						// Check for collisions
						// 1. Wait until at least one message is available
						if (UAVParam.vBuffer[numUAV].isEmpty()) {
							Tools.waiting(UAVParam.MESSAGE_WAITING_TIME);
						} else {
							// 2. First iteration through the virtual buffer to check collisions between messages
							Iterator<IncomingMessage> it = UAVParam.vBuffer[numUAV].iterator();
							IncomingMessage prev, pos, next;
							prev = it.next();
							prev.checked = true;
							// 2.1. Waiting until the first message is transmitted
							//		Meanwhile, another message could be set as the first one, but it will be detected on the second iteration
							//		and all the process will be repeated again
							long now = System.nanoTime();
							while (prev.end > now) {
								Tools.waiting(UAVParam.MESSAGE_WAITING_TIME);
								now = System.nanoTime();
							}
							// 2.2. Update the late completely received message finishing time
							if (prev.end > UAVParam.maxCompletedTEndTime[numUAV]) {
								UAVParam.maxCompletedTEndTime[numUAV] = prev.end;
							}
							// 2.3. Iteration over the rest of the elements to check collisions
							while (it.hasNext()) {
								pos = it.next();
								pos.checked = true;
								// Collides with the previous message?
								if (pos.start <= UAVParam.maxCompletedTEndTime[numUAV]) {
									prev.overlapped = true;
									pos.overlapped = true;
								}
								// This message is being transmitted?
								if (pos.end > now) {
									// As the message has not been fully transmitted, we stop the iteration, ignoring this message on the second iteration
									pos.checked = false;
									if (pos.overlapped) {
										prev.checked = false;
									}
									break;
								} else {
									// Update the late completely received message finishing time
									if (pos.end > UAVParam.maxCompletedTEndTime[numUAV]) {
										UAVParam.maxCompletedTEndTime[numUAV] = pos.end;
									}
									prev = pos;
								}
							}
							// 3. Second iteration discarding collided messages and storing received messages on the FIFO buffer
							it = UAVParam.vBuffer[numUAV].iterator();
							while (it.hasNext()) {
								next = it.next();
								if (next.checked) {
									it.remove();
									UAVParam.receivedPacket[numUAV]++;
									UAVParam.vBufferUsedSpace.addAndGet(numUAV, -next.message.length);
									if (!next.overlapped) {
										if (UAVParam.mBuffer[numUAV].offerLast(next)) {
											UAVParam.successfullyEnqueued[numUAV]++;
										} else {
											UAVParam.receiverQueueFull.incrementAndGet(numUAV);
										}
									} else {
										UAVParam.discardedForCollision[numUAV]++;
									}
								} else {
									break;
								}
							}
						}
					} else {
						// Wait for transmission end
						IncomingMessage message = UAVParam.mBuffer[numUAV].peekFirst();
						long now = System.nanoTime();
						while (message.end > now) {
							Tools.waiting(UAVParam.MESSAGE_WAITING_TIME);
						}
						receivedBuffer = UAVParam.mBuffer[numUAV].pollFirst().message;
					}
				}
				return receivedBuffer;
			} else {
				while (UAVParam.mBuffer[numUAV].isEmpty()) {
					Tools.waiting(UAVParam.MESSAGE_WAITING_TIME);
				}
				UAVParam.receivedPacket[numUAV]++;
				return UAVParam.mBuffer[numUAV].pollFirst().message;
			}
		}
	}
	
	/** API: Gets the planned speed. */
	public static double getPlannedSpeed(int numUAV) {
		return UAVParam.initialSpeeds[numUAV];
	}

	/** API: Provides the latest received data from the flight controller.
	 * <p>Long. time.
	 * <p>Point2D.Double. UTM coordinates.
	 * <p>double. Absolute altitude.
	 * <p>double. Speed.
	 * <p>double. Acceleration. */
	public static Quintet<Long, java.awt.geom.Point2D.Double, Double, Double, Double> getData(int numUAV) {
		return UAVParam.uavCurrentData[numUAV].getData();
	}

	/** API: Provides the latest location in UTM meters (x,y) received from the flight controller. */
	public static Point2D.Double getUTMLocation(int numUAV) {
		return UAVParam.uavCurrentData[numUAV].getUTMLocation();
	}

	/** API: Provides the latest location in Geographic coordinates (x=longitude,y=latitude) received from the flight controller. */
	public static Point2D.Double getGeoLocation(int numUAV) {
		return UAVParam.uavCurrentData[numUAV].getGeoLocation();
	}
	
	/** API: Gets n last known locations (x,y,z) of the UAV in UTM coordinates (relative altitude).
	 * <p>The locations time increases with the position in the array. */
	public static Point3D[] getLastKnownLocations(int numUAV) {
		return UAVParam.lastLocations[numUAV].getLastPositions();
	}

	/** API: Provides the latest relative altitude from the ground (m) received from the flight controller. */
	public static double getZRelative (int numUAV) {
		return UAVParam.uavCurrentData[numUAV].getZRelative();
	}

	/** API: Provides the latest altitude (m) received from the flight controller. */
	public static double getZ (int numUAV) {
		return UAVParam.uavCurrentData[numUAV].getZ();
	}

	/** API: Provides the latest ground speed (m/s) received from the flight controller. */
	public static double getSpeed (int numUAV) {
		return UAVParam.uavCurrentData[numUAV].getSpeed();
	}

	/** API: Provides the latest three axes components of the speed (m/s) reveived from the flight controller. */
	public static Triplet<Double, Double, Double> getSpeeds(int numUAV) {
		return UAVParam.uavCurrentData[numUAV].getSpeeds();
	}

	/** API: Provides the latest heading (rad) received from the flight controller. */
	public static double getHeading (int numUAV) {
		return UAVParam.uavCurrentData[numUAV].getHeading();
	}

	

	
}