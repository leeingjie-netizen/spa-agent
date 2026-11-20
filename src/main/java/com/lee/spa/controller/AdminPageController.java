package com.lee.spa.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping({"/admin", "/admin/"})
    public String administratorPage() { return "redirect:/admin/index.html"; }
}
