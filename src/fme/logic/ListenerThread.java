package fme.logic;

import static fme.pojo.State.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.javatuples.Pair;
import org.javatuples.Triplet;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import api.Copter;
import api.GUI;
import api.Tools;
import api.pojo.FlightMode;
import api.pojo.Location2D;
import api.pojo.UTMCoordinates;
import api.pojo.formations.FlightFormation;
import api.pojo.formations.FlightFormation.Formation;
import fme.pojo.Message;
import main.Text;

/** Developed by: Francisco José Fabra Collado, from GRC research group in Universitat Politècnica de València (Valencia, Spain). */

public class ListenerThread extends Thread {
	
	private int numUAV;
	private long selfId;
	private boolean isMaster;
	
	byte[] inBuffer;
	Input input;

	@SuppressWarnings("unused")
	private ListenerThread() {}
	
	public ListenerThread(int numUAV) {
		this.numUAV = numUAV;
		this.selfId = Tools.getIdFromPos(numUAV);
		this.isMaster = FMeHelper.isMaster(numUAV);
		
		this.inBuffer = new byte[Tools.DATAGRAM_MAX_LENGTH];
		this.input = new Input(inBuffer);
	}

	@Override
	public void run() {
		while (!Tools.areUAVsAvailable()) {
			Tools.waiting(FMeParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** START PHASE */
		Map<Long, UTMCoordinates> UAVsDetected = null;
		GUI.log(numUAV, FMeText.START);
		if (this.isMaster) {
			GUI.logVerbose(numUAV, FMeText.MASTER_START_LISTENER);
			UAVsDetected = new HashMap<Long, UTMCoordinates>();	// Detecting UAVs
			int numUAVsDetected = 0;
			Long idSlave;
			UTMCoordinates slaveLocation;
			while (FMeParam.state.get(numUAV) == START) {
				inBuffer = Copter.receiveMessage(numUAV, FMeParam.RECEIVING_TIMEOUT);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();
					
					if (type == Message.HELLO) {
						// Read id (MAC on real UAV) of UAVs and write it into Map if is not duplicated
						idSlave = input.readLong();
						slaveLocation = new UTMCoordinates(input.readDouble(), input.readDouble());
						// ADD to Map the new UAV detected
						UAVsDetected.put(idSlave, slaveLocation);
						if (UAVsDetected.size() > numUAVsDetected) {
							numUAVsDetected = UAVsDetected.size();
							GUI.log(FMeText.MASTER_DETECTED_UAVS + numUAVsDetected);
						}
					}
				}
				// Coordination with ArduSim
				if (Tools.isSetupInProgress() || Tools.isSetupFinished()) {// The setup could be too fast
					FMeParam.state.set(numUAV, SETUP);
				}
			}
		} else {
			GUI.logVerbose(numUAV, FMeText.LISTENER_WAITING);
			while (FMeParam.state.get(numUAV) == START) {
				// Discard message
				Copter.receiveMessage(numUAV, FMeParam.RECEIVING_TIMEOUT);
				// Coordination with ArduSim
				if (Tools.isSetupInProgress() || Tools.isSetupFinished()) {
					FMeParam.state.set(numUAV, SETUP);
				}
			}
		}
		
		/** SETUP PHASE */
		GUI.log(numUAV, FMeText.SETUP);
		GUI.updateProtocolState(numUAV, FMeText.SETUP);
		long lastReceivedReadyToFly = 0;	// Used in slaves to detect the end of this phase
		Map<Long, Long> acks = null;
		int numSlaves = 0;
		FlightFormation flightFormation = null;
		Location2D targetLocation = null;
		double takeoffAltitudePhase1 = 0;
		double takeoffAltitudePhase2 = 0;
		long idPrev = 0;					// Identifier of the previous UAV in the takeoff sequence
		int numUAVs = 0;					// Number of UAVs in the formation, including the master UAV
		int flightFormationPosition = 0;	// Position of the master UAV in the flight formation
		if (this.isMaster) {
			numSlaves = UAVsDetected.size();
			Map<Long, UTMCoordinates> groundLocations = new HashMap<Long, UTMCoordinates>();
			groundLocations.putAll(UAVsDetected);
			groundLocations.put(this.selfId, Copter.getUTMLocation(numUAV));	// Adding master UAV
			
			// 1. Get the location and yaw of the master UAV, and the take off altitude
			Location2D masterLocation;
			double masterYaw;
			if (Tools.getArduSimRole() == Tools.MULTICOPTER) {
				masterLocation = Copter.getLocation(numUAV);
				masterYaw = Copter.getHeading(numUAV);
			} else {
				masterLocation = Location2D.NewLocation(FMeParam.masterInitialLatitude, FMeParam.masterInitialLongitude);
				masterYaw = FMeParam.masterInitialYaw;
			}
			double takeoffAltitude = FMeParam.slavesStartingAltitude;
			double takeOffAltitudeStepOne;
			if (takeoffAltitude <= 5.0) {
				takeOffAltitudeStepOne = 2.0;
			} else if (takeoffAltitude >= 10.0) {
				takeOffAltitudeStepOne = 5.0;
			} else {
				takeOffAltitudeStepOne = takeoffAltitude / 2;
			}
			
			// 2. Calculus of the slaves distribution that better fit the current layout on the ground
			Formation flyingFormation = FlightFormation.getFlyingFormation();
			double minFlyingFormationDistance = FlightFormation.getFlyingFormationDistance();
			FlightFormation airFormation = FlightFormation.getFormation(flyingFormation, numSlaves + 1, minFlyingFormationDistance);
			Triplet<Integer, Long, UTMCoordinates>[] match =
					FlightFormation.matchIDs(groundLocations, masterYaw, false, this.selfId, airFormation);
			
			// 3. Define the takeoff sequence and store data
			byte[] outBuffer = new byte[Tools.DATAGRAM_MAX_LENGTH];
			Output output = new Output(outBuffer);
			Pair<Integer, Long>[] sequence = FlightFormation.getTakeoffSequence(UAVsDetected, match);
			Map<Long, byte[]> messages = new HashMap<>((int)Math.ceil(numSlaves / 0.75) + 1);
			long prevId, nextId;
			Pair<Integer, Long> current;
			// i represents the position in the takeoff sequence
			for (int i = 0; i < numSlaves; i++) {
				current = sequence[i];
				
				if (i == 0) {
					prevId = FlightFormation.BROADCAST_MAC_ID;
				} else {
					prevId = sequence[i - 1].getValue1();
				}
				if (i == numSlaves - 1) {
					nextId = FlightFormation.BROADCAST_MAC_ID;
				} else {
					nextId = sequence[i + 1].getValue1();
				}
				
				output.clear();
				output.writeShort(Message.DATA);
				output.writeLong(current.getValue1());
				output.writeLong(prevId);
				output.writeLong(nextId);
				output.writeShort(flyingFormation.getFormationId());
				output.writeInt(numSlaves + 1);
				output.writeDouble(minFlyingFormationDistance);
				output.writeInt(current.getValue0());
				UTMCoordinates masterLoc = masterLocation.getUTMLocation();
				output.writeDouble(masterLoc.x);
				output.writeDouble(masterLoc.y);
				output.writeDouble(masterYaw);
				output.writeDouble(takeOffAltitudeStepOne);
				output.writeDouble(takeoffAltitude);
				output.flush();
				messages.put(current.getValue1(), Arrays.copyOf(outBuffer, output.position()));
			}
			FMeParam.data.set(messages.values().toArray(new byte[messages.size()][]));
			
			try {
				output.close();
			} catch (KryoException e) {}
			
			
			// 4. Wait for data ack from all the slaves
			GUI.logVerbose(numUAV, FMeText.MASTER_DATA_ACK_LISTENER);
			acks = new HashMap<Long, Long>((int)Math.ceil(numSlaves / 0.75) + 1);
			while (FMeParam.state.get(numUAV) == SETUP) {
				inBuffer = Copter.receiveMessage(numUAV);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();

					if (type == Message.DATA_ACK) {
						long idSlave = input.readLong();
						acks.put(idSlave, idSlave);
						if (acks.size() == numSlaves) {
							FMeParam.state.set(numUAV, READY_TO_FLY);
						}
					}
				}
			}
		} else {
			GUI.logVerbose(numUAV, FMeText.SLAVE_WAIT_DATA_LISTENER);
			while (FMeParam.state.get(numUAV) == SETUP) {
				inBuffer = Copter.receiveMessage(numUAV);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();
					
					if (type == Message.DATA && FMeParam.flyingFormation.get(numUAV) == null) {
						long id = input.readLong();
						if (id == selfId) {
							idPrev = input.readLong();
							FMeParam.idNext.set(numUAV, input.readLong());
							Formation receivedFormation = Formation.getFormation(input.readShort());
							numUAVs = input.readInt();
							double minDistance = input.readDouble();
							flightFormationPosition = input.readInt();
							UTMCoordinates masterLocation = new UTMCoordinates(input.readDouble(), input.readDouble());
							double masterYaw = input.readDouble();
							takeoffAltitudePhase1 = input.readDouble();
							takeoffAltitudePhase2 = input.readDouble();
							FMeParam.flyingFormation.set(numUAV, receivedFormation);
							flightFormation = FlightFormation.getFormation(receivedFormation, numUAVs, minDistance);
							
							UTMCoordinates targetLocationUTM = flightFormation.getLocation(flightFormationPosition, masterLocation, masterYaw);
							targetLocation = Location2D.NewLocation(targetLocationUTM);
						}
					}
					
					if (type == Message.READY_TO_FLY) {
						lastReceivedReadyToFly = System.currentTimeMillis();
						FMeParam.state.set(numUAV, READY_TO_FLY);
					}
				}
			}
		}
		
		/** READY TO FLY PHASE */
		GUI.log(numUAV, FMeText.READY_TO_FLY);
		GUI.updateProtocolState(numUAV, FMeText.READY_TO_FLY);
		long lastReceivedTakeoffEnd = 0;	// For the timeout at the end of the takeoff process
		long cicleTime;
		if (this.isMaster) {
			GUI.logVerbose(numUAV, FMeText.MASTER_READY_TO_FLY_ACK_LISTENER);
			acks.clear();
			while (FMeParam.state.get(numUAV) == READY_TO_FLY) {
				inBuffer = Copter.receiveMessage(numUAV);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();

					if (type == Message.READY_TO_FLY_ACK) {
						long idSlave = input.readLong();
						acks.put(idSlave, idSlave);
						if (acks.size() == numSlaves) {
							FMeParam.state.set(numUAV, WAIT_SLAVES);
						}
					}
				}
			}
			
			/** WAIT SLAVES PHASE */
			GUI.log(numUAV, FMeText.WAIT_SLAVES);
			GUI.updateProtocolState(numUAV, FMeText.WAIT_SLAVES);
			GUI.logVerbose(numUAV, FMeText.MASTER_TARGET_REACHED_ACK_LISTENER);
			acks.clear();
			while (FMeParam.state.get(numUAV) == WAIT_SLAVES) {
				inBuffer = Copter.receiveMessage(numUAV);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();

					if (type == Message.TARGET_REACHED_ACK) {
						long idSlave = input.readLong();
						acks.put(idSlave, idSlave);
						if (acks.size() == numSlaves) {
							FMeParam.state.set(numUAV, READY_TO_START);
						}
					}
				}
			}
			
		} else {
			GUI.logVerbose(numUAV, FMeText.SLAVE_WAIT_READY_TO_FLY_LISTENER);
			while (FMeParam.state.get(numUAV) == READY_TO_FLY) {
				inBuffer = Copter.receiveMessage(numUAV, FMeParam.RECEIVING_TIMEOUT);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();
					
					if (type == Message.READY_TO_FLY) {
						lastReceivedReadyToFly = System.currentTimeMillis();
					}
				}
				
				if (System.currentTimeMillis() - lastReceivedReadyToFly > FMeParam.SETUP_TIMEOUT) {
					if (idPrev == FlightFormation.BROADCAST_MAC_ID) {
						FMeParam.state.set(numUAV, TAKING_OFF);
					} else {
						FMeParam.state.set(numUAV, WAIT_TAKE_OFF);
					}
				}
			}
			
			/** WAIT TAKE OFF PHASE */
			if (FMeParam.state.get(numUAV) == WAIT_TAKE_OFF) {
				GUI.log(numUAV, FMeText.WAIT_TAKE_OFF);
				GUI.updateProtocolState(numUAV, FMeText.WAIT_TAKE_OFF);
				GUI.logVerbose(numUAV, FMeText.LISTENER_WAITING_TAKE_OFF);
				while (FMeParam.state.get(numUAV) == WAIT_TAKE_OFF) {
					inBuffer = Copter.receiveMessage(numUAV);
					if (inBuffer != null) {
						input.setBuffer(inBuffer);
						short type = input.readShort();
						
						if (type == Message.TAKE_OFF_NOW || type == Message.TARGET_REACHED_ACK) {
							long id = input.readLong();
							if (id == idPrev) {
								FMeParam.state.set(numUAV, TAKING_OFF);
							}
						}
					}
				}
			}
			
			/** TAKING OFF PHASE */
			GUI.log(numUAV, FMeText.TAKING_OFF);
			GUI.updateProtocolState(numUAV, FMeText.TAKING_OFF);
			double minAltitude = Copter.getMinTargetAltitude(takeoffAltitudePhase1);
			if (!Copter.takeOffNonBlocking(numUAV, takeoffAltitudePhase1)) {
				GUI.exit(FMeText.TAKE_OFF_ERROR + " " + selfId);
			}
			GUI.logVerbose(numUAV, FMeText.LISTENER_WAITING);
			cicleTime = System.currentTimeMillis();
			long logTime = cicleTime;
			while (FMeParam.state.get(numUAV) == TAKING_OFF) {
				// Discard message
				Copter.receiveMessage(numUAV, FMeParam.RECEIVING_TIMEOUT);
				// Wait until target altitude is reached
				if (System.currentTimeMillis() - cicleTime > FMeParam.TAKE_OFF_CHECK_TIMEOUT) {
					if (Copter.getZRelative(numUAV) >= minAltitude) {
						FMeParam.state.set(numUAV, MOVE_TO_TARGET);
					} else {
						if (System.currentTimeMillis() - logTime > FMeParam.TAKE_OFF_LOG_TIMEOUT) {
							GUI.logVerbose(numUAV, Text.ALTITUDE_TEXT
									+ " = " + String.format("%.2f", Copter.getZ(numUAV))
									+ " " + Text.METERS);
							logTime = logTime + FMeParam.TAKE_OFF_LOG_TIMEOUT;
						}
						
						cicleTime = cicleTime + FMeParam.TAKE_OFF_CHECK_TIMEOUT;
					}
				}
			}
			
			/** MOVE TO TARGET PHASE */
			GUI.log(numUAV, FMeText.MOVE_TO_TARGET);
			GUI.updateProtocolState(numUAV, FMeText.MOVE_TO_TARGET);
			GUI.logVerbose(numUAV, FMeText.LISTENER_WAITING);
			
			//Necessary to avoid a crash (remote control not present)
			if (!Copter.setFlightMode(numUAV, FlightMode.LOITER) || !Copter.setHalfThrottle(numUAV)) {
				GUI.exit(FMeText.TAKE_OFF_ERROR + " " + selfId);
			}
			long now = System.currentTimeMillis();
			while (System.currentTimeMillis() - now < FMeParam.HOVERING_TIMEOUT) {
				Copter.receiveMessage(numUAV, FMeParam.RECEIVING_TIMEOUT);
			}
			if (!Copter.setFlightMode(numUAV, FlightMode.GUIDED)) {
				GUI.exit(FMeText.MOVE_ERROR + " " + selfId);
			}
			
			double min = Copter.getMinTargetAltitude(takeoffAltitudePhase2);
			double max = Copter.getMaxTargetAltitude(takeoffAltitudePhase2);
			if (!Copter.moveUAVNonBlocking(numUAV, targetLocation.getGeoLocation(), (float)takeoffAltitudePhase2)) {
				GUI.exit(FMeText.MOVE_ERROR + " " + selfId);
			}
			cicleTime = System.currentTimeMillis();
			double alt;
			UTMCoordinates targetLocationUTM = targetLocation.getUTMLocation();
			while (FMeParam.state.get(numUAV) == MOVE_TO_TARGET) {
				// Discard message
				Copter.receiveMessage(numUAV, FMeParam.RECEIVING_TIMEOUT);
				// Wait until target location is reached
				if (System.currentTimeMillis() - cicleTime > FMeParam.MOVE_CHECK_TIMEOUT) {
					if (Copter.getUTMLocation(numUAV).distance(targetLocationUTM) <= FMeParam.MIN_DISTANCE_TO_TARGET) {
						alt = Copter.getZRelative(numUAV);
						if (alt >= min && alt <= max) {
							FMeParam.state.set(numUAV, TARGET_REACHED);
						}
					} else {
						cicleTime = cicleTime + FMeParam.MOVE_CHECK_TIMEOUT;
					}
				}
			}
			
			/** TARGET REACHED PHASE */
			GUI.log(numUAV, FMeText.TARGET_REACHED);
			GUI.updateProtocolState(numUAV, FMeText.TARGET_REACHED);
			GUI.logVerbose(numUAV, FMeText.SLAVE_WAIT_TAKEOFF_END_ACK);
			while (FMeParam.state.get(numUAV) == TARGET_REACHED) {
				inBuffer = Copter.receiveMessage(numUAV);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();
					
					if (type == Message.TAKEOFF_END) {
						lastReceivedTakeoffEnd = System.currentTimeMillis();
						FMeParam.state.set(numUAV, READY_TO_START);
					}
				}
			}
			
		}
		
		/** READY TO START PHASE */
		GUI.log(numUAV, FMeText.READY_TO_START);
		GUI.updateProtocolState(numUAV, FMeText.READY_TO_START);
		if (this.isMaster) {
			GUI.logVerbose(numUAV, FMeText.MASTER_TAKEOFF_END_ACK_LISTENER);
			acks.clear();
			while (FMeParam.state.get(numUAV) == READY_TO_START) {
				inBuffer = Copter.receiveMessage(numUAV);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();

					if (type == Message.TAKEOFF_END_ACK) {
						long idSlave = input.readLong();
						acks.put(idSlave, idSlave);
						if (acks.size() == numSlaves) {
							FMeParam.state.set(numUAV, SETUP_FINISHED);
						}
					}
				}
			}
		} else {
			GUI.logVerbose(numUAV, FMeText.SLAVE_WAIT_TAKEOFF_END_LISTENER);
			while (FMeParam.state.get(numUAV) == READY_TO_START) {
				inBuffer = Copter.receiveMessage(numUAV, FMeParam.RECEIVING_TIMEOUT);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();
					
					if (type == Message.TAKEOFF_END) {
						lastReceivedTakeoffEnd = System.currentTimeMillis();
					}
				}
				
				if (System.currentTimeMillis() - lastReceivedTakeoffEnd > FMeParam.TAKEOFF_TIMEOUT) {
					FMeParam.state.set(numUAV, SETUP_FINISHED);
				}
			}
		}
		
		/** SETUP FINISHED PHASE */
		GUI.log(numUAV, FMeText.SETUP_FINISHED);
		GUI.updateProtocolState(numUAV, FMeText.SETUP_FINISHED);
		GUI.logVerbose(numUAV, FMeText.LISTENER_WAITING);
		while (FMeParam.state.get(numUAV) == SETUP_FINISHED) {
			// Discard message
			Copter.receiveMessage(numUAV, FMeParam.RECEIVING_TIMEOUT);
			// Coordination with ArduSim
			if (Tools.isExperimentInProgress()) {
				FMeParam.state.set(numUAV, FOLLOWING);
			}
		}
		
		/** FOLLOWING PHASE */
		GUI.log(numUAV, FMeText.FOLLOWING);
		GUI.updateProtocolState(numUAV, FMeText.FOLLOWING);
		int waitingTime;
		if (this.isMaster) {
			// Wait until the master UAV descends below a threshold (in the remote thread)
			while (FMeParam.state.get(numUAV) == FOLLOWING) {
				Tools.waiting(FMeParam.STATE_CHANGE_TIMEOUT);
			}
		} else {
			GUI.logVerbose(numUAV, FMeText.SLAVE_WAIT_ORDER_LISTENER);
			UTMCoordinates masterLocation;
			while (FMeParam.state.get(numUAV) == FOLLOWING) {
				inBuffer = Copter.receiveMessage(numUAV);
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();
					
					if (type == Message.I_AM_HERE){
						masterLocation = new UTMCoordinates(input.readDouble(), input.readDouble());
						double relAltitude = input.readDouble();
						double yaw = input.readDouble();
						targetLocation = Location2D.NewLocation(flightFormation.getLocation(flightFormationPosition, masterLocation, yaw));
						Copter.moveUAV(numUAV, targetLocation.getGeoLocation(), (float)relAltitude);
					}
					
					if (type == Message.LAND) {
						UTMCoordinates centerUAVFinalLocation = new UTMCoordinates(input.readDouble(), input.readDouble());
						double yaw = input.readDouble();
						double minLandDistance = input.readDouble();
						FlightFormation landingFormation = FlightFormation.getFormation(FMeParam.flyingFormation.get(numUAV),
								numUAVs, minLandDistance);
						UTMCoordinates landingLocationUTM = landingFormation.getLocation(flightFormationPosition, centerUAVFinalLocation, yaw);
						targetLocation = Location2D.NewLocation(landingLocationUTM);
						FMeParam.state.set(numUAV, MOVE_TO_LAND);
					}
				}
			}
			
			/** MOVE TO LAND PHASE */
			GUI.log(numUAV, FMeText.MOVE_TO_LAND);
			GUI.updateProtocolState(numUAV, FMeText.MOVE_TO_LAND);
			GUI.logVerbose(numUAV, FMeText.LISTENER_WAITING);
			double relAltitude = (float)Copter.getZRelative(numUAV);
			double min = Copter.getMinTargetAltitude(relAltitude);
			double max = Copter.getMaxTargetAltitude(relAltitude);
			double alt;
			if (!Copter.moveUAVNonBlocking(numUAV, targetLocation.getGeoLocation(), (float)relAltitude)) {
				GUI.exit(FMeText.MOVE_ERROR + " " + selfId);
			}
			
			cicleTime = System.currentTimeMillis();
			while (FMeParam.state.get(numUAV) == MOVE_TO_LAND) {
				// Wait until target location is reached
				alt = Copter.getZRelative(numUAV);
				if (targetLocation.distance(Copter.getUTMLocation(numUAV)) <= FMeParam.MIN_DISTANCE_TO_TARGET
						&& alt >= min && alt <= max) {
					FMeParam.state.set(numUAV, LANDING);
				} else {
					cicleTime = cicleTime + FMeParam.MOVE_CHECK_TIMEOUT;
					waitingTime = (int) (cicleTime - System.currentTimeMillis());
					if (waitingTime > 0) {
						Tools.waiting(waitingTime);
					}
				}
			}
		}
		
		/** LANDING PHASE */
		if (!Copter.setFlightMode(numUAV, FlightMode.LAND)) {
			GUI.exit(FMeText.LAND_ERROR + " " + selfId);
		}
		GUI.log(numUAV, FMeText.LANDING);
		GUI.updateProtocolState(numUAV, FMeText.LANDING);
		GUI.logVerbose(numUAV, FMeText.LISTENER_WAITING);
		cicleTime = System.currentTimeMillis();
		while (FMeParam.state.get(numUAV) == LANDING) {
			if(!Copter.isFlying(numUAV)) {
				FMeParam.state.set(numUAV, FINISH);
			} else {
				cicleTime = cicleTime + FMeParam.LAND_CHECK_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		}
		
		/** FINISH PHASE */
		GUI.log(numUAV, FMeText.FINISH);
		GUI.updateProtocolState(numUAV, FMeText.FINISH);
		GUI.logVerbose(numUAV, FMeText.LISTENER_FINISHED);
	}
	
	
}
