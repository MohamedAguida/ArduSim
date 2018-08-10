package api;

import java.awt.Color;
import java.awt.geom.Point2D;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import api.pojo.StatusPacket;
import main.Param;
import main.Param.SimulatorState;
import pccompanion.logic.PCCompanionParam;
import main.Text;
import main.ArduSimTools;
import sim.board.BoardHelper;
import sim.gui.MainWindow;
import sim.logic.SimParam;

/** This class consists exclusively of static methods that help the developer to validate and show information on screen. */

public class GUI {
	
	/** Sends information to the main window log and console.
	 * <p>The window log is only updated when performing simulations. */
	public static void log(String text) {
		final String res;
		if (Param.simStatus == SimulatorState.TEST_IN_PROGRESS) {
			res = Tools.timeToString(Param.startTime, System.currentTimeMillis())
					+ " " + text;
		} else {
			res = text;
		}
		System.out.println(res);
		System.out.flush();
		// Update GUI only when using simulator and the main window is already loaded
		if (Param.role == Tools.SIMULATOR && MainWindow.buttonsPanel != null && MainWindow.buttonsPanel.logArea != null) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					MainWindow.buttonsPanel.logArea.append(res + "\n");
					int pos = MainWindow.buttonsPanel.logArea.getText().length();
					MainWindow.buttonsPanel.logArea.setCaretPosition( pos );
				}
			});
		}
	}
	
	/** Returns true if verbose logging feature is enabled. */
	public static boolean isVerboseLoggingEnabled() {
		return Param.verboseLogging;
	}
	
	/** Sends information to the main window upper-right corner label when a protocol needs it.
	 * <p>The label is only updated when performing simulations. */
	public static void updateGlobalInformation(final String text) {
		// Update GUI only when using simulator
		if (Param.role == Tools.SIMULATOR) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					MainWindow.buttonsPanel.statusLabel.setText(text);
				}
			});
		}
	}
	
	/** Updates the protocol state on the progress dialog.
	 * <p>The progress dialog is only updated when performing simulations. */
	public static void updateProtocolState(final int numUAV, final String state) {
		// Update GUI only when using simulator
		if (MainWindow.progressDialog != null) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					MainWindow.progressDialog.panels[numUAV].protStateLabel.setText(state);
				}
			});
		}
	}

	/** Program termination when a fatal error happens.
	 * <p>On a real UAV shows the message in console and exits.
	 * <p>On simulation, the message is shown in a dialog, virtual UAVs are stopped and exits. */
	public static void exit(String message) {
		if (Param.role == Tools.SIMULATOR) {
			JOptionPane.showMessageDialog(null, message, Text.FATAL_ERROR, JOptionPane.ERROR_MESSAGE);
			if (Param.simStatus != SimulatorState.CONFIGURING
				&& Param.simStatus != SimulatorState.CONFIGURING_PROTOCOL) {
				ArduSimTools.closeSITL();
			}
		} else {
			System.out.println(Text.FATAL_ERROR + ": " + message);
			System.out.flush();
		}
		System.exit(1);
	}

	/** Warns the user with a dialog when performing simulations. On a real UAV the console is used. */
	public static void warn(String title, String message) {
		if (Param.role == Tools.SIMULATOR) {
			JOptionPane.showMessageDialog(null, message, title, JOptionPane.WARNING_MESSAGE);
		} else {
			System.out.println(title + ": " + message);
			System.out.flush();
		}
	}
	
	/** Locates a UTM point on the screen, using the current screen scale. */
	public static Point2D.Double locatePoint(double inUTMX, double inUTMY) {
		return BoardHelper.locatePoint(inUTMX, inUTMY);
	}
	
	/** Provides the Color associated to a UAV and that should be used to draw protocol elements. */
	public static Color getUAVColor(int numUAV) {
		return SimParam.COLOR[numUAV % SimParam.COLOR.length];
	}
	
	/** Provides a list with the UAVs detected by the PCCompanion to be used by the protocol dialog. */
	public static StatusPacket[] getDetectedUAVs() {
		return PCCompanionParam.connectedUAVs.get();
	}

}
