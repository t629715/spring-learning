package com.learning.spring.controller;

import com.learning.spring.mvc.annotation.Controller;
import com.learning.spring.mvc.annotation.RequestMapping;

@RequestMapping("/controller")
@Controller
public class FirstController {
    @RequestMapping("/first")
    public String testController(){
        return "success";
    }
}
