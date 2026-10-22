package ru.nsu.naboka.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/start")
public class AppController {

    @GetMapping()
    public String startForm(){
        return "startForm";
    }

    @GetMapping("/search")
    public String searchTicker(@RequestParam String query, Model model){
        //поиск списка тикеров

        model.addAttribute("query", query);
        model.addAttribute("coins" /*, спиcок монет*/);
        return "startForm";
    }

    @GetMapping("/search")
    public String getInformation(){
        //поиск инфы по тикеру
        return "startForm";
    }

}
