package com.example.demo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DigitalLoopstation extends Application {

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            44100.0f, 16, 1, true, false
    );

    private static final int MAX_TRACKS = 4;

    private Button recordButton;
    private Button stopRecordButton;
    private Button playAllButton;
    private Button clearAllButton;
    private Button saveProjectButton;
    private Button loadProjectButton;
    private Label statusLabel;
    private VBox tracksContainer;

    private AudioRecorder audioRecorder;
    private List<LoopTrack> loopTracks;
    private boolean isPlayingAll = false;
    private DatabaseManager dbManager;
    private Integer currentProjectId = null;
    private String currentProjectName = null;

    @Override
    public void start(Stage primaryStage) {
        loopTracks = new ArrayList<>();
        dbManager = new DatabaseManager();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #2b2b2b;");

        Label titleLabel = new Label("🎵 Digital Loopstation");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        HBox titleBox = new HBox(titleLabel);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(0, 0, 20, 0));

        VBox controlPanel = createControlPanel();

        tracksContainer = new VBox(10);
        tracksContainer.setPadding(new Insets(20, 0, 0, 0));

        for (int i = 0; i < MAX_TRACKS; i++) {
            LoopTrack track = new LoopTrack(i + 1);
            loopTracks.add(track);
            tracksContainer.getChildren().add(track.getTrackPane());
        }

        ScrollPane scrollPane = new ScrollPane(tracksContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #2b2b2b; -fx-background-color: #2b2b2b;");

        VBox topSection = new VBox(10, titleBox, controlPanel);
        root.setTop(topSection);
        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 850, 750);
        primaryStage.setTitle("Digital Loopstation");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> cleanup());
        primaryStage.show();
    }

    private VBox createControlPanel() {
        VBox panel = new VBox(15);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: #3a3a3a; -fx-background-radius: 10;");

        statusLabel = new Label("Ready to record");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #4CAF50;");

        HBox buttonsBox1 = new HBox(15);
        buttonsBox1.setAlignment(Pos.CENTER);

        recordButton = createStyledButton("🎙️ Record", "#f44336");
        stopRecordButton = createStyledButton("⏹️ Stop", "#FF9800");
        playAllButton = createStyledButton("▶️ Play All", "#4CAF50");
        clearAllButton = createStyledButton("🗑️ Clear All", "#9E9E9E");

        stopRecordButton.setDisable(true);

        recordButton.setOnAction(e -> startRecording());
        stopRecordButton.setOnAction(e -> stopRecording());
        playAllButton.setOnAction(e -> togglePlayAll());
        clearAllButton.setOnAction(e -> clearAllTracks());

        buttonsBox1.getChildren().addAll(recordButton, stopRecordButton, playAllButton, clearAllButton);

        HBox buttonsBox2 = new HBox(15);
        buttonsBox2.setAlignment(Pos.CENTER);

        saveProjectButton = createStyledButton("💾 Save Project", "#2196F3");
        loadProjectButton = createStyledButton("📂 Load Project", "#9C27B0");

        saveProjectButton.setOnAction(e -> saveProject());
        loadProjectButton.setOnAction(e -> loadProject());

        buttonsBox2.getChildren().addAll(saveProjectButton, loadProjectButton);

        panel.getChildren().addAll(statusLabel, buttonsBox1, buttonsBox2);

        return panel;
    }

    private Button createStyledButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 10 20;" +
                        "-fx-background-radius: 5;" +
                        "-fx-cursor: hand;"
        );
        button.setPrefWidth(150);

        button.setOnMouseEntered(e -> button.setOpacity(0.8));
        button.setOnMouseExited(e -> button.setOpacity(1.0));

        return button;
    }

    private void startRecording() {
        LoopTrack availableTrack = loopTracks.stream()
                .filter(track -> !track.hasAudio())
                .findFirst()
                .orElse(null);

        if (availableTrack == null) {
            updateStatus("All tracks are full. Clear a track first.", true);
            return;
        }

        audioRecorder = new AudioRecorder(AUDIO_FORMAT);
        audioRecorder.startRecording();

        recordButton.setDisable(true);
        stopRecordButton.setDisable(false);
        playAllButton.setDisable(true);
        clearAllButton.setDisable(true);

        updateStatus("Recording to Track " + availableTrack.getTrackNumber() + "...", false);
        recordButton.setStyle(recordButton.getStyle() + "-fx-background-color: #d32f2f;");
    }

    private void stopRecording() {
        if (audioRecorder != null) {
            byte[] audioData = audioRecorder.stopRecording();

            if (audioData != null && audioData.length > 0) {
                LoopTrack availableTrack = loopTracks.stream()
                        .filter(track -> !track.hasAudio())
                        .findFirst()
                        .orElse(null);

                if (availableTrack != null) {
                    availableTrack.setAudioData(audioData);
                    updateStatus("Recording saved to Track " + availableTrack.getTrackNumber(), false);
                }
            }
        }

        recordButton.setDisable(false);
        stopRecordButton.setDisable(true);
        playAllButton.setDisable(false);
        clearAllButton.setDisable(false);

        recordButton.setStyle(createStyledButton("🎙️ Record", "#f44336").getStyle());
        audioRecorder = null;
    }

    private void togglePlayAll() {
        if (isPlayingAll) {
            stopAllTracks();
        } else {
            playAllTracks();
        }
    }

    private void playAllTracks() {
        boolean anyTrackPlaying = false;

        for (LoopTrack track : loopTracks) {
            if (track.hasAudio()) {
                track.play();
                anyTrackPlaying = true;
            }
        }

        if (anyTrackPlaying) {
            isPlayingAll = true;
            playAllButton.setText("⏸️ Stop All");
            updateStatus("Playing all tracks...", false);
        } else {
            updateStatus("No tracks to play. Record something first!", true);
        }
    }

    private void stopAllTracks() {
        for (LoopTrack track : loopTracks) {
            track.stop();
        }
        isPlayingAll = false;
        playAllButton.setText("▶️ Play All");
        updateStatus("All tracks stopped", false);
    }

    private void clearAllTracks() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear All Tracks");
        alert.setHeaderText("Are you sure?");
        alert.setContentText("This will delete all recorded loops.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                stopAllTracks();
                for (LoopTrack track : loopTracks) {
                    track.clear();
                }
                currentProjectId = null;
                currentProjectName = null;
                updateStatus("All tracks cleared", false);
            }
        });
    }

    private void saveProject() {
        boolean hasAnyAudio = loopTracks.stream().anyMatch(LoopTrack::hasAudio);
        if (!hasAnyAudio) {
            updateStatus("No audio to save. Record something first!", true);
            return;
        }

        TextInputDialog dialog = new TextInputDialog(currentProjectName != null ? currentProjectName : "");
        dialog.setTitle("Save Project");
        dialog.setHeaderText("Save your loopstation project");
        dialog.setContentText("Project name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (name.trim().isEmpty()) {
                updateStatus("Project name cannot be empty!", true);
                return;
            }

            try {
                if (currentProjectId != null) {
                    dbManager.updateProject(currentProjectId, name, loopTracks);
                    updateStatus("Project '" + name + "' updated successfully!", false);
                } else {
                    currentProjectId = dbManager.saveProject(name, loopTracks);
                    updateStatus("Project '" + name + "' saved successfully!", false);
                }
                currentProjectName = name;
            } catch (SQLException e) {
                updateStatus("Error saving project: " + e.getMessage(), true);
                e.printStackTrace();
            }
        });
    }

    private void loadProject() {
        try {
            List<ProjectInfo> projects = dbManager.getAllProjects();

            if (projects.isEmpty()) {
                updateStatus("No saved projects found!", true);
                return;
            }

            ChoiceDialog<ProjectInfo> dialog = new ChoiceDialog<>(projects.get(0), projects);
            dialog.setTitle("Load Project");
            dialog.setHeaderText("Select a project to load");
            dialog.setContentText("Choose project:");

            Optional<ProjectInfo> result = dialog.showAndWait();
            result.ifPresent(project -> {
                try {
                    stopAllTracks();

                    for (LoopTrack track : loopTracks) {
                        track.clear();
                    }

                    dbManager.loadProject(project.getId(), loopTracks);
                    currentProjectId = project.getId();
                    currentProjectName = project.getName();

                    updateStatus("Project '" + project.getName() + "' loaded successfully!", false);
                } catch (SQLException e) {
                    updateStatus("Error loading project: " + e.getMessage(), true);
                    e.printStackTrace();
                }
            });
        } catch (SQLException e) {
            updateStatus("Error retrieving projects: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    private void updateStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " +
                    (isError ? "#f44336" : "#4CAF50") + ";");
        });
    }

    private void cleanup() {
        if (audioRecorder != null) {
            audioRecorder.stopRecording();
        }
        for (LoopTrack track : loopTracks) {
            track.stop();
        }
        dbManager.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

class DatabaseManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/loopstation_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Guihan26";

    private Connection connection;

    public DatabaseManager() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Database connected successfully!");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Database connection failed!");
            e.printStackTrace();
        }
    }

    public int saveProject(String projectName, List<LoopTrack> tracks) throws SQLException {
        String insertProject = "INSERT INTO projects (name) VALUES (?)";
        PreparedStatement projectStmt = connection.prepareStatement(insertProject, Statement.RETURN_GENERATED_KEYS);
        projectStmt.setString(1, projectName);
        projectStmt.executeUpdate();

        ResultSet rs = projectStmt.getGeneratedKeys();
        int projectId = 0;
        if (rs.next()) {
            projectId = rs.getInt(1);
        }

        saveTracks(projectId, tracks);

        return projectId;
    }

    public void updateProject(int projectId, String projectName, List<LoopTrack> tracks) throws SQLException {
        // Update project name
        String updateProject = "UPDATE projects SET name = ?, last_modified = CURRENT_TIMESTAMP WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(updateProject);
        stmt.setString(1, projectName);
        stmt.setInt(2, projectId);
        stmt.executeUpdate();

        // Delete old tracks
        String deleteTracks = "DELETE FROM tracks WHERE project_id = ?";
        PreparedStatement deleteStmt = connection.prepareStatement(deleteTracks);
        deleteStmt.setInt(1, projectId);
        deleteStmt.executeUpdate();

        // Save new tracks
        saveTracks(projectId, tracks);
    }

    private void saveTracks(int projectId, List<LoopTrack> tracks) throws SQLException {
        String insertTrack = "INSERT INTO tracks (project_id, track_number, audio_data, volume, is_muted) VALUES (?, ?, ?, ?, ?)";

        for (LoopTrack track : tracks) {
            if (track.hasAudio()) {
                PreparedStatement trackStmt = connection.prepareStatement(insertTrack);
                trackStmt.setInt(1, projectId);
                trackStmt.setInt(2, track.getTrackNumber());
                trackStmt.setBytes(3, track.getAudioData());
                trackStmt.setFloat(4, track.getVolume());
                trackStmt.setBoolean(5, track.isMuted());
                trackStmt.executeUpdate();
            }
        }
    }

    public void loadProject(int projectId, List<LoopTrack> tracks) throws SQLException {
        String selectTracks = "SELECT track_number, audio_data, volume, is_muted FROM tracks WHERE project_id = ?";
        PreparedStatement stmt = connection.prepareStatement(selectTracks);
        stmt.setInt(1, projectId);
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
            int trackNumber = rs.getInt("track_number");
            byte[] audioData = rs.getBytes("audio_data");
            float volume = rs.getFloat("volume");
            boolean isMuted = rs.getBoolean("is_muted");

            LoopTrack track = tracks.get(trackNumber - 1);
            track.setAudioData(audioData);
            track.setVolume(volume);
            track.setMuted(isMuted);
        }
    }

    public List<ProjectInfo> getAllProjects() throws SQLException {
        List<ProjectInfo> projects = new ArrayList<>();
        String select = "SELECT id, name, created_at, last_modified FROM projects ORDER BY last_modified DESC";
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(select);

        while (rs.next()) {
            projects.add(new ProjectInfo(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getTimestamp("created_at"),
                    rs.getTimestamp("last_modified")
            ));
        }

        return projects;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

class ProjectInfo {
    private int id;
    private String name;
    private Timestamp createdAt;
    private Timestamp lastModified;

    public ProjectInfo(int id, String name, Timestamp createdAt, Timestamp lastModified) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name + " (Modified: " + lastModified + ")";
    }
}

class AudioRecorder {
    private TargetDataLine targetLine;
    private ByteArrayOutputStream recordedData;
    private Thread recordingThread;
    private volatile boolean isRecording = false;

    public AudioRecorder(AudioFormat format) {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Line not supported");
                return;
            }
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public void startRecording() {
        try {
            recordedData = new ByteArrayOutputStream();
            targetLine.open();
            targetLine.start();
            isRecording = true;

            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isRecording) {
                    int bytesRead = targetLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        recordedData.write(buffer, 0, bytesRead);
                    }
                }
            });
            recordingThread.start();

        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public byte[] stopRecording() {
        isRecording = false;

        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        return recordedData != null ? recordedData.toByteArray() : null;
    }
}

class AudioPlayer {
    private SourceDataLine sourceLine;
    private byte[] audioData;
    private AudioFormat format;
    private Thread playbackThread;
    private volatile boolean isPlaying = false;
    private volatile boolean isMuted = false;
    private volatile float volume = 1.0f;

    public AudioPlayer(byte[] audioData, AudioFormat format) {
        this.audioData = audioData;
        this.format = format;
        initializeLine();
    }

    private void initializeLine() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            sourceLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceLine.open(format);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public void play() {
        if (isPlaying) return;

        isPlaying = true;
        sourceLine.start();

        playbackThread = new Thread(() -> {
            while (isPlaying) {
                try {
                    ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while (isPlaying && (bytesRead = bais.read(buffer)) != -1) {
                        if (!isMuted && volume > 0) {
                            byte[] adjustedBuffer = applyVolume(buffer, bytesRead);
                            sourceLine.write(adjustedBuffer, 0, bytesRead);
                        } else {
                            Thread.sleep(bytesRead * 1000L / (long)(format.getSampleRate() * format.getFrameSize()));
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        playbackThread.start();
    }

    private byte[] applyVolume(byte[] buffer, int length) {
        byte[] adjusted = new byte[length];

        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short)((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            float processedSample = sample * volume;
            processedSample = Math.max(-32768, Math.min(32767, processedSample));

            short finalSample = (short)processedSample;
            adjusted[i] = (byte)(finalSample & 0xFF);
            adjusted[i + 1] = (byte)((finalSample >> 8) & 0xFF);
        }
        return adjusted;
    }

    public void stop() {
        isPlaying = false;
        if (playbackThread != null) {
            try {
                playbackThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (sourceLine != null) {
            sourceLine.stop();
        }
    }

    public void close() {
        stop();
        if (sourceLine != null) {
            sourceLine.close();
        }
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}

class LoopTrack {
    private int trackNumber;
    private byte[] audioData;
    private AudioPlayer audioPlayer;
    private Pane trackPane;

    private Button playButton;
    private Button muteButton;
    private Slider volumeSlider;
    private Button deleteButton;
    private ProgressBar waveformBar;
    private Label trackLabel;

    private boolean isMuted = false;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 1, true, false);

    public LoopTrack(int trackNumber) {
        this.trackNumber = trackNumber;
        createTrackPane();
    }

    private void createTrackPane() {
        HBox mainBox = new HBox(15);
        mainBox.setAlignment(Pos.CENTER_LEFT);
        mainBox.setPadding(new Insets(15));
        mainBox.setStyle("-fx-background-color: #3a3a3a; -fx-background-radius: 8;");

        trackLabel = new Label("Track " + trackNumber);
        trackLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #888; -fx-min-width: 80;");

        waveformBar = new ProgressBar(0);
        waveformBar.setPrefWidth(150);
        waveformBar.setPrefHeight(30);
        waveformBar.setStyle("-fx-accent: #4CAF50;");

        playButton = createTrackButton("▶️");
        playButton.setOnAction(e -> togglePlay());
        playButton.setDisable(true);

        muteButton = createTrackButton("🔊");
        muteButton.setOnAction(e -> toggleMute());
        muteButton.setDisable(true);

        deleteButton = createTrackButton("🗑️");
        deleteButton.setOnAction(e -> clear());
        deleteButton.setDisable(true);

        volumeSlider = new Slider(0, 100, 100);
        volumeSlider.setPrefWidth(100);
        volumeSlider.setDisable(true);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (audioPlayer != null) {
                audioPlayer.setVolume(newVal.floatValue() / 100.0f);
            }
        });

        Label volumeLabel = new Label("Vol:");
        volumeLabel.setStyle("-fx-text-fill: #888;");

        HBox volumeBox = new HBox(5, volumeLabel, volumeSlider);
        volumeBox.setAlignment(Pos.CENTER_LEFT);

        mainBox.getChildren().addAll(trackLabel, waveformBar, playButton, muteButton, volumeBox, deleteButton);
        HBox.setHgrow(waveformBar, Priority.ALWAYS);

        trackPane = mainBox;
    }

    private Button createTrackButton(String text) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: #4a4a4a;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-padding: 8;" +
                        "-fx-background-radius: 5;" +
                        "-fx-cursor: hand;"
        );
        button.setPrefWidth(45);
        button.setOnMouseEntered(e -> button.setOpacity(0.8));
        button.setOnMouseExited(e -> button.setOpacity(1.0));
        return button;
    }

    public void setAudioData(byte[] data) {
        this.audioData = data;
        if (audioPlayer != null) {
            audioPlayer.close();
        }
        audioPlayer = new AudioPlayer(data, AUDIO_FORMAT);

        playButton.setDisable(false);
        muteButton.setDisable(false);
        volumeSlider.setDisable(false);
        deleteButton.setDisable(false);

        trackLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4CAF50; -fx-min-width: 80;");
        waveformBar.setProgress(1.0);
    }

    private void togglePlay() {
        if (audioPlayer == null) return;

        if (audioPlayer.isPlaying()) {
            audioPlayer.stop();
            playButton.setText("▶️");
            waveformBar.setStyle("-fx-accent: #4CAF50;");
        } else {
            audioPlayer.play();
            playButton.setText("⏸️");
            waveformBar.setStyle("-fx-accent: #2196F3;");
        }
    }

    private void toggleMute() {
        if (audioPlayer == null) return;

        isMuted = !isMuted;
        audioPlayer.setMuted(isMuted);
        muteButton.setText(isMuted ? "🔇" : "🔊");
    }

    public void play() {
        if (audioPlayer != null && !audioPlayer.isPlaying()) {
            audioPlayer.play();
            playButton.setText("⏸️");
            waveformBar.setStyle("-fx-accent: #2196F3;");
        }
    }

    public void stop() {
        if (audioPlayer != null && audioPlayer.isPlaying()) {
            audioPlayer.stop();
            playButton.setText("▶️");
            waveformBar.setStyle("-fx-accent: #4CAF50;");
        }
    }

    public void clear() {
        if (audioPlayer != null) {
            audioPlayer.close();
            audioPlayer = null;
        }
        audioData = null;

        playButton.setDisable(true);
        muteButton.setDisable(true);
        volumeSlider.setDisable(true);
        deleteButton.setDisable(true);

        playButton.setText("▶️");
        muteButton.setText("🔊");
        volumeSlider.setValue(100);
        waveformBar.setProgress(0);

        trackLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #888; -fx-min-width: 80;");
        isMuted = false;
    }

    public boolean hasAudio() {
        return audioData != null;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public Pane getTrackPane() {
        return trackPane;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    public float getVolume() {
        return (float) (volumeSlider.getValue() / 100.0);
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void setVolume(float volume) {
        volumeSlider.setValue(volume * 100);
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
        if (audioPlayer != null) {
            audioPlayer.setMuted(muted);
        }
        muteButton.setText(muted ? "🔇" : "🔊");
    }
}