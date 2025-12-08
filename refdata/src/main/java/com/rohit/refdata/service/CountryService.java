package com.rohit.refdata.service;

import com.rohit.refdata.dto.CountryDto;
import com.rohit.refdata.entity.Country;
import com.rohit.refdata.repository.CountryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CountryService {

    private final CountryRepository countryRepository;

    public CountryService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    public List<CountryDto> getAllCountries() {
        List<Country> entities = countryRepository.findAll();

        return entities.stream()
                .map(country -> new CountryDto(country.getCode(), country.getName()))
                .collect(Collectors.toList());
    }
}
