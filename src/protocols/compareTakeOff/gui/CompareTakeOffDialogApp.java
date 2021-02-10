package protocols.compareTakeOff.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import main.ArduSimTools;
import main.Text;
import main.sim.logic.SimParam;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class CompareTakeOffDialogApp extends Application {
    @Override
    public void start(Stage stage){
        CompareTakeOffSimProperties properties = new CompareTakeOffSimProperties();
        ResourceBundle resources = null;
        try{
            FileInputStream fis = new FileInputStream(SimParam.protocolParamFile);
            resources = new PropertyResourceBundle(fis);
            fis.close();
        }catch (IOException e){
            ArduSimTools.warnGlobal(Text.LOADING_ERROR, Text.PROTOCOL_PARAMETERS_FILE_NOT_FOUND);
        }
        //load the fxml and the controller
        FXMLLoader loader = new FXMLLoader(getClass().getResource("CompareTakeOffConfigScene.fxml"));
        CompareTakeOffDialogController controller = new CompareTakeOffDialogController(resources,properties,stage);
        loader.setController(controller);
        loader.setResources(resources);

        //set the scene
        stage.setTitle("Compare take off Dialog");
        try{
            stage.setScene(new Scene(loader.load()));
        }catch(IOException e){
            ArduSimTools.warnGlobal(Text.LOADING_ERROR, Text.ERROR_LOADING_FXML);
        }
        stage.setOnCloseRequest(event-> System.exit(0));
        stage.show();
    }
}