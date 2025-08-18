package com.x64dev.watcher.models.laravel;

import com.x64dev.watcher.service.LaravelService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class LaravelLogWatcher {
    @Autowired
    LaravelService laravelService;
    private final Path logFilePath;
    private final List<LaravelLogListener> listeners;
    private final ExecutorService executorService;
    private WatchService watchService;
    /**
     * -- GETTER --
     *  Check if the watcher is currently running
     */
    @Getter
    private volatile boolean running = false;
    private long lastFileSize = 0;
    private RandomAccessFile randomAccessFile;

    public LaravelLogWatcher(String logFilePath) throws IOException {
            this.logFilePath = Paths.get(logFilePath);
            this.listeners = new CopyOnWriteArrayList<>();
            this.executorService = Executors.newSingleThreadExecutor();

            // Initialize file size
            File file = new File(logFilePath);
            if (file.exists()) {
                this.lastFileSize = file.length();
            }
    }

    /**
     * Add a listener for log entries
     */
    public void addListener(LaravelLogListener listener) {
        for(LaravelLogListener l : listeners){
            LaravelSessionLogListener sessionLogListener = (LaravelSessionLogListener) l;
            LaravelSessionLogListener sessionLogListenerNew = (LaravelSessionLogListener) listener;
            if(sessionLogListener.getSession().getId().equals(sessionLogListenerNew.getSession().getId())){
                return;
            }
        }
        listeners.add(listener);
    }

    /**
     * Remove a listener
     */
    public void removeListener(WebSocketSession session) {
        for(LaravelLogListener l : listeners){
            LaravelSessionLogListener sessionLogListener = (LaravelSessionLogListener) l;
            if(sessionLogListener.getSession().getId().equals(session.getId())){
                listeners.remove(l);
                return;
            }
        }
    }

    /**
     * Start watching the log file
     */
    public void startWatching() throws IOException {
        try{
            if (running) {
                throw new IllegalStateException("Watcher is already running");
            }

            running = true;
            watchService = FileSystems.getDefault().newWatchService();

            // Register the directory containing the log file
            Path parentDir = logFilePath.getParent();
            if (parentDir == null) {
                parentDir = Paths.get(".");
            }

            parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            // Open file for reading
            randomAccessFile = new RandomAccessFile(logFilePath.toFile(), "r");
            randomAccessFile.seek(lastFileSize);

            // Start watching in a separate thread
            executorService.submit(this::watchForChanges);

            System.out.println("Started watching Laravel log file: " + logFilePath);
        }catch (IOException e){
            log.error("Failed to start watching file: ", e);
        }

    }

    /**
     * Stop watching the log file
     */
    public void stopWatching() throws IOException {
        running = false;

        if (watchService != null) {
            watchService.close();
        }

        if (randomAccessFile != null) {
            randomAccessFile.close();
        }

        executorService.shutdown();
        System.out.println("Stopped watching Laravel log file");
    }

    /**
     * Main watch loop
     */
    private void watchForChanges() {
        try {
            while (running) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    Path changed = (Path) event.context();
                    if (changed.equals(logFilePath.getFileName())) {
                        processFileChanges();
                    }
                }

                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyListenersError(e);
        } catch (Exception e) {
            notifyListenersError(e);
        }
    }

    /**
     * Process new content added to the log file
     */
    private void processFileChanges() {
        try {
            File file = logFilePath.toFile();
            long currentFileSize = file.length();

            if (currentFileSize > lastFileSize) {
                // File has grown, read new content
                randomAccessFile.seek(lastFileSize);

                StringBuilder newContent = new StringBuilder();
                String line;
                while ((line = randomAccessFile.readLine()) != null) {
                    newContent.append(line).append("\n");
                }

                if (!newContent.isEmpty()) {
                    processNewLogContent(newContent.toString());
                }

                lastFileSize = currentFileSize;
            } else if (currentFileSize < lastFileSize) {
                // File was truncated or rotated
                randomAccessFile.close();
                randomAccessFile = new RandomAccessFile(file, "r");
                lastFileSize = 0;

                // Read entire file
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = randomAccessFile.readLine()) != null) {
                    content.append(line).append("\n");
                }

                if (!content.isEmpty()) {
                    processNewLogContent(content.toString());
                }

                lastFileSize = currentFileSize;
            }
        } catch (IOException e) {
            notifyListenersError(e);
        }
    }

    /**
     * Process new log content and notify listeners
     */
    private void processNewLogContent(String content) {
        List<LaravelLogEntry> newEntries = LaravelService.parseLogEntries(content);

        for (LaravelLogEntry entry : newEntries) {
            notifyListenersNewEntry(entry);
        }
    }

    /**
     * Notify all listeners of a new log entry
     */
    private void notifyListenersNewEntry(LaravelLogEntry entry) {
        for (LaravelLogListener listener : listeners) {
            try {
                listener.onLogEntry(entry);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        }
    }

    /**
     * Notify all listeners of an error
     */
    private void notifyListenersError(Exception error) {
        for (LaravelLogListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                System.err.println("Error notifying listener of error: " + e.getMessage());
            }
        }
    }

    /**
     * Get the number of registered listeners
     */
    public int getListenerCount() {
        return listeners.size();
    }
}
