package com.protocols.mission.logic;

import com.api.API;
import com.api.ArduSim;
import com.api.Copter;
import com.api.GUI;
import com.api.communications.CommLink;
import com.esotericsoftware.kryo.io.Output;
import com.protocols.followme.logic.FollowMeText;
import com.protocols.followme.pojo.Message;
import com.uavController.UAVCurrentStatus;
import es.upv.grc.mapper.Location2DUTM;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static com.protocols.followme.pojo.State.*;
import static com.protocols.mission.logic.MissionHelper.statusGlobal;

/** Developed by: Francisco Jos&eacute; Fabra Collado, from GRC research group in Universitat Polit&egrave;cnica de Val&egrave;ncia (Valencia, Spain). */

public class MissionTalkerThread extends Thread {

	private AtomicInteger currentState;

	private Copter copter;
	private GUI gui;
	private ArduSim ardusim;
	private CommLink link;
	private byte[] outBuffer;
	private Output output;
	private byte[] message;

	private long cicleTime;		// Cicle time used for sending messages
	private long waitingTime;	// (ms) Time to wait between two sent messages
	private int myID ;
	int PacketCounter = 0 ;
	protected static volatile boolean protocolStarted = false;
	UAVCurrentStatus currentStatus;
	@SuppressWarnings("unused")
	private MissionTalkerThread() {}

	public MissionTalkerThread(int numUAV) {
		//super(FollowMeText.TALKER_THREAD + numUAV);
		//currentState = FollowMeParam.state[numUAV];
		myID  = numUAV;
		this.ardusim = API.getArduSim();
		this.copter = API.getCopter(numUAV);
		this.gui = API.getGUI(numUAV);
		this.link = CommLink.getCommLink(numUAV);
		this.outBuffer = new byte[CommLink.DATAGRAM_MAX_LENGTH];
		this.output = new Output(outBuffer);

		this.cicleTime = 0;
	}

	@Override
	public void run() {
		/* FOLLOWING PHASE */
		//gui.logVerboseUAV(FollowMeText.MASTER_WAIT_ALTITUDE);
		//while (!protocolStarted) {
		//	ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
		//}
		Location2DUTM here;
		double z, yaw;

		cicleTime = System.currentTimeMillis();

		//while (currentState.get() == FOLLOWING) {
		try{
			while (true) {
				PacketCounter = PacketCounter + 1;
				here = copter.getLocationUTM();
				z = copter.getAltitudeRelative();
				yaw = copter.getHeading();
				output.reset();
				output.writeShort(Message.I_AM_HERE);
				output.writeInt(myID);
				output.writeInt(PacketCounter);
				output.writeDouble(here.x);
				output.writeDouble(here.y);
				output.writeDouble(z);
				output.writeDouble(yaw);
				output.flush();
				message = Arrays.copyOf(outBuffer, output.position());
				link.sendBroadcastMessage(message);
				int battery = copter.getBattery();

				//gui.log("UAV"+myID+": "+UAVParam.uavCurrentStatus[myID].toString());;
				//if (myID==2){
					//copter.setParameter(CopterParam.RTL_ALTITUDE,copter.getAltitude()*2);
				//}
				// Timer

				Thread.sleep(20000);
		//		gui.log("UAV "+myID+": the global status is: ");

		//		for (int i=0;i<statusGlobal.length;i++){
		//			gui.log("UAV"+i+": "+statusGlobal[i]);
		//		}
//				gui.log("=====================================================");
			}

		}catch (Exception e){
			System.out.println(e.getMessage());
		}

		// FINISH PHASE
		gui.logVerboseUAV(FollowMeText.TALKER_FINISHED);
	}



}