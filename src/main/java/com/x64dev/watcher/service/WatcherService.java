package com.x64dev.watcher.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class WatcherService {
    public List<String> getAvailableSites(){
        var sites = new ArrayList<String>();
        Path directory = Paths.get(System.getenv("SITES_BASE_URI"));
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(directory)){
            for (Path path : stream){
               if(Files.isDirectory(path)){
                   sites.add(path.getFileName().toString());
               }
            }
        }catch (IOException e){
            log.error("Failed to walk the sites directory: {}", e.getMessage(), e);
        }
        return sites;
    }
}
