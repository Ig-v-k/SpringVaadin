package com.full_webapp.vsapp;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import lombok.extern.java.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jdk.nashorn.internal.objects.NativeMath.log;

/*
 * 	Main
 */
@SpringBootApplication
public class VsappApplication {
  public static void main(String[] args) {
	SpringApplication.run(VsappApplication.class, args);
  }
}

/*
 * 	UI
 */
@Route("")
@Log
class MainView extends VerticalLayout {
  private static final long serialVersionUID = 1L;
  private final ContactService contactService;

  private final Grid<Contact> grid = new Grid<>();
  private final TextField filterText = new TextField();

  public MainView(ContactService contactService) {
	this.contactService = contactService;
	addClassName("list-view");
	setSizeFull();
	configureFilter();
	configureGrid();

	add(filterText, grid);
	updateList();
  }

  private void configureGrid() {
	grid.addClassName("contact-grid");
	grid.setSizeFull();
//	grid.setItems(contactService.findAll());
//	grid.setItems(Arrays.asList(contactRepository.getOne(1L), contactRepository.getOne(2L))); // <--- Error: No Session
//	grid.setColumns("firstName", "lastName", "email", "status"); // <--- Error: IllegalStateException ... cannot access with mod. 'public'

	grid.addColumn(Contact::getFirstName).setHeader("First Name").setSortable(true);
	grid.addColumn(Contact::getLastName).setHeader("Last Name").setSortable(true);
	grid.addColumn(Contact::getEmail).setHeader("Email").setSortable(true);
	grid.addColumn(Contact::getStatus).setHeader("Status").setSortable(true);

//	grid.removeColumnByKey("company");
	grid.addColumn(contact -> {
	  Company company = contact.getCompany();
	  return company == null ? "-" : company.getName();
	}).setHeader("Company");

	grid.getColumns().forEach(contactColumn -> contactColumn.setAutoWidth(true));
  }

  private void configureFilter() {
	filterText.setPlaceholder("Filter by name...");
	filterText.setClearButtonVisible(true);
	filterText.setValueChangeMode(ValueChangeMode.LAZY);
	filterText.addValueChangeListener(e -> updateList());
  }

  private void updateList() {
	grid.setItems(contactService.findAll(filterText.getValue()));
  }
}

/*
 * 	Repository
 */
@Repository
interface ContactRepository extends JpaRepository<Contact, Long> {
  @Query("select c from Contact c " +
		"where lower(c.firstName) like lower(concat('%', :searchTerm, '%')) " +
		"or lower(c.lastName) like lower(concat('%', :searchTerm, '%'))") //
  List<Contact> search(@Param("searchTerm") String searchTerm);
}

@Repository
interface CompanyRepository extends JpaRepository<Company, Long> {
}

/*
 * 	Model
 */
@MappedSuperclass
abstract class AbstractEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  private Long id;

  public Long getId() {
	return id;
  }

  public boolean isPersisted() {
	return id != null;
  }

  public AbstractEntity(){}

  @Override
  public int hashCode() {
	if (getId() != null) {
	  return getId().hashCode();
	}
	return super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
	if (this == obj) {
	  return true;
	}
	if (obj == null) {
	  return false;
	}
	if (getClass() != obj.getClass()) {
	  return false;
	}
	AbstractEntity other = (AbstractEntity) obj;
	if (getId() == null || other.getId() == null) {
	  return false;
	}
	return getId().equals(other.getId());
  }
}

@Entity
class Contact extends AbstractEntity implements Cloneable {

  public enum Status {
	ImportedLead, NotContacted, Contacted, Customer, ClosedLost
  }

  @NotNull
  @NotEmpty
  private String firstName = "";

  @NotNull
  @NotEmpty
  private String lastName = "";

  @ManyToOne
  @JoinColumn(name = "company_id")
  private Company company;

  @Enumerated(EnumType.STRING)
  @NotNull
  private Contact.Status status;

  @Email
  @NotNull
  @NotEmpty
  private String email = "";

  public Contact(){}

  public String getEmail() {
	return email;
  }

  public void setEmail(String email) {
	this.email = email;
  }

  public Status getStatus() {
	return status;
  }

  public void setStatus(Status status) {
	this.status = status;
  }

  public String getLastName() {
	return lastName;
  }

  public void setLastName(String lastName) {
	this.lastName = lastName;
  }

  public String getFirstName() {
	return firstName;
  }

  public void setFirstName(String firstName) {
	this.firstName = firstName;
  }

  public void setCompany(Company company) {
	this.company = company;
  }

  public Company getCompany() {
	return company;
  }

  @Override
  public String toString() {
	return firstName + " " + lastName;
  }

}

@Entity
class Company extends AbstractEntity {
  private String name;

  @OneToMany(mappedBy = "company", fetch = FetchType.EAGER)
  private final List<Contact> employees = new LinkedList<>();

  public Company() {
  }

  public Company(String name) {
	setName(name);
  }

  public String getName() {
	return name;
  }

  public void setName(String name) {
	this.name = name;
  }

  public List<Contact> getEmployees() {
	return employees;
  }
}

/*
 * 	Service
 */
@Service
class GreetService {
  public String greet(String name) {
	if (name == null || name.isEmpty()) {
	  return "Hello anonymous user";
	} else {
	  return "Hello " + name;
	}
  }
}

@Service
class CompanyService {

  private final CompanyRepository companyRepository;

  public CompanyService(CompanyRepository companyRepository) {
	this.companyRepository = companyRepository;
  }

  public List<Company> findAll() {
	return companyRepository.findAll();
  }

}

@Log
@Service
class ContactService {
  private final ContactRepository contactRepository;
  private final CompanyRepository companyRepository;

  ContactService(ContactRepository contactRepository, CompanyRepository companyRepository) {
	this.contactRepository = contactRepository;
	this.companyRepository = companyRepository;
  }

  public List<Contact> findAll() {
	return contactRepository.findAll();
  }

  public List<Contact> findAll(String filterText) {
	if(filterText == null || filterText.isEmpty()) {
	  return contactRepository.findAll();
	} else  {
	  return  contactRepository.search(filterText);
	}
  }

  public long count() {
	return contactRepository.count();
  }

  public void delete(Contact contact) {
	contactRepository.delete(contact);
  }

  public void save(Contact contact) {
	if (contact == null) {
	  log(Level.SEVERE, "Contact is null. Are you sure you have connected your form to the application?");
	  return;
	}
	contactRepository.save(contact);
  }

  @PostConstruct
  public void populateTestData() {

	if (companyRepository.count() == 0) {
	  companyRepository.saveAll(
			Stream.of("Path-Way Electronics", "E-Tech Management", "Path-E-Tech Management")
				  .map(Company::new)
				  .collect(Collectors.toList()));
	}

	if (contactRepository.count() == 0) {
	  Random r = new Random(0);
	  List<Company> companies = companyRepository.findAll();
	  contactRepository.saveAll(
			Stream.of("Gabrielle Patel", "Brian Robinson", "Eduardo Haugen",
				  "Koen Johansen", "Alejandro Macdonald", "Angel Karlsson", "Yahir Gustavsson", "Haiden Svensson",
				  "Emily Stewart", "Corinne Davis", "Ryann Davis", "Yurem Jackson", "Kelly Gustavsson",
				  "Eileen Walker", "Katelyn Martin", "Israel Carlsson", "Quinn Hansson", "Makena Smith",
				  "Danielle Watson", "Leland Harris", "Gunner Karlsen", "Jamar Olsson", "Lara Martin",
				  "Ann Andersson", "Remington Andersson", "Rene Carlsson", "Elvis Olsen", "Solomon Olsen",
				  "Jaydan Jackson", "Bernard Nilsen")
				  .map(name -> {
					String[] split = name.split(" ");
					Contact contact = new Contact();
					contact.setFirstName(split[0]);
					contact.setLastName(split[1]);
					contact.setCompany(companies.get(r.nextInt(companies.size())));
					contact.setStatus(Contact.Status.values()[r.nextInt(Contact.Status.values().length)]);
					String email = (contact.getFirstName() + "." + contact.getLastName() + "@" + contact.getCompany().getName().replaceAll("[\\s-]", "") + ".com").toLowerCase();
					contact.setEmail(email);
					return contact;
				  }).collect(Collectors.toList()));
	}
  }
}

/*
 * 	Type's
 */