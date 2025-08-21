package com.x64dev.watcher.service;

import com.x64dev.watcher.models.SiteContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AuxService {
    @Autowired
    private Environment env;

    public List<SiteContainer> getAvailableSites(){
        List<SiteContainer> sites = new ArrayList<>();

        try(DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(env.getProperty("SITES_BASE_URI")));){
            for(Path path : stream){
                if(Files.isDirectory(path)){
                    String dir = path.getName(path.getNameCount()-1).toString();
                    SiteContainer site = new SiteContainer();
                    site.setName(toCamelCase(dir.replaceAll("[.-]", " ").replace("com", "")));
                    site.setSite(dir);
                    site.setFile(env.getProperty("SITES_BASE_URI")+dir+"/laravel.log");

                    sites.add(site);
                }
            }
        }catch (IOException e){
            log.error("Error while scanning available sites");
        }
        return sites;
    }


    private String toCamelCase(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        String[] words = input.trim().toLowerCase().split("[-_\\s]+");
        StringBuilder result = new StringBuilder(words[0]);

        for (int i = 1; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)))
                        .append(words[i].substring(1));
            }
        }

        return result.toString();
    }
}
