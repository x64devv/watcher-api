package com.x64dev.watcher.controllers;

import com.x64dev.watcher.models.ApiResponse;
import com.x64dev.watcher.service.WatcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/")
public class WatcherController {

    @Autowired
    WatcherService watcherService;
    @CrossOrigin(origins = "*")
    @GetMapping("/sites")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableSites(){
        var sites = watcherService.getAvailableSites();
        ApiResponse<List<String>> resp = new ApiResponse<>();
        resp.setMessage("Available sites");
        resp.setData(sites);
        return new ResponseEntity<>(resp, HttpStatus.OK);
    }
}
