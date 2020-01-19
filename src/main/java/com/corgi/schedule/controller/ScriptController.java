package com.corgi.schedule.controller;

import com.corgi.schedule.service.MapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
/**
 * @author tairanliu
 */
@RestController
@RequestMapping("script")
public class ScriptController {
    @Autowired
    private MapService mapService;

    @GetMapping("init_station")
    public String initStation(@RequestParam("city") String city) {
        return mapService.initCity(city);
    }
}
