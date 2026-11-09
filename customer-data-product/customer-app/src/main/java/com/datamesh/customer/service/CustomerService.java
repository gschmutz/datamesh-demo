package com.datamesh.customer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.datamesh.customer.dto.CustomerDTO;
import com.datamesh.customer.dto.AddressDTO;
import com.datamesh.customer.entity.Address;
import com.datamesh.customer.entity.Person;
import com.datamesh.customer.eventproducer.CustomerStateProducer;
import com.datamesh.customer.repository.CustomerRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    // GuS: added
    private CustomerStateProducer customerStateProducer;

    public CustomerService(CustomerRepository customerRepository, CustomerStateProducer customerStateProducer) {
        this.customerRepository = customerRepository;
        this.customerStateProducer = customerStateProducer;
    }

    /**
     * Get's the customer by customer Number
     *
     * @param customerNumber The number
     * @return The customer if exists
     */
    public Optional<Person> getCustomer(String customerNumber) {
        LOGGER.info("getCustomer({})", customerNumber);

        return customerRepository.findById(customerNumber);
    }

    private Address convert(AddressDTO addressDTO) {
        var address = new Address();
        address.setStreet(addressDTO.street());
        address.setZipCode(addressDTO.zipCode());
        address.setCity(addressDTO.city());
        address.setState(addressDTO.state());
        return address;
    }

    /**
     * Onboard's a new customer, given by the customerDTO parameter.
     * @param customerDTO       the customer object to add
     * @return
     */
    public String onboardCustomer (CustomerDTO customerDTO) {
        LOGGER.info("onboardCustomer({})", customerDTO);
        // build the person
        var person = new Person();
        // assign an id (was previously null which caused Hibernate to error on persist)
        person.setId(customerDTO.id());
        person.setLogin(customerDTO.login());
        person.setActive(customerDTO.active());
        person.setVerified(customerDTO.verified());
        person.setVip(customerDTO.vip());
        person.setFirstName(customerDTO.firstName());
        person.setLastName(customerDTO.lastName());
        person.setEmailAddress(customerDTO.emailAddress());
        person.setPhoneNumber(customerDTO.phoneNumber());
        person.setPreferredContact(customerDTO.preferredContact());
        person.setSegment(customerDTO.segment());
        person.setOrganizationId(customerDTO.organizationId());

        // attach addresses and ensure the bidirectional relationship is set
        for (var addressDTO : customerDTO.addresses()) {
            var address = convert(addressDTO);
            person.addAddress(address);
        }

        person.setOnboardedDate(Instant.now());

        // use the returned instance, it carries the audit timestamps (save may merge into a copy)
        var saved = customerRepository.saveAndFlush(person);

        //Publish the event
        customerStateProducer.send(saved);

        return saved.getId();
    }
}
