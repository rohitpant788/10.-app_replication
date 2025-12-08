package com.rohit.refdata.repository;

import com.rohit.refdata.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, String> {
}
