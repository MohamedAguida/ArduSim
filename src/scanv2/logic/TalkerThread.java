package scanv2.logic;

import static scanv2.pojo.State.*;

import java.awt.geom.Point2D;
import java.util.Arrays;

import com.esotericsoftware.kryo.io.Output;

import api.Copter;
import api.GUI;
import api.Tools;
import api.pojo.formations.FlightFormation;
import scanv2.pojo.Message;

public class TalkerThread extends Thread {

	private int numUAV;
	private long selfId;
	private boolean isMaster;
	
	private byte[] outBuffer;
	private Output output;
	private byte[] message;
	
	private long cicleTime;		// Cicle time used for sending messages
	private int waitingTime;	// Time to wait between two sent messages
	
	@SuppressWarnings("unused")
	private TalkerThread() {}

	public TalkerThread(int numUAV) {
		this.numUAV = numUAV;
		this.selfId = Tools.getIdFromPos(numUAV);
		this.isMaster = ScanHelper.isMaster(numUAV);
		
		this.outBuffer = new byte[Tools.DATAGRAM_MAX_LENGTH];
		this.output = new Output(outBuffer);
		
		this.cicleTime = 0;
	}

	@Override
	public void run() {
		
		while (!Tools.areUAVsAvailable()) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** START PHASE */
		if (this.isMaster) {
			GUI.logVerbose(numUAV, ScanText.TALKER_WAITING);
			while (ScanParam.state.get(numUAV) == START) {
				Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
			}
		} else {
			GUI.logVerbose(numUAV, ScanText.SLAVE_START_TALKER);
			output.clear();
			output.writeShort(Message.HELLO);
			output.writeLong(selfId);
			Point2D.Double initialPos = Copter.getUTMLocation(numUAV);
			output.writeDouble(initialPos.x);
			output.writeDouble(initialPos.y);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == START) {
				Copter.sendBroadcastMessage(numUAV, message);

				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		}
		while (ScanParam.state.get(numUAV) < SETUP) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** SETUP PHASE */
		if (this.isMaster) {
			GUI.logVerbose(numUAV, ScanText.MASTER_DATA_TALKER);
			byte[][] messages = ScanParam.data.get();
			if (messages != null) {
				int length = messages.length;
				
				cicleTime = System.currentTimeMillis();
				while (ScanParam.state.get(numUAV) == SETUP) {
					for (int i = 0; i < length; i++) {
						Copter.sendBroadcastMessage(numUAV, messages[i]);
					}
					
					// Timer
					cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
					waitingTime = (int) (cicleTime - System.currentTimeMillis());
					if (waitingTime > 0) {
						Tools.waiting(waitingTime);
					}
				}
			}
		} else {
			GUI.logVerbose(numUAV, ScanText.SLAVE_WAIT_LIST_TALKER);
			output.clear();
			output.writeShort(Message.DATA_ACK);
			output.writeLong(selfId);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == SETUP) {
				if (ScanParam.uavMissionReceivedGeo.get(numUAV) != null) {
					Copter.sendBroadcastMessage(numUAV, message);
				}
				
				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		}
		while (ScanParam.state.get(numUAV) < READY_TO_FLY) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** READY TO FLY PHASE */
		if (this.isMaster) {
			GUI.logVerbose(numUAV, ScanText.MASTER_READY_TO_FLY_TALKER);
			output.clear();
			output.writeShort(Message.READY_TO_FLY);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == READY_TO_FLY) {
				Copter.sendBroadcastMessage(numUAV, message);
				
				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		} else {
			GUI.logVerbose(numUAV, ScanText.SLAVE_READY_TO_FLY_CONFIRM_TALKER);
			output.clear();
			output.writeShort(Message.READY_TO_FLY_ACK);
			output.writeLong(selfId);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == READY_TO_FLY) {
				Copter.sendBroadcastMessage(numUAV, message);
				
				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		}
		while (ScanParam.state.get(numUAV) < WAIT_TAKE_OFF) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** WAIT TAKE OFF PHASE */
		if (ScanParam.state.get(numUAV) == WAIT_TAKE_OFF) {
			GUI.logVerbose(numUAV, ScanText.TALKER_WAITING);
			while (ScanParam.state.get(numUAV) == WAIT_TAKE_OFF) {
				Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
			}
		}
		while (ScanParam.state.get(numUAV) < TAKING_OFF) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** TAKING OFF PHASE */
		GUI.logVerbose(numUAV, ScanText.TALKER_WAITING);
		while (ScanParam.state.get(numUAV) == TAKING_OFF) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		while (ScanParam.state.get(numUAV) < MOVE_TO_TARGET) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** MOVE TO TARGET PHASE */
		long idNext = ScanParam.idNext.get(numUAV);
		if (idNext == FlightFormation.BROADCAST_MAC_ID) {
			GUI.logVerbose(numUAV, ScanText.TALKER_WAITING);
			while (ScanParam.state.get(numUAV) == MOVE_TO_TARGET) {
				Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
			}
		} else {
			GUI.logVerbose(numUAV, ScanText.TALKER_TAKE_OFF_COMMAND);
			output.clear();
			output.writeShort(Message.TAKE_OFF_NOW);
			output.writeLong(idNext);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == MOVE_TO_TARGET) {
				Copter.sendBroadcastMessage(numUAV, message);
				
				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		}
		while (ScanParam.state.get(numUAV) < TARGET_REACHED) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** TARGET REACHED PHASE */
		boolean iAmCenter = ScanParam.iAmCenter[numUAV].get();
		if (iAmCenter) {
			GUI.logVerbose(numUAV, ScanText.TALKER_WAITING);
			while (ScanParam.state.get(numUAV) == TARGET_REACHED) {
				Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
			}
		} else {
			GUI.logVerbose(numUAV, ScanText.NO_CENTER_TARGET_REACHED_TALKER);
			output.clear();
			output.writeShort(Message.TARGET_REACHED_ACK);
			output.writeLong(selfId);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == TARGET_REACHED) {
				Copter.sendBroadcastMessage(numUAV, message);
				
				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		}
		while (ScanParam.state.get(numUAV) < READY_TO_START) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** READY TO START */
		if (iAmCenter) {
			GUI.logVerbose(numUAV, ScanText.CENTER_TAKEOFF_END_TALKER);
			output.clear();
			output.writeShort(Message.TAKEOFF_END);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == READY_TO_START) {
				Copter.sendBroadcastMessage(numUAV, message);
				
				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		} else {
			GUI.logVerbose(numUAV, ScanText.NO_CENTER_TAKEOFF_END_ACK_TALKER);
			output.clear();
			output.writeShort(Message.TAKEOFF_END_ACK);
			output.writeLong(selfId);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == READY_TO_START) {
				Copter.sendBroadcastMessage(numUAV, message);
				
				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		}
		while (ScanParam.state.get(numUAV) < SETUP_FINISHED) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** SETUP FINISHED PHASE */
		GUI.logVerbose(numUAV, ScanText.TALKER_WAITING);
		while (ScanParam.state.get(numUAV) == SETUP_FINISHED) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		while (ScanParam.state.get(numUAV) < FOLLOWING_MISSION) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** COMBINED PHASE MOVE_TO_WP & WP_REACHED */
		int currentWP = 0;
		while (ScanParam.state.get(numUAV) == FOLLOWING_MISSION) {
			
			/** WP_REACHED PHASE */
			if (iAmCenter) {
				GUI.logVerbose(numUAV, ScanText.TALKER_WAITING);
				while (ScanParam.wpReachedSemaphore.get(numUAV) == currentWP) {
					Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
				}
			} else {
				GUI.logVerbose(numUAV, ScanText.NO_CENTER_WAYPOINT_REACHED_ACK_TALKER);
				output.clear();
				output.writeShort(Message.WAYPOINT_REACHED_ACK);
				output.writeLong(selfId);
				output.writeInt(currentWP);
				output.flush();
				message = Arrays.copyOf(outBuffer, output.position());
				
				cicleTime = System.currentTimeMillis();
				while (ScanParam.wpReachedSemaphore.get(numUAV) == currentWP) {
					Copter.sendBroadcastMessage(numUAV, message);
					
					// Timer
					cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
					waitingTime = (int) (cicleTime - System.currentTimeMillis());
					if (waitingTime > 0) {
						Tools.waiting(waitingTime);
					}
				}
			}
			
			/** MOVE_TO_WP PHASE */
			if (ScanParam.state.get(numUAV) == FOLLOWING_MISSION) {
				currentWP++;
				if (iAmCenter) {
					GUI.logVerbose(numUAV, ScanText.CENTER_SEND_MOVE);
					output.clear();
					output.writeShort(Message.MOVE_TO_WAYPOINT);
					output.writeInt(currentWP);
					output.flush();
					message = Arrays.copyOf(outBuffer, output.position());
					
					cicleTime = System.currentTimeMillis();
					while (ScanParam.moveSemaphore.get(numUAV) == currentWP) {
						Copter.sendBroadcastMessage(numUAV, message);
						
						// Timer
						cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
						waitingTime = (int) (cicleTime - System.currentTimeMillis());
						if (waitingTime > 0) {
							Tools.waiting(waitingTime);
						}
					}
				} else {
					GUI.logVerbose(numUAV, ScanText.NO_CENTER_WAYPOINT_REACHED_ACK_TALKER);
					output.clear();
					output.writeShort(Message.WAYPOINT_REACHED_ACK);
					output.writeLong(selfId);
					output.writeInt(currentWP - 1);
					output.flush();
					message = Arrays.copyOf(outBuffer, output.position());
					
					cicleTime = System.currentTimeMillis();
					while (ScanParam.moveSemaphore.get(numUAV) == currentWP) {
						Copter.sendBroadcastMessage(numUAV, message);
						
						// Timer
						cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
						waitingTime = (int) (cicleTime - System.currentTimeMillis());
						if (waitingTime > 0) {
							Tools.waiting(waitingTime);
						}
					}
				}
			}
		}
		while (ScanParam.state.get(numUAV) < MOVE_TO_LAND) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** MOVE TO LAND PHASE */
		if (ScanParam.state.get(numUAV) == MOVE_TO_LAND) {
			while (ScanParam.state.get(numUAV) < LANDING) {
				Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
			}
		}
		
		/** LANDING PHASE */
		if (iAmCenter) {
			GUI.logVerbose(numUAV, ScanText.CENTER_SEND_LAND);
			output.clear();
			output.writeShort(Message.LAND);
			Point2D.Double currentLocation = Copter.getUTMLocation(numUAV);
			output.writeDouble(currentLocation.x);
			output.writeDouble(currentLocation.y);
			output.flush();
			message = Arrays.copyOf(outBuffer, output.position());
			
			cicleTime = System.currentTimeMillis();
			while (ScanParam.state.get(numUAV) == LANDING) {
				Copter.sendBroadcastMessage(numUAV, message);
				
				// Timer
				cicleTime = cicleTime + ScanParam.SENDING_TIMEOUT;
				waitingTime = (int) (cicleTime - System.currentTimeMillis());
				if (waitingTime > 0) {
					Tools.waiting(waitingTime);
				}
			}
		}
		while (ScanParam.state.get(numUAV) < FINISH) {
			Tools.waiting(ScanParam.STATE_CHANGE_TIMEOUT);
		}
		
		/** FINISH PHASE */
		GUI.logVerbose(numUAV, ScanText.TALKER_FINISHED);
	}
}
