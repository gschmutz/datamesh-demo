package com.datamesh.customer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.datamesh.customer.dto.CustomerDTO;
import com.datamesh.customer.entity.Person;
import com.datamesh.customer.service.CustomerService;

import java.net.URI;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("{customerNumber}")
    public ResponseEntity<Person> getCustomerByNumber(@PathVariable String customerNumber) {
        return ResponseEntity.of(customerService.getCustomer(customerNumber));
    }

    @PostMapping
    //@Bulkhead(name = "onboardcustomer", fallbackMethod = "onboardcustomerError")
    private ResponseEntity<?> onboardCustomer(@RequestBody CustomerDTO customer) {

        String customerNumber = customerService.onboardCustomer(customer);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{customerNumber}").buildAndExpand(customerNumber).toUri();
        return ResponseEntity.created(location).build();
    }

    public ResponseEntity<?> onboardCustomerError(CustomerDTO customer, Throwable throwable) {
        LOGGER.error("onboardCustomerError", throwable);

        return ResponseEntity.status(503).build();
    }
}
