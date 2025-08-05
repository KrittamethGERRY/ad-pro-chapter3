package se233.chapter3.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import se233.chapter3.Launcher;
import se233.chapter3.model.FileFreq;
import se233.chapter3.model.PdfDocument;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class MainViewController {
    private boolean isPopup = false;

    LinkedHashMap<String, List<FileFreq>> uniqueSets;
    @FXML
    private ListView<AbstractMap.SimpleEntry<String, String>> inputListView;
    @FXML
    private Button startButton;
    @FXML
    private ListView listView;
    @FXML
    private MenuItem closeMenu;
    @FXML
    public void initialize() {
        inputListView.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            final boolean isAccepted = db.getFiles().get(0).getName().toLowerCase().endsWith(".pdf");
            if (db.hasFiles() && isAccepted) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });
        inputListView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                String filePath;
                int total_files = db.getFiles().size();
                for (int i = 0; i < total_files; i++) {
                    File file = db.getFiles().get(i);
                    filePath = file.getAbsolutePath();
                    inputListView.getItems().add(new AbstractMap.SimpleEntry<>(file.getName(), filePath));
                }
                event.setDropCompleted(success);
                event.consume();
            }
        });
        inputListView.setCellFactory(listView -> new javafx.scene.control.ListCell<AbstractMap.SimpleEntry<String, String>>() {
            @Override
            protected void updateItem(AbstractMap.SimpleEntry<String, String> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getKey());
                }
            }
        });
        startButton.setOnAction(event -> {
                    Parent bgRoot = Launcher.primaryStage.getScene().getRoot();
                    Task<Void> processTask = new Task<Void>() {
                        @Override
                        public Void call() throws IOException {
                            ProgressIndicator pi = new ProgressIndicator();
                            VBox box = new VBox(pi);
                            box.setAlignment(Pos.CENTER);
                            Launcher.primaryStage.getScene().setRoot(box);
                            ExecutorService executor = Executors.newFixedThreadPool(4);
                            final ExecutorCompletionService<Map<String, FileFreq>> completionService = new ExecutorCompletionService<>(executor);
                            List<AbstractMap.SimpleEntry<String,String>> inputListViewItems = inputListView.getItems();
                            int total_files = inputListViewItems.size();
                            Map<String, FileFreq>[] wordMap = new Map[total_files];
                            for (int i = 0; i < total_files; i++) {
                                try {
                                    String filePath = inputListViewItems.get(i).getValue();
                                    PdfDocument p = new PdfDocument(filePath);
                                    completionService.submit(new WordCountMapTask(p));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            for (int i = 0; i < total_files; i++) {
                                try {
                                    Future<Map<String, FileFreq>> future = completionService.take();
                                    wordMap[i] = future.get();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            try {
                                WordCountReduceTask merger = new WordCountReduceTask(wordMap);
                                Future<LinkedHashMap<String, List<FileFreq>>> future = executor.submit(merger);
                                LinkedHashMap<String, List<FileFreq>> unsortedList = future.get();
                                for (int i = 0; i < total_files; i++) {
                                    uniqueSets = (LinkedHashMap<String, List<FileFreq>>) unsortedList.entrySet().stream().sorted(Map.Entry.<String, List<FileFreq>>comparingByValue((list1, list2) ->
                                                Integer.compare( list1.stream().mapToInt(FileFreq::getFreq).sum(), list2.stream().mapToInt(FileFreq::getFreq).sum())).reversed())
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1, LinkedHashMap::new));
                                }
                                LinkedHashMap<String, List<FileFreq>> tempSet = new LinkedHashMap<>(uniqueSets);
                                uniqueSets.clear();
                                tempSet.forEach((key, value) -> {
                                    String tempKey = ": (";
                                    ArrayList<Integer> freqs = new ArrayList<>();
                                    for (FileFreq num : value) {
                                        freqs.add(num.getFreq());
                                        Collections.sort(freqs);
                                    }
                                    for (int i = freqs.size() - 1 ; i >= 0; i--) {
                                        tempKey += freqs.get(i) + ", ";
                                    }
                                    if (tempKey.endsWith(", ")) {
                                        tempKey = tempKey.substring(0, tempKey.length() - 2);
                                    }
                                    tempKey = tempKey.trim() + ')';
                                    uniqueSets.put(key + tempKey, value);
                                });

                                listView.getItems().addAll(uniqueSets.keySet());
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                executor.shutdown();
                            }
                            return null;
                        }
                    };

                    processTask.setOnSucceeded(e -> {
                        Launcher.primaryStage.getScene().setRoot(bgRoot);
                    });
                    Thread thread = new Thread(processTask);
                    thread.setDaemon(true);
                    thread.start();
                });

        listView.setOnMouseClicked(event -> {
            List<FileFreq> listOfLinks = uniqueSets.get(listView.getSelectionModel().getSelectedItem());
            ListView<FileFreq> popupListView = new ListView<>();
            listOfLinks.sort(Comparator.comparing(FileFreq::getFreq).reversed());
            LinkedHashMap<FileFreq, String> lookupTable = new LinkedHashMap<>();
            for (int i = 0; i < listOfLinks.size(); i++) {
                lookupTable.put(listOfLinks.get(i), listOfLinks.get(i).getPath());
                popupListView.getItems().add(listOfLinks.get(i));
            }
            popupListView.setPrefWidth(Region.USE_COMPUTED_SIZE);
            popupListView.setPrefHeight(popupListView.getItems().size() * 40);
            popupListView.setOnMouseClicked(e -> {

                Launcher.hs.showDocument("file:///" + lookupTable.get(popupListView.getSelectionModel().getSelectedItem()));
                popupListView.getScene().getWindow().hide();
            });
            Popup popup = new Popup();
            popup.getContent().add(popupListView);
            popup.show(Launcher.primaryStage);
        });

        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                try {
                    Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "Do you want to close the document?");
                    confirmAlert.showAndWait();
                    if (confirmAlert.getResult() == ButtonType.OK) {
                        Runtime.getRuntime().exec("taskkill /f /im Acrobat.exe");
                        new Alert(Alert.AlertType.INFORMATION, "Documents closed").showAndWait();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        closeMenu.setOnAction(e -> {
            System.exit(0);
        });

    }
}
