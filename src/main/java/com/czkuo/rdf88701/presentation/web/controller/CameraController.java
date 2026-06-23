package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.camera.CameraModbusService;
import com.czkuo.rdf88701.common.dto.camera.TwoCamerasSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/camera")
@RequiredArgsConstructor
public class CameraController {

    private final CameraModbusService svc;

    @GetMapping("/snapshot")     public TwoCamerasSnapshot snapshot() { return svc.readSnapshot(); }
    @PostMapping("/cam1/first")  public String c1f(){ svc.triggerCam1First();  return "OK"; }
    @PostMapping("/cam1/second") public String c1s(){ svc.triggerCam1Second(); return "OK"; }
    @PostMapping("/cam2/first")  public String c2f(){ svc.triggerCam2First();  return "OK"; }
    @PostMapping("/cam2/second") public String c2s(){ svc.triggerCam2Second(); return "OK"; }
    @PostMapping("/resetAll")    public String rst(){ svc.resetAll();           return "OK"; }
}