package osgi.enroute.trains.person.provider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Component;

import osgi.enroute.trains.passenger.api.Person;
import osgi.enroute.trains.passenger.api.PersonDatabase;

/**
 * 
 */
@Component(name = "osgi.enroute.trains.person")
public class PersonImpl implements PersonDatabase {

	// for now just keep in memory
	// TODO persist this?
	private Map<String, Person> persons = new ConcurrentHashMap<>();
	
	@Override
	public Person register(String email, String firstName, String lastName) {
		return register(email, firstName, lastName, null, null, null, null);
	}
	
	@Override
	public Person register(String email, String firstName, String lastName, 
			String company, String phone,
			String picture, String website) {
		Person p = new Person();
		p.firstName = firstName;
		p.lastName = lastName;
		p.email = email;
		
		p.company = company;
		p.phone  = phone;
		
		p.website = website;
		p.picture = picture;
		
		// TODO what kind of id?
		p.id = ""+persons.size();
		
		persons.put(p.id, p);
		return p;	
	}

	@Override
	public List<Person> getPersons() {
		return persons.values().stream().collect(Collectors.toList());
	}

	@Override
	public Person getPerson(String id) {
		return persons.get(id);
	}

}
