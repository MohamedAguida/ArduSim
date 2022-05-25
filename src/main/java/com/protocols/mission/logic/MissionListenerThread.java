package com.protocols.mission.logic;

import com.api.*;
import com.api.communications.CommLink;
import com.api.masterslavepattern.MasterSlaveHelper;
import com.esotericsoftware.kryo.io.Input;
import com.protocols.followme.logic.FollowMeParam;
import com.protocols.followme.logic.FollowMeText;
import com.protocols.followme.pojo.Message;
import es.upv.grc.mapper.*;
import java.io.*;
import java.util.Arrays;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import static com.protocols.followme.pojo.State.*;
import static com.protocols.mission.logic.MissionHelper.*;

/** Developed by: Francisco Jos&eacute; Fabra Collado, from GRC research group in Universitat Polit&egrave;cnica de Val&egrave;ncia (Valencia, Spain). */

public class MissionListenerThread extends Thread {

	private AtomicInteger currentState;

	private int numUAV;
	private long selfId;
	private boolean isMaster;
	private Copter copter;
	private MasterSlaveHelper msHelper;
	private SafeTakeOffHelper takeOffHelper;
	private GUI gui;
	private ArduSim ardusim;



	private int status[];
	int numUAVs = 7 ;
	private Random rand;
	public  int counterTest=0;
	private int vote;

	private CommLink link;
	byte[] inBuffer;
	Input input;

	@SuppressWarnings("unused")
	private MissionListenerThread() {}

	public double calculateDistance(
			double x1,
			double y1,
			double x2,
			double y2) {
		return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
	}


	public MissionListenerThread(int numUAV) {
		super(FollowMeText.LISTENER_THREAD + numUAV);
		//currentState = FollowMeParam.state[numUAV];
		this.numUAV = numUAV;
		this.copter = API.getCopter(numUAV);
		this.selfId = this.copter.getID();
		this.takeOffHelper = this.copter.getSafeTakeOffHelper();
		this.gui = API.getGUI(numUAV);
		this.link = CommLink.getCommLink(numUAV);
		this.inBuffer = new byte[CommLink.DATAGRAM_MAX_LENGTH];
		this.input = new Input(inBuffer);
		this.ardusim = API.getArduSim();
		//this.distance = new double[numUAVs][numUAVs];

		this.status = new int[numUAVs];
		this.rand = new Random() ;
		this.vote = ardusim.getNumUAVs()/2;
	}

	@Override
	public void run() {
		while (!ardusim.isAvailable()) {
			ardusim.sleep(FollowMeParam.STATE_CHANGE_TIMEOUT);
		}

		//System.out.println("I'm listner, before starting the phase");
		/* FOLLOWING PHASE */
		gui.logUAV(FollowMeText.FOLLOWING);
		gui.updateProtocolState(FollowMeText.FOLLOWING);
		long waitingTime;
		//gui.log("I'm here before start talker thread" + numUAV);
		Location2DUTM Location;
		Location2DUTM RssiLocation;
		double x, rssi_x;
		double y, rssi_y;
		gui.log("hadi men bara");
		//while (currentState.get() == FOLLOWING) {
		try {
			while (true) {

				inBuffer = link.receiveMessage();
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();
					int id = input.readInt();
					if (type == Message.I_AM_HERE) {
						//masterLocation = new Location2DUTM(input.readDouble(), input.readDouble());
						x = input.readDouble() ;
						y = input.readDouble() ;

						// uav 1 (x1,y1)
						// uav 2 (x2,y2)


						// uav1 ===> uav2  === x2,y2 -- x1n,y1n
						// uav2 ==> uav1 === x1,y1 -- x2n1,y2n1

						rssi_x = x + Math.random()*5 * (rand.nextBoolean() ? -1 : 1);
						rssi_y = y + Math.random()*5 * (rand.nextBoolean() ? -1 : 1);
						RssiLocation = new Location2DUTM(rssi_x, rssi_y);
						int selfID = (int) selfId ;
						Location2DUTM selfLocation;
						selfLocation = copter.getLocationUTM();
						synchronized (this) {
							rssi[selfID][id] = selfLocation.distance(RssiLocation);
						}

						counterTest++;
						gui.log("this is counter test"+counterTest);
						for (int i=0; i<maliciousUAVs.length;i++){

							if (id==maliciousUAVs[i] && counterTest>3){
								x=0;
								y=Math.sqrt(rssi[selfID][id]*rssi[selfID][id] - selfLocation.x * selfLocation.x) + selfLocation.y ;
							}
						}
/*
						if (id==2 && counterTest>30){
							x=0;
							y=Math.sqrt(rssi[selfID][id]*rssi[selfID][id] - selfLocation.x * selfLocation.x) + selfLocation.y ;
						}
 */

						Location = new Location2DUTM(x, y);
						//RssiLocation = new Location2DUTM(rssi_x, rssi_y);
						double relAltitude = input.readDouble();
						double yaw = input.readDouble();
						synchronized (this) {
							distance[selfID][id] = selfLocation.distance(Location);
						}

						double difference = distance[selfID][id]-rssi[selfID][id] ;
						if ( (difference > 6) || (difference < -6)){
							gui.log("UAV" + selfId + ": the position of the UAV"+id+" is not accurate !");
							gui.log("UAV" + selfId + ": distance to UAV" +id+ " is:" + distance[selfID][id]);
							gui.log("UAV" + selfId + ": RssiDistance to UAV" +id+ " is:" + rssi[selfID][id]);
							System.out.println();
						}
						else {
							//int targetUAV = selfID;
							//while (targetUAV == selfId) {
							//	targetUAV = rand.ints(0, 5).findFirst().getAsInt();
							//}
							Thread.sleep(selfID*10+10000);
							//gui.log("distance from UAV"+selfID);
							/*
							for (int i = 0; i< distance.length; i++){
								for (int j=0;j< distance[i].length;j++){

									gui.log("UAV"+i+" to UAV"+j+ distance[i][j]);
								}
							}
							 */

							for (int targetUAV=0; targetUAV<numUAVs; targetUAV++) {

								if (targetUAV==selfID || statusGlobal[targetUAV]>=vote || statusGlobal[selfID]>=vote){
									continue;
								}
								int juryUAV1 = selfID;

								do {
									juryUAV1 = rand.ints(0, numUAVs).findFirst().getAsInt();
								} while (juryUAV1 == selfID || juryUAV1 == targetUAV || statusGlobal[juryUAV1]>=vote);

								int juryUAV2 = selfID;

								do {
									juryUAV2 = rand.ints(0, numUAVs).findFirst().getAsInt();
								} while (juryUAV2 == selfID || juryUAV2 == targetUAV || juryUAV2 == juryUAV1 || statusGlobal[juryUAV2]>=vote);



								// start checking if the coordinations are the same !
								// src: http://paulbourke.net/geometry/circlesphere/?fbclid=IwAR2bL_rmkSpOcnX0API-XJk41pM0FMQ7-1CUflVqIyvrh1tkDR38xENQhhI
								gui.log("juryUAV1:"+juryUAV1);
								gui.log("juryUAV2:"+juryUAV2);
								gui.log("targetUAV:"+targetUAV);



								double x_target = API.getCopter(targetUAV).getLocationUTM().x;
								double y_target = API.getCopter(targetUAV).getLocationUTM().y;

								double x_jury1  = API.getCopter(juryUAV1).getLocationUTM().x;
								double y_jury1  = API.getCopter(juryUAV1).getLocationUTM().y;

								double x_jury2  = API.getCopter(juryUAV2).getLocationUTM().x;
								double y_jury2  = API.getCopter(juryUAV2).getLocationUTM().y;

								double x_self  = API.getCopter(selfID).getLocationUTM().x;
								double y_self = API.getCopter(selfID).getLocationUTM().y;

								for (int i=0; i<maliciousUAVs.length;i++){
									if (targetUAV==maliciousUAVs[i]){
										x_target = x ;
										y_target = y ;
									}
									/*
									else if(juryUAV1==maliciousUAVs[i]){
										x_jury1=0;
										y_jury1=Math.sqrt(rssi[juryUAV1][id]*rssi[juryUAV1][id] - x_self * x_self) + y_self ;
									}
									else if(juryUAV1==maliciousUAVs[i]){
										x_jury2=0;
										y_jury2=Math.sqrt(rssi[juryUAV2][id]*rssi[juryUAV2][id] - x_self * x_self) + y_self  ;
									}

									 */
								}

								double radius_pow_jury1 = rssi[juryUAV1][targetUAV]* rssi[juryUAV1][targetUAV];
								double X_diff_jury1 = x_jury1 - x_target;
								double Y_diff_jury1 = y_jury1 - y_target;
								double testDiff_jury1 = radius_pow_jury1 - (X_diff_jury1 * X_diff_jury1 + Y_diff_jury1 * Y_diff_jury1);

								double radius_pow_self = rssi[selfID][targetUAV]* rssi[selfID][targetUAV];
								double X_diff_self = x_self - x_target;
								double Y_diff_self = y_self - y_target;
								double testDiff_self = radius_pow_self - (X_diff_self * X_diff_self + Y_diff_self * Y_diff_self);

								double radius_pow_jury2 = rssi[juryUAV2][targetUAV] * rssi[juryUAV2][targetUAV];
								double X_diff_jury2 = x_jury2 - x_target;
								double Y_diff_jury2 = y_jury2 - y_target;
								double testDiff_jury2 = radius_pow_jury2 - (X_diff_jury2 * X_diff_jury2 + Y_diff_jury2 * Y_diff_jury2);


								if ((testDiff_jury1 < 1 || testDiff_jury1 > -1) && (testDiff_self < 1 || testDiff_self > -1) && (testDiff_jury2 < 1 || testDiff_jury2 > -1)) {
									gui.log("UAV" + selfID + ": the position of the UAV" + targetUAV + " is accurate !");
									status[targetUAV]=2;
									status[juryUAV1]=2;
									status[juryUAV2]=2;
								} else {
									if (status[juryUAV1]==2 && status[juryUAV2]==2 ) {
										status[targetUAV]=1;
										statusGlobal[targetUAV]=statusGlobal[targetUAV] + 1;


										gui.log("this is from coordination test");
										gui.log("UAV" + selfID + ": the position of the UAV" + targetUAV + " is not accurate !");
									}
									}
								gui.log("==================================================");
							}
							gui.log("UAV "+selfID+" is saying that: ");
							for (int i=0;i<status.length;i++){
								gui.log("UAV"+i+" is: "+status[i]);
								if (statusGlobal[i]>=vote) {
									API.getCopter(i).land();
									continue;
								}
								status[i]=0;
							}
							gui.log("==================================================");
							//Thread.sleep(2000);



						}
					}
				}
			}
		}catch (Exception e){
		System.out.println(e.getMessage());
		}
		gui.logUAV(FollowMeText.FINISH);
		gui.updateProtocolState(FollowMeText.FINISH);
		gui.logVerboseUAV(FollowMeText.LISTENER_FINISHED);
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




