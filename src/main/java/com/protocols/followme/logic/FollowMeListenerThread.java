package com.protocols.followme.logic;

import com.api.*;
import com.api.communications.CommLink;
import com.api.masterslavepattern.MasterSlaveHelper;
import com.api.masterslavepattern.safeTakeOff.SafeTakeOffContext;
import com.esotericsoftware.kryo.io.Input;
import com.protocols.followme.pojo.Message;
import com.setup.Param;
import com.setup.sim.logic.SimParam;
import com.uavController.UAVParam;
import es.upv.grc.mapper.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.protocols.followme.pojo.State.*;

/** Developed by: Francisco Jos&eacute; Fabra Collado, from GRC research group in Universitat Polit&egrave;cnica de Val&egrave;ncia (Valencia, Spain). */

public class FollowMeListenerThread extends Thread {

	private AtomicInteger currentState;

	private int numUAV;
	private long selfId;
	private boolean isMaster;
	private Copter copter;
	private MasterSlaveHelper msHelper;
	private SafeTakeOffHelper takeOffHelper;
	private GUI gui;
	private ArduSim ardusim;

	private double distance[];

	private CommLink link;
	byte[] inBuffer;
	Input input;

	@SuppressWarnings("unused")
	private FollowMeListenerThread() {}

	public double calculateDistance(
			double x1,
			double y1,
			double x2,
			double y2) {
		return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
	}


	public FollowMeListenerThread(int numUAV) {
		super(FollowMeText.LISTENER_THREAD + numUAV);
		currentState = FollowMeParam.state[numUAV];
		this.numUAV = numUAV;
		this.copter = API.getCopter(numUAV);
		this.selfId = this.copter.getID();
		this.msHelper = this.copter.getMasterSlaveHelper();
		this.isMaster = this.msHelper.isMaster();
		this.takeOffHelper = this.copter.getSafeTakeOffHelper();
		this.gui = API.getGUI(numUAV);
		this.link = CommLink.getCommLink(numUAV);
		this.inBuffer = new byte[CommLink.DATAGRAM_MAX_LENGTH];
		this.input = new Input(inBuffer);
		this.ardusim = API.getArduSim();
		this.distance = new double[5];
	}

	@Override
	public void run() {
		while (!ardusim.isAvailable()) {
			ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
		}

		// START PHASE
		gui.logUAV(FollowMeText.START);
		// Let the master detect slaves until the setup button is pressed
		Map<Long, Location2DUTM> UAVsDetected = null;
		if (this.isMaster) {
			gui.logVerboseUAV(FollowMeText.SLAVE_START_LISTENER);
			final AtomicInteger totalDetected = new AtomicInteger();
			UAVsDetected = msHelper.DiscoverSlaves(numUAVs -> {
				// Just for logging purposes
				if (numUAVs > totalDetected.get()) {
					totalDetected.set(numUAVs);
					gui.log(FollowMeText.MASTER_DETECTED_UAVS + numUAVs);
				}
				// We decide to continue when the setup button is pressed
				return numUAVs == API.getArduSim().getNumUAVs() - 1;
			});
		} else {
			gui.logVerboseUAV(FollowMeText.LISTENER_WAITING);
			msHelper.DiscoverMaster();
		}

		while (Param.simStatus != Param.SimulatorState.SETUP_IN_PROGRESS) {
			ardusim.sleep(SimParam.SHORT_WAITING_TIME);
		}

		/* TAKE OFF PHASE */
		currentState.set(TAKE_OFF);
		gui.logUAV(FollowMeText.SETUP);
		gui.updateProtocolState(FollowMeText.SETUP);
		// 1. Synchronize master with slaves to get the takeoff sequence in the take off context object
		SafeTakeOffContext takeOff;
		if (this.isMaster) {
			double formationYaw;
			if (ardusim.getArduSimRole() == ArduSim.MULTICOPTER) {
				formationYaw = copter.getHeading();
			} else {
				formationYaw = FollowMeParam.masterInitialYaw;
			}
			takeOff = takeOffHelper.getMasterContext(UAVsDetected,
					UAVParam.airFormation.get(),
					formationYaw, FollowMeParam.slavesStartingAltitude, true, true);
		} else {
			takeOff = takeOffHelper.getSlaveContext(false);
		}
		gui.logUAV("ready to takeOff");
		// 2. Take off all the UAVs
		takeOffHelper.start(takeOff, () -> currentState.set(SETUP_FINISHED));
		while (currentState.get() < SETUP_FINISHED) {
			ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
		}

		/* SETUP FINISHED PHASE */
		gui.logUAV(FollowMeText.SETUP_FINISHED);
		gui.updateProtocolState(FollowMeText.SETUP_FINISHED);
		gui.logVerboseUAV(FollowMeText.LISTENER_WAITING);
		while (currentState.get() == SETUP_FINISHED) {
			ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
			// Coordination with ArduSim
			if (ardusim.isExperimentInProgress()) {
				currentState.set(FOLLOWING);
			}
		}

		/* FOLLOWING PHASE */
		gui.logUAV(FollowMeText.FOLLOWING);
		gui.updateProtocolState(FollowMeText.FOLLOWING);
		long waitingTime;
		gui.log("I'm here before start talker thread" + numUAV);
		if (this.isMaster) {
			new FollowMeTalkerThread(numUAV).start();
			// Wait until the master UAV descends below a threshold (in the remote thread)
			while (currentState.get() == FOLLOWING) {
				ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
			}
		} else {

		gui.log("I'm here after start talker thread" + numUAV);
		gui.logVerboseUAV(FollowMeText.SLAVE_WAIT_ORDER_LISTENER);
		Location2DUTM masterLocation;
		Location2DUTM Location;
		Location2DGeo targetLocation = null;
		Location3D targetLocationLanding = null;
		while (currentState.get() == FOLLOWING) {
			inBuffer = link.receiveMessage();
			if (inBuffer != null) {
				input.setBuffer(inBuffer);
				short type = input.readShort();
				int id = input.readInt();

				if (type == Message.I_AM_HERE) {
					//masterLocation = new Location2DUTM(input.readDouble(), input.readDouble());
					Location = new Location2DUTM(input.readDouble(), input.readDouble());
					double relAltitude = input.readDouble();
					double yaw = input.readDouble();
						try {
							targetLocation = takeOff.getFormationFlying().get2DUTMLocation(Location, takeOff.getFormationPosition()).getGeo();
							copter.moveTo(new Location3DGeo(targetLocation, relAltitude));
							Location2DUTM selfLocation;
							selfLocation = copter.getLocationUTM();
							distance[id] = selfLocation.distance(Location);
							//gui.logVerbose("Hello"+distance[id]);
						} catch (LocationNotReadyException e) {
							gui.log(e.getMessage());
							e.printStackTrace();
							// Fatal error. It lands
							currentState.set(LANDING);
						}







				}
				// distance function (x,y,z)
				// getDistance() == distance calculated with x,y,z
				// d = getDistance()
				// dNoise = d + rv ; [a,b]
				// dRssi = d + rv1 ;
				// RSSI= f(dNoise) ==> dNoise = f-1(RSSI)
				// distance function(other parm)

				// two distance to  be coompared
				// 1 is based on x,y,z  = noise1 ==> received from other uavs
				// 2 is based on x,y,z + noise2 == >
				// 1-2 = noise1 - noise2

				// 1-2 = noise1 - noise2 (x1,y1,z1) - (x,y,z)


				if (type == Message.LAND) {
					Location2DUTM centerUAVFinalLocation = new Location2DUTM(input.readDouble(), input.readDouble());
					double yaw = input.readDouble();
					Location2DUTM landingLocationUTM = UAVParam.groundFormation.get().get2DUTMLocation(centerUAVFinalLocation, takeOff.getFormationPosition());
					try {
						targetLocationLanding = new Location3D(landingLocationUTM, copter.getAltitudeRelative());
						currentState.set(MOVE_TO_LAND);
					} catch (LocationNotReadyException e) {
						gui.log(e.getMessage());
						e.printStackTrace();
						// Fatal error. It lands
						currentState.set(LANDING);
					}
				}
			}
		}

		// MOVE TO LAND PHASE
		if (currentState.get() == MOVE_TO_LAND) {
			gui.logUAV(FollowMeText.MOVE_TO_LAND);
			gui.updateProtocolState(FollowMeText.MOVE_TO_LAND);
			gui.logVerboseUAV(FollowMeText.LISTENER_WAITING);
			MoveTo moveTo = copter.moveTo(targetLocationLanding, new MoveToListener() {

				@Override
				public void onFailure() {
					gui.exit(FollowMeText.MOVE_ERROR + " " + selfId);
				}

				@Override
				public void onCompleteActionPerformed() {
					// Nothing to do, as we wait until the target location is reached with Thread.join()
				}
			});
			moveTo.start();
			try {
				moveTo.join();
			} catch (InterruptedException ignored) {
			}
			currentState.set(LANDING);
		}
	}

		// LANDING PHASE
		if (!copter.land()) {
			gui.exit(FollowMeText.LAND_ERROR + " " + selfId);
		}
		gui.logUAV(FollowMeText.LANDING);
		gui.updateProtocolState(FollowMeText.LANDING);
		gui.logVerboseUAV(FollowMeText.LISTENER_WAITING);
		long cicleTime = System.currentTimeMillis();
		while (currentState.get() == LANDING) {
			if(!copter.isFlying()) {
				currentState.set(FINISH);
			} else {
				cicleTime = cicleTime + FollowMeParam.LAND_CHECK_TIMEOUT;
				waitingTime = cicleTime - System.currentTimeMillis();
				if (waitingTime > 0) {
					ardusim.sleep(waitingTime);
				}
			}
		}

		// FINISH PHASE
		gui.logUAV(FollowMeText.FINISH);
		gui.updateProtocolState(FollowMeText.FINISH);
		gui.logVerboseUAV(FollowMeText.LISTENER_FINISHED);
	}


}