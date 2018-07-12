package uavController;

import org.javatuples.Quartet;

import api.Tools;

/** This class generates and object that contains the current status information received from the UAV. */

public class UAVCurrentStatus {
	
	// Current values
	private double voltage = -1;			// Volts
	private double current = -1;			// Amperes
	private int remainingBattery = -1;		// Percentage
	private double cpuLoad = -1;			// Percentage
	
	/** Updates current values with raw data received. */
	public synchronized void update(int voltage, int current, int remainingBattery, int load) {
		this.voltage = Tools.round(voltage * 0.001, 3);
		if (current != -1) {	// ardupilot measures the current
			this.current = Tools.round(current * 0.01, 2);
		}
		this.remainingBattery = remainingBattery;
		this.cpuLoad = Tools.round(load * 0.1, 1);
	}
	/** Returns the battery level (V) or -1 if unknown. */
	public synchronized double getVoltage() {
		return this.voltage;
	}
	/** Returns the battery current (A) or -1 if unknown. */
	public synchronized double getCurrent() {
		return this.current;
	}
	/** Returns the battery remaining level (%) or -1 if unknown.
	 * <p>100% is the level when started, even if the battery was not fully charged!. */
	public synchronized int getRemainingBattery() {
		return this.remainingBattery;
	}
	/** Returns the CPU load (%) or -1 if unknown. */
	public synchronized double getCPULoad() {
		return this.cpuLoad;
	}
	
	/** Gets the current status: voltage(V), current (mAh), cpu load (%), remaining battery (%). */
	public synchronized Quartet<Double, Double, Integer, Double> getStatus() {
		return Quartet.with(this.voltage, this.current, this.remainingBattery, this.cpuLoad);
	}
	
	public String toString() {
		return "Status: " + String.format("%.3f", this.voltage) + " V, " + String.format("%.2f", this.current)
		+ " A, battery " + this.remainingBattery + " %, CPUload " + String.format("%.1f", this.cpuLoad) + " %";
	}
}