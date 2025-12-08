package com.rohit.refdata.controller;

import com.rohit.refdata.dto.CountryDto;
import com.rohit.refdata.service.CountryService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/refdata")
@CrossOrigin(origins = "*") // later you can restrict to your React origin
public class RefDataController {

    private final CountryService countryService;

    public RefDataController(CountryService countryService) {
        this.countryService = countryService;
    }

    @GetMapping("/countries")
    public List<CountryDto> getCountries() {
        return countryService.getAllCountries();
    }
}
