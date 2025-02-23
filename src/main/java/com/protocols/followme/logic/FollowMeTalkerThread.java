package com.protocols.followme.logic;

import com.api.API;
import com.api.ArduSim;
import com.api.Copter;
import com.api.GUI;
import com.api.communications.CommLink;
import com.esotericsoftware.kryo.io.Output;
import com.protocols.followme.pojo.Message;
import es.upv.grc.mapper.Location2DUTM;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static com.protocols.followme.pojo.State.*;

/** Developed by: Francisco Jos&eacute; Fabra Collado, from GRC research group in Universitat Polit&egrave;cnica de Val&egrave;ncia (Valencia, Spain). */

public class FollowMeTalkerThread extends Thread {

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
	protected static volatile boolean protocolStarted = false;

	@SuppressWarnings("unused")
	private FollowMeTalkerThread() {}

	public FollowMeTalkerThread(int numUAV) {
		super(FollowMeText.TALKER_THREAD + numUAV);
		currentState = FollowMeParam.state[numUAV];
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
		gui.logVerboseUAV(FollowMeText.MASTER_WAIT_ALTITUDE);
		while (!protocolStarted) {
			ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
		}

		Location2DUTM here;
		double z, yaw;
		cicleTime = System.currentTimeMillis();
		while (currentState.get() == FOLLOWING) {
			here = copter.getLocationUTM();
			z = copter.getAltitudeRelative();
			yaw = copter.getHeading();
			output.reset();
			output.writeShort(Message.I_AM_HERE);
			output.writeInt(myID);
			output.writeDouble(here.x);
			output.writeDouble(here.y);
			output.writeDouble(z);
			output.writeDouble(yaw);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			link.sendBroadcastMessage(message);

			// Timer
			cicleTime = cicleTime + FollowMeParam.sendPeriod;
			waitingTime = cicleTime - System.currentTimeMillis();
			if (waitingTime > 0) {
				ardusim.sleep(waitingTime);
			}
		}
		while (currentState.get() < LANDING) {
			ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
		}

		// LANDING PHASE
		gui.logVerboseUAV(FollowMeText.MASTER_SEND_LAND);
		output.reset();
		output.writeShort(Message.LAND);
		here = copter.getLocationUTM();
		output.writeDouble(here.x);
		output.writeDouble(here.y);
		output.writeDouble(copter.getHeading());
		output.flush();
		message = Arrays.copyOf(outBuffer, output.position());

		cicleTime = System.currentTimeMillis();
		while (currentState.get() == LANDING) {
			link.sendBroadcastMessage(message);

			// Timer
			cicleTime = cicleTime + FollowMeParam.SENDING_TIMEOUT;
			waitingTime = cicleTime - System.currentTimeMillis();
			if (waitingTime > 0) {
				ardusim.sleep(waitingTime);
			}
		}
		while (currentState.get() < FINISH) {
			ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
		}

		// FINISH PHASE
		gui.logVerboseUAV(FollowMeText.TALKER_FINISHED);
	}



}