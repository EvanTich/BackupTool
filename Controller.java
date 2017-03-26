import javafx.animation.PauseTransition;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

public class Controller {

    private Stage primaryStage;

    @FXML
    private ListView<File> files;

    @FXML
    private Button addFiles;

    @FXML
    private Button removeFiles;

    @FXML
    private TextField copyTo;
    private File copyToPath;

    @FXML
    private Button setDrive;

    @FXML
    private Label notifications;
    private PauseTransition notificationEnd;

    public boolean setUp() {
        primaryStage = Main.primaryStage;

        copyToPath = new File("");
        copyTo.setText(copyToPath.getAbsolutePath());

        files.setOnDragDetected(x -> {
            files.startDragAndDrop(TransferMode.ANY);
            x.consume();
        });

        files.setOnDragOver(x -> {
            if(x.getGestureSource() != files && x.getDragboard().hasFiles())
                x.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            x.consume();
        });

        files.setOnDragDropped(x -> {
            Dragboard drag = x.getDragboard();
            if(drag.hasFiles()) {
                // add the dragged files
                addFiles(files, drag.getFiles());
            }

            x.setDropCompleted(true);
            x.consume();
        });

        files.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        addFiles.setOnAction(x -> {
            // be able to copy folders too -> not possible with javafx framework, this sucks
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Choose Files to Add");
            List<File> nFiles = fileChooser.showOpenMultipleDialog(primaryStage);

            Controller.addFiles(files, nFiles);

            x.consume();
        });

        removeFiles.setOnAction(x -> {
            files.getItems().removeAll(files.getSelectionModel().getSelectedItems());

            x.consume();
        });

        setDrive.setOnAction(x -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose Backup Drive");
            File drive = chooser.showDialog(primaryStage);

            if(drive != null && drive.isDirectory()) {
                copyTo.setText(drive.getAbsolutePath());
                copyToPath = drive;
            }

            x.consume();
        });

        copyTo.setOnDragDetected(x -> {
            copyTo.startDragAndDrop(TransferMode.ANY);
            x.consume();
        });

        copyTo.setOnDragOver(x -> {
            if(x.getGestureSource() != copyTo && x.getDragboard().hasFiles() && x.getDragboard().getFiles().get(0).isDirectory())
                x.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            x.consume();
        });

        copyTo.setOnDragDropped(x -> {
            Dragboard drag = x.getDragboard();
            if(drag.hasFiles()) {
                File f = drag.getFiles().get(0);
                if(f.isDirectory()) {
                    copyTo.setText(f.getAbsolutePath());
                    copyToPath = f;
                }
            }

            x.setDropCompleted(true);
            x.consume();
        });

        notificationEnd = new PauseTransition(Duration.seconds(5));
        notificationEnd.setOnFinished(x -> {
            notifications.getParent().setVisible(false);
            notifications.setText("");

        });

        return true;
    }

    @FXML
    public boolean backup() {
        if(files.getItems().size() == 0) {
            setNotification("No files to backup.", "orange");
            return false;
        } else if(copyToPath == null || !copyToPath.isDirectory()) {
            setNotification("No directory to backup to.", "orange");
            return false;
        }

        boolean fullSuccess = true;

        List<File> stuff = files.getItems();
        for(File file : stuff)
            if(!copyDirectory(file, new File(copyToPath, file.getName())))
                fullSuccess = false;

        if(fullSuccess)
            setNotification("Files successfully backed up!", "green");
        else {
            setNotification("One or more files have failed to backup.", "red");
            return false;
        }

        return true;
    }

    @FXML
    public boolean delete() {
        // if user not ok with this or no items to delete
        if(files.getItems().size() == 0) {
            setNotification("No files to delete.", "orange");
            return false;
        } else if(!showWarning()) {
            setNotification("User declined deletion.", "orange");
            return false;
        }

        List<String> names = files.getItems().stream().map(File::getName).collect(Collectors.toList());

        try {
            Files.walkFileTree(copyToPath.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        // check if file was made during backup (so check the names)
                        if(names.contains(file.toFile().getName()))
                            Files.delete(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    if(e != null) {
                        e.printStackTrace();
                        return FileVisitResult.TERMINATE;
                    }

                    try {
                        // don't delete the original folder/file(s)
                        if(names.contains(dir.toFile().getName()))
                            Files.delete(dir);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            setNotification("Files successfully deleted!", "green");

            return true;
        } catch (IOException e) {
            // bad bad bad
            e.printStackTrace();
        }

        setNotification("Failed to delete the selected files.", "red");
        return false;
    }

    @FXML
    public void exit() {
        primaryStage.close();
    }

    @FXML
    public boolean saveSettings() {
        if(files.getItems().size() == 0) {
            setNotification("No settings to save.", "orange");
            return false;
        }

        // saves files to backup, and backup location
        Properties props = new Properties();

        try(FileOutputStream writer = new FileOutputStream("./settings.properties")) {
            String str = files.getItems().toString();
            str = str.substring(1, str.length() - 1);
            props.setProperty("files", str);
            props.setProperty("backup", copyToPath.getAbsolutePath());
            props.store(writer, "These are the saved properties for the Backup jar. If this file is changed then the program may not work properly.");

            setNotification("Settings saved!", "green");

            return true;
        } catch (IOException e) {
            // sucks
        }

        setNotification("Failed to save the settings.", "red");

        return false;
    }

    @FXML
    public boolean loadSettings() {
        Properties props = new Properties();

        try(FileInputStream reader = new FileInputStream("./settings.properties")) {

            props.load(reader);

            ObservableList<File> stuff = files.getItems();
            stuff.removeIf(x -> true);

            for(String file : props.getProperty("files").split(", "))
                stuff.add( new File(file) );

            files.setItems(stuff);

            copyToPath = new File(props.getProperty("backup"));
            copyTo.setText(copyToPath.getAbsolutePath());

            setNotification("Settings loaded!", "green");

            return true;
        } catch (IOException e) {
            // feels bad man
        }

        setNotification("Error loading settings, does the file exist?", "red");

        return false;
    }

    /**
     * I was too lazy writing the delete method, fight me.
     * @return false if the user is not ok with the deletion, true otherwise.
     */
    private boolean showWarning() {
        Alert warning = new Alert(Alert.AlertType.CONFIRMATION);
        warning.setTitle("Delete Confirmation");
        warning.setHeaderText("Are you sure?");
        warning.setContentText(
                "This may cause the unwanted side effect of deleting files of the same name and type " +
                "in any of the subfolders in the backup directory. If you don't understand the last " +
                "statement or do not want this to happen, press Cancel."
        );

        Optional<ButtonType> result = warning.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void setNotification(String text, String color) {
        javafx.scene.Parent parent = notifications.getParent();
        parent.setStyle("-fx-background-color: " + color + ';');
        notifications.setText(text);
        parent.setVisible(true);

        notificationEnd.playFromStart();
    }

    private static boolean copyDirectory(File source, File target) {
        if(source.isDirectory()) {

            if(!target.exists())
                target.mkdirs();

            String[] list = source.list();

            if(list == null)
                return true;

            boolean fullSuccess = true;

            for(String file : list)
                if(!copyDirectory( new File(source, file), new File(target, file) ))
                    fullSuccess = false;

            return fullSuccess;
        } else { // if is file

            try {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException e) {
                // nah
            }
        }

        return false;
    }

    private static void addFiles(ListView<File> listView, List<File> files) {
        if(files != null) {
            ObservableList<File> stuff = listView.getItems();
            stuff.addAll(files);
            listView.setItems(stuff);
        }
    }
}
