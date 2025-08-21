package com.x64dev.watcher.controllers;

import com.x64dev.watcher.models.ApiResponse;
import com.x64dev.watcher.models.SiteContainer;
import com.x64dev.watcher.service.AuxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AuxController {

    @Autowired
    private AuxService auxService;

    @GetMapping("/sites")
    public ResponseEntity<ApiResponse<List<SiteContainer>>> getAvailableSites(){
       var sites = auxService.getAvailableSites();
       var response = new ApiResponse<List<SiteContainer>>();
       response.setMessage("Sites Available");
       response.setData(sites);
       return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
