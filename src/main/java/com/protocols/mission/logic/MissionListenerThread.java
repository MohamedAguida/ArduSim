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
	int numUAVs = 5 ;
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
		numUAVs = API.getArduSim().getNumUAVs();
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
	//public static synchronized void increment(int i){
	//	  statusGlobal[i]=statusGlobal[i]+1 ;
	//}
	//public static synchronized void initial(int i){
	//	statusGlobal[i]=0;
	//}
	public int sum(int num){
		int summ = 0 ;
		for (int i=0;i<num;i++){
			summ= summ + i ;
		}
		return  summ;
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
		Location2DUTM Location;
		Location2DUTM RssiLocation;
		double x, rssi_x;
		double y, rssi_y;
		int PacketID;
		try {
			while (true) {

				inBuffer = link.receiveMessage();
				if (inBuffer != null) {
					input.setBuffer(inBuffer);
					short type = input.readShort();
					int id = input.readInt();
					if (type == Message.I_AM_HERE) {
						PacketID = input.readInt();
						x = input.readDouble() ;
						y = input.readDouble() ;

						rssi_x = x + Math.random()*5 * (rand.nextBoolean() ? -1 : 1);
						rssi_y = y + Math.random()*5 * (rand.nextBoolean() ? -1 : 1);

						RssiLocation = new Location2DUTM(rssi_x, rssi_y);
						int selfID = (int) selfId ;
						Location2DUTM selfLocation;
						selfLocation = copter.getLocationUTM();
						synchronized (this) {
							rssi[selfID][id] = selfLocation.distance(RssiLocation);
						}

					//	counterTest++;

						for (int i=0; i<maliciousUAVs.size();i++){

							if (id==maliciousUAVs.get(i)){
								x=0;
								y=Math.sqrt(rssi[selfID][id]*rssi[selfID][id] - selfLocation.x * selfLocation.x) + selfLocation.y ;
							}
						}


						Location = new Location2DUTM(x, y);
						//RssiLocation = new Location2DUTM(rssi_x, rssi_y);
						double relAltitude = input.readDouble();
						double yaw = input.readDouble();
						synchronized (this) {
							distance[selfID][id] = selfLocation.distance(Location);
						}

						double difference = distance[selfID][id]-rssi[selfID][id] ;
						if ( (difference > 10) || (difference < -10)){
							gui.log("UAV" + selfId + ": the position of the UAV"+id+" is not accurate !");
							gui.log("UAV" + selfId + ": distance to UAV" +id+ " is:" + distance[selfID][id]);
							gui.log("UAV" + selfId + ": RssiDistance to UAV" +id+ " is:" + rssi[selfID][id]);
							status[id]=1;
							//increment(id);
							int temp1 = statusGlobal.incrementAndGet(id);
							System.out.println();
						}
						else {

							Thread.sleep(selfID*10+10000);
							int counter = 0 ;
							//int targetUAV;

							//while (counter <= numUAVs)
							for (int targetUAV=0;targetUAV<numUAVs;targetUAV++){
								//targetUAV = counter % numUAVs ;

								//counter++ ;
								if (statusGlobal.get(selfID)>=1){
									break;
								}
								if (targetUAV==selfID || statusGlobal.get(targetUAV)>=vote){
									continue;
								}
								int juryUAV1 = selfID;

								do {
									juryUAV1 = rand.ints(0, numUAVs).findFirst().getAsInt();
								} while (juryUAV1 == selfID || juryUAV1 == targetUAV || statusGlobal.get(juryUAV1)>=1);

								int juryUAV2 = selfID;

								do {
									juryUAV2 = rand.ints(0, numUAVs).findFirst().getAsInt();
								} while (juryUAV2 == selfID || juryUAV2 == targetUAV || juryUAV2 == juryUAV1 || statusGlobal.get(juryUAV2)>=1);



								// start checking if the coordinations are the same !
								// src: http://paulbourke.net/geometry/circlesphere/?fbclid=IwAR2bL_rmkSpOcnX0API-XJk41pM0FMQ7-1CUflVqIyvrh1tkDR38xENQhhI
//								gui.log("juryUAV1:"+juryUAV1);
//								gui.log("juryUAV2:"+juryUAV2);
//								gui.log("targetUAV:"+targetUAV);



								double x_target = API.getCopter(targetUAV).getLocationUTM().x;
								double y_target = API.getCopter(targetUAV).getLocationUTM().y;

								double x_jury1  = API.getCopter(juryUAV1).getLocationUTM().x;
								double y_jury1  = API.getCopter(juryUAV1).getLocationUTM().y;

								double x_jury2  = API.getCopter(juryUAV2).getLocationUTM().x;
								double y_jury2  = API.getCopter(juryUAV2).getLocationUTM().y;

								double x_self  = API.getCopter(selfID).getLocationUTM().x;
								double y_self = API.getCopter(selfID).getLocationUTM().y;

								for (int i=0; i<maliciousUAVs.size();i++){
									if (targetUAV==maliciousUAVs.get(i)){
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
		//							gui.log("UAV" + selfID + ": the position of the UAV" + targetUAV + " is accurate !");
									status[targetUAV]=-1;
									status[juryUAV1]=-1;
									status[juryUAV2]=-1;
								} else {
									if (status[juryUAV1]==-1 && status[juryUAV2]==-1 ) {
										status[targetUAV]= status[targetUAV] + 1;
										//synchronized (this) {
										//	statusGlobal[targetUAV] = statusGlobal[targetUAV] + 1;
										//}

		//								gui.log("this is from coordination test");
		//								gui.log("UAV" + selfID + ": the position of the UAV" + targetUAV + " is not accurate !");
									}
									}
								//gui.log("==================================================");
							}
//							gui.log("UAV "+selfID+" is saying that: ");
							//

							//synchronized (this) {
							//	cnt = pauseIt.incrementAndGet();
							//}


							if (statusGlobal.get(selfID)>=vote){
							}
							else {
								for (int i = 0; i < status.length; i++) {
//								gui.log("UAV"+i+" is: "+status[i]);
									if (status[i]==1) {
										if (statusGlobal.get(i) < vote) {
											int temp = statusGlobal.incrementAndGet(i);
											//synchronized (this) {
											//	statusGlobal[i] = statusGlobal[i] + 1;
											//}
										}
									}
									else if (status[i]==-1){
										status[i]=0;
									}
									if (statusGlobal.get(i) >= vote) {
										API.getCopter(i).land();
										continue;
									}

//									gui.log("UAV"+selfId+": UAV"+i+" is: "+status[i]);
								}
								//while (cnt!=numUAVs){
								//}
								//Thread.sleep(2000);
								if (selfID==18){
									gui.log("phase start:===========================================" );
								}
								pauseIt.set(0);
								for (int i = 0; i < statusGlobal.length(); i++) {
									if (statusGlobal.get(i) < vote && statusGlobal.get(i) > 0 && selfID == 18) {
										//synchronized (this) {
										//	statusGlobal[i] = 0;
										//}
								//		initial(i);
										gui.log("UAV"+i+" statusGlobal is "+ statusGlobal.get(i));
										//statusGlobal.set(i,0);
									}
								}
								if (selfID==18){
									gui.log("phase ended ============================================" );
								}

							}
//							gui.log("==================================================");
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




