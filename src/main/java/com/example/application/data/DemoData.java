package com.example.application.data;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The demo data, generated from {@code benchmark.seed}: the same seed gives the same data, ids
 * included. 200 contacts with unique names, more than the grid renders at once.
 *
 * <p>Generated while the context starts, before the web server answers: the benchmark takes a
 * page that answers as data that is complete.
 */
@Component
public class DemoData {

    public static final List<String> STATUSES = List.of(
            "Imported lead", "Not contacted", "Contacted", "Customer", "Closed (lost)");

    private static final List<String> COMPANIES = List.of(
            "Phillips Van Heusen Corp.", "Avaya Inc.", "Laboratory Corporation of America Holdings",
            "AutoZone, Inc.", "Linens 'n Things Inc.");

    private static final List<String> FIRST_NAMES = List.of(
            "Eula", "Barry", "Eugenia", "Alejandro", "Cora", "Marguerite", "Mildred", "Gene", "Lettie",
            "Mabel", "Jordan", "Marie", "Rose", "Garrett", "Barbara", "Jean", "Jack", "Theodore",
            "Louisa", "Harvey", "Ida", "Oscar", "Nellie", "Walter", "Hattie");

    private static final List<String> LAST_NAMES = List.of(
            "Lane", "Rodriquez", "Selvi", "Miles", "Tesi", "Ishii", "Jacobs", "Goodman", "Bennett",
            "Leach", "Miccinesi", "Parkes", "Gray", "Stokes", "Matthieu", "Rhodes", "Romoli", "Okafor",
            "Lindqvist", "Brennan", "Castillo", "Novak", "Fujita", "Halvorsen", "Mercer");

    private static final int CONTACTS = 200;

    private final ContactRepository contacts;
    private final CompanyRepository companies;
    private final StatusRepository statuses;
    private final long seed;

    public DemoData(ContactRepository contacts, CompanyRepository companies, StatusRepository statuses,
                    @Value("${benchmark.seed:1}") long seed) {
        this.contacts = contacts;
        this.companies = companies;
        this.statuses = statuses;
        this.seed = seed;
    }

    @PostConstruct
    void generate() {
        if (contacts.count() > 0) {
            return;
        }
        Random random = new Random(seed);
        List<Status> savedStatuses = STATUSES.stream().map(name -> statuses.save(new Status(name))).toList();
        List<Company> savedCompanies = COMPANIES.stream().map(name -> {
            Company company = new Company();
            company.setName(name);
            return companies.save(company);
        }).toList();

        List<String[]> names = new ArrayList<>();
        for (String first : FIRST_NAMES) {
            for (String last : LAST_NAMES) {
                names.add(new String[]{first, last});
            }
        }
        Collections.shuffle(names, random);
        for (String[] name : names.subList(0, CONTACTS)) {
            Company company = savedCompanies.get(random.nextInt(savedCompanies.size()));
            Contact contact = new Contact();
            contact.setFirstName(name[0]);
            contact.setLastName(name[1]);
            contact.setEmail((name[0] + "." + name[1]).toLowerCase(Locale.ROOT) + "@" + domainOf(company));
            contact.setCompany(company);
            contact.setStatus(savedStatuses.get(random.nextInt(savedStatuses.size())));
            contacts.save(contact);
        }
    }

    private static String domainOf(Company company) {
        return company.getName().split("[^A-Za-z]")[0].toLowerCase(Locale.ROOT) + ".example";
    }
}
