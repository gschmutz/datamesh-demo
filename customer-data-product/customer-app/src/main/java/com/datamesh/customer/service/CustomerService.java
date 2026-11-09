package com.datamesh.customer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.datamesh.customer.dto.CustomerDTO;
import com.datamesh.customer.dto.AddressDTO;
import com.datamesh.customer.dto.PhoneDTO;
import com.datamesh.customer.entity.Phone;
import com.datamesh.customer.entity.Address;
import com.datamesh.customer.entity.AddressType;
import com.datamesh.customer.entity.Country;
import com.datamesh.customer.entity.PhoneType;
import com.datamesh.customer.entity.Person;
import com.datamesh.customer.entity.Segment;
import com.datamesh.customer.repository.AddressTypeRepository;
import com.datamesh.customer.repository.CountryRepository;
import com.datamesh.customer.repository.PhoneTypeRepository;
import com.datamesh.customer.repository.SegmentRepository;
import com.datamesh.customer.eventproducer.CustomerStateProducer;
import com.datamesh.customer.repository.CustomerRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final SegmentRepository segmentRepository;
    private final CountryRepository countryRepository;
    private final AddressTypeRepository addressTypeRepository;
    private final PhoneTypeRepository phoneTypeRepository;

    // GuS: added
    private CustomerStateProducer customerStateProducer;

    public CustomerService(CustomerRepository customerRepository, SegmentRepository segmentRepository,
                           CountryRepository countryRepository, AddressTypeRepository addressTypeRepository,
                           PhoneTypeRepository phoneTypeRepository, CustomerStateProducer customerStateProducer) {
        this.customerRepository = customerRepository;
        this.segmentRepository = segmentRepository;
        this.countryRepository = countryRepository;
        this.addressTypeRepository = addressTypeRepository;
        this.phoneTypeRepository = phoneTypeRepository;
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

    /** Looks up the segment by id; unknown ids are rejected. */
    private Segment resolveSegment(Integer id) {
        if (id == null) {
            return null;
        }
        return segmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown segment id: " + id));
    }

    /** Looks up the country by code, creating it on first use. */
    private Country resolveCountry(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return countryRepository.findById(code).orElseGet(() -> countryRepository.save(new Country(code, code)));
    }

    /** Looks up the address type by id; unknown ids are rejected. */
    private AddressType resolveAddressType(Integer id) {
        if (id == null) {
            return null;
        }
        return addressTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown address type id: " + id));
    }

    /** Looks up the phone type by id; unknown ids are rejected. */
    private PhoneType resolvePhoneType(Integer id) {
        if (id == null) {
            return null;
        }
        return phoneTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown phone type id: " + id));
    }

    private Address convert(AddressDTO addressDTO) {
        var address = new Address();
        address.setStreet(addressDTO.street());
        address.setZipCode(addressDTO.zipCode());
        address.setCity(addressDTO.city());
        address.setState(addressDTO.state());
        address.setCountry(resolveCountry(addressDTO.countryCode()));
        address.setType(resolveAddressType(addressDTO.addressTypeId()));
        return address;
    }

    private Phone convert(PhoneDTO phoneDTO) {
        var phone = new Phone();
        phone.setType(resolvePhoneType(phoneDTO.phoneTypeId()));
        phone.setNumber(phoneDTO.number());
        phone.setAvailability(phoneDTO.availability());
        return phone;
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
        person.setPreferredContact(customerDTO.preferredContact());
        person.setSegment(resolveSegment(customerDTO.segmentId()));
        person.setDateOfBirth(customerDTO.dateOfBirth());
        person.setPreferredLanguage(customerDTO.preferredLanguage());
        person.setMarketingConsent(customerDTO.marketingConsent());
        person.setLifecycleStatus(customerDTO.lifecycleStatus());

        // attach addresses and ensure the bidirectional relationship is set
        for (var addressDTO : customerDTO.addresses()) {
            var address = convert(addressDTO);
            person.addAddress(address);
        }

        // attach phone numbers (optional)
        if (customerDTO.phones() != null) {
            for (var phoneDTO : customerDTO.phones()) {
                person.addPhone(convert(phoneDTO));
            }
        }

        person.setOnboardedDate(Instant.now());

        // use the returned instance, it carries the audit timestamps (save may merge into a copy)
        var saved = customerRepository.saveAndFlush(person);

        //Publish the event
        customerStateProducer.send(saved);

        return saved.getId();
    }
}
